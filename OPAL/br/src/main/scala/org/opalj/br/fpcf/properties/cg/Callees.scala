/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package br
package fpcf
package properties
package cg

import scala.collection.immutable.IntMap

import org.opalj.collection.IntIterator
import org.opalj.collection.immutable.IntTrieSet
import org.opalj.fpcf.Entity
import org.opalj.fpcf.FallbackReason
import org.opalj.fpcf.Property
import org.opalj.fpcf.PropertyIsNotDerivedByPreviouslyExecutedAnalysis
import org.opalj.fpcf.PropertyKey
import org.opalj.fpcf.PropertyMetaInformation
import org.opalj.fpcf.PropertyStore
import org.opalj.value.ValueInformation
import org.opalj.br.analyses.DeclaredMethods

sealed trait CalleesPropertyMetaInformation extends PropertyMetaInformation {

    final type Self = Callees
}

/**
 * Encapsulates all possible callees of a method, as computed by a set of cooperating call graph
 * analyses.
 */
sealed trait Callees extends Property with CalleesPropertyMetaInformation {

    /**
     * Is there a call to method `target` at `pc`?
     */
    def containsCall(pc: Int, target: DeclaredMethod): Boolean

    def containsDirectCall(pc: Int, target: DeclaredMethod): Boolean

    def containsIndirectCall(pc: Int, target: DeclaredMethod): Boolean

    /**
     * PCs of call sites that at least one of the analyses could not resolve completely.
     */
    def incompleteCallSites(implicit propertyStore: PropertyStore): IntIterator

    /**
     * Returns whether at least on analysis could not resolve the call site at `pc` completely.
     */
    def isIncompleteCallSite(pc: Int)(implicit propertyStore: PropertyStore): Boolean

    /**
     * States whether there is at least one call site that could not be resolved.
     */
    def hasIncompleteCallSites: Boolean

    /**
     * Potential callees of the call site at `pc`. The callees may not match the invocation
     * instruction at the pc and a remapping of parameters using [[indirectCallParameters]] may be
     * necessary.
     */
    def callees(
        pc: Int
    )(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Iterator[DeclaredMethod]

    /**
     * Potential callees of the call site at `pc`. The callees will match the invocation
     * instruction at the pc.
     */
    def directCallees(
        pc: Int
    )(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Iterator[DeclaredMethod]

    /**
     * Potential callees of the call site at `pc`. The callees will not match the invocation
     * instruction at the pc and a remapping of parameters using [[indirectCallParameters]] may be
     * necessary.
     */
    def indirectCallees(
        pc: Int
    )(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Iterator[DeclaredMethod]

    /**
     * Number of potential callees at the call site at `pc`.
     */
    def numCallees(pc: Int)(implicit propertyStore: PropertyStore): Int

    /**
     * PCs of all call sites in the method.
     */
    // TODO Use IntIterator once we have our own IntMap
    def callSitePCs(implicit propertyStore: PropertyStore): Iterator[Int]

    /**
     * Map of pc to potential callees of the call site at that pc. The callees may not match the
     * invocation instruction at the pc and a remapping of parameters using
     * [[indirectCallParameters]] may be necessary.
     */
    def callSites()(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Map[Int, Iterator[DeclaredMethod]]

    /**
     * Map of pc to potential direct callees of the call site at that pc. The callees will match the
     * invocation instruction at the pc.
     */
    def directCallSites()(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Map[Int, Iterator[DeclaredMethod]]

    /**
     * Map of pc to potential indirect callees of the call site at that pc. The callees will not
     * match the invocation instruction at the pc and remapping of parameters using
     * [[indirectCallParameters]] may be necessary.
     */
    def indirectCallSites()(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Map[Int, Iterator[DeclaredMethod]]

    /**
     * Returns for a given call site pc and indirect target method the receiver information.
     * If the receiver can not be determined, the `scala.Option` will be empty, otherwise it will
     * contain all [[PCs]] and the the negative indices of parameters that may define the value of
     * the receiver.
     * The parameter at index 0 always corresponds to the *this* local and is `null` for static
     * methods.
     */
    def indirectCallReceiver(pc: Int, callee: DeclaredMethod): Option[(ValueInformation, PCs)]

    /**
     * Returns for a given call site pc and indirect target method the sequence of parameter
     * sources. If a parameter source can not be determined, the `scala.Option` will be empty,
     * otherwise it will contain all PCs and the negative indices of parameters that may define the
     * value of the corresponding actual parameter.
     * The parameter at index 0 always corresponds to the *this* local and is `null` for static
     * methods.
     */
    def indirectCallParameters(
        pc:     Int,
        callee: DeclaredMethod
    )(implicit propertyStore: PropertyStore): Seq[Option[(ValueInformation, IntTrieSet)]]

    /**
     * Creates a copy of the current callees object, including the additional callee information
     * specified in the parameters.
     */
    def updateWithCallees(
        directCallees:          IntMap[IntTrieSet],
        indirectCallees:        IntMap[IntTrieSet],
        incompleteCallSites:    PCs,
        indirectCallReceivers:  IntMap[IntMap[Option[(ValueInformation, PCs)]]],
        indirectCallParameters: IntMap[IntMap[Seq[Option[(ValueInformation, PCs)]]]]
    ): Callees

    final def key: PropertyKey[Callees] = Callees.key
}

/**
 * Callees class used for final results where the callees are already aggregated.
 */
sealed class ConcreteCallees(
        private[this] val directCalleesIds:        IntMap[IntTrieSet],
        private[this] val indirectCalleesIds:      IntMap[IntTrieSet],
        private[this] val _incompleteCallSites:    PCs,
        private[this] val _indirectCallReceivers:  IntMap[IntMap[Option[(ValueInformation, PCs)]]],
        private[this] val _indirectCallParameters: IntMap[IntMap[Seq[Option[(ValueInformation, PCs)]]]]
) extends Callees {

    override def incompleteCallSites(implicit propertyStore: PropertyStore): IntIterator = {
        _incompleteCallSites.iterator
    }

    override def isIncompleteCallSite(pc: Int)(implicit propertyStore: PropertyStore): Boolean = {
        _incompleteCallSites.contains(pc)
    }

    override def hasIncompleteCallSites: Boolean = {
        _incompleteCallSites.nonEmpty
    }

    override def callees(
        pc: Int
    )(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Iterator[DeclaredMethod] = {
        directCallees(pc) ++ indirectCallees(pc)
    }

    override def directCallees(
        pc: Int
    )(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Iterator[DeclaredMethod] = {
        val directCalleeIds = directCalleesIds.getOrElse(pc, IntTrieSet.empty)
        directCalleeIds.iterator.map[DeclaredMethod](declaredMethods.apply)
    }

    override def indirectCallees(
        pc: Int
    )(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Iterator[DeclaredMethod] = {
        val indirectCalleeIds = indirectCalleesIds.getOrElse(pc, IntTrieSet.empty)
        indirectCalleeIds.iterator.map[DeclaredMethod](declaredMethods.apply)
    }

    override def numCallees(pc: Int)(implicit propertyStore: PropertyStore): Int = {
        directCalleesIds(pc).size + indirectCalleesIds.get(pc).map(_.size).getOrElse(0)
    }

    override def callSitePCs(implicit propertyStore: PropertyStore): Iterator[Int] = {
        directCalleesIds.keysIterator ++ indirectCalleesIds.keysIterator
    }

    override def callSites()(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): IntMap[Iterator[DeclaredMethod]] = {
        var res = IntMap(directCallSites().toStream: _*)

        for ((pc, indirect) ← indirectCallSites()) {
            res = res.updateWith(pc, indirect, (direct, indirect) ⇒ direct ++ indirect)
        }

        res
    }

    override def directCallSites()(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Map[Int, Iterator[DeclaredMethod]] = {
        directCalleesIds.mapValues { calleeIds ⇒
            calleeIds.iterator.map[DeclaredMethod](declaredMethods.apply)
        }
    }

    override def indirectCallSites()(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Map[Int, Iterator[DeclaredMethod]] = {
        indirectCalleesIds.mapValues { calleeIds ⇒
            calleeIds.iterator.map[DeclaredMethod](declaredMethods.apply)
        }
    }

    override def indirectCallReceiver(
        pc: Opcode, callee: DeclaredMethod
    ): Option[(ValueInformation, PCs)] = {
        _indirectCallReceivers(pc)(callee.id)
    }

    override def indirectCallParameters(
        pc:     Int,
        method: DeclaredMethod
    )(
        implicit
        propertyStore: PropertyStore
    ): Seq[Option[(ValueInformation, IntTrieSet)]] = {
        _indirectCallParameters(pc)(method.id)
    }

    override def updateWithCallees(
        directCallees:          IntMap[IntTrieSet],
        indirectCallees:        IntMap[IntTrieSet],
        incompleteCallSites:    PCs,
        indirectCallReceivers:  IntMap[IntMap[Option[(ValueInformation, PCs)]]],
        indirectCallParameters: IntMap[IntMap[Seq[Option[(ValueInformation, PCs)]]]]
    ): Callees = {
        new ConcreteCallees(
            directCalleesIds.unionWith(directCallees, (_, l, r) ⇒ l ++ r),
            indirectCalleesIds.unionWith(indirectCallees, (_, l, r) ⇒ l ++ r),
            _incompleteCallSites ++ incompleteCallSites,
            _indirectCallReceivers.unionWith(
                indirectCallReceivers,
                (_, r, l) ⇒ {
                    r.unionWith(
                        l,
                        (_, _, _) ⇒ throw new UnknownError("Indirect callee derived by two analyses")
                    )
                }
            ),
            _indirectCallParameters.unionWith(
                indirectCallParameters,
                (_, r, l) ⇒ {
                    r.unionWith(
                        l,
                        (_, _, _) ⇒ throw new UnknownError("Indirect callee derived by two analyses")
                    )
                }
            )
        )
    }

    override def containsCall(pc: Int, target: DeclaredMethod): Boolean = {
        containsDirectCall(pc, target) || containsIndirectCall(pc, target)
    }

    override def containsDirectCall(pc: Int, target: DeclaredMethod): Boolean = {
        directCalleesIds.contains(pc) && directCalleesIds(pc).contains(target.id)
    }

    override def containsIndirectCall(pc: Int, target: DeclaredMethod): Boolean = {
        indirectCalleesIds.contains(pc) && indirectCalleesIds(pc).contains(target.id)
    }
}

object NoCallees extends Callees {

    override def incompleteCallSites(implicit propertyStore: PropertyStore): IntIterator =
        IntIterator.empty

    override def isIncompleteCallSite(pc: Int)(implicit propertyStore: PropertyStore): Boolean =
        false

    override def hasIncompleteCallSites: Boolean =
        false

    override def callees(pc: Int)(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Iterator[DeclaredMethod] = Iterator.empty

    override def directCallees(pc: Int)(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Iterator[DeclaredMethod] = Iterator.empty

    override def indirectCallees(pc: Int)(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Iterator[DeclaredMethod] = Iterator.empty

    override def numCallees(pc: Int)(implicit propertyStore: PropertyStore): Int = 0

    override def callSitePCs(implicit propertyStore: PropertyStore): IntIterator = IntIterator.empty

    override def callSites()(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): IntMap[Iterator[DeclaredMethod]] = IntMap.empty

    override def directCallSites()(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): IntMap[Iterator[DeclaredMethod]] = IntMap.empty

    override def indirectCallSites()(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): IntMap[Iterator[DeclaredMethod]] = IntMap.empty

    override def indirectCallReceiver(
        pc: Opcode, callee: DeclaredMethod
    ): Option[(ValueInformation, PCs)] = None

    override def indirectCallParameters(
        pc:     Int,
        method: DeclaredMethod
    )(
        implicit
        propertyStore: PropertyStore
    ): Seq[Option[(ValueInformation, IntTrieSet)]] = Seq.empty

    override def updateWithCallees(
        directCallees:          IntMap[IntTrieSet],
        indirectCallees:        IntMap[IntTrieSet],
        incompleteCallSites:    PCs,
        indirectCallReceivers:  IntMap[IntMap[Option[(ValueInformation, PCs)]]],
        indirectCallParameters: IntMap[IntMap[Seq[Option[(ValueInformation, PCs)]]]]
    ): ConcreteCallees = {
        new ConcreteCallees(
            directCallees,
            indirectCallees,
            incompleteCallSites,
            indirectCallReceivers,
            indirectCallParameters
        )
    }

    override def containsCall(pc: Int, target: DeclaredMethod): Boolean = false

    override def containsDirectCall(pc: Int, target: DeclaredMethod): Boolean = false

    override def containsIndirectCall(pc: Int, target: DeclaredMethod): Boolean = false
}

object NoCalleesDueToNotReachableMethod extends Callees {

    override def incompleteCallSites(implicit propertyStore: PropertyStore): IntIterator =
        IntIterator.empty

    override def isIncompleteCallSite(pc: Int)(implicit propertyStore: PropertyStore): Boolean =
        false

    override def hasIncompleteCallSites: Boolean =
        false

    override def callees(pc: Int)(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Iterator[DeclaredMethod] = Iterator.empty

    override def directCallees(pc: Int)(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Iterator[DeclaredMethod] = Iterator.empty

    override def indirectCallees(pc: Int)(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): Iterator[DeclaredMethod] = Iterator.empty

    override def numCallees(pc: Int)(implicit propertyStore: PropertyStore): Int = 0

    override def callSitePCs(implicit propertyStore: PropertyStore): IntIterator = IntIterator.empty

    override def callSites()(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): IntMap[Iterator[DeclaredMethod]] = IntMap.empty

    override def directCallSites()(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): IntMap[Iterator[DeclaredMethod]] = IntMap.empty

    override def indirectCallSites()(
        implicit
        propertyStore:   PropertyStore,
        declaredMethods: DeclaredMethods
    ): IntMap[Iterator[DeclaredMethod]] = IntMap.empty

    override def indirectCallReceiver(
        pc: Opcode, callee: DeclaredMethod
    ): Option[(ValueInformation, PCs)] = None

    override def indirectCallParameters(
        pc:     Int,
        method: DeclaredMethod
    )(
        implicit
        propertyStore: PropertyStore
    ): Seq[Option[(ValueInformation, IntTrieSet)]] = Seq.empty

    override def updateWithCallees(
        directCallees:          IntMap[IntTrieSet],
        indirectCallees:        IntMap[IntTrieSet],
        incompleteCallSites:    PCs,
        indirectCallReceivers:  IntMap[IntMap[Option[(ValueInformation, PCs)]]],
        indirectCallParameters: IntMap[IntMap[Seq[Option[(ValueInformation, PCs)]]]]
    ): Callees = throw new IllegalStateException("Unreachable methods can't be updated!")

    override def containsCall(pc: Int, target: DeclaredMethod): Boolean = false

    override def containsDirectCall(pc: Int, target: DeclaredMethod): Boolean = false

    override def containsIndirectCall(pc: Int, target: DeclaredMethod): Boolean = false
}

object Callees extends CalleesPropertyMetaInformation {

    final val key: PropertyKey[Callees] = {
        val name = "opalj.Callees"
        PropertyKey.create(
            name,
            (_: PropertyStore, reason: FallbackReason, _: Entity) ⇒ reason match {
                case PropertyIsNotDerivedByPreviouslyExecutedAnalysis ⇒
                    NoCalleesDueToNotReachableMethod
                case _ ⇒
                    throw new IllegalStateException(s"analysis required for property: $name")
            }
        )
    }
}
