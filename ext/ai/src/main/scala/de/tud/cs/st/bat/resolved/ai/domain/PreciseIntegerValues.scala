/* License (BSD Style License):
 * Copyright (c) 2009 - 2013
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
 *  - Neither the name of the Software Technology Group or Technische
 *    Universität Darmstadt nor the names of its contributors may be used to
 *    endorse or promote products derived from this software without specific
 *    prior written permission.
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
package de.tud.cs.st
package bat
package resolved
package ai
package domain

import de.tud.cs.st.util.{ Answer, Yes, No, Unknown }

/**
 * Domain to track integer values at a configurable level of precision.
 *
 * @author Michael Eichberg
 */
trait PreciseIntegerValues[+I] extends Domain[I] {

    // -----------------------------------------------------------------------------------
    //
    // REPRESENTATION OF INTEGER LIKE VALUES
    //
    // -----------------------------------------------------------------------------------

    /**
     * Determines for an integer value that is updated how large the update can be
     * before we stop the precise tracking of the value and represent the respective
     * value as "some integer value".
     *
     * This value is only taken into consideration when two paths are merged.
     *
     * The default value is 25 which will effectively unroll a loop with a loop
     * counter that is incremented by one in each round up to 25 times.
     *
     * This is a runtime configurable setting that may affect the overall precision of
     * subsequent analyses that require knowledge about integers.
     */
    def maxSpread = 25

    /**
     * Determines if an exception is thrown in case of a '''potential''' division by zero.
     * I.e., this setting controls whether we throw a division by zero exception if we
     * know nothing about the concrete value of the denominator or not.
     * However, if we know that the denominator is 0 we will always throw an exception.
     */
    def divisionByZeroIfUnknown = true

    private final val typesAnswer: IsPrimitiveType = IsPrimitiveType(IntegerType)

    /**
     * Abstracts over all values with computational type `integer`.
     */
    sealed trait IntegerLikeValue extends Value { this: DomainValue ⇒

        final def computationalType: ComputationalType = ComputationalTypeInt

        final def types: TypesAnswer[_] = typesAnswer

    }

    protected def newIntegerValue(): DomainValue

    protected def newByteValue(): DomainValue

    /**
     * Represents a specific, but unknown integer value.
     *
     * Models the top value of this domain's lattice.
     */
    trait AnIntegerValue extends IntegerLikeValue { this: DomainValue ⇒ }

    /**
     * Represents a concrete integer value.
     */
    trait IntegerValue extends IntegerLikeValue { this: DomainValue ⇒

        val initial: Int

        val value: Int

        /**
         * Creates a new IntegerValue with the given value as the current value,
         * but the same initial value.
         *
         * This method must not check whether the initial value and the new value
         * exceed the spread. This is done by the merge method.
         */
        protected def update(newValue: Int): DomainValue

        private[PreciseIntegerValues] def updateValue(newValue: Int): DomainValue =
            update(newValue)
    }

    protected def spread(a: Int, b: Int) = Math.abs(a - b)

    abstract override def types(value: DomainValue): TypesAnswer[_] =
        value match {
            case integerLikeValue: IntegerLikeValue ⇒ integerLikeValue.types
            case _                                  ⇒ super.types(value)
        }

    def newCharValue(pc: Int, value: Char): DomainValue

    //
    // QUESTION'S ABOUT VALUES
    //

    protected def getIntValue[T](value: DomainValue)(f: Int ⇒ T)(orElse: ⇒ T): T =
        value match {
            case v: IntegerValue ⇒ f(v.value)
            case _               ⇒ orElse
        }

    protected def getIntValues[T](
        value1: DomainValue,
        value2: DomainValue)(
            f: (Int, Int) ⇒ T)(orElse: ⇒ T): T =
        getIntValue(value1) { v1 ⇒
            getIntValue(value2) { v2 ⇒ f(v1, v2) }(orElse)
        } {
            orElse
        }

    def areEqual(value1: DomainValue, value2: DomainValue): Answer =
        getIntValues(value1, value2) { (v1, v2) ⇒ Answer(v1 == v2) } { Unknown }

    def isSomeValueInRange(
        value: DomainValue,
        lowerBound: Int,
        upperBound: Int): Boolean =
        getIntValue(value) { v ⇒ lowerBound <= v && v <= upperBound }(true)

    def isSomeValueNotInRange(
        value: DomainValue,
        lowerBound: Int,
        upperBound: Int): Boolean =
        getIntValue(value) { v ⇒
            v < lowerBound || v > upperBound
        } {
            !(lowerBound == Int.MinValue && upperBound == Int.MaxValue)
        }

    def isLessThan(
        smallerValue: DomainValue,
        largerValue: DomainValue): Answer =
        getIntValues(smallerValue, largerValue) { (v1, v2) ⇒ Answer(v1 < v2) } { Unknown }

    def isLessThanOrEqualTo(
        smallerOrEqualValue: DomainValue,
        equalOrLargerValue: DomainValue): Answer =
        getIntValues(smallerOrEqualValue, equalOrLargerValue) { (v1, v2) ⇒
            Answer(v1 <= v2)
        } { Unknown }

    protected def updateLocals(
        oldValue: DomainValue,
        newValue: DomainValue,
        operands: Operands,
        locals: Locals): (Operands, Locals) =
        (
            operands,
            locals.map { local ⇒ if (local eq oldValue) newValue else local }
        )

    override def establishValue(
        pc: Int,
        theValue: Int,
        value: DomainValue,
        operands: Operands,
        locals: Locals): (Operands, Locals) =
        updateLocals(value, newIntegerValue(pc, theValue), operands, locals)

    override def establishAreEqual(
        pc: Int,
        value1: DomainValue,
        value2: DomainValue,
        operands: Operands,
        locals: Locals): (Operands, Locals) = {
        getIntValue(value1) { v1 ⇒
            assume(value2.isInstanceOf[AnIntegerValue])
            val result = updateLocals(value2, newIntegerValue(pc, v1), operands, locals)
            result
        } {
            getIntValue(value2) { v2 ⇒
                val result = updateLocals(value1, newIntegerValue(pc, v2), operands, locals)
                result
            } {
                (operands, locals)
            }
        }
    }

    // -----------------------------------------------------------------------------------
    //
    // HANDLING OF COMPUTATIONS
    //
    // -----------------------------------------------------------------------------------

    //
    // UNARY EXPRESSIONS
    //
    def ineg(pc: Int, value: DomainValue) = value match {
        case v: IntegerValue ⇒ v.updateValue(-v.value)
        case _               ⇒ value
    }

    //
    // BINARY EXPRESSIONS
    //

    def iadd(pc: Int, value1: DomainValue, value2: DomainValue): DomainValue =
        getIntValues(value1, value2) { (v1, v2) ⇒
            newIntegerValue(pc, v1 + v2)
        } {
            newIntegerValue
        }

    def iand(pc: Int, value1: DomainValue, value2: DomainValue): DomainValue =
        getIntValues(value1, value2) { (v1, v2) ⇒
            newIntegerValue(pc, v1 & v2)
        } {
            newIntegerValue
        }

    def idiv(pc: Int, value1: DomainValue, value2: DomainValue): Computation[DomainValue, DomainValue] =
        getIntValues(value1, value2) { (v1, v2) ⇒
            if (v2 == 0)
                ThrowsException(newInitializedObject(pc, ObjectType.ArithmeticException))
            else
                ComputedValue(newIntegerValue(pc, v1 / v2))
        } {
            if (divisionByZeroIfUnknown)
                ComputedValueAndException(
                    newIntegerValue,
                    newInitializedObject(pc, ObjectType.ArithmeticException))
            else
                ComputedValue(newIntegerValue)
        }

    def imul(pc: Int, value1: DomainValue, value2: DomainValue): DomainValue =
        getIntValues(value1, value2) { (v1, v2) ⇒
            newIntegerValue(pc, v1 * v2)
        } {
            newIntegerValue
        }

    def ior(pc: Int, value1: DomainValue, value2: DomainValue): DomainValue =
        getIntValues(value1, value2) { (v1, v2) ⇒
            newIntegerValue(pc, v1 | v2)
        } {
            newIntegerValue
        }

    def irem(pc: Int, value1: DomainValue, value2: DomainValue): DomainValue =
        getIntValues(value1, value2) { (v1, v2) ⇒
            newIntegerValue(pc, v1 % v2)
        } {
            newIntegerValue
        }

    def ishl(pc: Int, value1: DomainValue, value2: DomainValue): DomainValue =
        getIntValues(value1, value2) { (v1, v2) ⇒
            newIntegerValue(pc, v1 << v2)
        } {
            newIntegerValue
        }

    def ishr(pc: Int, value1: DomainValue, value2: DomainValue): DomainValue =
        getIntValues(value1, value2) { (v1, v2) ⇒
            newIntegerValue(pc, v1 >> v2)
        } {
            newIntegerValue
        }

    def isub(pc: Int, value1: DomainValue, value2: DomainValue): DomainValue =
        getIntValues(value1, value2) { (v1, v2) ⇒
            newIntegerValue(pc, v1 - v2)
        } {
            newIntegerValue
        }

    def iushr(pc: Int, value1: DomainValue, value2: DomainValue): DomainValue =
        getIntValues(value1, value2) { (v1, v2) ⇒
            newIntegerValue(pc, v1 >>> v2)
        }(newIntegerValue)

    def ixor(pc: Int, value1: DomainValue, value2: DomainValue): DomainValue =
        getIntValues(value1, value2) { (v1, v2) ⇒
            newIntegerValue(pc, v1 ^ v2)
        } {
            newIntegerValue
        }

    def iinc(pc: Int, value: DomainValue, increment: Int) =
        value match {
            case v: IntegerValue ⇒ v.updateValue(v.value + increment)
            case _               ⇒ value
        }

    //
    // TYPE CONVERSION INSTRUCTIONS
    //

    def i2b(pc: Int, value: DomainValue): DomainValue =
        getIntValue(value)(v ⇒ newByteValue(pc, v.toByte))(newByteValue)

    def i2c(pc: Int, value: DomainValue): DomainValue =
        getIntValue(value)(v ⇒ newCharValue(pc, v.toChar))(newByteValue)

    def i2s(pc: Int, value: DomainValue): DomainValue =
        getIntValue(value)(v ⇒ newShortValue(pc, v.toShort))(newByteValue)

    def i2d(pc: Int, value: DomainValue): DomainValue = newDoubleValue(pc)
    def i2f(pc: Int, value: DomainValue): DomainValue = newFloatValue(pc)
    def i2l(pc: Int, value: DomainValue): DomainValue = newLongValue(pc)
}

trait DefaultPreciseIntegerValues[+I]
        extends DefaultValueBinding[I]
        with PreciseIntegerValues[I] {

    case class AnIntegerValue() extends super.AnIntegerValue {

        override def merge(pc: Int, value: DomainValue): Update[DomainValue] =
            value match {
                case _: IntegerLikeValue ⇒ NoUpdate
                case other               ⇒ MetaInformationUpdateIllegalValue
            }

        override def adapt[TDI >: I](targetDomain: Domain[TDI], pc: Int): targetDomain.DomainValue =
            targetDomain match {
                case d: DefaultPreciseIntegerValues[I] ⇒
                    this.asInstanceOf[targetDomain.DomainValue]
                case _ ⇒ super.adapt(targetDomain, pc)
            }
    }

    case class IntegerValue private (
        val initial: Int,
        val value: Int)
            extends super.IntegerValue {

        def this(value: Int) = this(value, value)

        protected def update(newValue: Int): DomainValue = IntegerValue(initial, newValue)

        override def merge(pc: Int, value: DomainValue): Update[DomainValue] =
            value match {
                case AnIntegerValue() ⇒ StructuralUpdate(value)
                case IntegerValue(otherInitial, otherValue) ⇒
                    if (this.value == otherValue) {
                        if (this.initial == otherInitial)
                            NoUpdate
                        else if (spread(this.value, this.initial) > spread(this.value, otherInitial)) {
                            MetaInformationUpdate(IntegerValue(this.initial, this.value))
                        } else {
                            MetaInformationUpdate(IntegerValue(otherInitial, this.value))
                        }
                    } else {
                        // the value is only allowed to grow in one direction!
                        val newInitial =
                            if (Math.abs(otherValue - this.initial) > Math.abs(otherValue - otherInitial))
                                this.initial
                            else
                                otherInitial
                        val spread = Math.abs(otherValue - newInitial)
                        if (spread > maxSpread || // test for the boundary condition
                            // test if the value is no longer growing in one direction
                            spread < Math.abs(this.value - this.initial)) {
                            StructuralUpdate(AnIntegerValue())
                        } else {
                            StructuralUpdate(IntegerValue(newInitial, otherValue))
                        }
                    }
                case other ⇒ MetaInformationUpdateIllegalValue
            }

        override def adapt[ThatI >: I](targetDomain: Domain[ThatI], pc: Int): targetDomain.DomainValue =
            if (targetDomain.isInstanceOf[DefaultPreciseIntegerValues[ThatI]]) {
                val thatDomain = targetDomain.asInstanceOf[DefaultPreciseIntegerValues[ThatI]]
                thatDomain.IntegerValue(this.initial, this.value).
                    asInstanceOf[targetDomain.DomainValue]
            } else {
                super.adapt(targetDomain, pc)
            }

        override def toString: String = "IntegerValue("+value+",initial="+initial+")"
    }

    def newBooleanValue(): DomainValue = AnIntegerValue()
    def newBooleanValue(pc: Int): DomainValue = AnIntegerValue()
    def newBooleanValue(pc: Int, value: Boolean): DomainValue =
        if (value) new IntegerValue(1) else new IntegerValue(0)

    def newByteValue() = AnIntegerValue()
    def newByteValue(pc: Int): DomainValue = AnIntegerValue()
    def newByteValue(pc: Int, value: Byte) = new IntegerValue(value)

    def newShortValue() = AnIntegerValue()
    def newShortValue(pc: Int): DomainValue = AnIntegerValue()
    def newShortValue(pc: Int, value: Short) = new IntegerValue(value)

    def newCharValue() = AnIntegerValue()
    def newCharValue(pc: Int): DomainValue = AnIntegerValue()
    def newCharValue(pc: Int, value: Char) = new IntegerValue(value)

    def newIntegerValue() = AnIntegerValue()
    def newIntegerValue(pc: Int): DomainValue = AnIntegerValue()
    def newIntegerValue(pc: Int, value: Int) = new IntegerValue(value)
    def newIntegerConstant0: DomainValue = new IntegerValue(0)

}

