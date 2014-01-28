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
package l0

import de.tud.cs.st.util.{ Answer, Yes, No, Unknown }

/**
 * Support the computation with long values at the type level.
 *
 * @author Michael Eichberg
 */
trait TypeLevelLongValues[+I] extends Domain[I] {

    // -----------------------------------------------------------------------------------
    //
    // REPRESENTATION OF LONG VALUES
    //
    // -----------------------------------------------------------------------------------

    trait LongValue
            extends Value
            with IsLongValue { this: DomainValue ⇒

        override final def computationalType: ComputationalType = ComputationalTypeLong

    }

    // -----------------------------------------------------------------------------------
    //
    // HANDLING OF COMPUTATIONS
    //
    // -----------------------------------------------------------------------------------

    //
    // RELATIONAL OPERATORS
    //
    override def lcmp(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue =
        IntegerValue(pc)

    //
    // UNARY EXPRESSIONS
    //
    override def lneg(pc: PC, value: DomainValue) = LongValue(pc)

    //
    // BINARY EXPRESSIONS
    //

    override def ladd(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue =
        LongValue(pc)

    override def land(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue =
        LongValue(pc)

    override def ldiv(
        pc: PC,
        value1: DomainValue,
        value2: DomainValue): IntegerLikeValueOrArithmeticException =
        ComputedValue(LongValue(pc))

    override def lmul(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue =
        LongValue(pc)

    override def lor(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue =
        LongValue(pc)

    override def lrem(
        pc: PC,
        value1: DomainValue,
        value2: DomainValue): IntegerLikeValueOrArithmeticException =
        ComputedValue(LongValue(pc))

    override def lshl(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue =
        LongValue(pc)

    override def lshr(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue =
        LongValue(pc)

    override def lsub(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue =
        LongValue(pc)

    override def lushr(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue =
        LongValue(pc)

    override def lxor(pc: PC, value1: DomainValue, value2: DomainValue): DomainValue =
        LongValue(pc)

    //
    // CONVERSION INSTRUCTIONS
    //

    override def l2d(pc: PC, value: DomainValue): DomainValue =
        DoubleValue(pc)

    override def l2f(pc: PC, value: DomainValue): DomainValue =
        FloatValue(pc)

    override def l2i(pc: PC, value: DomainValue): DomainValue =
        IntegerValue(pc)
}



