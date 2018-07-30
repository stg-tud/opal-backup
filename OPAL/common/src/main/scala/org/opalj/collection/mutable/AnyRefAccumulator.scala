/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.collection.mutable

/**
 * A list based accumulator of values '''and''' collections of values.
 *
 * @note This is all but not a general purpose data-structure!
 *
 * @tparam A A type which is NOT a subtype of `Iterator[_]`.
 *
 * @author Michael Eichberg
 */
final class AnyRefAccumulator[A <: AnyRef] private (
        private var data: List[AnyRef] // either a value of type A or a non-empty iterator of A
) extends {

    def isEmpty: Boolean = data.isEmpty
    def nonEmpty: Boolean = data.nonEmpty

    def +=(i: A): Unit = {
        data ::= i
    }

    def ++=(is: TraversableOnce[A]): Unit = {
        is match {
            case it: Iterator[A] ⇒
                if (it.hasNext) data ::= it
            case is /*not a traversable once...*/ ⇒
                if (is.nonEmpty) data ::= is.toIterator
        }
    }

    /**
     * Returns and removes the next value.
     */
    def pop(): A = {
        data.head match {
            case it: Iterator[A @unchecked] ⇒
                val v = it.next()
                if (!it.hasNext) data = data.tail
                v
            case v: A @unchecked ⇒
                data = data.tail
                v
        }
    }

}

/**
 * Factory to create [[AnyRefAccumulator]]s.
 */
object AnyRefAccumulator {

    def empty[N >: Null <: AnyRef]: AnyRefAccumulator[N] = new AnyRefAccumulator[N](Nil)
}

