/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package pointsto

import scala.collection.mutable

import org.opalj.collection.immutable.UIDSet
import org.opalj.fpcf.Entity
import org.opalj.fpcf.EOptionP
import org.opalj.fpcf.EPS
import org.opalj.fpcf.Property
import org.opalj.fpcf.PropertyStore
import org.opalj.br.fpcf.cg.properties.Callees
import org.opalj.br.fpcf.pointsto.properties.PointsTo
import org.opalj.br.DeclaredMethod
import org.opalj.br.DefinedMethod
import org.opalj.br.Method
import org.opalj.br.ObjectType
import org.opalj.br.fpcf.cg.properties.NoCallees
import org.opalj.tac.fpcf.analyses.cg.CGState
import org.opalj.tac.fpcf.properties.TACAI

/**
 * Encapsulates the state of the analysis, analyzing a certain method.
 */
class PointsToState private (
        override val method:                       DefinedMethod,
        override protected[this] var _tacDependee: EOptionP[Method, TACAI]
) extends CGState {

    private[this] val _pointsToSets: mutable.Map[Entity, UIDSet[ObjectType]] = mutable.Map.empty

    // todo document
    // if we get an update for e: we have to update all points-to sets for the entities in map(e)
    private[this] val _dependeeToDependers: mutable.Map[Entity, mutable.Set[Entity]] = mutable.Map.empty

    private[this] val _dependerToDependees: mutable.Map[Entity, mutable.Set[Entity]] = mutable.Map.empty

    private[this] val _dependees: mutable.Map[Entity, EOptionP[Entity, PointsTo]] = mutable.Map.empty

    private[this] var _calleesDependee: Option[EOptionP[DeclaredMethod, Callees]] = None

    def callees(ps: PropertyStore): Callees = {
        val calleesProperty = if (_calleesDependee.isDefined) {

            _calleesDependee.get
        } else {
            val calleesProperty = ps(method, Callees.key)
            _calleesDependee = Some(calleesProperty)
            calleesProperty
        }

        if (calleesProperty.isEPK)
            NoCallees
        else calleesProperty.ub
    }

    def updateCalleesDependee(newCallees: EPS[DeclaredMethod, Callees]): Unit = {
        _calleesDependee = Some(newCallees)
    }

    override def dependees: Traversable[EOptionP[Entity, Property]] = {
        tacDependee() ++
            _dependees.values ++
            _calleesDependee.filter(_.isRefinable)
    }

    override def hasOpenDependencies: Boolean = {
        tacDependee().isDefined ||
            _dependees.nonEmpty ||
            (_calleesDependee.isDefined && _calleesDependee.get.isRefinable)
    }

    def addPointsToDependency(
        depender: Entity,
        dependee: EOptionP[Entity, PointsTo]
    ): Unit = {
        _dependeeToDependers.getOrElseUpdate(dependee.e, mutable.Set.empty).add(depender)
        _dependerToDependees.getOrElseUpdate(depender, mutable.Set.empty).add(dependee.e)
        _dependees(dependee.e) = dependee
        assert(_dependees.contains(dependee.e) && _dependeeToDependers.contains(dependee.e))
    }

    def removePointsToDependee(eps: EPS[Entity, PointsTo]): Unit = {
        val dependee = eps.e
        assert(_dependees.contains(dependee))
        _dependees.remove(dependee)
        val dependers = _dependeeToDependers(dependee)
        _dependeeToDependers.remove(dependee)
        for (depender ← dependers) {
            val dependees = _dependerToDependees(depender)
            dependees.remove(dependee)
            if (dependees.isEmpty)
                _dependerToDependees.remove(depender)
        }
    }

    def updatePointsToDependee(eps: EOptionP[Entity, PointsTo]): Unit = {
        _dependees(eps.e) = eps
    }

    def getOrRetrievePointsToEPS(
        dependee: Entity, ps: PropertyStore
    ): EOptionP[Entity, PointsTo] = {
        _dependees.getOrElse(dependee, ps(dependee, PointsTo.key))
    }

    def setOrUpdatePointsToSet(e: Entity, p2s: UIDSet[ObjectType]): Unit = {
        if (_pointsToSets.contains(e)) {
            val newPointsToSet = _pointsToSets(e) ++ p2s
            _pointsToSets(e) = newPointsToSet
        } else {
            _pointsToSets(e) = p2s
        }
    }

    def clearPointsToSet(): Unit = {
        _pointsToSets.clear()
    }

    def pointsToSets: Iterator[(Entity, UIDSet[ObjectType])] = {
        _pointsToSets.iterator
    }

    def dependersOf(dependee: Entity): Traversable[Entity] = _dependeeToDependers(dependee)

    def hasDependees(potentialDepender: Entity): Boolean =
        _dependerToDependees.contains(potentialDepender)

    def addIncompletePointsToInfo(pc: Int): Unit = {
        // Todo: We need a mechanism to mark points-to sets as incomplete
    }

    // todo separate CGState trait
    override def hasNonFinalCallSite: Boolean = ???

}

object PointsToState {
    def apply(
        method:      DefinedMethod,
        tacDependee: EOptionP[Method, TACAI]
    ): PointsToState = {
        new PointsToState(method, tacDependee)
    }
}
