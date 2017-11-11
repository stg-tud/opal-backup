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
package properties

sealed trait TypeImmutabilityPropertyMetaInformation extends PropertyMetaInformation {

    final type Self = TypeImmutability
}

/**
 * Specifies if all instances of a respective type (this includes the instances of the
 * type's subtypes) are (conditionally) immutable. Conditionally immutable means that only the
 * instance of the type itself is guaranteed to be immutable, but not all reachable objects.
 * In general, all -- so called -- immutable collections are only conditionally immutable. I.e.,
 * the collection as a whole is only immutable if only immutable objects are stored in the
 * collection. If this is not the case, the collection is only conditionally immutable.
 *
 * This property is of particular interest if the precise type cannot be computed statically. This
 * property basically depends on the [[org.opalj.br.analyses.TypeExtensibilityKey]] and
 * [[ClassImmutability]].
 *
 * @author Michael Eichberg
 */
sealed trait TypeImmutability extends OrderedProperty with TypeImmutabilityPropertyMetaInformation {

    /**
     * Returns the key used by all `TypeImmutability` properties.
     */
    final def key = TypeImmutability.key

    def isMutable: Boolean

    def isConditionallyImmutable: Boolean

    def join(other: TypeImmutability): TypeImmutability
}
/**
 * Common constants use by all [[TypeImmutability]] properties associated with methods.
 */
object TypeImmutability extends TypeImmutabilityPropertyMetaInformation {

    /**
     * The key associated with every [[TypeImmutability]] property.
     */
    final val key: PropertyKey[TypeImmutability] = PropertyKey.create(
        "TypeImmutability",
        // The default property that will be used if no analysis is able
        // to (directly) compute the respective property.
        MutableType,
        // When we have a cycle all properties are necessarily at least conditionally
        // immutable hence, we can leverage the "immutability" of one of the members of
        // the cycle and wait for the automatic propagation...
        ImmutableType
    )
}

/**
 * An instance of the respective class is effectively immutable. I.e., after creation it is not
 * possible for a client to set a field or to call a method that updates the internal state
 */
case object ImmutableType extends TypeImmutability {

    final val isRefineable = false
    final val isMutable = false
    final val isConditionallyImmutable = false

    def join(that: TypeImmutability): TypeImmutability = {
        if (this == that) this else that
    }

    def isValidSuccessorOf(other: OrderedProperty): Option[String] = {
        if (other.isRefineable)
            None
        else
            Some(s"impossible refinement of $other to $this")
    }
}

case object UnknownTypeImmutability extends TypeImmutability {

    final val isRefineable = true
    final val isMutable = false
    final val isConditionallyImmutable = false

    def join(that: TypeImmutability): TypeImmutability = {
        if (that == MutableType) MutableType else this
    }

    def isValidSuccessorOf(other: OrderedProperty): Option[String] = {
        if (other == UnknownTypeImmutability)
            None
        else
            Some(s"impossible refinement of $other to $this")
    }
}

case object ConditionallyImmutableType extends TypeImmutability {

    final val isRefineable = false
    final val isMutable = false
    final val isConditionallyImmutable = true

    def join(that: TypeImmutability): TypeImmutability = {
        that match {
            case MutableType | UnknownTypeImmutability ⇒ that
            case _                                     ⇒ this
        }
    }

    def isValidSuccessorOf(other: OrderedProperty): Option[String] = {
        if (other.isRefineable)
            None
        else
            Some(s"impossible refinement of $other to $this")
    }

}

case object AtLeastConditionallyImmutableType extends TypeImmutability {

    final val isRefineable = true
    final val isMutable = false
    final val isConditionallyImmutable = false

    def join(other: TypeImmutability): TypeImmutability = {
        other match {
            case MutableType | UnknownTypeImmutability | ConditionallyImmutableType ⇒ other
            case _ ⇒ this
        }
    }

    def isValidSuccessorOf(other: OrderedProperty): Option[String] = {
        if (other.isRefineable)
            None
        else
            Some(s"impossible refinement of $other to $this")
    }

}

case object MutableType extends TypeImmutability {

    final val isRefineable = false
    final val isMutable = true
    final val isConditionallyImmutable = false

    def join(other: TypeImmutability): this.type = this

    def isValidSuccessorOf(other: OrderedProperty): Option[String] = {
        if (other.isRefineable)
            None
        else
            Some(s"impossible refinement of $other to $this")
    }

}
