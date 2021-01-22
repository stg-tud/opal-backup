/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package cg
package xta

import org.opalj.br.ClassHierarchy
import org.opalj.br.DeclaredMethod
import org.opalj.br.DefinedMethod
import org.opalj.br.Method
import org.opalj.br.PC
import org.opalj.br.ReferenceType
import org.opalj.br.fpcf.properties.cg.Callees
import org.opalj.br.fpcf.properties.cg.InstantiatedTypes
import org.opalj.collection.immutable.UIDSet
import org.opalj.fpcf.EOptionP
import org.opalj.fpcf.SomeEOptionP
import org.opalj.tac.fpcf.properties.TACAI

import scala.collection.mutable

/**
 * Manages the state of each method analyzed by [[TypePropagationAnalysis]].
 *
 * @param method The method under analysis.
 * @param typeSetEntity The entity which holds the type set of the method.
 * @param _tacDependee Dependee for the three-address code of the method.
 * @param _ownInstantiatedTypesDependee Dependee for the type set of the method.
 * @param _calleeDependee Dependee for the callee property of the method.
 */
final class TypePropagationState(
        override val method:                       DefinedMethod,
        val typeSetEntity:                         TypeSetEntity,
        override protected[this] var _tacDependee: EOptionP[Method, TACAI],

        private[this] var _ownInstantiatedTypesDependee: EOptionP[TypeSetEntity, InstantiatedTypes],
        private[this] var _calleeDependee:               EOptionP[DefinedMethod, Callees]
) extends TACAIBasedAnalysisState {

    var methodWritesArrays: Boolean = false
    var methodReadsArrays: Boolean = false

    /////////////////////////////////////////////
    //                                         //
    //           own types (method)            //
    //                                         //
    /////////////////////////////////////////////

    def updateOwnInstantiatedTypesDependee(eps: EOptionP[TypeSetEntity, InstantiatedTypes]): Unit = {
        _ownInstantiatedTypesDependee = eps
    }

    def ownInstantiatedTypes: UIDSet[ReferenceType] = {
        if (_ownInstantiatedTypesDependee.hasUBP)
            _ownInstantiatedTypesDependee.ub.types
        else
            UIDSet.empty
    }

    def newInstantiatedTypes(seenTypes: Int): TraversableOnce[ReferenceType] = {
        if (_ownInstantiatedTypesDependee.hasUBP) {
            _ownInstantiatedTypesDependee.ub.dropOldest(seenTypes)
        } else {
            UIDSet.empty
        }
    }

    /////////////////////////////////////////////
    //                                         //
    //                 callees                 //
    //                                         //
    /////////////////////////////////////////////

    private[this] var _seenCallees: mutable.Set[(PC, DeclaredMethod)] = mutable.Set.empty

    def isSeenCallee(pc: PC, callee: DeclaredMethod): Boolean = _seenCallees.contains((pc, callee))

    def addSeenCallee(pc: PC, callee: DeclaredMethod): Unit = {
        assert(!isSeenCallee(pc, callee))
        _seenCallees.add((pc, callee))
    }

    def calleeDependee: Option[EOptionP[DefinedMethod, Callees]] = {
        if (_calleeDependee.isRefinable) {
            Some(_calleeDependee)
        } else {
            None
        }
    }

    def updateCalleeDependee(calleeDependee: EOptionP[DefinedMethod, Callees]): Unit = {
        _calleeDependee = calleeDependee
    }

    /////////////////////////////////////////////
    //                                         //
    //           forward propagation           //
    //                                         //
    /////////////////////////////////////////////

    private[this] var _forwardPropagationEntities: mutable.Set[TypeSetEntity] = mutable.Set.empty
    private[this] var _forwardPropagationFilters: mutable.Map[TypeSetEntity, UIDSet[ReferenceType]] = mutable.Map.empty

    def forwardPropagationEntities: Traversable[TypeSetEntity] = _forwardPropagationEntities

    def forwardPropagationFilters(typeSetEntity: TypeSetEntity): UIDSet[ReferenceType] = _forwardPropagationFilters(typeSetEntity)

    /**
     * Registers a new set entity to consider for forward propagation alongside a set of filters. If the
     * set entity was already registered, the new type filters are added to the existing ones.
     *
     * @param typeSetEntity The set entity to register.
     * @param typeFilters Set of types to filter for forward propagation.
     * @return True if the set of filters has changed compared to the ones which were previously known, otherwise
     *         False.
     */
    def registerForwardPropagationEntity(
        typeSetEntity: TypeSetEntity,
        typeFilters:   UIDSet[ReferenceType]
    )(
        implicit
        classHierarchy: ClassHierarchy
    ): Boolean = {
        assert(typeFilters.nonEmpty)
        val alreadyExists = _forwardPropagationEntities.contains(typeSetEntity)
        if (!alreadyExists) {
            val compactedFilters = rootTypes(typeFilters)
            _forwardPropagationEntities += typeSetEntity
            _forwardPropagationFilters += typeSetEntity → compactedFilters
            true
        } else {
            val existingTypeFilters = _forwardPropagationFilters(typeSetEntity)
            val newFilters = rootTypes(existingTypeFilters union typeFilters)
            _forwardPropagationFilters.update(typeSetEntity, newFilters)
            newFilters != existingTypeFilters
        }
    }

    /////////////////////////////////////////////
    //                                         //
    //           backward propagation          //
    //                                         //
    /////////////////////////////////////////////

    private[this] var _backwardPropagationDependees: mutable.Map[TypeSetEntity, EOptionP[TypeSetEntity, InstantiatedTypes]] =
        mutable.Map.empty
    private[this] var _backwardPropagationFilters: mutable.Map[TypeSetEntity, UIDSet[ReferenceType]] = mutable.Map.empty

    def backwardPropagationDependeeInstantiatedTypes(typeSetEntity: TypeSetEntity): UIDSet[ReferenceType] = {
        val dependee = _backwardPropagationDependees(typeSetEntity)
        if (dependee.hasUBP)
            dependee.ub.types
        else
            UIDSet.empty
    }

    def backwardPropagationDependeeIsRegistered(typeSetEntity: TypeSetEntity): Boolean =
        _backwardPropagationDependees.contains(typeSetEntity)

    def backwardPropagationFilters(typeSetEntity: TypeSetEntity): UIDSet[ReferenceType] =
        _backwardPropagationFilters(typeSetEntity)

    def updateBackwardPropagationFilters(
        typeSetEntity: TypeSetEntity,
        typeFilters:   UIDSet[ReferenceType]
    )(
        implicit
        classHierarchy: ClassHierarchy
    ): Boolean = {
        assert(typeFilters.nonEmpty)
        val alreadyExists = _backwardPropagationFilters.contains(typeSetEntity)
        if (!alreadyExists) {
            val compactedFilters = rootTypes(typeFilters)
            _backwardPropagationFilters += typeSetEntity → compactedFilters
            true
        } else {
            val existingTypeFilters = _backwardPropagationFilters(typeSetEntity)
            val newFilters = rootTypes(existingTypeFilters union typeFilters)
            _backwardPropagationFilters.update(typeSetEntity, newFilters)
            newFilters != existingTypeFilters
        }
    }

    def updateBackwardPropagationDependee(eps: EOptionP[TypeSetEntity, InstantiatedTypes]): Unit = {
        _backwardPropagationDependees.update(eps.e, eps)
    }

    def seenTypes(typeSetEntity: TypeSetEntity): Int = {
        val dependee = _backwardPropagationDependees(typeSetEntity)
        if (dependee.hasUBP)
            dependee.ub.numElements
        else
            0
    }

    /////////////////////////////////////////////
    //                                         //
    //      general dependency management      //
    //                                         //
    /////////////////////////////////////////////

    override def hasOpenDependencies: Boolean = {
        super.hasOpenDependencies ||
            _ownInstantiatedTypesDependee.isRefinable ||
            _calleeDependee.isRefinable ||
            _backwardPropagationDependees.nonEmpty
    }

    override def dependees: Set[SomeEOptionP] = {
        var dependees = super.dependees

        dependees += _ownInstantiatedTypesDependee

        if (calleeDependee.isDefined)
            dependees += calleeDependee.get

        // Note: The values are copied here. The "++" operator on List
        // forces immediate evaluation of the map values iterator.
        dependees ++= _backwardPropagationDependees.valuesIterator

        dependees
    }

    /////////////////////////////////////////////
    //                                         //
    //      general helper functions           //
    //                                         //
    /////////////////////////////////////////////

    /**
     * For a given set of reference types, returns all types in the set for which no other type in
     * the set is a supertype.
     *
     * For example: If the set contains types A and B where B is a subtype of A, a single-element
     * set of A is returned.
     *
     * If the type java.lang.Object is in the input set, a single-element set containing just
     * java.lang.Object is returned, since Object is a supertype of all other types.
     */
    // IMPROVE: could be implemented with linear runtime.
    private[this] def rootTypes(
        types: UIDSet[ReferenceType]
    )(
        implicit
        classHierarchy: ClassHierarchy
    ): UIDSet[ReferenceType] = {
        if (types.size <= 1)
            return types;

        types.filter(t1 ⇒ !types.exists(t2 ⇒ t1 != t2 && classHierarchy.isSubtypeOf(t1, t2)))
    }
}
