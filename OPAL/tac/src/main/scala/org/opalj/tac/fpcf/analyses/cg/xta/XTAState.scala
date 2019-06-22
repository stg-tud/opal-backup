/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package cg
package xta

import scala.collection.mutable

import org.opalj.collection.immutable.UIDSet
import org.opalj.fpcf.Entity
import org.opalj.fpcf.EOptionP
import org.opalj.fpcf.Property
import org.opalj.br.DefinedMethod
import org.opalj.br.Field
import org.opalj.br.Method
import org.opalj.br.ObjectType
import org.opalj.br.fpcf.properties.cg.Callees
import org.opalj.br.fpcf.properties.cg.InstantiatedTypes
import org.opalj.br.ArrayType
import org.opalj.br.ReferenceType
import org.opalj.tac.fpcf.properties.TACAI

/**
 * Manages the state used by the [[XTACallGraphAnalysis]].
 *
 * @author Andreas Bauer
 */
class XTAState(
        override val method:                       DefinedMethod,
        override protected[this] var _tacDependee: EOptionP[Method, TACAI],
        // TODO AB: is that even a dependee?? we build this ourselves per-method!
        // maybe if other analyses update this, it is one? E.g. InstantiatedTypesAnalysis due to constructors
        private[this] var _ownInstantiatedTypesDependee: EOptionP[DefinedMethod, InstantiatedTypes],

        // dependees for callees ...
        // we need these to find potential new data flows
        // TODO AB I'm quite sure we only need callees

        // TODO AB Can this depencency become final?
        // TODO AB Maybe only if there are no possible additional virtual call sites?
        private[this] var _calleeDependee: EOptionP[DefinedMethod, Callees],

        // Field stuff
        // TODO AB optimize...
        private[this] var _readFields:    Set[Field],
        private[this] var _writtenFields: Set[Field],
        // we only need type updates of fields the method READS
        private[this] var _readFieldTypeDependees: mutable.Map[Field, EOptionP[Field, InstantiatedTypes]],

        // Array stuff
        val methodWritesArrays: Boolean,
        val methodReadsArrays:  Boolean
) extends CGState {

    // TODO AB more efficient data type for this?
    // TODO AB there is possibly a good way to optimize this away since we create all new callees ourselves
    // TODO AB maybe we also need to store the PC of the callsite here.
    private[this] var _seenCallees: mutable.Set[DefinedMethod] = mutable.Set.empty

    // TODO AB is there a better data type for this?
    private[this] var _calleeSeenTypes: mutable.LongMap[Int] = mutable.LongMap.empty

    // TODO AB These should be removed once they're final, right?
    // TODO AB Can they become final? Probably not!
    // TODO AB Does this have to be a map?
    private[this] var _calleeInstantiatedTypesDependees: mutable.Map[DefinedMethod, EOptionP[DefinedMethod, InstantiatedTypes]] = mutable.Map.empty

    // functionally the same as calleeSeenTypes, but Field does not have an ID we can use
    private[this] var _readFieldSeenTypes: mutable.Map[Field, Int] = mutable.Map.empty

    // Array stuff
    // Note: ArrayType uses a cache internally, so identical array types will be represented by the same object.
    private[this] var _readArraysTypeDependees: mutable.Map[ArrayType, EOptionP[ArrayType, InstantiatedTypes]] = mutable.Map.empty
    private[this] var _readArraysSeenTypes: mutable.Map[ArrayType, Int] = mutable.Map.empty

    // TODO AB: dependency to InstantiatedTypes of fields it reads --> update own set on update
    // TODO AB: store fields it writes --> update types when own types receive an update

    private[this] val _virtualCallSites: mutable.LongMap[mutable.Set[CallSiteT]] = mutable.LongMap.empty

    /////////////////////////////////////////////
    //                                         //
    //          instantiated types             //
    //                                         //
    /////////////////////////////////////////////

    // NOTE AB "own instantiated types": is the method's set of available

    def updateOwnInstantiatedTypesDependee(
        ownInstantiatedTypesDependee: EOptionP[DefinedMethod, InstantiatedTypes]
    ): Unit = {
        _ownInstantiatedTypesDependee = ownInstantiatedTypesDependee
    }

    def ownInstantiatedTypesDependee(): Option[EOptionP[DefinedMethod, InstantiatedTypes]] = {
        if (_ownInstantiatedTypesDependee.isRefinable)
            Some(_ownInstantiatedTypesDependee)
        else
            None
    }

    def ownInstantiatedTypesUB: UIDSet[ReferenceType] = {
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

    // Callee stuff

    def calleeDependee(): Option[EOptionP[DefinedMethod, Callees]] = {
        if (_calleeDependee.isRefinable) {
            Some(_calleeDependee)
        } else {
            None
        }
    }

    def updateCalleeDependee(calleeDependee: EOptionP[DefinedMethod, Callees]): Unit = {
        _calleeDependee = calleeDependee
    }

    def seenCallees: Set[DefinedMethod] = {
        // TODO AB not efficient?
        _seenCallees.toSet
    }

    def updateSeenCallees(newCallees: Set[DefinedMethod]): Unit = {
        _seenCallees ++= newCallees
    }

    def calleeSeenTypes(callee: DefinedMethod): Int = {
        _calleeSeenTypes(callee.id.toLong)
    }

    def updateCalleeSeenTypes(callee: DefinedMethod, numberOfTypes: Int): Unit = {
        assert(numberOfTypes >= _calleeSeenTypes.getOrElse(callee.id.toLong, 0))
        _calleeSeenTypes.update(callee.id.toLong, numberOfTypes)
    }

    def updateCalleeInstantiatedTypesDependee(
        eps: EOptionP[DefinedMethod, InstantiatedTypes]
    ): Unit = {
        _calleeInstantiatedTypesDependees.update(eps.e, eps)
    }

    // Field stuff

    def writtenFields: Set[Field] = _writtenFields

    def fieldIsRead(f: Field): Boolean = _readFields.contains(f)
    def fieldIsWritten(f: Field): Boolean = _writtenFields.contains(f)

    def updateAccessedFieldInstantiatedTypesDependee(
        eps: EOptionP[Field, InstantiatedTypes]
    ): Unit = {
        _readFieldTypeDependees.update(eps.e, eps)
    }

    def fieldSeenTypes(field: Field): Int = {
        _readFieldSeenTypes.getOrElse(field, 0)
    }

    def updateReadFieldSeenTypes(field: Field, numberOfTypes: Int): Unit = {
        assert(numberOfTypes >= _readFieldSeenTypes.getOrElse(field, 0))
        _readFieldSeenTypes.update(field, numberOfTypes)
    }

    // Array stuff

    def availableArrayTypes: UIDSet[ArrayType] = {
        ownInstantiatedTypesUB collect { case at: ArrayType ⇒ at }
    }

    def updateReadArrayInstantiatedTypesDependee(
        eps: EOptionP[ArrayType, InstantiatedTypes]
    ): Unit = {
        _readArraysTypeDependees.update(eps.e, eps)
    }

    def arrayTypeSeenTypes(arrayType: ArrayType): Int = {
        _readArraysSeenTypes.getOrElse(arrayType, 0)
    }

    def updateArrayTypeSeenTypes(arrayType: ArrayType, numberOfTypes: Int): Unit = {
        assert(numberOfTypes >= arrayTypeSeenTypes(arrayType))
        _readArraysSeenTypes.update(arrayType, numberOfTypes)
    }

    /////////////////////////////////////////////
    //                                         //
    //          virtual call sites             //
    //                                         //
    /////////////////////////////////////////////

    override def hasNonFinalCallSite: Boolean = _virtualCallSites.nonEmpty

    def addVirtualCallSite(objectType: ObjectType, callSite: CallSiteT): Unit = {
        val oldValOpt = _virtualCallSites.get(objectType.id.toLong)
        if (oldValOpt.isDefined)
            oldValOpt.get += callSite
        else {
            _virtualCallSites += (objectType.id.toLong → mutable.Set(callSite))
        }
    }

    def getVirtualCallSites(objectType: ObjectType): scala.collection.Set[CallSiteT] = {
        _virtualCallSites.getOrElse(objectType.id.toLong, scala.collection.Set.empty)
    }

    def removeCallSite(instantiatedType: ObjectType): Unit = {
        _virtualCallSites -= instantiatedType.id.toLong
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
            _calleeInstantiatedTypesDependees.nonEmpty ||
            _readFieldTypeDependees.nonEmpty ||
            _readArraysSeenTypes.nonEmpty
    }

    override def dependees: List[EOptionP[Entity, Property]] = {
        var dependees = super.dependees

        if (ownInstantiatedTypesDependee().isDefined)
            dependees ::= ownInstantiatedTypesDependee().get

        if (calleeDependee().isDefined)
            dependees ::= calleeDependee().get

        if (_calleeInstantiatedTypesDependees.nonEmpty)
            dependees ++= _calleeInstantiatedTypesDependees.values

        if (_readFieldTypeDependees.nonEmpty)
            dependees ++= _readFieldTypeDependees.values

        if (_readArraysTypeDependees.nonEmpty)
            dependees ++= _readArraysTypeDependees.values

        dependees
    }
}
