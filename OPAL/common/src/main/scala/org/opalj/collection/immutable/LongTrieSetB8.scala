/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.collection.immutable

import scala.annotation.switch

import java.lang.{Long ⇒ JLong}

/**
 * An unordered set of long values backed by a trie set with a branching factor of 8.
 *
 * @author Michael Eichberg
 */
sealed abstract class LongTrieSetB8 {
    longSet ⇒

    def size: Int
    def +(v: Long): LongTrieSetB8
    def contains(v: Long): Boolean
    def foreach[U](f: Long ⇒ U): Unit
}

object LongTrieSetB8 {
    val empty: LongTrieSetB8 = new LargeLongTrieSetB8(0, null)
    def apply(v: Long) = new LargeLongTrieSetB8(1, new JLong(v))
}

final private[immutable] class LargeLongTrieSetB8Node(
        val _000: AnyRef = null, // either a LargeLongTrieSetB8Node, a Long Value or null
        val _001: AnyRef = null, // either a LargeLongTrieSetB8Node, a Long Value or null
        val _010: AnyRef = null, // either a LargeLongTrieSetB8Node, a Long Value or null
        val _011: AnyRef = null, // either a LargeLongTrieSetB8Node, a Long Value or null
        val _100: AnyRef = null, // either a LargeLongTrieSetB8Node, a Long Value or null
        val _101: AnyRef = null, // either a LargeLongTrieSetB8Node, a Long Value or null
        val _110: AnyRef = null, // either a LargeLongTrieSetB8Node, a Long Value or null
        val _111: AnyRef = null // either a LargeLongTrieSetB8Node, a Long Value or null
) {

    def copy(
        _000: AnyRef = _000,
        _001: AnyRef = _001,
        _010: AnyRef = _010,
        _011: AnyRef = _011,
        _100: AnyRef = _100,
        _101: AnyRef = _101,
        _110: AnyRef = _110,
        _111: AnyRef = _111
    ): LargeLongTrieSetB8Node = {
        new LargeLongTrieSetB8Node(_000, _001, _010, _011, _100, _101, _110, _111)
    }

    def update(index: Int, n: AnyRef): LargeLongTrieSetB8Node = {
        (index: @switch) match {
            case 0 ⇒ copy(_000 = n)
            case 1 ⇒ copy(_001 = n)
            case 2 ⇒ copy(_010 = n)
            case 3 ⇒ copy(_011 = n)
            case 4 ⇒ copy(_100 = n)
            case 5 ⇒ copy(_101 = n)
            case 6 ⇒ copy(_110 = n)
            case 7 ⇒ copy(_111 = n)
        }
    }

    def foreach[U](f: Long ⇒ U): Unit = {
        def processValue(n: AnyRef): Unit = {
            n match {
                case null                      ⇒ // nothing to do
                case value: JLong              ⇒ f(value.longValue())
                case n: LargeLongTrieSetB8Node ⇒ n.foreach(f)
            }
        }
        processValue(_000)
        processValue(_001)
        processValue(_010)
        processValue(_011)
        processValue(_100)
        processValue(_101)
        processValue(_110)
        processValue(_111)

    }

    def +(v: JLong, level: Int): LargeLongTrieSetB8Node = {
        def +@(index: Int, n: AnyRef): LargeLongTrieSetB8Node = {
            n match {
                case null ⇒ update(index, v)
                case value: JLong ⇒
                    if (value.longValue() == v)
                        return this;
                    else {
                        // IMPROVE Determine the number of shared bits and then create the data-structure from the leaves...
                        update(index, LargeLongTrieSetB8Node(value, level + 3) + (v, level + 3))
                    }
                case n: LargeLongTrieSetB8Node ⇒
                    val newN = n + (v, level + 3)
                    if (newN ne n) {
                        update(index, newN)
                    } else {
                        return this;
                    }
            }
        }

        val branchBits = ((v >> level) & 7L).toInt
        val updatedThis = branchBits match {
            case 0 ⇒ +@(0, _000)
            case 1 ⇒ +@(1, _001)
            case 2 ⇒ +@(2, _010)
            case 3 ⇒ +@(3, _011)
            case 4 ⇒ +@(4, _100)
            case 5 ⇒ +@(5, _101)
            case 6 ⇒ +@(6, _110)
            case 7 ⇒ +@(7, _111)
        }
        updatedThis
    }
}

object LargeLongTrieSetB8Node {

    def apply(v: JLong, level: Int): LargeLongTrieSetB8Node = {
        val branchBits = (((v >> level) & 7L).toInt: @switch)
        branchBits match {
            case 0 ⇒ new LargeLongTrieSetB8Node(_000 = v)
            case 1 ⇒ new LargeLongTrieSetB8Node(_001 = v)
            case 2 ⇒ new LargeLongTrieSetB8Node(_010 = v)
            case 3 ⇒ new LargeLongTrieSetB8Node(_011 = v)
            case 4 ⇒ new LargeLongTrieSetB8Node(_100 = v)
            case 5 ⇒ new LargeLongTrieSetB8Node(_101 = v)
            case 6 ⇒ new LargeLongTrieSetB8Node(_110 = v)
            case 7 ⇒ new LargeLongTrieSetB8Node(_111 = v)
        }
    }
}

final private[immutable] case class LargeLongTrieSetB8 private[immutable] (
        size: Int,
        root: AnyRef
) extends LongTrieSetB8 {

    def +(v: Long): LargeLongTrieSetB8 = {
        root match {
            case root: LargeLongTrieSetB8Node ⇒
                val newRoot = root + (v, 0)
                if (newRoot ne root) {
                    new LargeLongTrieSetB8(size + 1, newRoot)
                } else {
                    this
                }

            case value: JLong ⇒
                if (v == value.longValue())
                    this
                else {
                    val newRoot = LargeLongTrieSetB8Node(value, 0) + (v, 0)
                    new LargeLongTrieSetB8(2, newRoot)
                }

            case null ⇒
                new LargeLongTrieSetB8(1, new JLong(v))
        }
    }

    final override def contains(v: Long): Boolean = {
        var key = v
        var node = root
        do {
            node match {
                case n: LargeLongTrieSetB8Node ⇒
                    node = ((key & 7L).toInt: @switch) match {
                        case 0 ⇒ n._000
                        case 1 ⇒ n._001
                        case 2 ⇒ n._010
                        case 3 ⇒ n._011
                        case 4 ⇒ n._100
                        case 5 ⇒ n._101
                        case 6 ⇒ n._110
                        case 7 ⇒ n._111
                    }
                    key = key >> 3
                case value: JLong ⇒
                    return v == value.longValue();
                case null ⇒
                    return false;
            }
        } while (node ne null)
        false
    }

    final override def foreach[U](f: Long ⇒ U): Unit = {
        root match {
            case n: LargeLongTrieSetB8Node ⇒ n.foreach(f)
            case value: JLong              ⇒ f(value.longValue())
            case null                      ⇒ // nothing to do
        }
    }
}
