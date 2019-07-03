/* BSD 2-Clause License - see OPAL/LICENSE for details. */

package org.opalj.collection.immutable

import org.opalj.collection.RefIterator
import org.opalj.collection.UID
import org.opalj.collection.mutable.RefArrayStack

/**
 * A set of objects of type UID. This set is defined over the ids of the objects and 
 * NOT over the objects themselves. I.e., at any given time no two different objects 
 * which have the same id will be found in the id (provided that the ids are not changed
 * after adding the object to this set, which is a pre-requisite.)
 *
 * @note Though `equals` and `hashCode` are implemented, comparing UID trie sets
 *       is still not efficient (n * log n) because the structure of the trie
 *       depends on the insertion order.
 */
sealed abstract class GrowableUIDTrieSet[T <: UID] { intSet ⇒

    def isEmpty: Boolean
    def isSingletonSet: Boolean
    def size: Int
    /**
     * Tests if this set contains a value with the same id as the given value.
     * I.e., no comparison of the values is done, but only the underlying ids
     * are compared.
     */
    final def contains(value: T): Boolean = containsId(value.id)
    def containsId(id: Int): Boolean
    def foreach[U](f: T ⇒ U): Unit
    def forall(p: T ⇒ Boolean): Boolean
    def iterator: RefIterator[T]
    def +(value: T): GrowableUIDTrieSet[T]

    final override def equals(other: Any): Boolean = {
        other match {
            case that: GrowableUIDTrieSet[_] ⇒ this.equals(that)
            case _                           ⇒ false
        }
    }

    def equals(other: GrowableUIDTrieSet[_]): Boolean
}

object GrowableUIDTrieSet {

    def empty[T <: UID]: GrowableUIDTrieSet[T] = {
        GrowableUIDTrieSet0.asInstanceOf[GrowableUIDTrieSet[T]]
    }

    def apply[T <: UID](value: T): GrowableUIDTrieSet[T] = {
        new GrowableUIDTrieSet1(value)
    }
}

/**
 * The common superclass of the nodes of the trie.
 */
private[immutable] sealed trait GrowableUIDTrieSetN[T <: UID] {

    def foreach[U](f: T ⇒ U): Unit

    def forall(p: T ⇒ Boolean): Boolean

    //
    // IMPLEMENTATION "INTERNAL" METHODS
    //
    // Adds the value with the id to the trie at the specified level where `key is id >> level`.
    private[immutable] def +(value: T, id: Int, key: Int, level: Int): GrowableUIDTrieSetN[T]
    private[immutable] def containsId(id: Int, key: Int): Boolean
    private[immutable] def toString(indent: Int): String
}

/**
 * The common superclass of the leafs of the trie.
 */
private[immutable] sealed abstract class GrowableUIDTrieSetL[T <: UID]
    extends GrowableUIDTrieSet[T]
    with GrowableUIDTrieSetN[T] {

    final override private[immutable] def containsId(id: Int, key: Int): Boolean = {
        this.containsId(id)
    }

    final override private[immutable] def toString(indent: Int): String = {
        (" " * indent) + toString()
    }

}

case object GrowableUIDTrieSet0 extends GrowableUIDTrieSetL[UID] {
    override def isSingletonSet: Boolean = false
    override def isEmpty: Boolean = true
    override def size: Int = 0
    override def foreach[U](f: UID ⇒ U): Unit = {}
    override def forall(p: UID ⇒ Boolean): Boolean = true
    override def +(i: UID): GrowableUIDTrieSet1[UID] = new GrowableUIDTrieSet1(i)
    override def iterator: RefIterator[UID] = RefIterator.empty
    override def containsId(id: Int): Boolean = false

    override def equals(other: GrowableUIDTrieSet[_]): Boolean = other eq this
    override def hashCode: Int = 0
    override def toString: String = "GrowableUIDTrieSet()"

    override private[immutable] def +(
        value: UID,
        id:    Int,
        key:   Int,
        level: Int
    ): GrowableUIDTrieSetN[UID] = {
        this + value
    }

}

final class GrowableUIDTrieSet1[T <: UID](val i: T) extends GrowableUIDTrieSetL[T] {
    override def isEmpty: Boolean = false
    override def isSingletonSet: Boolean = true
    override def size: Int = 1
    override def foreach[U](f: T ⇒ U): Unit = { f(i) }
    override def forall(p: T ⇒ Boolean): Boolean = p(i)
    override def +(value: T): GrowableUIDTrieSetL[T] = {
        val v = this.i
        if (v.id == value.id) this else new GrowableUIDTrieSet2(v, value)
    }
    override def iterator: RefIterator[T] = RefIterator(i)
    override def containsId(id: Int): Boolean = id == i.id

    override def equals(other: GrowableUIDTrieSet[_]): Boolean = {
        (other eq this) || (other match {
            case that: GrowableUIDTrieSet1[_] ⇒ this.i.id == that.i.id
            case that                         ⇒ false
        })
    }

    override def hashCode: Int = i.id

    override def toString: String = s"GrowableUIDTrieSet($i)"

    override private[immutable] def +(
        value: T,
        id:    Int,
        key:   Int,
        level: Int
    ): GrowableUIDTrieSetN[T] = {
        this + value
    }

}

/**
 * Represents an ordered set of two values where i1 has to be smaller than i2.
 */
private[immutable] final class GrowableUIDTrieSet2[T <: UID] private[immutable] (
        val i1: T, val i2: T
) extends GrowableUIDTrieSetL[T] {

    override def isEmpty: Boolean = false
    override def isSingletonSet: Boolean = false
    override def size: Int = 2
    override def iterator: RefIterator[T] = RefIterator(i1, i2)
    override def foreach[U](f: T ⇒ U): Unit = { f(i1); f(i2) }
    override def forall(p: T ⇒ Boolean): Boolean = { p(i1) && p(i2) }
    override def containsId(id: Int): Boolean = id == i1.id || id == i2.id
    override def +(value: T): GrowableUIDTrieSetL[T] = {
        val id = value.id

        val i1 = this.i1
        if (i1.id == id) {
            return this;
        }

        val i2 = this.i2
        if (i2.id == id) {
            this
        } else {
            new GrowableUIDTrieSet3(i1, i2, value)
        }
    }

    override def equals(other: GrowableUIDTrieSet[_]): Boolean = {
        (other eq this) || (
            other match {
                case that: GrowableUIDTrieSet2[_] ⇒
                    (this.i1.id == that.i1.id && this.i2.id == that.i2.id) ||
                        (this.i1.id == that.i2.id && this.i2.id == that.i1.id)
                case that ⇒
                    false
            }
        )
    }

    override def hashCode: Int = i1.id ^ i2.id // ordering independent

    override def toString: String = s"GrowableUIDTrieSet($i1, $i2)"

    override private[immutable] def +(
        value: T,
        id:    Int,
        key:   Int,
        level: Int
    ): GrowableUIDTrieSetN[T] = {
        this + value
    }

}

/**
 * Represents an ordered set of three int values: i1 < i2 < i3.
 */
private[immutable] final class GrowableUIDTrieSet3[T <: UID] private[immutable] (
        val i1: T, val i2: T, val i3: T
) extends GrowableUIDTrieSetL[T] {

    override def size: Int = 3
    override def isEmpty: Boolean = false
    override def isSingletonSet: Boolean = false
    override def containsId(id: Int): Boolean = id == i1.id || id == i2.id || id == i3.id
    override def iterator: RefIterator[T] = RefIterator(i1, i2, i3)
    override def foreach[U](f: T ⇒ U): Unit = { f(i1); f(i2); f(i3) }
    override def forall(p: T ⇒ Boolean): Boolean = { p(i1) && p(i2) && p(i3) }

    override def +(value: T): GrowableUIDTrieSet[T] = {
        val id = value.id
        val newSet = this + (value, id, id, 0)
        if (newSet ne this)
            new GrowableUIDTrieSet4x(4, newSet)
        else
            this
    }

    override def equals(other: GrowableUIDTrieSet[_]): Boolean = {
        (other eq this) || (
            other match {
                case that: GrowableUIDTrieSet3[_] ⇒
                    that.containsId(this.i1.id) &&
                        that.containsId(this.i2.id) &&
                        that.containsId(this.i3.id)
                case that ⇒
                    false
            }
        )
    }

    override def hashCode: Int = i1.id ^ i2.id ^ i3.id // ordering independent

    override def toString: String = s"GrowableUIDTrieSet($i1, $i2, $i3)"

    override private[immutable] def +(
        value: T,
        id:    Int,
        key:   Int,
        level: Int
    ): GrowableUIDTrieSetN[T] = {
        val i1 = this.i1
        val i1Id = i1.id
        if (id == i1Id)
            return this;

        val i2 = this.i2
        val i2Id = i2.id
        if (id == i2Id)
            return this;

        val i3 = this.i3
        val i3Id = i3.id
        if (id == i3Id)
            return this;

        if ((key & 1) == 0) {
            if ((i1Id >> level & 1) == 0) {
                if ((i2Id >> level & 1) == 0) {
                    if ((i3Id >> level & 1) == 0) {
                        new GrowableUIDTrieSetNode_0(value, this)
                    } else {
                        new GrowableUIDTrieSetNode_0(
                            i3,
                            new GrowableUIDTrieSet3(value, i1, i2)
                        )
                    }
                } else {
                    if ((i3Id >> level & 1) == 0) {
                        new GrowableUIDTrieSetNode_0(
                            i2,
                            new GrowableUIDTrieSet3(value, i1, i3)
                        )
                    } else {
                        new GrowableUIDTrieSetNode_0_1(
                            value,
                            new GrowableUIDTrieSet1(i1),
                            new GrowableUIDTrieSet2(i2, i3)
                        )
                    }
                }
            } else {
                // value >  _0, i1 =>  _1, ...
                if ((i2Id >> level & 1) == 0) {
                    if ((i3Id >> level & 1) == 0) {
                        new GrowableUIDTrieSetNode_0(
                            i1,
                            new GrowableUIDTrieSet3(value, i2, i3)
                        )
                    } else {
                        // value >  _0, i1 =>  _1, i2 => _ 0, i3 => _1
                        new GrowableUIDTrieSetNode_0_1(
                            value,
                            new GrowableUIDTrieSet1(i2),
                            new GrowableUIDTrieSet2(i1, i3)
                        )
                    }
                } else {
                    // value >  _0, i1 =>  _1, i2 => _ 1, ...
                    if ((i3Id >> level & 1) == 0) {
                        new GrowableUIDTrieSetNode_0_1(
                            value,
                            new GrowableUIDTrieSet1(i3),
                            new GrowableUIDTrieSet2(i1, i2)
                        )
                    } else {
                        new GrowableUIDTrieSetNode_1(value, this)
                    }
                }
            }
        } else {
            // value =>  _1,  ...
            if ((i1Id >> level & 1) == 0) {
                if ((i2Id >> level & 1) == 0) {
                    if ((i3Id >> level & 1) == 0) {
                        new GrowableUIDTrieSetNode_0(value, this)
                    } else {
                        new GrowableUIDTrieSetNode_0_1(
                            value,
                            new GrowableUIDTrieSet2(i1, i2),
                            new GrowableUIDTrieSet1(i3)
                        )
                    }
                } else {
                    // value =>  _1, i1 => 0,  i2 => _1
                    if ((i3Id >> level & 1) == 0) {
                        new GrowableUIDTrieSetNode_0_1(
                            value,
                            new GrowableUIDTrieSet2(i1, i3),
                            new GrowableUIDTrieSet1(i2)
                        )
                    } else {
                        new GrowableUIDTrieSetNode_1(
                            i1,
                            new GrowableUIDTrieSet3(value, i2, i3)
                        )
                    }
                }
            } else {
                // value =>  _1, i1 =>  _1, ...
                if ((i2Id >> level & 1) == 0) {
                    if ((i3Id >> level & 1) == 0) {
                        new GrowableUIDTrieSetNode_0_1(
                            value,
                            new GrowableUIDTrieSet2(i2, i3),
                            new GrowableUIDTrieSet1(i1)
                        )
                    } else {
                        // value =>  _1, i1 =>  _1, i2 => _ 0, i3 => _1
                        new GrowableUIDTrieSetNode_1(
                            i2,
                            new GrowableUIDTrieSet3(value, i1, i3)
                        )
                    }
                } else {
                    // value =>  _1, i1 =>  _1, i2 => _ 1, ...
                    if ((i3Id >> level & 1) == 0) {
                        new GrowableUIDTrieSetNode_1(
                            i3,
                            new GrowableUIDTrieSet3(value, i1, i2)
                        )
                    } else {
                        new GrowableUIDTrieSetNode_1(value, this)
                    }
                }
            }
        }
    }
}

private[immutable] final class GrowableUIDTrieSet4x[T <: UID](
        val size: Int,
        root:     GrowableUIDTrieSetN[T]
) extends GrowableUIDTrieSet[T] {

    assert(size >= 4)

    override def isEmpty: Boolean = false
    override def isSingletonSet: Boolean = false
    override def containsId(id: Int): Boolean = root.containsId(id, id)
    override def foreach[U](f: T ⇒ U): Unit = root.foreach(f)
    override def forall(p: T ⇒ Boolean): Boolean = root.forall(p)

    override def iterator: RefIterator[T] = new RefIterator[T] {
        private[this] var currentNode = root
        private[this] var index = 0
        private[this] val furtherNodes = RefArrayStack.empty[GrowableUIDTrieSetN[T]]
        def hasNext: Boolean = currentNode ne null
        def next: T = {
            (this.currentNode: @unchecked) match {
                case n: GrowableUIDTrieSet1[T] ⇒
                    this.currentNode = if (furtherNodes.nonEmpty) furtherNodes.pop() else null
                    n.i
                case n: GrowableUIDTrieSet2[T] ⇒
                    if (index == 0) {
                        index = 1
                        n.i1
                    } else {
                        this.currentNode = if (furtherNodes.nonEmpty) furtherNodes.pop() else null
                        index = 0
                        n.i2
                    }
                case n: GrowableUIDTrieSet3[T] ⇒
                    if (index == 0) {
                        index = 1
                        n.i1
                    } else if (index == 1) {
                        index = 2
                        n.i2
                    } else {
                        this.currentNode = if (furtherNodes.nonEmpty) furtherNodes.pop() else null
                        index = 0
                        n.i3
                    }
                case n: GrowableUIDTrieSetNode_0[T] ⇒
                    currentNode = n._0
                    n.v
                case n: GrowableUIDTrieSetNode_1[T] ⇒
                    currentNode = n._1
                    n.v
                case n: GrowableUIDTrieSetNode_0_1[T] ⇒
                    currentNode = n._0
                    furtherNodes.push(n._1)
                    n.v
            }
        }
    }

    override def equals(that: GrowableUIDTrieSet[_]): Boolean = {
        (that eq this) || (that.size == this.size && this.forall(uid ⇒ that.containsId(uid.id)))
    }

    override def hashCode: Int = root.hashCode * size

    override def +(value: T): GrowableUIDTrieSet[T] = {
        val id = value.id
        val root = this.root
        val newRoot = root + (value, id, id, 0)
        if (newRoot ne root) {
            new GrowableUIDTrieSet4x(size + 1, newRoot)
        } else {
            this
        }
    }

    override def toString: String = s"GrowableUIDTrieSet(#$size, ${root.toString(1)})"

}

private[immutable] final class GrowableUIDTrieSetNode_0_1[T <: UID](
        val v:  T, // value with the current prefix...
        val _0: GrowableUIDTrieSetN[T],
        val _1: GrowableUIDTrieSetN[T]
) extends GrowableUIDTrieSetN[T] {

    override def foreach[U](f: T ⇒ U): Unit = { f(v); _0.foreach(f); _1.foreach(f) }

    override def forall(p: T ⇒ Boolean): Boolean = { p(v) && _0.forall(p) && _1.forall(p) }

    override def hashCode: Int = v.id ^ _0.hashCode ^ _1.hashCode

    override private[immutable] def +(
        value: T,
        id:    Int,
        key:   Int,
        level: Int
    ): GrowableUIDTrieSetN[T] = {
        val v = this.v
        if (v.id != id) {
            if ((key & 1) == 0) {
                val new_0 = _0.+(value, id, key >> 1, level + 1)
                if (new_0 ne _0) {
                    new GrowableUIDTrieSetNode_0_1(v, new_0, _1)
                } else {
                    this
                }
            } else {
                val new_1 = _1.+(value, id, key >> 1, level + 1)
                if (new_1 ne _1) {
                    new GrowableUIDTrieSetNode_0_1(v, _0, new_1)
                } else {
                    this
                }
            }
        } else {
            this
        }
    }

    override private[immutable] def containsId(id: Int, key: Int): Boolean = {
        this.v.id == id || {
            val newKey = key >> 1
            if ((key & 1) == 0) _0.containsId(id, newKey) else _1.containsId(id, newKey)
        }
    }

    override private[immutable] def toString(indent: Int): String = {
        val spaces = " " * indent
        s"N($v,\n${spaces}0=>${_0.toString(indent + 1)},\n${spaces}1=>${_1.toString(indent + 1)})"
    }

}

private[immutable] final class GrowableUIDTrieSetNode_0[T <: UID](
        val v:  T,
        val _0: GrowableUIDTrieSetN[T]
) extends GrowableUIDTrieSetN[T] {

    override def hashCode: Int = v.id ^ _0.hashCode

    override def foreach[U](f: T ⇒ U): Unit = { f(v); _0.foreach(f) }

    override def forall(p: T ⇒ Boolean): Boolean = { p(v) && _0.forall(p) }

    override private[immutable] def +(
        value: T,
        id:    Int,
        key:   Int,
        level: Int
    ): GrowableUIDTrieSetN[T] = {
        val v = this.v
        val vId = v.id
        if (vId != id) {
            if ((key & 1) == 0) {
                // let's check if we can improve the balancing of the tree by putting
                // the new value in this node and moving this node further down the tree...
                if (((vId >> level) & 1) == 1) {
                    if (!_0.containsId(id, key >> 1)) {
                        new GrowableUIDTrieSetNode_0_1(value, _0, new GrowableUIDTrieSet1(v))
                    } else {
                        this
                    }
                } else {
                    val new_0 = _0.+(value, id, key >> 1, level + 1)
                    if (new_0 ne _0) {
                        new GrowableUIDTrieSetNode_0(v, new_0)
                    } else {
                        this
                    }
                }
            } else {
                val new_1 = new GrowableUIDTrieSet1(value)
                new GrowableUIDTrieSetNode_0_1(v, _0, new_1)
            }
        } else {
            this
        }
    }

    private[immutable] def containsId(id: Int, key: Int): Boolean = {
        this.v.id == id || ((key & 1) == 0 && _0.containsId(id, key >> 1))
    }

    private[immutable] def toString(indent: Int): String = {
        val spaces = " " * indent
        s"N($v,\n${spaces}0=>${_0.toString(indent + 1)})"
    }
}

private[immutable] final class GrowableUIDTrieSetNode_1[T <: UID](
        val v:  T,
        val _1: GrowableUIDTrieSetN[T]
) extends GrowableUIDTrieSetN[T] {

    override def hashCode: Int = v.id ^ _1.hashCode

    override def foreach[U](f: T ⇒ U): Unit = { f(v); _1.foreach(f) }

    override def forall(p: T ⇒ Boolean): Boolean = { p(v) && _1.forall(p) }

    override private[immutable] def +(
        value: T,
        id:    Int,
        key:   Int,
        level: Int
    ): GrowableUIDTrieSetN[T] = {
        val v = this.v
        val vId = v.id
        if (vId != id) {
            if ((key & 1) == 0) {
                val new_0 = new GrowableUIDTrieSet1(value)
                new GrowableUIDTrieSetNode_0_1(v, new_0, _1)
            } else {
                // let's check if we can improve the balancing of the tree by putting
                // the new value in this node and moving this node further down the tree...
                if (((vId >> level) & 1) == 0) {
                    if (!_1.containsId(id, key >> 1)) {
                        new GrowableUIDTrieSetNode_0_1(value, new GrowableUIDTrieSet1(v), _1)
                    } else {
                        this
                    }
                } else {
                    val new_1 = _1.+(value, id, key >> 1, level + 1)
                    if (new_1 ne _1) {
                        new GrowableUIDTrieSetNode_1(v, new_1)
                    } else {
                        this
                    }
                }
            }
        } else {
            this
        }
    }

    override private[immutable] def containsId(id: Int, key: Int): Boolean = {
        this.v.id == id || ((key & 1) == 1 && _1.containsId(id, key >> 1))
    }

    override private[immutable] def toString(indent: Int): String = {
        val spaces = " " * indent
        s"N($v,\n${spaces}1=>${_1.toString(indent + 1)})"
    }

}