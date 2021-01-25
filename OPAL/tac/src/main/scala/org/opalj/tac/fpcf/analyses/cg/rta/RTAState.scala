/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package cg
package rta

import scala.collection.mutable

import org.opalj.collection.immutable.UIDSet
import org.opalj.fpcf.EOptionP
import org.opalj.fpcf.SomeEOptionP
import org.opalj.br.DefinedMethod
import org.opalj.br.ObjectType
import org.opalj.br.analyses.SomeProject
import org.opalj.br.Method
import org.opalj.br.fpcf.properties.cg.InstantiatedTypes
import org.opalj.br.ReferenceType
import org.opalj.tac.fpcf.properties.TACAI

/**
 * Manages the state used by the [[RTACallGraphAnalysis]].
 *
 * @author Florian Kuebler
 */
class RTAState(
        override val method:                          DefinedMethod,
        override protected[this] var _tacDependee:    EOptionP[Method, TACAI],
        private[this] var _instantiatedTypesDependee: EOptionP[SomeProject, InstantiatedTypes]
) extends CGState {
    private[this] val _virtualCallSites: mutable.LongMap[mutable.Set[CallSiteT]] = mutable.LongMap.empty

    /////////////////////////////////////////////
    //                                         //
    //          instantiated types             //
    //                                         //
    /////////////////////////////////////////////

    def updateInstantiatedTypesDependee(
        instantiatedTypesDependee: EOptionP[SomeProject, InstantiatedTypes]
    ): Unit = {
        _instantiatedTypesDependee = instantiatedTypesDependee
    }

    def instantiatedTypesDependee(): Option[EOptionP[SomeProject, InstantiatedTypes]] = {
        if (_instantiatedTypesDependee.isRefinable)
            Some(_instantiatedTypesDependee)
        else
            None
    }

    def instantiatedTypesUB: UIDSet[ReferenceType] = {
        if (_instantiatedTypesDependee.hasUBP)
            _instantiatedTypesDependee.ub.types
        else
            UIDSet.empty
    }

    def newInstantiatedTypes(seenTypes: Int): TraversableOnce[ReferenceType] = {
        if (_instantiatedTypesDependee.hasUBP) {
            _instantiatedTypesDependee.ub.dropOldest(seenTypes)
        } else {
            UIDSet.empty
        }
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
        _instantiatedTypesDependee.isRefinable || super.hasOpenDependencies
    }

    override def dependees: Set[SomeEOptionP] = {
        if (instantiatedTypesDependee().isDefined)
            super.dependees + instantiatedTypesDependee().get
        else
            super.dependees
    }
}
