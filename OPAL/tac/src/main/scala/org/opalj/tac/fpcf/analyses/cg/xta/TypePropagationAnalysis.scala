/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package cg
package xta

import scala.collection.mutable
import scala.collection.mutable.ListBuffer

import org.opalj.log.OPALLogger
import org.opalj.collection.immutable.UIDSet
import org.opalj.fpcf.Entity
import org.opalj.fpcf.EOptionP
import org.opalj.fpcf.EPK
import org.opalj.fpcf.EPS
import org.opalj.fpcf.EUBP
import org.opalj.fpcf.InterimEP
import org.opalj.fpcf.InterimEUBP
import org.opalj.fpcf.InterimPartialResult
import org.opalj.fpcf.InterimUBP
import org.opalj.fpcf.PartialResult
import org.opalj.fpcf.ProperPropertyComputationResult
import org.opalj.fpcf.PropertyBounds
import org.opalj.fpcf.PropertyKind
import org.opalj.fpcf.PropertyStore
import org.opalj.fpcf.Results
import org.opalj.fpcf.SomeEPS
import org.opalj.fpcf.SomePartialResult
import org.opalj.value.ValueInformation
import org.opalj.br.ArrayType
import org.opalj.br.DeclaredMethod
import org.opalj.br.DefinedMethod
import org.opalj.br.Field
import org.opalj.br.Method
import org.opalj.br.ObjectType
import org.opalj.br.ReferenceType
import org.opalj.br.analyses.DeclaredMethodsKey
import org.opalj.br.analyses.SomeProject
import org.opalj.br.analyses.cg.InitialEntryPointsKey
import org.opalj.br.fpcf.BasicFPCFTriggeredAnalysisScheduler
import org.opalj.br.fpcf.FPCFAnalysis
import org.opalj.br.fpcf.properties.cg.Callees
import org.opalj.br.fpcf.properties.cg.Callers
import org.opalj.br.fpcf.properties.cg.InstantiatedTypes
import org.opalj.br.instructions.CHECKCAST
import org.opalj.br.instructions.INVOKESTATIC
import org.opalj.br.Code
import org.opalj.tac.fpcf.properties.TACAI

final class TypePropagationAnalysis private[analyses] (
        val project:     SomeProject,
        selectSetEntity: SetEntitySelector
) extends ReachableMethodAnalysis {

    private[this] val _trace: TypePropagationTrace = new TypePropagationTrace()

    private type State = TypePropagationState

    override def processMethod(definedMethod: DefinedMethod, tacEP: EPS[Method, TACAI]): ProperPropertyComputationResult = {

        val setEntity = selectSetEntity(definedMethod)
        val instantiatedTypesEOptP = propertyStore(setEntity, InstantiatedTypes.key)
        val calleesEOptP = propertyStore(definedMethod, Callees.key)

        _trace.traceInit(definedMethod)

        implicit val state: TypePropagationState =
            new TypePropagationState(definedMethod, setEntity, tacEP, instantiatedTypesEOptP, calleesEOptP)
        implicit val partialResults: ListBuffer[SomePartialResult] = new ListBuffer[SomePartialResult]()

        if (calleesEOptP.hasUBP)
            processCallees(calleesEOptP.ub)
        processTACStatements
        processArrayTypes(state.ownInstantiatedTypes)

        returnResults(partialResults)
    }

    /**
     * Processes the method upon initialization. Finds field/array accesses and wires up dependencies accordingly.
     * @param state
     */
    private def processTACStatements(implicit state: State, partialResults: ListBuffer[SomePartialResult]): Unit = {
        val bytecode = state.method.definedMethod.body.get
        val tac = state.tac
        tac.stmts.foreach {
            case stmt @ Assignment(_, _, expr) if expr.isFieldRead ⇒ {
                val fieldRead = expr.asFieldRead
                if (fieldRead.declaredFieldType.isReferenceType) {
                    // Checkcast optimization. TODO document.
                    val nextInstruction = bytecode.instructions(bytecode.pcOfNextInstruction(stmt.pc))
                    val mostPreciseFieldType =
                        if (nextInstruction.isCheckcast)
                            nextInstruction.asInstanceOf[CHECKCAST].referenceType
                        else
                            fieldRead.declaredFieldType.asReferenceType

                    fieldRead.resolveField match {
                        case Some(f: Field) ⇒
                            registerEntityForBackwardPropagation(f, mostPreciseFieldType)
                        case None ⇒
                            val ef = ExternalField(fieldRead.declaringClass, fieldRead.name, fieldRead.declaredFieldType)
                            registerEntityForBackwardPropagation(ef, mostPreciseFieldType)
                    }
                }
            }
            case fieldWrite: FieldWriteAccessStmt[DUVar[ValueInformation]] ⇒ {
                if (fieldWrite.declaredFieldType.isReferenceType) {
                    fieldWrite.resolveField match {
                        case Some(f: Field) ⇒
                            registerEntityForForwardPropagation(f, Set(f.fieldType.asReferenceType))
                        case None ⇒
                            val ef = ExternalField(fieldWrite.declaringClass, fieldWrite.name, fieldWrite.declaredFieldType)
                            registerEntityForForwardPropagation(ef, Set(ef.declaredFieldType.asReferenceType))
                    }
                }
            }
            case Assignment(_, _, expr) if expr.astID == ArrayLoad.ASTID ⇒ {
                state.methodReadsArrays = true
            }
            case stmt: Stmt[DUVar[ValueInformation]] if stmt.astID == ArrayStore.ASTID ⇒ {
                state.methodWritesArrays = true
            }
            case _ ⇒
        }
    }

    private def c(state: State)(eps: SomeEPS): ProperPropertyComputationResult = eps match {

        case EUBP(e: DefinedMethod, _: Callees) ⇒
            assert(e == state.method)
            _trace.traceCalleesUpdate(e)
            handleUpdateOfCallees(eps.asInstanceOf[EPS[DefinedMethod, Callees]])(state)

        case EUBP(e: SetEntity, t: InstantiatedTypes) if e == state.setEntity ⇒
            _trace.traceTypeUpdate(state.method, e, t.types)
            handleUpdateOfOwnTypeSet(eps.asInstanceOf[EPS[SetEntity, InstantiatedTypes]])(state)

        case EUBP(e: SetEntity, t: InstantiatedTypes) ⇒
            _trace.traceTypeUpdate(state.method, e, t.types)
            handleUpdateOfBackwardPropagationTypeSet(eps.asInstanceOf[EPS[SetEntity, InstantiatedTypes]])(state)

        case _ ⇒
            sys.error("received unexpected update")
    }

    private def handleUpdateOfCallees(eps: EPS[DefinedMethod, Callees])(implicit state: State): ProperPropertyComputationResult = {
        state.updateCalleeDependee(eps)
        implicit val partialResults: ListBuffer[SomePartialResult] = new ListBuffer[SomePartialResult]()
        processCallees(eps.ub)
        returnResults(partialResults)
    }

    private def handleUpdateOfOwnTypeSet(eps: EPS[SetEntity, InstantiatedTypes])(implicit state: State): ProperPropertyComputationResult = {
        val previouslySeenTypes = state.ownInstantiatedTypes.size
        state.updateOwnInstantiatedTypesDependee(eps)
        val unseenTypes = UIDSet(eps.ub.dropOldest(previouslySeenTypes).toSeq: _*)

        implicit val partialResults: ListBuffer[SomePartialResult] = new ListBuffer[SomePartialResult]()
        for (fpe ← state.forwardPropagationEntities) {
            val filters = state.forwardPropagationFilters(fpe)
            val propagation = propagateTypes(fpe, unseenTypes, filters)
            if (propagation.isDefined)
                partialResults += propagation.get
        }

        processArrayTypes(unseenTypes)

        returnResults(partialResults)
    }

    private def handleUpdateOfBackwardPropagationTypeSet(eps: EPS[SetEntity, InstantiatedTypes])(implicit state: State): ProperPropertyComputationResult = {
        val setEntity = eps.e
        val previouslySeenTypes = state.seenTypes(setEntity)
        state.updateBackwardPropagationDependee(eps)
        val unseenTypes = UIDSet(eps.ub.dropOldest(previouslySeenTypes).toSeq: _*)

        val filters = state.backwardPropagationFilters(setEntity)
        val propagationResult = propagateTypes(state.setEntity, unseenTypes, filters.toSet)

        returnResults(propagationResult)
    }

    private def processArrayTypes(unseenTypes: UIDSet[ReferenceType])(implicit state: State, partialResults: ListBuffer[SomePartialResult]): Unit = {
        for (t ← unseenTypes if t.isArrayType; at = t.asArrayType if at.elementType.isReferenceType) {
            if (state.methodWritesArrays) {
                registerEntityForForwardPropagation(at, Set(at.componentType.asReferenceType))
            }
            if (state.methodReadsArrays) {
                registerEntityForBackwardPropagation(at, at.componentType.asReferenceType)
            }
        }
    }

    private def processCallees(callees: Callees)(implicit state: State, partialResults: ListBuffer[SomePartialResult]): Unit = {
        val bytecode = state.method.definedMethod.body.get
        for {
            pc ← callees.callSitePCs
            callee ← callees.callees(pc)
            if !state.isSeenCallee(pc, callee)

            // Special case: Object.<init> is implicitly called as a super call by any method X.<init>.
            // The "this" type X will flow to the type set of Object.<init>. Since Object.<init> is usually
            // part of the external world, the external world type set is then polluted with any types which
            // was constructed anywhere in the program.
            // TODO AB Maybe this case can be handled more gracefully. There is some more info in the paper.
            if !(callee.declaringClassType == ObjectType.Object && callee.name == "<init>")
        } {
            // Some sanity checks ...
            // Methods with multiple defined methods should never appear as callees.
            assert(!callee.hasMultipleDefinedMethods)
            // Instances of DefinedMethod we see should only be those where the method is defined in the class file of
            // the declaring class type (i.e., it is not a DefinedMethod instance of some inherited method).
            assert(!callee.hasSingleDefinedMethod || (callee.declaringClassType == callee.asDefinedMethod.definedMethod.classFile.thisType))

            // Remember callee (with PC) so we don't have to process it again later.
            state.addSeenCallee(pc, callee)

            maybeRegisterMethodForForwardPropagation(callee, pc, bytecode)

            maybeRegisterMethodForBackwardPropagation(callee, pc, bytecode)
        }
    }

    private def maybeRegisterMethodForBackwardPropagation(callee: DeclaredMethod, pc: Int, bytecode: Code)(implicit state: State, partialResults: ListBuffer[SomePartialResult]): Unit = {
        val returnValueIsUsed = {
            val tacIndex = state.tac.pcToIndex(pc)
            val tacInstr = state.tac.instructions(tacIndex)
            tacInstr.isAssignment
        }

        if (returnValueIsUsed) {
            // Internally, generic methods have return type "Object" due to type erasure. In many cases
            // (but not all!), the Java compiler will place the "actual" return type within a checkcast
            // instruction right after the call.
            val mostPreciseReturnType = {
                val nextPc = bytecode.pcOfNextInstruction(pc)
                val nextInstruction = bytecode.instructions(nextPc)
                if (nextInstruction.isCheckcast) {
                    nextInstruction.asInstanceOf[CHECKCAST].referenceType
                } else {
                    callee.descriptor.returnType
                }
            }

            // Return type could also be a basic type (i.e., int). We don't care about those.
            if (mostPreciseReturnType.isReferenceType) {
                registerEntityForBackwardPropagation(callee, mostPreciseReturnType.asReferenceType)
            }
        }
    }

    private def maybeRegisterMethodForForwardPropagation(callee: DeclaredMethod, pc: Int, bytecode: Code)(implicit state: State, partialResults: ListBuffer[SomePartialResult]): Unit = {
        // Pre-process parameter types
        val params = mutable.Set[ReferenceType]()
        for (param ← callee.descriptor.parameterTypes) {
            if (param.isReferenceType) {
                params += param.asReferenceType
            }
        }

        // This is the only place where we can find out whether a VirtualDeclaredMethod is static or not!
        val isStaticCall = bytecode.instructions(pc).isInstanceOf[INVOKESTATIC]
        assert(!callee.hasSingleDefinedMethod || (isStaticCall && callee.asDefinedMethod.definedMethod.isStatic))

        // If the call is not static, we need to take the implicit "this" parameter into account.
        if (!isStaticCall) {
            params += callee.declaringClassType
        }

        // If we do not have any params at this point, there is no forward propagation!
        if (params.isEmpty) {
            return ;
        }

        // TODO AB toSet is not a constant time operation(?)
        registerEntityForForwardPropagation(callee, params.toSet)
    }

    private def registerEntityForForwardPropagation(e: Entity, filters: Set[ReferenceType])(implicit state: State, partialResults: ListBuffer[SomePartialResult]): Unit = {
        // TODO AB If we register a method for both forward and backward propagation, this is called twice.
        // (-> Check if this is a problem for performance and maybe memoize the result?)
        val setEntity = selectSetEntity(e)
        if (setEntity == state.setEntity) {
            return ;
        }

        val filterSetHasChanged = state.registerForwardPropagationEntity(setEntity, filters)
        if (filterSetHasChanged) {
            val propagationResult = propagateTypes(setEntity, state.ownInstantiatedTypes, state.forwardPropagationFilters(setEntity))
            if (propagationResult.isDefined)
                partialResults += propagationResult.get
        }
    }

    private def registerEntityForBackwardPropagation(e: Entity, mostPreciseUpperBound: ReferenceType)(implicit state: State, partialResults: ListBuffer[SomePartialResult]): Unit = {
        val setEntity = selectSetEntity(e)
        if (setEntity == state.setEntity) {
            return ;
        }

        val filter = Set(mostPreciseUpperBound)

        if (!state.backwardPropagationDependeeIsRegistered(setEntity)) {
            val dependee = propertyStore(setEntity, InstantiatedTypes.key)

            state.updateBackwardPropagationDependee(dependee)
            state.updateBackwardPropagationFilters(setEntity, filter)

            if (dependee.hasNoUBP) {
                return ;
            }

            val propagation = propagateTypes(state.setEntity, dependee.ub.types, filter)
            if (propagation.isDefined) {
                partialResults += propagation.get
            }
        } else {
            val filterSetHasChanged = state.updateBackwardPropagationFilters(setEntity, filter)
            if (filterSetHasChanged) {
                // Since the filters were updated, it is possible that types which were previously seen but not
                // propagated are now relevant for back propagation. Therefore, we need to propagate from the
                // entire dependee type set.
                val allDependeeTypes = state.backwardPropagationDependeeInstantiatedTypes(setEntity)
                val propagation = propagateTypes(state.setEntity, allDependeeTypes, filter)
                if (propagation.isDefined) {
                    partialResults += propagation.get
                }
            }
        }
    }

    private def candidateMatchesTypeFilter(candidateType: ReferenceType, filterType: ReferenceType): Boolean = {
        val answer = classHierarchy.isASubtypeOf(candidateType, filterType)

        if (answer.isYesOrNo) {
            // Here, we know for sure that the candidate type is or is not a subtype of the filter type.
            answer.isYes
        } else {
            // If the answer is Unknown, we don't know for sure whether the candidate is a subtype of the filter type.
            // However, ClassHierarchy returns Unknown even for cases where it is very unlikely that this is the case.
            // Therefore, we take some more features into account to make the filtering more precise.

            // Important: This decision is a possible but unlikely cause of unsoundness in the call graph!

            // If the filter type is not a project type (i.e., it is external), we assume that any candidate type
            // is a subtype. This can be any external type or project types for which we have incomplete supertype
            // information.
            // If the filter type IS a project type, we consider the candidate type not to be a subtype since this is
            // very likely to be not the case. For the candidate type, there are two options: Either it is an external
            // type, in which case the candidate type could only be a subtype if project types are available in the
            // external type's project at compile time. This is very unlikely since external types are almost always
            // from libraries (like the JDK) which are not available in the analysis context, and which were almost
            // certainly compiled separately ("Separate Compilation Assumption").
            // The other option is that the candidate is also a project type, in which case we should have gotten a
            // definitive Yes/No answer before. Since we didn't get one, the candidate type probably has a supertype
            // which is not a project type. In that case, the above argument applies similarly.
            val filterTypeIsProjectType = filterType match {
                case ot: ObjectType ⇒ project.isProjectType(ot)
                case at: ArrayType  ⇒ project.isProjectType(at.elementType.asObjectType)
            }

            !filterTypeIsProjectType
        }
    }

    private def propagateTypes[E >: Null <: SetEntity](
        targetSetEntity: E,
        newTypes:        UIDSet[ReferenceType],
        filters:         Set[ReferenceType]
    ): Option[PartialResult[E, InstantiatedTypes]] = {
        assert(newTypes.nonEmpty)

        val filteredTypes = newTypes.filter(nt ⇒ filters.exists(f ⇒ candidateMatchesTypeFilter(nt, f)))

        if (filteredTypes.nonEmpty) {
            _trace.traceTypePropagation(targetSetEntity, filteredTypes)
            val partialResult = PartialResult[E, InstantiatedTypes](
                targetSetEntity,
                InstantiatedTypes.key,
                updateInstantiatedTypes(targetSetEntity, filteredTypes)
            )

            Some(partialResult)
        } else {
            None
        }
    }

    // TODO AB something like this appears in several places; should maybe move to some Utils class
    private def updateInstantiatedTypes[E >: Null <: Entity](
        entity:               E,
        newInstantiatedTypes: UIDSet[ReferenceType]
    )(
        eop: EOptionP[E, InstantiatedTypes]
    ): Option[InterimEP[E, InstantiatedTypes]] = eop match {
        case InterimUBP(ub: InstantiatedTypes) ⇒
            val newUB = ub.updated(newInstantiatedTypes)
            if (newUB.types.size > ub.types.size)
                Some(InterimEUBP(entity, newUB))
            else
                None

        case _: EPK[_, _] ⇒
            val newUB = InstantiatedTypes.apply(newInstantiatedTypes)
            Some(InterimEUBP(entity, newUB))

        case r ⇒ throw new IllegalStateException(s"unexpected previous result $r")
    }

    private def returnResults(
        partialResults: TraversableOnce[SomePartialResult]
    )(implicit state: State): ProperPropertyComputationResult = {
        // Always re-register the continuation. It is impossible for all dependees to be final in XTA/...
        Results(
            InterimPartialResult(state.dependees, c(state)),
            partialResults
        )
    }
}

final class TypePropagationAnalysisScheduler(
        val selectSetEntity: SetEntitySelector
) extends BasicFPCFTriggeredAnalysisScheduler {
    override type InitializationData = Null

    override def triggeredBy: PropertyKind = Callers.key

    override def init(p: SomeProject, ps: PropertyStore): Null = {
        val declaredMethods = p.get(DeclaredMethodsKey)
        val entryPoints = p.get(InitialEntryPointsKey)
        // TODO AB placeholder!
        val initialInstantiatedTypes = UIDSet(ObjectType.String, ArrayType(ObjectType.String))

        // Pre-initialize [Ljava/lang/String;
        ps.preInitialize(ArrayType(ObjectType.String), InstantiatedTypes.key) {
            case _: EPK[_, _] ⇒ InterimEUBP(ArrayType(ObjectType.String), InstantiatedTypes(UIDSet(ObjectType.String)))
            case eps          ⇒ throw new IllegalStateException(s"unexpected property: $eps")
        }

        // TODO AB more sophisticated handling needed for library mode!
        for (ep ← entryPoints; method = declaredMethods(ep)) {
            if (method.name != "main") {
                OPALLogger.warn("xta", "initial type assignment to entry points other than 'main' methods not implemented yet!")(p.logContext)
            } else {
                val setEntity = selectSetEntity(method)

                ps.preInitialize(setEntity, InstantiatedTypes.key) {
                    case _: EPK[_, _] ⇒ InterimEUBP(setEntity, InstantiatedTypes(initialInstantiatedTypes))
                    case eps          ⇒ throw new IllegalStateException(s"unexpected property: $eps")
                }
            }
        }

        null
    }

    override def register(project: SomeProject, propertyStore: PropertyStore, i: Null): FPCFAnalysis = {
        val analysis = new TypePropagationAnalysis(project, selectSetEntity)
        propertyStore.registerTriggeredComputation(Callers.key, analysis.analyze)
        analysis
    }

    override def uses: Set[PropertyBounds] = PropertyBounds.ubs(InstantiatedTypes, Callees, TACAI)

    override def derivesEagerly: Set[PropertyBounds] = Set.empty

    override def derivesCollaboratively: Set[PropertyBounds] = PropertyBounds.ubs(InstantiatedTypes)
}