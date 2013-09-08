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

/**
 * Basic implementations of methods for loading and storing values in/from arrays.
 *
 * (BY default we ignore `ArrayStoreExceptions`, `ArrayIndexOutOfBoundsExceptions`
 * and `NegativeArraySizeExceptions`.)
 *
 * @author Michael Eichberg
 */
trait TypeLevelArrayInstructions { this: Domain[_] ⇒

    //
    // STORING AND LOADING VALUES FROM ARRAYS
    //

    def baload(pc: Int, index: DomainValue, arrayref: DomainValue): ArrayLoadResult =
        types(arrayref) match {
            case HasSingleReferenceTypeBound(ArrayType(componentType)) ⇒
                ComputedValue(TypedValue(componentType))
            case _ ⇒ domainException(
                this,
                "cannot determine the type of the array's content, the array may contain either booleans or byte values: "+arrayref
            )
        }

    def bastore(pc: Int, value: DomainValue, index: DomainValue, arrayref: DomainValue) =
        ComputationWithSideEffectOnly

    def caload(pc: Int, index: DomainValue, arrayref: DomainValue): ArrayLoadResult =
        ComputedValue(SomeCharValue)
    def castore(pc: Int, value: DomainValue, index: DomainValue, arrayref: DomainValue) =
        ComputationWithSideEffectOnly

    def daload(pc: Int, index: DomainValue, arrayref: DomainValue): ArrayLoadResult =
        ComputedValue(SomeDoubleValue)
    def dastore(pc: Int, value: DomainValue, index: DomainValue, arrayref: DomainValue) =
        ComputationWithSideEffectOnly

    def faload(pc: Int, index: DomainValue, arrayref: DomainValue): ArrayLoadResult =
        ComputedValue(SomeFloatValue)
    def fastore(pc: Int, value: DomainValue, index: DomainValue, arrayref: DomainValue) =
        ComputationWithSideEffectOnly

    def iaload(pc: Int, index: DomainValue, arrayref: DomainValue): ArrayLoadResult =
        ComputedValue(SomeIntegerValue)
    def iastore(pc: Int, value: DomainValue, index: DomainValue, arrayref: DomainValue) =
        ComputationWithSideEffectOnly

    def laload(pc: Int, index: DomainValue, arrayref: DomainValue): ArrayLoadResult =
        ComputedValue(SomeLongValue)
    def lastore(pc: Int, value: DomainValue, index: DomainValue, arrayref: DomainValue) =
        ComputationWithSideEffectOnly

    def saload(pc: Int, index: DomainValue, arrayref: DomainValue): ArrayLoadResult =
        ComputedValue(SomeShortValue)
    def sastore(pc: Int, value: DomainValue, index: DomainValue, arrayref: DomainValue) =
        ComputationWithSideEffectOnly

    //
    // LENGTH OF AN ARRAY
    //
    def arraylength(pc: Int, value: DomainValue): NumericValueOrNullPointerException =
        ComputedValue(SomeIntegerValue)
}


