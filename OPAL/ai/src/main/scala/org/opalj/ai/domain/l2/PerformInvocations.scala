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
package domain
package l2

import org.opalj.util.{ Answer, Yes, No, Unknown }

import org.opalj.br._
import org.opalj.br.instructions._
import org.opalj.br.analyses.{ Project, ClassHierarchy }

import org.opalj.ai.util.Locals

/**
 * Mix in this trait if methods that are called by `invokeXXX` instructions should
 * actually be interpreted using a custom domain.
 *
 * @author Michael Eichberg
 */
trait PerformInvocations extends l0.TypeLevelInvokeInstructions {
    callingDomain: ValuesFactory with ReferenceValuesDomain with MethodCallsDomain with Configuration with TheProject[_] with TheCode with domain.ClassHierarchy ⇒

    /**
     * Identifies recursive calls.
     *
     * @note This function can simply always return `false`, if the domain that is used
     *      for analyzing the called method does not follow method invocations (does
     *      not perform invocations.) I.e., the domain used for analyzing a called method
     *      can be ''any'' domain; in particular a domain that does not perform
     *      invocations.
     */
    def isRecursive(
        definingClass: ClassFile,
        method: Method,
        operands: Operands): Boolean

    def shouldInvocationBePerformed(
        definingClass: ClassFile,
        method: Method): Boolean

    /**
     * Encapsulates the information required to perform the invocation of the target
     * method.
     */
    trait InvokeExecutionHandler {

        /**
         * The domain that will be used to perform the abstract interpretation of the
         * called method.
         *
         * In general, explicit support is required to identify recursive calls
         * if the domain also follows method invocations,
         */
        val domain: TargetDomain with MethodCallResults

        /**
         *  The abstract interpreter that will be used for the abstract interpretation.
         */
        def ai: AI[_ >: domain.type]

        def perform(
            pc: PC,
            definingClass: ClassFile,
            method: Method,
            parameters: domain.Locals): MethodCallResult = {

            val aiResult =
                ai.perform(method.body.get, domain)(
                    List.empty[domain.DomainValue], parameters)
            transformResult(pc, method, aiResult)
        }

        /**
         * Converts the results of the evaluation of the called method into this domain.
         */
        protected[this] def transformResult(
            callerPC: PC,
            calledMethod: Method,
            result: AIResult { val domain: InvokeExecutionHandler.this.domain.type }): MethodCallResult = {
            val domain = result.domain
            val thrownExceptions = domain.thrownExceptions(callingDomain, callerPC)
            if (!domain.returnedNormally) {
                // The method must have returned with an exception or not at all...
                if (thrownExceptions.nonEmpty)
                    ThrowsException(thrownExceptions)
                else
                    ComputationFailed
            } else {
                if (calledMethod.descriptor.returnType eq VoidType) {
                    if (thrownExceptions.nonEmpty) {
                        ComputationWithSideEffectOrException(thrownExceptions)
                    } else {
                        ComputationWithSideEffectOnly
                    }
                } else {
                    val returnedValue = domain.returnedValue(callingDomain, callerPC)
                    if (thrownExceptions.nonEmpty) {
                        ComputedValueOrException(returnedValue.get, thrownExceptions)
                    } else {
                        ComputedValue(returnedValue.get)
                    }
                }
            }
        }
    }

    /**
     * Returns (most often creates) the [[InvokeExecutionHandler]] that will be
     * used to perform the abstract interpretation of the called method.
     */
    def invokeExecutionHandler(
        pc: PC,
        definingClass: ClassFile,
        method: Method,
        operands: Operands): InvokeExecutionHandler

    override def invokevirtual(
        pc: PC,
        declaringClass: ReferenceType,
        name: String,
        methodDescriptor: MethodDescriptor,
        operands: Operands): MethodCallResult =
        super.invokevirtual(pc, declaringClass, name, methodDescriptor, operands)

    override def invokeinterface(
        pc: PC,
        declaringClass: ObjectType,
        name: String,
        methodDescriptor: MethodDescriptor,
        operands: Operands): MethodCallResult =
        super.invokeinterface(pc, declaringClass, name, methodDescriptor, operands)

    override def invokespecial(
        pc: PC,
        declaringClass: ObjectType,
        name: String,
        methodDescriptor: MethodDescriptor,
        operands: Operands): MethodCallResult =
        super.invokespecial(pc, declaringClass, name, methodDescriptor, operands)

    /**
     * Implements the general strategy for handling "invokestatic" calls.
     */
    override def invokestatic(
        pc: PC,
        declaringClass: ObjectType,
        methodName: String,
        methodDescriptor: MethodDescriptor,
        operands: Operands): MethodCallResult = {

        def fallback() =
            baseInvokestatic(pc, declaringClass, methodName, methodDescriptor, operands)

        classHierarchy.resolveMethodReference(
            // the cast is safe since arrays do not have any static methods
            declaringClass.asObjectType,
            methodName,
            methodDescriptor,
            project) match {
                case Some(method) if !method.isNative ⇒
                    val classFile = project.classFile(method)
                    if (!shouldInvocationBePerformed(classFile, method) ||
                        isRecursive(classFile, method, operands))
                        fallback()
                    else
                        invokestatic(pc, classFile, method, operands)
                case _ ⇒
                    println(
                        "[info] method reference cannot be resolved: "+
                            declaringClass.toJava+
                            "{ static "+methodDescriptor.toJava(methodName)+"}")
                    fallback()
            }
    }

    /**
     * Handle those `invokestatic` calls for which we have no concrete method (e.g.,
     * the respective class file was never loaded or the method is native) or
     * if have a recursive invocation.
     */
    protected[this] def baseInvokestatic(
        pc: PC,
        declaringClass: ObjectType,
        name: String,
        methodDescriptor: MethodDescriptor,
        operands: Operands): MethodCallResult = {
        super.invokestatic(pc, declaringClass, name, methodDescriptor, operands)
    }

    /**
     * Performs the invocation of the given method using the given operands.
     */
    protected[this] def invokestatic(
        pc: PC,
        definingClass: ClassFile,
        method: Method,
        operands: Operands): MethodCallResult = {

        val executionHandler = invokeExecutionHandler(pc, definingClass, method, operands)
        import executionHandler.domain
        val parameters = PerformInvocations.mapOperandsToParameters(operands, method, domain)
        val callResult = executionHandler.perform(pc, definingClass, method, parameters)
        // TODO [Improvement] Add support to map a value back to a parameter if a passed parameter is returned. (E.g. Math.min(a,b) will either return a or b.) 
        callResult
    }
}
/**
 * General useful helper methods related to the invocation of methods.
 */
object PerformInvocations {

    /**
     * Maps a list of operands (e.g., as passed to the `invokeXXX` instructions) to
     * the list of parameters for the given method. The parameters are stored in the
     * local variables ([[Locals]])/registers of the method; i.e., this method
     * creates an initial assignment for the local variables that can directly
     * be used to pass them to [[AI]]'s `perform(...)(initialLocals)` method.
     *
     * @param operands The list of operands used to call the given method. The length
     *      of the list must be:
     *      {{{
     *      calledMethod.descriptor.parametersCount + { if (calledMethod.isStatic) 0 else 1 }
     *      }}}.
     *      I.e., the list of operands must contain one value per parameter and – 
     *      in case of instance methods – the receiver object. The list __must not
     *       contain additional values__. The latter is automatically ensured if this
     *      method is called (in)directly by [[AI]] and the operands were just passed
     *      through.
     *      If two or more operands are (reference) identical then the adaptation will only
     *      be performed once and the adapted value will be reused; this ensures that
     *      the relation between values remains stable.
     * @param calledMethod The method that will be evaluated using the given operands.
     * @param targetDomain The [[Domain]] that will be use to perform the abstract
     *      interpretation.
     */
    def mapOperandsToParameters[D <: ValuesDomain](
        operands: Operands[D#DomainValue],
        calledMethod: Method,
        targetDomain: ValuesDomain with ValuesFactory): Locals[targetDomain.DomainValue] = {

        implicit val domainValueTag = targetDomain.DomainValueTag
        val parameters = util.Locals[targetDomain.DomainValue](calledMethod.body.get.maxLocals)
        var localVariableIndex = 0
        var index = 0
        val operandsInParameterOrder = operands.reverse
        for (operand ← operandsInParameterOrder) {
            val parameter = {
                // Was the same value (determined by "eq") already adapted?
                var pOperands = operandsInParameterOrder
                var p = 0
                while (p < index && (pOperands.head ne operand)) {
                    p += 1; pOperands = pOperands.tail
                }
                if (p < index)
                    parameters(p)
                else
                    // the value was not previously adapted
                    operand.adapt(targetDomain, -(index + 1))
            }
            parameters.set(localVariableIndex, parameter)
            index += 1
            localVariableIndex += operand.computationalType.operandSize
        }

        parameters
    }
}

