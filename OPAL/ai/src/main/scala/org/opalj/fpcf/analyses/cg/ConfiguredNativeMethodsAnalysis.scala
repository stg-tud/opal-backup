/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package fpcf
package analyses
package cg

import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.opalj.br.DeclaredMethod
import org.opalj.br.Method
import org.opalj.br.MethodDescriptor
import org.opalj.br.ObjectType
import org.opalj.br.analyses.DeclaredMethods
import org.opalj.br.analyses.DeclaredMethodsKey
import org.opalj.br.analyses.SomeProject
import org.opalj.br.analyses.cg.InitialInstantiatedTypesKey
import org.opalj.collection.immutable.IntTrieSet
import org.opalj.collection.immutable.UIDSet
import org.opalj.fpcf.cg.properties.CallersProperty
import org.opalj.fpcf.cg.properties.InstantiatedTypes
import org.opalj.fpcf.cg.properties.NoCallers
import org.opalj.fpcf.cg.properties.StandardInvokeCalleesImplementation

class ConfiguredNativeMethodsAnalysis private[analyses] (
        final val project: SomeProject
) extends FPCFAnalysis {
    private case class NativeMethodData(
            cf:                String,
            m:                 String,
            desc:              String,
            instantiatedTypes: Option[Seq[String]],
            reachableMethods:  Option[Seq[ReachableMethod]]
    )
    private case class ReachableMethod(cf: String, m: String, desc: String)

    private val nativeMethodData: Map[(String, String, String), (Option[Seq[String]], Option[Seq[ReachableMethod]])] =
        project.config.as[Iterator[NativeMethodData]](
            "org.opalj.fpcf.analysis.RTACallGraphAnalysis.nativeMethods"
        ).map { action ⇒
                (action.cf, action.m, action.desc) →
                    ((action.instantiatedTypes, action.reachableMethods))
            }.toMap

    // todo maybe do this in before schedule
    private[this] val initialInstantiatedTypes: UIDSet[ObjectType] =
        UIDSet(project.get(InitialInstantiatedTypesKey).toSeq: _*)

    private[this] implicit val declaredMethods: DeclaredMethods = project.get(DeclaredMethodsKey)

    def getInstantiatedTypesUB(
        instantiatedTypesEOptP: EOptionP[SomeProject, InstantiatedTypes]
    ): UIDSet[ObjectType] = {
        instantiatedTypesEOptP match {
            case eps: EPS[_, _] ⇒ eps.ub.types
            case _              ⇒ initialInstantiatedTypes
        }
    }

    def analyze(declaredMethod: DeclaredMethod): PropertyComputationResult = {
        propertyStore(declaredMethod, CallersProperty.key) match {
            case FinalP(NoCallers) ⇒
                // nothing to do, since there is no caller
                return NoResult;

            case eps: EPS[_, _] ⇒
                if (eps.ub eq NoCallers) {
                    // we can not create a dependency here, so the analysis is not allowed to create
                    // such a result
                    throw new IllegalStateException("illegal immediate result for callers")
                }
            // the method is reachable, so we analyze it!
        }

        // we only allow defined methods
        if (!declaredMethod.hasSingleDefinedMethod)
            return NoResult;

        val method = declaredMethod.definedMethod

        if (method.isNative)
            return handleNativeMethod(declaredMethod, method);

        NoResult
    }

    /**
     * Handles configured calls and instantiations for a native method.
     */
    def handleNativeMethod(
        declaredMethod: DeclaredMethod,
        m:              Method
    ): PropertyComputationResult = {

        /**
         * Creates partial results for instantiated types given by their FQNs.
         */
        def instantiatedTypesResults(fqns: Seq[String]): Option[ProperPropertyComputationResult] = {
            val instantiatedTypesUB =
                getInstantiatedTypesUB(propertyStore(project, InstantiatedTypes.key))

            val newInstantiatedTypes =
                UIDSet(fqns.map(ObjectType(_)).filterNot(instantiatedTypesUB.contains): _*)

            if (newInstantiatedTypes.nonEmpty)
                Some(PartialResult(
                    p,
                    InstantiatedTypes.key,
                    InstantiatedTypesAnalysis.update(
                        p,
                        newInstantiatedTypes,
                        initialInstantiatedTypes
                    )
                ))
            else
                None
        }

        /**
         * Creates the results for callees and callers properties for the given methods.
         */
        def calleesResults(
            reachableMethods: Seq[ReachableMethod]
        ): List[ProperPropertyComputationResult] = {
            val calleesAndCallers = new CalleesAndCallers()
            for (reachableMethod ← reachableMethods.iterator) {
                val classType = ObjectType(reachableMethod.cf)
                val name = reachableMethod.m
                val descriptor = MethodDescriptor(reachableMethod.desc)
                val callee =
                    declaredMethods(classType, classType.packageName, classType, name, descriptor)
                calleesAndCallers.updateWithCall(declaredMethod, callee, 0)
            }
            val callees =
                new StandardInvokeCalleesImplementation(calleesAndCallers.callees, IntTrieSet.empty)
            Result(declaredMethod, callees) :: calleesAndCallers.partialResultsForCallers
        }

        val methodDataO =
            nativeMethodData.get((m.classFile.thisType.fqn, m.name, m.descriptor.toJVMDescriptor))

        if (methodDataO.isEmpty)
            return NoResult;

        val (instantiatedTypesO, reachableMethodsO) = methodDataO.get

        if (reachableMethodsO.isDefined) {
            val callResults = calleesResults(reachableMethodsO.get)
            if (instantiatedTypesO.isDefined) {
                val typesResult = instantiatedTypesResults(instantiatedTypesO.get)
                Results(callResults ++ typesResult)
            } else Results(callResults)
        } else if (instantiatedTypesO.isDefined) {
            Results(instantiatedTypesResults(instantiatedTypesO.get))
        } else {
            NoResult
        }
    }
}

object EagerConfiguredNativeMethodsAnalysis extends BasicFPCFTriggeredAnalysisScheduler {
    override def uses: Set[PropertyBounds] = Set(
        PropertyBounds.ub(CallersProperty),
        PropertyBounds.ub(InstantiatedTypes)
    )

    override def derivesCollaboratively: Set[PropertyBounds] = Set(
        PropertyBounds.ub(CallersProperty),
        PropertyBounds.ub(InstantiatedTypes)
    )

    override def derivesEagerly: Set[PropertyBounds] = Set.empty

    override def register(
        p: SomeProject, ps: PropertyStore, unused: Null
    ): ConfiguredNativeMethodsAnalysis = {
        val analysis = new ConfiguredNativeMethodsAnalysis(p)

        ps.registerTriggeredComputation(CallersProperty.key, analysis.analyze)

        analysis
    }
}
