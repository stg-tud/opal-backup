/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package fpcf
package par

import java.util.concurrent.atomic.AtomicReference

/**
 * Encapsulates the state of a single entity and its property of a specific kind.
 *
 * @note All operations are effectively atomic operations.
 */
sealed trait EPKState {

    /** Returns the current property extension. */
    def eOptionP: SomeEOptionP

    /** Returns `true` if no property has been computed yet; `false` otherwise. */
    final def isEPK: Boolean = eOptionP.isEPK

    /** Returns `true` if this entity/property pair is not yet final. */
    def isRefinable: Boolean

    def isFinal: Boolean

    /** Returns the underlying entity. */
    final def e: Entity = eOptionP.e

    /**
     * Updates the underlying `EOptionP` value.
     *
     * @note This function is only defined if the current `EOptionP` value is not already a
     *       final value. Hence, the client is required to handle (potentially) idempotent updates
     *       and to take care of appropriate synchronization.
     */
    def update(
        newEOptionP: SomeInterimEP,
        c:           OnUpdateContinuation,
        dependees:   Traversable[SomeEOptionP],
        debug:       Boolean
    ): SomeEOptionP

    /**
     * Adds the given E/PK as a depender on this E/PK instance.
     *
     * @note  This operation is idempotent; that is, adding the same EPK multiple times has no
     *        special effect.
     * @note  Adding a depender to a FinalEPK is not supported.
     */
    def addDepender(someEPK: SomeEPK): Unit

    /**
     * Removes the given E/PK from the list of dependers of this EPKState.
     *
     * @note This method is always defined and never throws an exception for convenience purposes.
     */
    def removeDepender(someEPK: SomeEPK): Unit

    def resetDependers(): Set[SomeEPK]

    def lastDependers(): Set[SomeEPK]

    /**
     * Returns the current `OnUpdateComputation` or `null`, if the `OnUpdateComputation` was
     * already triggered. This is an atomic operation. Additionally – in a second step –
     * removes the EPK underlying the EPKState from the the dependees and clears the dependees.
     *
     * @note This method is always defined and never throws an exception.
     */
    def clearOnUpdateComputationAndDependees(): OnUpdateContinuation

    /**
     * Returns `true` if the current `EPKState` has an `OnUpdateComputation` that was not yet
     * triggered.
     *
     * @note The returned value may have changed in the meantime; hence, this method
     *       can/should only be used as a hint.
     */
    def hasPendingOnUpdateComputation: Boolean

    /**
     * Returns `true` if and only if this EPKState has dependees.
     *
     * @note The set of dependees is only update when a property computation result is processed
     *       and there exists, w.r.t. an Entity/Property Kind pair, always at most one
     *       `PropertyComputationResult`.
     */
    def hasDependees: Boolean

    /**
     * Returns the current set of depeendes. Defined if and only if this `EPKState` is refinable.
     *
     * @note The set of dependees is only update when a property computation result is processed
     *       and there exists, w.r.t. an Entity/Property Kind pair, always at most one
     *       `PropertyComputationResult`.
     */
    def dependees: Traversable[SomeEOptionP]

}

/**
 *
 * @param eOptionPAR An atomic reference holding the current property extension; we need to
 *         use an atomic reference to enable concurrent update operations as required
 *         by properties computed using partial results.
 *         The referenced `EOptionP` is never null.
 * @param cAR The on update continuation function; null if triggered.
 * @param dependees The dependees; never updated concurrently.
 */
final class InterimEPKState(
        var eOptionP:            SomeEOptionP,
        val cAR:                 AtomicReference[OnUpdateContinuation],
        @volatile var dependees: Traversable[SomeEOptionP],
        var dependersAR:         AtomicReference[Set[SomeEPK]]
) extends EPKState {

    assert(eOptionP.isRefinable)

    override def isRefinable: Boolean = true
    override def isFinal: Boolean = false

    override def addDepender(someEPK: SomeEPK): Unit = {
        val dependersAR = this.dependersAR
        if (dependersAR == null)
            return ;

        var prev, next: Set[SomeEPK] = null
        do {
            prev = dependersAR.get()
            next = prev + someEPK
        } while (!dependersAR.compareAndSet(prev, next))
    }

    override def removeDepender(someEPK: SomeEPK): Unit = {
        val dependersAR = this.dependersAR
        if (dependersAR == null)
            return ;

        var prev, next: Set[SomeEPK] = null
        do {
            prev = dependersAR.get()
            next = prev - someEPK
        } while (!dependersAR.compareAndSet(prev, next))
    }

    override def lastDependers(): Set[SomeEPK] = {
        val dependers = dependersAR.get()
        dependersAR = null
        dependers
    }

    override def clearOnUpdateComputationAndDependees(): OnUpdateContinuation = {
        val c = cAR.getAndSet(null)
        dependees = Nil
        c
    }

    override def hasPendingOnUpdateComputation: Boolean = cAR.get() != null

    override def update(
        eOptionP:  SomeInterimEP,
        c:         OnUpdateContinuation,
        dependees: Traversable[SomeEOptionP],
        debug:     Boolean
    ): SomeEOptionP = {
        val oldEOptionP = this.eOptionP
        if (debug) oldEOptionP.checkIsValidPropertiesUpdate(eOptionP, dependees)

        this.eOptionP = eOptionP

        val oldOnUpdateContinuation = cAR.getAndSet(c)
        assert(oldOnUpdateContinuation == null)

        assert(this.dependees.isEmpty)
        this.dependees = dependees

        oldEOptionP
    }

    override def hasDependees: Boolean = dependees.nonEmpty

    override def toString: String = {
        "InterimEPKState("+
            s"eOptionP=${eOptionPAR.get},"+
            s"<hasOnUpdateComputation=${cAR.get() != null}>,"+
            s"dependees=$dependees,"+
            s"dependers=${dependersAR.get()})"
    }
}

final class FinalEPKState(override val eOptionP: SomeEOptionP) extends EPKState {

    override def isRefinable: Boolean = false
    override def isFinal: Boolean = true

    override def update(newEOptionP: SomeInterimEP, debug: Boolean): SomeEOptionP = {
        throw new UnknownError(s"the final property $eOptionP can't be updated to $newEOptionP")
    }

    override def resetDependers(): Set[SomeEPK] = {
        throw new UnknownError(s"the final property $eOptionP can't have dependers")
    }

    override def lastDependers(): Set[SomeEPK] = {
        throw new UnknownError(s"the final property $eOptionP can't have dependers")
    }

    override def addDepender(epk: SomeEPK): Unit = {
        throw new UnknownError(s"final properties can't have dependers")
    }

    override def removeDepender(someEPK: SomeEPK): Unit = { /* There is nothing to do! */ }

    override def clearOnUpdateComputationAndDependees(): OnUpdateContinuation = {
        null
    }

    override def dependees: Traversable[SomeEOptionP] = {
        throw new UnknownError("final properties don't have dependees")
    }

    override def hasDependees: Boolean = false

    override def setOnUpdateComputationAndDependees(
        c:         OnUpdateContinuation,
        dependees: Traversable[SomeEOptionP]
    ): Unit = {
        throw new UnknownError("final properties can't have \"OnUpdateContinuations\"")
    }

    override def hasPendingOnUpdateComputation: Boolean = false

    override def toString: String = s"FinalEPKState(finalEP=$eOptionP)"
}

object EPKState {

    def apply(finalEP: SomeFinalEP): EPKState = new FinalEPKState(finalEP)

    def apply(eOptionP: SomeEOptionP): EPKState = {
        new InterimEPKState(
            new AtomicReference[SomeEOptionP](eOptionP),
            new AtomicReference[OnUpdateContinuation]( /*null*/ ),
            Nil,
            new AtomicReference[Set[SomeEPK]](Set.empty)
        )
    }

    def apply(
        eOptionP:  SomeEOptionP,
        c:         OnUpdateContinuation,
        dependees: Traversable[SomeEOptionP]
    ): EPKState = {
        new InterimEPKState(
            new AtomicReference[SomeEOptionP](eOptionP),
            new AtomicReference[OnUpdateContinuation](c),
            dependees,
            new AtomicReference[Set[SomeEPK]](Set.empty)
        )
    }

    def unapply(epkState: EPKState): Some[SomeEOptionP] = Some(epkState.eOptionP)

}