/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2014
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

import org.opalj.collection.immutable.UIDSet

import br._

/**
 * Implementation of an abstract interpretation framework – also referred to as OPAL.
 *
 * Please note, that OPAL/the abstract interpreter just refers to the classes and traits
 * defined in this package (`ai`). The classes and traits defined in the sub-packages
 * (in particular in `domain`) are not considered to be part of the core of OPAL/the
 * abstract interpreter.
 *
 * @note This framework assumes that the analyzed bytecode is valid; i.e., the JVM's
 *      bytecode verifier would be able to verify the code. Furthermore, load-time errors
 *      (e.g., `LinkageErrors`) are – by default – completely ignored to facilitate the
 *      analysis of parts of a project. In general, if the presented bytecode is not valid,
 *      the result is undefined (i.e., OPAL may report meaningless results, crash or run
 *      indefinitely).
 *
 * @see [[org.opalj.ai.AI]] - Implements the abstract interpreter that
 *      processes a methods code and uses an analysis-specific domain to perform the
 *      abstract computations.
 * @see [[org.opalj.ai.Domain]] - The core interface between the abstract
 *      interpretation framework and the abstract domain that is responsible for
 *      performing the abstract computations.
 *
 * @author Michael Eichberg
 */
package object ai {

    import language.existentials

    /**
     * Type alias that can be used if the AI can use all kinds of domains.
     *
     * @note This type alias serves comprehension purposes only.
     */
    type SomeAI[D <: Domain] = AI[_ >: D]

    /**
     * A value of type `ValueOrigin` identifies the origin of a value. In most cases the
     * value is equal to the program counter of the instruction that created the value.
     * However, for the values passed to a method, the index is conceptually:
     *  `-1-(isStatic ? 0 : 1)-(the index of the parameter adjusted by the computational
     * type of the previous parameters)`.
     *
     * For example, in case of an instance method with the signature:
     * {{{
     * public void (double d/*parameter index:0*/, Object o/*parameter index:1*/){...}
     * }}}
     *
     *  - The value `-1` is used to identify the implicit `this` reference.
     *
     *  - The value `-2` identifies the value of the parameter `d`.
     *
     *  - The value `-4` identifies the parameter `o`. (The parameter `d` is a value of
     * computational-type category 2 and needs two stack/operands values.)
     *
     * The range of values is: [-257,65535]. Hence, whenever a value of type `ValueOrigin`
     * is required/is expected it is possible to use a value with type `PC` unless
     * the program counters identifies the start of a subroutine ([[SUBROUTINE_START]],
     * [[SUBROUTINE_END]], [[SUBROUTINE]]).
     *
     * Recall that the maximum size of the method
     * parameters array is 255. If necessary, the first slot is required for the `this`
     * reference. Furthermore, for `long` and `double` values two slots are necessary; hence
     * the smallest number used to encode that the value is an actual parameter is
     * `-256`.
     *
     * The value `-257` is used to encode that the origin of the value is out
     * of the scope of the analyzed program ([[ConstantValueOrigin]]). This value is
     * currently only used for the implicit value of `IF_XXX` instructions.
     */
    type ValueOrigin = Int

    /**
     * Special value that is added to the work list/list of evaluated instructions
     * before the program counter of the first
     * instruction of a subroutine.
     */
    // some value smaller than -65536 to avoid confusion with local variable indexes
    // in the worklist this value is replaced by the local variable index
    // once we encounter a ret insruction.
    final val SUBROUTINE_START = -80000008
    /**
     * Special value that is added to the list of evaluated instructions
     * to mark the end of the evaluation of a subroutine.
     */
    final val SUBROUTINE_END = -88888888

    /**
     * Special value that is added to the work list to mark the beginning of a
     * subroutine call.
     */
    final val SUBROUTINE = -90000009 // some value smaller than -2^16

    /**
     * Used to identify that the origin of the value is outside of the program.
     *
     * For example, the VM sometimes performs comparisons against predetermined fixed
     * values (specified in the JVM Spec.). The origin associated with such values is
     * determined by this value.
     */
    final val ConstantValueOrigin: ValueOrigin = -257

    type Operands[T >: Null <: Domain#DomainValue] = List[T]
    type AnOperandsArray[T >: Null <: Domain#DomainValue] = Array[Operands[T]]
    type TheOperandsArray[T >: Null <: (Domain with Singleton)#Operands] = Array[T]

    type Locals[T >: Null <: Domain#DomainValue] = org.opalj.ai.util.Locals[T]
    type ALocalsArray[T >: Null <: Domain#DomainValue] = Array[Locals[T]]
    type TheLocalsArray[T >: Null <: (Domain with Singleton)#Locals] = Array[T]

    def memoryLayoutToText(
        domain: Domain)(
            operandsArray: domain.OperandsArray,
            localsArray: domain.LocalsArray): String = {
        (
            for {
                ((operands, locals), pc) ← (operandsArray.zip(localsArray)).zipWithIndex
                if operands != null /*|| locals != null*/
            } yield {
                val localsWithIndex =
                    for {
                        (local, index) ← locals.zipWithIndex
                        if (local != null)
                    } yield {
                        "("+index+":"+local+")"
                    }

                "PC: "+pc + operands.mkString("\n\tOperands: ", " <- ", "") +
                    localsWithIndex.mkString("\n\tLocals: [", ",", "]")
            }
        ).mkString("Operands and Locals: \n", "\n", "\n")
    }
}
