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
package ai

/**
 * Centralizes all configuration options related to how a domain should handle
 * situations in which the information about a value is (often) not completely available and
 * which could lead to some kind of exception.
 *
 * Basically all domains that perform some kind of abstraction should mix in this trait
 * and query the respective method to decide if a respective exception should be thrown
 * if it is possible that an exception may be thrown.
 *
 * ==Usage==
 * If you need to adapt a setting just override the respective method in your domain.
 *
 * In general, the [[org.opalj.ai.domain.ThrowAllPotentialExceptionsConfiguration]]
 * should be used as it generates all exceptions that may be thrown.
 *
 * @author Michael Eichberg
 */
trait Configuration {

    /**
     * Determines the behavior how method calls are handled where the exceptions that the
     * called method may throw are unknown.
     */
    def throwExceptionsOnMethodCall: ExceptionsRaisedByCalledMethod

    /**
     * If `true` a `NullPointerExceptions` is thrown if the exception that is to be
     * thrown is not not known to be null.
     */
    def throwNullPointerExceptionOnThrow: Boolean

    /**
     * Returns `true` if potential `NullPointerExceptions` should be thrown and `false`
     * if such `NullPointerExceptions` should be ignored. However, if the interpreter
     * identifies a situation in which a `NullPointerException` is guaranteed to be
     * thrown, it will be thrown. Example:
     * {{{
     * def demo(o : Object) {
     *      o.toString  // - If "true", a NullPointerException will ALSO be thrown;
     *                  //   the operation also succeeds.
     *                  // - If "false" the operation will "just" succeed
     * }
     * }}}
     */
    def throwNullPointerExceptionOnMethodCall: Boolean

    /**
     * Returns `true` if potential `NullPointerExceptions` should be thrown and `false`
     * if such `NullPointerExceptions` should be ignored. However, if the interpreter
     * identifies a situation in which a `NullPointerException` is guaranteed to be
     * thrown, it will be thrown.
     */
    def throwNullPointerExceptionOnFieldAccess: Boolean

    /**
     * If `true`, all instructions that may raise an arithmetic exception (e.g., ''idiv'',
     * ''ldiv'') should do so if it is impossible to statically determine that no
     * exception will occur. But, if we can statically determine that the operation will
     * raise an exception then the exception will be thrown – independently of this
     * setting. Furthermore, if we can statically determine that no exception will be
     * raised, no exception will be thrown. Hence, this setting only affects computations
     * with values with ''incomplete'' information.
     */
    def throwArithmeticExceptions: Boolean

    /**
     * Returns `true` if potential `NullPointerExceptions` should be thrown and `false`
     * if such `NullPointerExceptions` should be ignored. However, if the interpreter
     * identifies a situation in which a `NullPointerException` is guaranteed to be
     * thrown, it will be thrown.
     */
    def throwNullPointerExceptionOnMonitorAccess: Boolean

    /**
     * If `true` then `monitorexit` and the `(XXX)return` instructions will throw
     * `IllegalMonitorStateException`s unless the analysis is able to determine that
     * the exception is guaranteed to be raised.
     */
    def throwIllegalMonitorStateException: Boolean

    /**
     * Returns `true` if potential `NullPointerExceptions` should be thrown and `false`
     * if such `NullPointerExceptions` should be ignored. However, if the interpreter
     * identifies a situation in which a `NullPointerException` is guaranteed to be
     * thrown, it will be thrown.
     */
    def throwNullPointerExceptionOnArrayAccess: Boolean

    /**
     * If `true` an `ArrayIndexOutOfBoundsException` is thrown if the index cannot
     * be verified to be valid.
     */
    def throwArrayIndexOutOfBoundsException: Boolean

    /**
     * If `true` an `ArrayStoreException` is thrown if it cannot be verified that the
     * value can be stored in the array.
     */
    def throwArrayStoreException: Boolean

    /**
     * If `true` a `NegativeArraySizeException` is thrown if the index cannot be
     * verified to be positive.
     */
    def throwNegativeArraySizeException: Boolean

    /**
     * If `true` a `ClassCastException` is thrown by `CHECKCAST` instructions if it
     * cannot be verified that no `ClassCastException` will be thrown.
     */
    def throwClassCastException: Boolean

    /**
     * Throw a `ClassNotFoundException` if the a specific reference type is not
     * known in the current context. The context is typically a specific `Project`.
     */
    def throwClassNotFoundException: Boolean

}
