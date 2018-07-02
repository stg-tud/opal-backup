/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2017
 * Software Technology Group
 * Department of Computer Science
 * Technische Universität Darmstadt
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  - Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 *  - Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package org.opalj
package fpcf
package par

import java.util.concurrent.atomic.AtomicInteger
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

import scala.collection.JavaConverters._
import org.opalj.io

/**
 * Enables the tracing of key events during the analysis progress.
 *
 * @author Michael Eichberg
 */
trait PropertyStoreTracer {

    //
    // POTENTIALLY CALLED CONCURRENTLY
    //

    def force(e: Entity, pkId: Int): Unit

    def forceForComputedEPK(e: Entity, pkId: Int): Unit

    //
    // CALLED SEQUENTIALLY
    //

    /** Called if a lazy or fallback computation is eventually scheduled. */
    def schedulingLazyComputation(e: Entity, pkId: Int): Unit

    /**
     * Called when a property is updated.
     *
     * @param oldEPS The old value of the property; may be null.
     * @param newEPS The new value of the property.
     */
    def update(oldEPS: SomeEPS, newEPS: SomeEPS): Unit

    def notification(newEPS: SomeEPS, depender: SomeEPK): Unit

    def delayedNotification(newEPS: SomeEPS): Unit

    def immediateDependeeUpdate(
        e: Entity, lb: Property, ub: Property,
        processedDependee:    SomeEOptionP,
        currentDependee:      SomeEPS,
        updateAndNotifyState: UpdateAndNotifyState
    ): Unit

    def handlingResult(
        r:                               PropertyComputationResult,
        forceEvaluation:                 Boolean,
        epksWithNotYetNotifiedDependers: Set[SomeEPK]
    ): Unit

    def metaInformationDeleted(finalEP: SomeFinalEP): Unit

    def reachedQuiescence(): Unit

    def firstException(t: Throwable): Unit
}

sealed trait StoreEvent {
    def eventId: Int

    def toTxt: String
}

case class Force(
        eventId: Int,
        e:       Entity,
        pkId:    Int
) extends StoreEvent {

    override def toString: String = {
        s"Force($eventId,$e@${System.identityHashCode(e).toHexString},${PropertyKey.name(pkId)})"
    }

    override def toTxt: String = {
        s"$eventId: Force($e@${System.identityHashCode(e).toHexString},${PropertyKey.name(pkId)})"
    }
}

case class ForceForComputedEPK(
        eventId: Int,
        e:       Entity,
        pkId:    Int
) extends StoreEvent {

    override def toString: String = {
        s"ForceForComputedEPK($eventId,"+
            s"$e@${System.identityHashCode(e).toHexString},${PropertyKey.name(pkId)})"
    }

    override def toTxt: String = {
        s"$eventId: ForceForComputedEPK("+
            s"$e@${System.identityHashCode(e).toHexString},${PropertyKey.name(pkId)})"
    }
}

case class LazyComputationScheduled(
        eventId: Int,
        e:       Entity,
        pkId:    Int
) extends StoreEvent {

    override def toString: String = {
        s"LazyComputationScheduled($eventId,"+
            s"$e@${System.identityHashCode(e).toHexString},${PropertyKey.name(pkId)})"
    }

    override def toTxt: String = {
        s"$eventId: LazyComputationScheduled("+
            s"$e@${System.identityHashCode(e).toHexString},${PropertyKey.name(pkId)})"
    }
}

case class PropertyUpdate(
        eventId: Int,
        oldEPS:  SomeEPS,
        newEPS:  SomeEPS
) extends StoreEvent {

    override def toString: String = s"PropertyUpdate($eventId,oldEPS=$oldEPS,newEPS=$newEPS)"
    override def toTxt: String = s"$eventId: PropertyUpdate(oldEPS=$oldEPS,newEPS=$newEPS)"
}

case class DependerNotification(
        eventId:     Int,
        newEPS:      SomeEPS,
        dependerEPK: SomeEPK
) extends StoreEvent {

    override def toTxt: String = {
        s"$eventId: DependerNotification(newEPS=$newEPS, dependerEPK=$dependerEPK)"
    }

}

case class DelayedDependersNotification(
        eventId: Int,
        newEPS:  SomeEPS
) extends StoreEvent {

    override def toTxt: String = {
        s"$eventId: DelayedDependersNotification(newEPS=$newEPS)"
    }

}

case class ImmediateDependeeUpdate(
        eventId: Int,
        e:       Entity,
        lb:      Property, ub: Property,
        processedDependee:    SomeEOptionP,
        currentDependee:      SomeEPS,
        updateAndNotifyState: UpdateAndNotifyState
) extends StoreEvent {

    override def toTxt: String = {
        s"$eventId: ImmediateDependeeUpdate($e@${System.identityHashCode(e).toHexString},"+
            s"lb=$lb,processedDependee=$processedDependee,currentDependee=$currentDependee,"+
            s"updateAndNotifyState=$updateAndNotifyState)"
    }

}

case class HandlingResult(
        eventId:                         Int,
        r:                               PropertyComputationResult,
        forceEvaluation:                 Boolean,
        epksWithNotYetNotifiedDependers: Set[SomeEPK]
) extends StoreEvent {

    override def toTxt: String = {
        s"$eventId: HandlingResult($r,"+
            s"forceEvaluation=$forceEvaluation,"+
            s"epksWithNotYetNotifiedDependers=$epksWithNotYetNotifiedDependers)"
    }

}

case class MetaInformationDeleted(
        eventId: Int,
        finalEP: SomeFinalEP
) extends StoreEvent {

    override def toTxt: String = {
        s"$eventId: MetaInformationDeleted(${finalEP.toEPK})"
    }

}

case class FirstException(
        eventId:    Int,
        t:          Throwable,
        thread:     String,
        stackTrace: String
) extends StoreEvent {

    override def toTxt: String = {
        s"$eventId: FirstException(\n\t$t,\n\t$thread,\n\t$stackTrace\n)"
    }

}

case class ReachedQuiescence(eventId: Int) extends StoreEvent {

    override def toTxt: String = {
        s"$eventId: ReachedQuiescence"
    }
}

class RecordAllPropertyStoreTracer extends PropertyStoreTracer {

    val eventCounter = new AtomicInteger(0)

    private[this] val events = new ConcurrentLinkedQueue[StoreEvent]

    //
    // POTENTIALLY CALLED CONCURRENTLY
    //

    def force(e: Entity, pkId: Int): Unit = {
        events.offer(Force(eventCounter.incrementAndGet(), e, pkId))
    }

    def forceForComputedEPK(e: Entity, pkId: Int): Unit = {
        events.offer(ForceForComputedEPK(eventCounter.incrementAndGet(), e, pkId))
    }

    //
    // CALLED BY THE STORE UPDATES PROCESSOR THREAD
    //

    def schedulingLazyComputation(e: Entity, pkId: Int): Unit = {
        events offer LazyComputationScheduled(eventCounter.incrementAndGet(), e, pkId)
    }

    def update(oldEPS: SomeEPS, newEPS: SomeEPS): Unit = {
        events offer PropertyUpdate(eventCounter.incrementAndGet(), oldEPS, newEPS)
    }

    def notification(newEPS: SomeEPS, dependerEPK: SomeEPK): Unit = {
        events offer DependerNotification(eventCounter.incrementAndGet(), newEPS, dependerEPK)
    }

    def delayedNotification(newEPS: SomeEPS): Unit = {
        events offer DelayedDependersNotification(eventCounter.incrementAndGet(), newEPS)
    }

    def immediateDependeeUpdate(
        e: Entity, lb: Property, ub: Property,
        processedDependee: SomeEOptionP, currentDependee: SomeEPS,
        updateAndNotifyState: UpdateAndNotifyState
    ): Unit = {
        events offer ImmediateDependeeUpdate(
            eventCounter.incrementAndGet(),
            e, lb, ub,
            processedDependee,
            currentDependee,
            updateAndNotifyState
        )
    }

    def handlingResult(
        r:                               PropertyComputationResult,
        forceEvaluation:                 Boolean,
        epksWithNotYetNotifiedDependers: Set[SomeEPK]
    ): Unit = {
        val eventId = eventCounter.incrementAndGet()
        events offer HandlingResult(eventId, r, forceEvaluation, epksWithNotYetNotifiedDependers)
    }

    def metaInformationDeleted(finalEP: SomeFinalEP): Unit = {
        events offer MetaInformationDeleted(eventCounter.incrementAndGet(), finalEP)
    }

    def reachedQuiescence(): Unit = {
        events offer ReachedQuiescence(eventCounter.incrementAndGet())
    }

    def firstException(t: Throwable): Unit = {
        events offer FirstException(
            eventCounter.incrementAndGet(),
            t,
            Thread.currentThread().getName,
            Thread.currentThread().getStackTrace.mkString("\n\t", "\n\t", "\n") // dropWhile(_.getMethodName != "handleException")
        )
    }

    //
    // QUERYING THE EVENTS
    //

    def allEvents: List[StoreEvent] = events.iterator.asScala.toList.sortWith(_.eventId < _.eventId)

    def toTxt: String = {
        allEvents.map { e ⇒
            e match {
                case _: HandlingResult ⇒ "\n"+e.toTxt
                case e                 ⇒ "\t"+e.toTxt
            }
        }.mkString("Events [\n", "\n", "\n]")
    }

    def writeAsTxt: File = {
        io.write(toTxt, "Property Store Events", ".txt").toFile
    }

    def writeAsTxtAndOpen: File = {
        io.writeAndOpen(toTxt, "Property Store Events", ".txt")
    }
}
