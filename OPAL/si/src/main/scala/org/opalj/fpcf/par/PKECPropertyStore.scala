/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package fpcf
package par

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.{Arrays => JArrays}

import scala.collection.JavaConverters._

import org.opalj.log.OPALLogger.{debug ⇒ trace}
import org.opalj.fpcf.PropertyKey.fallbackPropertyBasedOnPKId
import org.opalj.fpcf.PropertyKind.SupportedPropertyKinds

/**
 * A concurrent implementation of the property store which executes the scheduled computations
 * in parallel.
 *
 * The number of threads that is used for parallelizing the computation is determined by: 
 * `NumberOfThreadsForProcessingPropertyComputations`
 *
 * @author Michael Eichberg
 */
final class PKECPropertyStore(
    final val taskManager : TaskManager
    ) extends ParallelPropertyStore { store ⇒

    private[par] implicit def self: PKECPropertyStore = this

    //
    //
    // PARALLELIZATION RELATED FUNCTIONALITY
    //
    //

    import taskManager.prepareThreadPool
    import taskManager.awaitPoolQuiescence
    import taskManager.parallelize
    import taskManager.forkResultHandler
    import taskManager.forkOnUpdateContinuation
    import taskManager.forkLazyPropertyComputation
    import taskManager.schedulePropertyComputation
    import taskManager.cleanUpThreadPool


    // --------------------------------------------------------------------------------------------
    //
    // STATISTICS
    //
    // --------------------------------------------------------------------------------------------

    private[this] var quiescenceCounter = 0
    override def quiescenceCount: Int = quiescenceCounter

    private[this] val scheduledOnUpdateComputationsCounter = new AtomicInteger(0)
    override def scheduledOnUpdateComputationsCount: Int = {
        scheduledOnUpdateComputationsCounter.get()
    }
    private[fpcf] def incrementOnUpdateContinuationsCounter(): Unit = {
        if (debug) scheduledOnUpdateComputationsCounter.incrementAndGet()
    }

    private[this] val scheduledTasksCounter = new AtomicInteger(0)
    override def scheduledTasksCount: Int = scheduledTasksCounter.get()
    private[fpcf] def incrementScheduledTasksCounter(): Unit = {
        if (debug) scheduledTasksCounter.incrementAndGet()
    }

    private[this] val fallbacksUsedForComputedPropertiesCounter = new AtomicInteger(0)
    override def fallbacksUsedForComputedPropertiesCount: Int = {
        fallbacksUsedForComputedPropertiesCounter.get()
    }
    override private[fpcf] def incrementFallbacksUsedForComputedPropertiesCounter(): Unit = {
        if (debug) fallbacksUsedForComputedPropertiesCounter.incrementAndGet()
    }

    //
    //
    // CORE DATA STRUCTURES
    //
    //

    // Per PropertyKind we use one concurrent hash map to store the entities' properties.
    // The value (EPKState) encompasses the current property along with some helper information.
    private[par] val properties: Array[ConcurrentHashMap[Entity, EPKState]] = {
        Array.fill(SupportedPropertyKinds) { new ConcurrentHashMap() }
    }

    /** 
     * Computations that will be triggered when a new property becomes available. 
     * 
     * Please note, that the triggered computations have to be registered strictly before the first
     * computation is started.
     */
    private[par] val triggeredComputations: Array[Array[SomePropertyComputation]] = {
        new Array(SupportedPropertyKinds)
    }

    // --------------------------------------------------------------------------------------------
    //
    // BASIC QUERY METHODS (ONLY TO BE CALLED WHEN THE STORE IS QUIESCENT)
    //
    // --------------------------------------------------------------------------------------------

    override def toString(printProperties: Boolean): String = {
        if (printProperties) {
            val ps = for {
                (entitiesMap, pkId) ← properties.iterator.zipWithIndex.take(PropertyKey.maxId + 1)
            } yield {
                entitiesMap.values().iterator().asScala
                    .map(_.eOptionP.toString.replace("\n", "\n\t"))
                    .toList.sorted
                    .mkString(s"Entities for property key $pkId:\n\t", "\n\t", "\n")
            }
            ps.mkString("PropertyStore(\n\t", "\n\t", "\n)")
        } else {
            s"PropertyStore(properties=${properties.iterator.map(_.size).sum})"
        }
    }

    override def entities(p: SomeEPS ⇒ Boolean): Iterator[Entity] = {
        this.properties.iterator.flatMap { propertiesPerKind ⇒
            propertiesPerKind
                .elements().asScala
                .collect { case EPKState(eps: SomeEPS) if p(eps) ⇒ eps.e }
        }
    }

    override def entities[P <: Property](pk: PropertyKey[P]): Iterator[EPS[Entity, P]] = {
        properties(pk.id)
            .values().iterator().asScala
            .collect { case EPKState(eps: EPS[Entity, P] @unchecked) ⇒ eps }
    }

    override def entities[P <: Property](lb: P, ub: P): Iterator[Entity] = {
        for { EPKState(ELUBP(e, `lb`, `ub`)) ← properties(lb.id).elements().asScala } yield { e }
    }

    override def entitiesWithLB[P <: Property](lb: P): Iterator[Entity] = {
        for { EPKState(ELBP(e, `lb`)) ← properties(lb.id).elements().asScala } yield { e }
    }

    override def entitiesWithUB[P <: Property](ub: P): Iterator[Entity] = {
        for { EPKState(EUBP(e, `ub`)) ← properties(ub.id).elements().asScala } yield { e }
    }

    override def properties[E <: Entity](e: E): Iterator[EPS[E, Property]] = {
        this.properties.iterator.flatMap { map ⇒
            val ePKState = map.get(e)
            if (ePKState != null && ePKState.eOptionP.isEPS)
                Iterator.single(ePKState.eOptionP.asInstanceOf[EPS[E, Property]])
            else
                Iterator.empty
        }

    }

    override def hasProperty(e: Entity, pk: PropertyKind): Boolean = {
        val state = properties(pk.id).get(e)
        state != null && {
            val eOptionP = state.eOptionP
            eOptionP.hasUBP || eOptionP.hasLBP
        }
    }

    override def isKnown(e: Entity): Boolean = {
        properties.exists(propertiesOfKind ⇒ propertiesOfKind.containsKey(e))
    }

    // --------------------------------------------------------------------------------------------
    //
    // CORE IMPLEMENTATION
    //
    // --------------------------------------------------------------------------------------------

    override def doScheduleEagerComputationForEntity[E <: Entity](
        e: E
    )(
        pc: PropertyComputation[E]
    ): Unit = {
        schedulePropertyComputation(e, pc)
    }

    override def doRegisterTriggeredComputation[E <: Entity, P <: Property](
        pk: PropertyKey[P],
        pc: PropertyComputation[E]
    ): Unit = {

        val pkId = pk.id
        val oldComputations: Array[SomePropertyComputation] = triggeredComputations(pkId)
        var newComputations: Array[SomePropertyComputation] = null

        if (oldComputations == null) {
            newComputations = Array[SomePropertyComputation](pc)
        } else {
            newComputations = JArrays.copyOf(oldComputations, oldComputations.length + 1)
            newComputations(oldComputations.length) = pc
        }
        triggeredComputations(pkId) = newComputations

        // Recall that the scheduler has to take care of registering a triggered computation
        // at the correct point in time; i.e., before the first analysis derives a respective
        // value! Hence, there is no need to immediately check that we have to trigger a 
        // computation.
    }

    override def force[E <: Entity, P <: Property](e: E, pk: PropertyKey[P]): Unit = this(e, pk)

    override def doSet(e: Entity, p: Property): Unit = {
        val oldP = properties(p.id).put(e, EPKState(FinalEP(e, p)))
        if (oldP != null) {
            throw new IllegalStateException(s"$e had already the property $oldP")
        }
    }

    override def doPreInitialize[E <: Entity, P <: Property](
        e:  E,
        pk: PropertyKey[P]
    )(
        pc: EOptionP[E, P] ⇒ InterimEP[E, P]
    ): Unit = {
        val pkId = pk.id
        val propertiesOfKind = properties(pkId)

        val newInterimP: SomeInterimEP =
            propertiesOfKind.get(e) match {
                case null  ⇒ pc(EPK(e, pk))
                case state ⇒ pc(state.eOptionP.asInstanceOf[EOptionP[E, P]])
            }
        propertiesOfKind.put(e, EPKState(newInterimP))
    }

    override protected[this] def doApply[E <: Entity, P <: Property](
        epk:  EPK[E, P],
        e:    E,
        pkId: Int
    ): EOptionP[E, P] = {
        val psOfKind = properties(pkId)

        // In the following, we just ensure that we only create a new EPKState object if
        // the chances are high that we need it. Conceptually, we just do a "putIfAbsent"
        val epkState = psOfKind.get(e)
        if (epkState != null) {
            // just return the current value
            return epkState.eOptionP.asInstanceOf[EOptionP[E, P]];
        }
        val oldEPKState = psOfKind.putIfAbsent(e, EPKState(epk) /* eager construction of an EPKState */ )
        if (oldEPKState != null) {
            return oldEPKState.eOptionP.asInstanceOf[EOptionP[E, P]];
        }

        // The entity was not yet known ... and we registered the EPKState object; let's check if:
        //  - we have to trigger a lazy property computation
        //  - we have to compute the fallback because no analysis is scheduled
        val lc = lazyComputations(pkId)
        if (lc != null) {
            forkLazyPropertyComputation(epk, lc.asInstanceOf[PropertyComputation[E]])
        } else if (propertyKindsComputedInThisPhase(pkId)) {
            // TODO Execute or register transformer...
            epk
        } else {
            // ... we have no lazy computation
            // ... the property is also not computed 
            val finalEP = computeFallback[E, P](e, pkId)
            finalUpdate(finalEP, potentiallyIdemPotentUpdate = true)
            finalEP
        }
    }

    // THIS METHOD IS NOT INTENDED TO BE USED BY CPropertyStores- IT IS ONLY MEANT TO
    // BE USED BY EXTERNAL TASKS.
    override def handleResult(r: PropertyComputationResult): Unit = forkResultHandler(r)

    private[par] def removeDependerFromDependees(
        depender:  SomeEPK,
        dependees: Traversable[SomeEOptionP]
    ): Unit = {
        dependees foreach { dependee ⇒
            val dependeeState = properties(dependee.pk.id).get(dependee.e)
            dependeeState.removeDepender(depender)
        }
    }

    private[this] def removeDependerFromDependees(dependerEPK: SomeEPK): Unit = {
        removeDependerFromDependees(
            dependerEPK, 
            properties(dependerEPK.pk.id).get(dependerEPK.e).dependees
        )
    }

    private[this] def notifyDepender(dependerEPK: SomeEPK, eps: SomeEPS): Unit = {
        // FIXME Remove DEPENDER from dependees!!!!
        forkOnUpdateContinuation(dependerEPK, eps.e, eps.pk)
    }

    private[this] def notifyDepender(dependerEPK: SomeEPK, finalEP: SomeFinalEP): Unit = {
        // FIXME Remove DEPENDER from dependees!!!!
        forkOnUpdateContinuation(dependerEPK, finalEP)
    }

    private[this] def finalUpdate(
        finalEP:                     SomeFinalEP,
        potentiallyIdemPotentUpdate: Boolean     = false
    ): Unit = {
        val oldState = properties(finalEP.pk.id).put(finalEP.e, EPKState(finalEP))
        if (oldState != null) {
            if (oldState.isFinal) {
                if (potentiallyIdemPotentUpdate)
                return ; // IDEMPOTENT UPDATE
                else
                throw new IllegalStateException(
                        s"the old state $oldState is already final (new: $finalEP)"
                    )
                    
            }

            if (debug) oldState.eOptionP.checkIsValidPropertiesUpdate(finalEP, Nil)

            // Recall that we clear the dependees eagerly
            assert(oldState.dependees.isEmpty)

            // We have a state object which means we may have dependers.
            // Note that – when we register a depender – it is the responsibility of the registrar
            // to check that it has seen the last property value after updating the dependers list!
            //
            // We now have to inform the dependers.
            // We can simply inform all dependers because is it guaranteed that they have not seen
            // the final value since dependencies on final values are not allowed!
            //
            // ... the following line may clear dependers that have not been triggered, but
            // this doesn't matter, because when registration happens, it is the responsibility
            // of that thread to afterwards check that the value hasn't changed.
            oldState.lastDependers().foreach(epk ⇒ notifyDepender(epk, finalEP))
        }
    }

    // NOTES regarding concurrency:
    // W.r.t. one e/pk there may be multiple executions of the this method concurrently!
    private[this] def interimUpdate(
        interimEP: SomeInterimEP,
        c:         OnUpdateContinuation,
        dependees: Traversable[SomeEOptionP]
    ): Unit = {
        val psPerKind = properties(interimEP.pk.id)

        // 1. Update the property if necessary.
        var stateUpdateRequired = true
        val state = {
            var existingState = psPerKind.get(interimEP.e)
            if (existingState == null) {
                val newState = EPKState(interimEP, c, dependees)
                existingState = psPerKind.putIfAbsent(interimEP.e, newState)
                if (existingState == null) {
                    stateUpdateRequired = false
                    newState
                } else {
                    existingState
                }
            } else {
                existingState
            }
        }
        var notificationRequired = false
        if (stateUpdateRequired) {
            val oldEOptionP = state.update(interimEP, c, dependees, debug)
            notificationRequired = interimEP.isUpdatedComparedTo(oldEOptionP)
            state
        }

        // ATTENTION:   - - - - - - - - - - - H E R E   A R E   D R A G O N S - - - - - - - - - - -
        //              As soon as we register with the first dependee, we can have concurrent
        //              updates, which (in an extreme case) can already be completely finished
        //              between every two statements in the following code! That is, the dependees
        //              and the continuation function given to this method may already be outdated!
        //              Hence, before we call the given OnUpdateContinuation due to an updated
        //              dependee, we have to check if it is still the current one! If the

        // 2. Register with dependees (as depender) and while doing so check if the value
        //    was updated.
        //    We stop the registration with the dependees when the continuation function
        //    is triggered.
        val dependerEPK = interimEP.toEPK
        dependees forall { processedDependee ⇒
            state.hasPendingOnUpdateComputation && {
                val psPerDependeeKind = properties(processedDependee.pk.id)
                val dependeeState = psPerDependeeKind.get(processedDependee.e)
                if (dependeeState.isRefinable) {
                    dependeeState.addDepender(dependerEPK)
                    val currentDependee = psPerDependeeKind.get(processedDependee.e).eOptionP
                    if (currentDependee.isUpdatedComparedTo(processedDependee)) {
                        // A dependee was updated; let's trigger the OnUpdateContinuation if it
                        // wasn't already triggered concurrently.
                        removeDependerFromDependees(dependerEPK, processedDependees)
                        val currentC = state.clearOnUpdateComputationAndDependees()
                        if (currentC != null) {
                            forkOnUpdateContinuation(dependerEPK, processedDependee.e, processedDependee.pk)
                        }
                        false // we don't need to register further dependers
                    } else {
                        true
                    }
                } else {
                    ???
                }
            }
        }

        // 3. Notify dependers
        if (notificationRequired) {
            state.resetDependers().foreach(epk ⇒ notifyDepender(epk, interimEP))
        }
    }

    protected[this] def processResult(r: PropertyComputationResult): Unit = handleExceptions {

        r.id match {

            case NoResult.id ⇒ {
                // A computation reported no result; i.e., it is not possible to
                // compute a/some property/properties for a given entity.
            }

            //
            // Result containers
            //

            case Results.id ⇒ r.asResults.foreach {
                // IMPROVE Determine if direct evaluation would be better than spawning new threads.
                forkResultHandler
            }

            case IncrementalResult.id ⇒
                val IncrementalResult(ir, npcs, propertyComputationsHint) = r
                npcs /*: Iterator[(PropertyComputation[e],e)]*/ foreach { npc ⇒
                    val (pc, e) = npc
                    forkPropertyComputation(e, pc)
                }
                processResult(ir)

            //
            // Methods which actually store results...
            //

            case Result.id ⇒
                val Result(finalEP) = r
                finalUpdate(finalEP)

            case MultiResult.id ⇒
                val MultiResult(results) = r
                results foreach { finalEP ⇒ finalUpdate(finalEP) }

            case InterimResult.id ⇒
                val interimR = r.asInterimResult
                interimUpdate(interimR.eps, interimR.c, interimR.dependees)

            /*

            case PartialResult.id ⇒
                val PartialResult(e, pk, _) = r
                type E = e.type
                type P = Property
                val eOptionP = apply[E, P](e: E, pk: PropertyKey[P])

                val partialResultsQueue = partialResults(pk.id).get(e)
                var nextPartialResult = partialResultsQueue.poll()
                var newEPSOption : Option[EPS[E,P]] = None
                var doForceEvaluation = forceEvaluation
                var theForceDependersNotifications = forceDependersNotifications
                do {
                    val NewProperty(PartialResult(_, _, u), nextForceEvaluation, nextForceDependersNotification  ) = nextPartialResult

                    newEPSOption match {
                        case Some(updatedEOptionP) ⇒
                            XXX updateEOption has to be stored... if the update does not result in a new result!
                    newEPSOption = u.asInstanceOf[EOptionP[E, P] ⇒ Option[EPS[E, P]]](updatedEOptionP)
                        case None ⇒
                            newEPSOption = u.asInstanceOf[EOptionP[E, P] ⇒ Option[EPS[E, P]]](eOptionP)
                            if (newEPSOption.isEmpty) {
                                if (tracer.isDefined) {
                                    val partialResult = r.asInstanceOf[SomePartialResult]
                                    tracer.get.uselessPartialResult(partialResult, eOptionP)
                                }
                                uselessPartialResultComputationCounter += 1
                            }
                    }
                    nextPartialResult = partialResultsQueue.poll()
                } while (nextPartialResult != null)

                if (newEPSOption.isDefined) {
                    val newEPS = newEPSOption.get
                    val epk = newEPS.toEPK
                    if (clearDependees(epk) > 0) {
                        throw new IllegalStateException(
                            s"partial result ($r) for property with dependees (and continuation function)"
                        )
                    }
                    forceDependersNotifications -= epk
                    if (isPropertyKeyForSimplePropertyBasedOnPKId(pk.id))
                        updateAndNotifyForSimpleP(newEPS.e, newEPS.ub, isFinal = false, pcrs = pcrs)
                    else
                        updateAndNotifyForRegularP(newEPS.e, newEPS.lb, newEPS.ub, pcrs = pcrs)
                }

            case InterimResult.id ⇒
                val InterimResult(e, lb, ub, seenDependees, c, onUpdateContinuationHint) = r
                val pk = ub.key
                val pkId = pk.id
                val epk = EPK(e, pk)

                if (forceEvaluation) forcedComputations(pkId).put(e, e)

                assertNoDependees(e, pkId)

                // 1. let's check if a seen dependee is already updated; if so, we directly
                //    schedule/execute the continuation function again to continue computing
                //    the property
                val seenDependeesIterator = seenDependees.toIterator
                while (seenDependeesIterator.hasNext) {
                    val seenDependee = seenDependeesIterator.next()

                    if (debug && seenDependee.isFinal) {
                        throw new IllegalStateException(
                            s"$e (lb=$lb, ub=$ub): dependency to final property: $seenDependee"
                        )
                    }

                    val seenDependeeE = seenDependee.e
                    val seenDependeePKId = seenDependee.pk.id
                    val propertiesOfEntity = properties(seenDependeePKId)
                    // seenDependee is guaranteed to be not null
                    // currentDependee may be null => newDependee is an EPK => no update
                    val currentDependee = propertiesOfEntity.get(seenDependeeE)
                    if (currentDependee != null && seenDependee != currentDependee) {
                        // Make the current result available for other threads, but
                        // do not yet trigger dependers; however, we have to ensure
                        // that the dependers are eventually triggered if any update
                        // was relevant!
                        val updateAndNotifyState = updateAndNotifyForRegularP(
                            e, lb, ub,
                            notifyDependersAboutNonFinalUpdates = false,
                            pcrs
                        )
                        if (updateAndNotifyState.isNotificationRequired) {
                            forceDependersNotifications += epk
                        }

                        if (tracer.isDefined)
                            tracer.get.immediateDependeeUpdate(
                                e, pk, seenDependee, currentDependee, updateAndNotifyState
                            )

                        if (onUpdateContinuationHint == CheapPropertyComputation) {
                            directDependeeUpdatesCounter += 1
                            // we want to avoid potential stack-overflow errors...
                            pcrs += c(currentDependee)
                        } else {
                            scheduledDependeeUpdatesCounter += 1
                            if (currentDependee.isFinal) {
                                val t = ImmediateOnFinalUpdateComputationTask(
                                    store,
                                    currentDependee.asFinal,
                                    previousResult = r,
                                    forceDependersNotifications,
                                    c
                                )
                                appendTask(seenDependees.size, t)
                            } else {
                                val t = ImmediateOnUpdateComputationTask(
                                    store,
                                    currentDependee.toEPK,
                                    previousResult = r,
                                    forceDependersNotifications,
                                    c
                                )
                                appendTask(seenDependees.size, t)
                            }
                            // We will postpone the notification to the point where
                            // the result(s) are handled...
                            forceDependersNotifications = Set.empty
                        }

                        return ;
                    }
                }

                // When we reach this point, all potential dependee updates are taken into account;
                // otherwise we would have had an early return

                // 2.1.  Update the value (trigger dependers/clear old dependees).
                if (updateAndNotifyForRegularP(e, lb, ub, pcrs = pcrs).areDependersNotified) {
                    forceDependersNotifications -= epk
                }

                // 2.2.  The most current value of every dependee was taken into account
                //       register with new (!) dependees.
                this.dependees(pkId).put(e, seenDependees)
                val updateFunction = (c, onUpdateContinuationHint)
                seenDependees foreach { dependee ⇒
                    val dependeeE = dependee.e
                    val dependeePKId = dependee.pk.id
                    dependers(dependeePKId).
                        computeIfAbsent(dependeeE, _ ⇒ new JHashMap()).put(epk, updateFunction)
                }

            case SimplePInterimResult.id ⇒
                // TODO Unify handling with InterimResult (avoid code duplication)
                val SimplePInterimResult(e, ub, seenDependees, c, onUpdateContinuationHint) = r
                val pk = ub.key
                val pkId = pk.id
                val epk = EPK(e, pk)

                if (forceEvaluation) forcedComputations(pkId).put(e, e)

                assertNoDependees(e, pkId)

                // 1. let's check if a seen dependee is already updated; if so, we directly
                //    schedule/execute the continuation function again to continue computing
                //    the property
                val seenDependeesIterator = seenDependees.toIterator
                while (seenDependeesIterator.hasNext) {
                    val seenDependee = seenDependeesIterator.next()

                    if (debug && seenDependee.isFinal) {
                        throw new IllegalStateException(
                            s"$e/$pk: dependency to final property: $seenDependee"
                        )
                    }

                    val seenDependeeE = seenDependee.e
                    val seenDependeePKId = seenDependee.pk.id
                    val propertiesOfEntity = properties(seenDependeePKId)
                    // seenDependee is guaranteed to be not null
                    // currentDependee may be null => newDependee is an EPK => no update
                    val currentDependee = propertiesOfEntity.get(seenDependeeE)
                    if (currentDependee != null && seenDependee != currentDependee) {
                        // Make the current result available for other threads, but
                        // do not yet trigger dependers; however, we have to ensure
                        // that the dependers are eventually triggered if any update
                        // was relevant!
                        val updateAndNotifyState = updateAndNotifyForSimpleP(
                            e, ub, false,
                            notifyDependersAboutNonFinalUpdates = false,
                            pcrs
                        )
                        if (updateAndNotifyState.isNotificationRequired) {
                            forceDependersNotifications += epk
                        }

                        if (tracer.isDefined)
                            tracer.get.immediateDependeeUpdate(
                                e, pk, seenDependee, currentDependee, updateAndNotifyState
                            )

                        if (onUpdateContinuationHint == CheapPropertyComputation) {
                            directDependeeUpdatesCounter += 1
                            // we want to avoid potential stack-overflow errors...
                            pcrs += c(currentDependee)
                        } else {
                            scheduledDependeeUpdatesCounter += 1
                            if (currentDependee.isFinal) {
                                val t = ImmediateOnFinalUpdateComputationTask(
                                    store,
                                    currentDependee.asFinal,
                                    previousResult = r,
                                    forceDependersNotifications,
                                    c
                                )
                                appendTask(seenDependees.size, t)
                            } else {
                                val t = ImmediateOnUpdateComputationTask(
                                    store,
                                    currentDependee.toEPK,
                                    previousResult = r,
                                    forceDependersNotifications,
                                    c
                                )
                                appendTask(seenDependees.size, t)
                            }
                            // We will postpone the notification to the point where
                            // the result(s) are handled...
                            forceDependersNotifications = Set.empty
                        }

                        return ;
                    }
                }

                // When we reach this point, all potential dependee updates are taken into account;
                // otherwise we would have had an early return

                // 2.1.  Update the value (trigger dependers/clear old dependees).
                if (updateAndNotifyForSimpleP(e, ub, isFinal = false, pcrs = pcrs).areDependersNotified) {
                    forceDependersNotifications -= epk
                }

                // 2.2.  The most current value of every dependee was taken into account;
                //       register with new (!) dependees.
                this.dependees(pkId).put(e, seenDependees)
                val updateFunction = (c, onUpdateContinuationHint)
                seenDependees foreach { dependee ⇒
                    val dependeeE = dependee.e
                    val dependeePKId = dependee.pk.id
                    dependers(dependeePKId).
                        computeIfAbsent(dependeeE, _ ⇒ new JHashMap()).
                        put(epk, updateFunction)
                }*/
        }

        /*
        do {
            while (pcrs.nonEmpty) {
                processResult(pcrs.pop())
            }
            if (forceDependersNotifications.nonEmpty) {
                val epk = forceDependersNotifications.head
                forceDependersNotifications = forceDependersNotifications.tail
                val eps = properties(epk.pk.id).get(epk.e)
                if (tracer.isDefined) tracer.get.delayedNotification(eps)

                notifyDependers(eps, pcrs)
            }
        } while (forceDependersNotifications.nonEmpty || pcrs.nonEmpty)
        */
    }

    override def waitOnPhaseCompletion(): Unit = handleExceptions {
        val maxPKIndex = PropertyKey.maxId

        prepareThreadPool()

        val continueComputation = new AtomicBoolean(false)
        do {
            continueComputation.set(false)
            awaitPoolQuiescence()
            quiescenceCounter += 1
            if (debug) trace("analysis progress", s"reached quiescence $quiescenceCounter")

            // We have reached quiescence....

            // 1. Let's search for all EPKs (not EPS) and use the fall back for them.
            //    (Recall that we return fallback properties eagerly if no analysis is
            //     scheduled or will be scheduled, However, it is still possible that we will
            //     not have computed a property for a specific entity if the underlying
            //     analysis doesn't compute one; in that case we need to put in fallback
            //     values.)
            var pkIdIterator = 0
            while (pkIdIterator <= maxPKIndex) {
                if (propertyKindsComputedInThisPhase(pkIdIterator)) {
                    val pkId = pkIdIterator
                    parallelize(() ⇒ {
                        val epkStateIterator =
                            properties(pkId)
                                .values.iterator().asScala
                                .filter { epkState ⇒
                                    epkState.isEPK &&
                                        // There is no suppression; i.e., we have no dependees
                                        epkState.dependees.isEmpty
                                }
                        if (epkStateIterator.hasNext) continueComputation.set(true)
                        epkStateIterator.foreach { epkState ⇒
                            println("State without dependees: "+epkState)
                            val e = epkState.e
                            val reason = PropertyIsNotDerivedByPreviouslyExecutedAnalysis
                            val p = fallbackPropertyBasedOnPKId(this, reason, e, pkId)
                            if (traceFallbacks) {
                                trace("analysis progress", s"used fallback $p for $e")
                            }
                            incrementFallbacksUsedForComputedPropertiesCounter()
                            finalUpdate(FinalEP(e, p))
                        }
                    })
                }
                pkIdIterator += 1
            }
            awaitPoolQuiescence()

            // 2... suppression

            // 3. Let's finalize remaining interim EPS; e.g., those related to
            //    collaboratively computed properties or "just all" if we don't have suppressed
            //    notifications. Recall that we may have cycles if we have no suppressed
            //    notifications, because in the latter case, we may have dependencies.
            //    We used no fallbacks, but we may still have collaboratively computed properties
            //    (e.g. CallGraph) which are not yet final; let's finalize them in the specified
            //    order (i.e., let's finalize the subphase)!
            while (!continueComputation.get() && subPhaseId < subPhaseFinalizationOrder.length) {
                val pksToFinalize = subPhaseFinalizationOrder(subPhaseId)
                if (debug) {
                    trace(
                        "analysis progress",
                        pksToFinalize.map(PropertyKey.name).mkString("finalization of: ", ", ", "")
                    )
                }
                // The following will also kill dependers related to anonymous computations using
                // the generic property key: "AnalysisKey"; i.e., those without explicit properties!
                pksToFinalize foreach { pk ⇒
                    val propertyKey = PropertyKey.key(pk.id)
                    parallelize(() ⇒ {
                        val dependeesIt = properties(pk.id).elements().asScala.filter(_.hasDependees)
                        if (dependeesIt.hasNext) continueComputation.set(true)
                        dependeesIt foreach { epkState ⇒
                            removeDependerFromDependees(EPK(epkState.e, propertyKey))
                        }
                    })
                }
                awaitPoolQuiescence()

                pksToFinalize foreach { pk ⇒
                    parallelize(() ⇒ {
                        val interimEPSStates = properties(pk.id).values().asScala.filter(_.isRefinable)
                        interimEPSStates foreach { interimEPKState ⇒
                            finalUpdate(interimEPKState.eOptionP.toFinalEP)
                        }
                    })
                }
                awaitPoolQuiescence()

                subPhaseId += 1
            }
        } while (continueComputation.get())

        // TODO assert that we don't have any more InterimEPKStates

        cleanUpThreadPool()
    }

}
