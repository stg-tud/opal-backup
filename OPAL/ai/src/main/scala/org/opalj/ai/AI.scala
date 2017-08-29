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
package ai

import scala.language.existentials

import scala.util.control.ControlThrowable
import scala.collection.BitSet

import org.opalj.collection.immutable.IntSet
import org.opalj.collection.immutable.:&:
import org.opalj.collection.immutable.Chain
import org.opalj.collection.immutable.Naught
import org.opalj.collection.immutable.{Chain ⇒ List}
import org.opalj.collection.immutable.{Naught ⇒ Nil}
import org.opalj.control.foreachNonNullValue
import org.opalj.bytecode.BytecodeProcessingFailedException
import org.opalj.collection.mutable.{Locals ⇒ Registers}
import org.opalj.ai.util.removeFirstUnless
import org.opalj.ai.util.containsInPrefix
import org.opalj.ai.util.insertBefore
import org.opalj.ai.util.insertBeforeIfNew
import org.opalj.br._
import org.opalj.br.instructions._

/**
 * A highly-configurable framework for the (abstract) interpretation of Java bytecode
 * that relies on OPAL's resolved representation ([[org.opalj.br]]) of Java bytecode.
 *
 * This framework basically traverses all instructions of a method in depth-first order
 * until an instruction is hit where multiple
 * control flows potentially join. This instruction is then only analyzed if no
 * further instruction can be evaluated where no paths join ([[org.opalj.br.Code.cfPCs]]).
 * Each instruction is then evaluated using a given (abstract) [[org.opalj.ai.Domain]].
 * The evaluation of a subroutine (Java code < 1.5) - in case of an unhandled
 * exception – is always first completed before the evaluation of the parent (sub)routine
 * is continued.
 *
 * ==Interacting with OPAL's Abstract Interpreter==
 * The primary means how to make use of this framework is to perform
 * an abstract interpretation of a method using a customized `Domain`. That
 * customized domain can be used, e.g., to build a call graph or to
 * do other intra-/interprocedural analyses while the code is analyzed.
 * Additionally, it is possible to analyze the result of an abstract interpretation.
 *
 * ==Thread Safety==
 * This class is thread-safe. However, to make it possible to use one abstract
 * interpreter instance for the concurrent abstract interpretation of independent
 * methods, the [[AITracer]] (if any) has to be thread-safe too.
 *
 * Hence, it is possible to use a single instance to analyze multiple methods in parallel.
 * However, if you want to be able to selectively abort the abstract interpretation
 * of some methods or want to selectively trace the interpretation of some methods, then
 * you should use multiple abstract interpreter instances.
 * Creating new instances is usually extremely cheap as this class
 * does not have any significant associated state.
 *
 * Subclasses '''are not required to be thread-safe and may have more complex state.'''
 *
 * @note
 *         OPAL does not make assumptions about the number of domain objects that
 *         are used. However, if a single domain object is used by multiple instances
 *         of this class and the abstract interpretations are executed concurrently, then
 *         the domain has to be thread-safe.
 *         The latter is trivially the case when the domain object itself does not have
 *         any state; however, most domain objects have some state.
 *
 * @note
 *         == Useless Joins Avoidance ==
 *         OPAL tries to minimize unnecessary joins by using the results of a naive live
 *         variables analysis (limited to the registers only!). This analysis helps to
 *         prevent unnecessary joins and also helps to reduce the overall number of
 *         processing steps. E.g., in the following case the swallowed exceptions that
 *         may occur whenever transformIt is called, would lead to an unnecessary
 *         join though the exception is not required!
 *         {{{
 *         if (enc != null) {
 *           try {
 *             return transformIt(transformIt(enc));
 *           } catch (RuntimeException re) {}
 *         }
 *         return "";
 *         }}}
 *         This analysis leads to an overall reduction in the number of evaluated instruction of
 *         about 4,5%. Additionally, it also reduces the effort spent on "expensive" joins which
 *         leads to an overall(!) improvement for the l1.DefaultDomain of ~8,5%.
 *
 *         ==Dead Variables Elimination based on Definitive Paths==
 *         (STILL IN DESIGN!!!!)
 *         ===Idea===
 *         Given an instruction i which may result in a fork of the control-flow (e.g.,
 *         a conditional branch or an invoke instruction that may throw a catched exception).
 *         If the (frist) evaluation of i definitively rules out several possible paths and - on
 *         all paths that are taken - some values are dead, but live on some of the other paths,
 *         then the respectively current values will never be propagated to the remaining paths,
 *         even if the remaining paths are eventually taken!
 *         This helps in variety of cases such as, e.g.,
 *         {{{
 *         var s : Object = null
 *         for{/* it can statically be determined that this path is taken at least once!*/} {
 *             s = "something else"
 *         }
 *         doIt(s); // here, "s" is guaranteed not to reference the orignal value "null"!
 *         }}}
 *         ===Implementation===
 *         When we have a fork, check if all paths...
 *
 *
 * ==Customizing the Abstract Interpretation Framework==
 * Customization of the abstract interpreter is done by creating new subclasses that
 * override the relevant methods (in particular: [[AI#isInterrupted]] and [[AI#tracer]]).
 *
 * @author Michael Eichberg
 */
abstract class AI[D <: Domain]( final val IdentifyDeadVariables: Boolean = true) {

    type SomeLocals[V <: d.DomainValue forSome { val d: D }] = Option[IndexedSeq[V]]

    /**
     * Determines whether a running (or to be started) abstract interpretation
     * should be interrupted (default: `false`).
     *
     * In general, interrupting the abstract interpreter may be meaningful if
     * the abstract interpretation takes too long or if the currently used domain
     * is not sufficiently precise enough/if additional information is needed to
     * continue with the analysis.
     *
     * Called during the abstract interpretation of a method to determine whether
     * the computation should be aborted. This method is ''always called directly before
     * the evaluation of the first/next instruction''. I.e., before the very first
     * instruction or after the ai has completely evaluated an instruction, updated the
     * memory and stated all constraints.
     *
     * @note When the abstract interpreter is currently waiting on the result of the
     *    interpretation of a called method it may take some time before the
     *    interpretation of the current method (this abstract interpreter) is actually
     *    aborted.
     *
     * This method '''needs to be overridden in subclasses to identify situations
     * in which a running abstract interpretation should be interrupted'''.
     */
    protected def isInterrupted: Boolean = false

    /**
     * The tracer (default: `None`) that is called by OPAL while performing the abstract
     * interpretation of a method.
     *
     * This method is called at different points to report on the analysis progress (see
     * [[org.opalj.ai.AITracer]] for further details)
     *
     * It is possible to attach/detach a tracer at any time.
     *
     * '''To attach a tracer to the abstract interpreter override this
     * method in subclasses''' and return some tracer object.
     */
    def tracer: Option[AITracer] = None

    /**
     *  Performs an abstract interpretation of the given method using the given domain.
     *
     *  @param method A non-native, non-abstract method of the given class file that
     *      will be analyzed. All parameters are automatically initialized with sensible
     *      default values.
     *  @param theDomain The domain that will be used to perform computations related
     *      to values.
     */
    def apply(method: Method, theDomain: D): AIResult { val domain: theDomain.type } = {
        perform(method, theDomain)(None)
    }

    /**
     * Returns the initial set of operands that will be used for the abstract
     * interpretation of the given method.
     *
     * In general, an empty list is returned as the JVM specification mandates
     * that the operand stack is empty at the very beginning of a method.
     *
     * This method is called by the `perform` method with the same signature. It
     * may be overridden by subclasses to perform some additional processing.
     */
    def initialOperands(method: Method, domain: D): domain.Operands = Naught

    /**
     * Returns the initial register assignment (the initialized locals) that is
     * used when analyzing a new method.
     *
     * Initially, only the registers that contain the method's parameters (including
     * the self reference (`this`)) are used. If no initial assignment is provided
     * (`someLocals == None`) a valid assignment is automatically created using
     * the domain. See `perform(...)` for further details regarding the initial
     * register assignment.
     *
     * This method is called by the `perform` method with the same signature. It
     * may be overridden by subclasses to perform some additional processing. In
     * that case, however, it is highly recommended to call this method to finalize the
     * initial assignment.
     *
     * @param method A non-native, non-abstract method. I.e., a method that has an
     *      implementation in Java bytecode (e.g., `method.body.isDefined === true`).
     * @param domain The domain that will be used to perform computations related
     *      to values.
     */
    def initialLocals(
        method: Method,
        domain: D
    )(
        someLocals: SomeLocals[domain.DomainValue] = None
    ): domain.Locals = {

        import domain.DomainValue

        val locals = someLocals.map { l ⇒
            val maxLocals = method.body.get.maxLocals

            assert(
                l.size >= (method.parameterTypes.size + (if (method.isStatic) 0 else 1)),
                "the number of initial locals is less than the number of parameters"
            )
            assert(
                l.size <= maxLocals,
                s"the number of initial locals ${l.size} is larger than max locals $maxLocals"
            )

            // ... the number of given locals is smaller than or equal to the number of
            // max locals (the former number still has to be larger or equal to the
            // number of parameter values (including "this"))
            val locals = Registers[domain.DomainValue](maxLocals)
            var i = l.size - 1
            while (i >= 0) {
                locals.set(i, l(i))
                i -= 1
            }
            locals
        }.getOrElse { // there are no locals at all...
            val code = method.body.get
            val locals = Registers[domain.DomainValue](code.maxLocals)
            var localVariableIndex = 0

            // Calculates the initial "PC" associated with a method's parameter.
            @inline def origin(localVariableIndex: Int) = -localVariableIndex - 1

            if (!method.isStatic) {
                val thisType = method.classFile.thisType
                val thisValue =
                    domain.NonNullObjectValue(origin(localVariableIndex), thisType)
                locals.set(localVariableIndex, thisValue)
                localVariableIndex += 1 /*==thisType.computationalType.operandSize*/
            }
            for (parameterType ← method.descriptor.parameterTypes) {
                val ct = parameterType.computationalType
                locals.set(
                    localVariableIndex,
                    domain.TypedValue(origin(localVariableIndex), parameterType)
                )
                localVariableIndex += ct.operandSize
            }
            locals
        }

        if (tracer.isDefined)
            tracer.get.initialLocals(domain)(locals)

        locals
    }

    /**
     * Analyzes the given method using the given domain and the pre-initialized parameter
     * values (if any). Basically, first the set of initial operands and locals is
     * calculated before the respective `perform(...,initialOperands,initialLocals)`
     * method is called.
     *
     * ==Controlling the AI==
     * The abstract interpretation of a method is aborted if the AI's `isInterrupted`
     * method returns true.
     *
     * @param method A non-abstract, non-native method of the given class file. I.e.,
     *      a method with a body.
     * @param theDomain The abstract domain that will be used for the abstract interpretation
     *      of the given method.
     * @param someLocals The initial register assignment (the parameters passed to the
     *      method). If the values passed to a method are already known, the
     *      abstract interpretation will be performed under that assumption. The specified
     *      number of locals has to be equal or larger than the number of parameters
     *      (including `this` in case of a non-static method.). If the number is lower
     *      than `method.body.maxLocals` it will be adjusted as required.
     * @return The result of the abstract interpretation. Basically, the calculated
     *      memory layouts; i.e., the list of operands and local variables before each
     *      instruction. Each calculated memory layout represents the layout before
     *      the instruction with the corresponding program counter was interpreted.
     *      If the interpretation was aborted, the returned result
     *      object contains all necessary information to continue the interpretation
     *      if needed/desired.
     */
    def perform(
        method:    Method,
        theDomain: D
    )(
        someLocals: Option[IndexedSeq[theDomain.DomainValue]] = None
    ): AIResult { val domain: theDomain.type } = {
        val body = method.body.get
        performInterpretation(method.isStrict, body, theDomain)(
            initialOperands(method, theDomain),
            initialLocals(method, theDomain)(someLocals)
        )
    }

    /**
     * Performs an abstract interpretation of the given (byte)code using
     * the given domain and the initial operand stack and initial register assignment.
     */
    def performInterpretation(
        strictfp:  Boolean,
        code:      Code,
        theDomain: D
    )(
        initialOperands: theDomain.Operands,
        initialLocals:   theDomain.Locals
    ): AIResult { val domain: theDomain.type } = {

        val codeLength = code.instructions.length

        val operandsArray = new Array[theDomain.Operands](codeLength)
        operandsArray(0) = initialOperands

        val localsArray = new Array[theDomain.Locals](codeLength)
        localsArray(0) = initialLocals

        val wl = AI.initialWorkList
        val ae: List[PC] /*alreadyEvaluated*/ = Nil
        continueInterpretation(code, theDomain)(wl, ae, operandsArray, localsArray)
    }

    def continueInterpretation(
        code:      Code,
        theDomain: D
    )(
        initialWorkList:  List[PC],
        alreadyEvaluated: List[PC],
        theOperandsArray: theDomain.OperandsArray,
        theLocalsArray:   theDomain.LocalsArray
    ): AIResult { val domain: theDomain.type } = {
        val classHierarchy: ClassHierarchy =
            theDomain match {
                case domainWithClassHierarchy: TheClassHierarchy ⇒
                    domainWithClassHierarchy.classHierarchy
                case _ ⇒
                    Code.BasicClassHierarchy
            }
        val (predecessorPCs, finalPCs, cfJoins) = code.predecessorPCs(classHierarchy)
        val liveVariables = code.liveVariables(predecessorPCs, finalPCs, cfJoins)
        continueInterpretation(
            code, cfJoins, liveVariables,
            theDomain
        )(
            initialWorkList, alreadyEvaluated,
            theOperandsArray, theLocalsArray,
            Nil, null, null
        )
    }

    /**
     * Performs additional initializations of the [[Domain]], if the `Domain` implements
     * the trait [[TheAI]], [[TheCodeStructure]], [[TheMemoryLayout]] or
     * [[CustomInitialization]].
     *
     * This method is called before the abstract interpretation is started/continued.
     */
    protected[this] def preInterpretationInitialization(
        code:          Code,
        instructions:  Array[Instruction],
        cfJoins:       BitSet,
        liveVariables: LiveVariables,
        theDomain:     D
    )(
        theOperandsArray:                    theDomain.OperandsArray,
        theLocalsArray:                      theDomain.LocalsArray,
        theMemoryLayoutBeforeSubroutineCall: List[(PC, theDomain.OperandsArray, theDomain.LocalsArray)],
        theSubroutinesOperandsArray:         theDomain.OperandsArray,
        theSubroutinesLocalsArray:           theDomain.LocalsArray
    ): Unit = {

        // The following order must not change:
        // (The order is part of the contract of AI.)
        theDomain match {
            case d: TheAI[D] ⇒ d.setAI(this)
            case _           ⇒ /*nothing to do*/
        }
        theDomain match {
            case d: TheCodeStructure ⇒ d.setCodeStructure(instructions, cfJoins, liveVariables)
            case _                   ⇒ /*nothing to do*/
        }
        theDomain match {
            case d: TheMemoryLayout ⇒
                d.setMemoryLayout(
                    theOperandsArray.asInstanceOf[d.OperandsArray],
                    theLocalsArray.asInstanceOf[d.LocalsArray],
                    theMemoryLayoutBeforeSubroutineCall.asInstanceOf[List[(d.OperandsArray, d.LocalsArray)]],
                    theSubroutinesOperandsArray.asInstanceOf[d.OperandsArray],
                    theSubroutinesLocalsArray.asInstanceOf[d.LocalsArray]
                )
            case _ ⇒ /*nothing to do*/
        }
        theDomain match {
            case d: CustomInitialization ⇒
                d.initProperties(code, cfJoins, theLocalsArray.asInstanceOf[d.LocalsArray](0))
            case _ ⇒ /*nothing to do*/
        }
    }

    /**
     * Continues the interpretation of/performs an abstract interpretation of
     * the given method (code) using the given domain.
     *
     * @param  code The bytecode that will be interpreted using the given domain.
     *
     * @param  cfJoins The set of instructions where two or more control flow
     *         paths join. The abstract interpretation framework will only perform a
     *         join operation for those instructions.
     *
     * @param  theDomain The domain that will be used to perform the domain
     *         dependent computations.
     *
     * @param  initialWorkList The list of program counters with which the interpretation
     *         will continue. If the method was never analyzed before, the list should just
     *         contain the value "0"; i.e., we start with the interpretation of the
     *         first instruction (see `initialWorkList`).
     *         '''Note that the worklist may contain negative values. These values are not
     *         related to a specific instruction per-se but encode the necessary information
     *         to handle subroutines. In case of calls to a subroutine we add the special
     *         values `SUBROUTINE` and `SUBROUTINE_START` to the list to encode when the
     *         evaluation started. This is needed to completely process the subroutine
     *         (to explore all paths) before we finally return to the main method.'''
     *
     * @param alreadyEvaluated The list of the program counters (PC) of the instructions
     *      that were already evaluated. Initially (i.e., if the given code is analyzed
     *      the first time) this list is empty.
     *      This list is primarily needed to correctly resolve jumps to sub routines
     *      (`JSR(_W)` and `RET` instructions.) For each instruction that was evaluated,
     *      the operands array and the locals array must be non-empty (not `null`).
     *
     * @param theOperandsArray The array that contains the operand stacks. Each value
     *      in the array contains the operand stack before the instruction with the
     *      corresponding index is executed. This array can be empty except of the
     *      indexes that are referred to by the `initialWorklist`.
     *      '''The `operandsArray` data structure is mutated by OPAL-AI and it is
     *      __recommended that a `Domain` does not directly mutate the state of
     *      this array__.'''
     *
     * @param theLocalsArray The array that contains the local variable assignments.
     *      Each value in the array contains the local variable assignments before
     *      the instruction with the corresponding program counter is executed.
     *      '''The `localsArray` data structure is mutated by OPAL-AI and it is
     *      __recommended that a `Domain` does not directly mutate the state of
     *      this array__.'''
     *
     * @param theSubroutinesOperandsArray The array that contains the intermediate information
     *      about the subroutines' operands.
     *      This value should be `null` unless we are continuing an aborted computation and
     *      a subroutine was already analyzed.
     *
     * @param theSubroutinesLocalsArray The array that contains the intermediate information
     *      about the subroutines' locals.
     *      This value should be `null` unless we are continuing an aborted computation and
     *      a subroutine was already analyzed.
     */
    def continueInterpretation(
        code: Code, cfJoins: BitSet, liveVariables: LiveVariables,
        theDomain: D
    )(
        initialWorkList:                     List[PC],
        alreadyEvaluated:                    List[PC],
        theOperandsArray:                    theDomain.OperandsArray,
        theLocalsArray:                      theDomain.LocalsArray,
        theMemoryLayoutBeforeSubroutineCall: List[(PC, theDomain.OperandsArray, theDomain.LocalsArray)],
        theSubroutinesOperandsArray:         theDomain.OperandsArray,
        theSubroutinesLocalsArray:           theDomain.LocalsArray
    ): AIResult { val domain: theDomain.type } = {

        assert(
            (theSubroutinesOperandsArray eq null) && (theSubroutinesLocalsArray eq null) ||
                (theSubroutinesOperandsArray ne null) && (theSubroutinesLocalsArray ne null),
            "inconsistent subroutine information"
        )
        assert(
            (theSubroutinesOperandsArray eq null) ||
                theSubroutinesOperandsArray.zipWithIndex.forall { opsPC ⇒
                    val (ops, pc) = opsPC
                    (ops eq null) || (theOperandsArray(pc) eq null)
                },
            "the regular operands and the subroutines operands contain conflicting information"
        )

        if (tracer.isDefined)
            tracer.get.continuingInterpretation(code, theDomain)(
                initialWorkList, alreadyEvaluated,
                theOperandsArray, theLocalsArray, theMemoryLayoutBeforeSubroutineCall
            )

        import theDomain.{DomainValue, ExceptionValue, ExceptionValues, Operands, Locals}
        import theDomain.{SingleValueConstraint, TwoValuesConstraint}

        // import reference values related functionality
        import theDomain.{refAreEqual, refAreNotEqual, RefAreEqual, RefAreNotEqual}
        import theDomain.{refIsNonNull, refIsNull, RefIsNonNull, RefIsNull}

        // import int values related functionality
        import theDomain.{intAreEqual, intAreNotEqual, IntAreEqual, IntAreNotEqual}
        import theDomain.{intIs0, intIsNot0, IntIs0, IntIsNot0}
        import theDomain.{intIsGreaterThan, intIsGreaterThan0, IntIsGreaterThan, IntIsGreaterThan0}
        import theDomain.{intIsLessThan, intIsLessThan0, IntIsLessThan, IntIsLessThan0}
        import theDomain.{intIsGreaterThanOrEqualTo, intIsGreaterThanOrEqualTo0}
        import theDomain.{IntIsGreaterThanOrEqualTo, IntIsGreaterThanOrEqualTo0}
        import theDomain.{intIsLessThanOrEqualTo, intIsLessThanOrEqualTo0}
        import theDomain.{IntIsLessThanOrEqualTo, IntIsLessThanOrEqualTo0}

        type SingleValueDomainTest = (PC, DomainValue) ⇒ Answer
        type TwoValuesDomainTest = (PC, DomainValue, DomainValue) ⇒ Answer

        val instructions: Array[Instruction] = code.instructions

        preInterpretationInitialization(
            code, instructions, cfJoins, liveVariables, theDomain
        )(
            theOperandsArray, theLocalsArray,
            theMemoryLayoutBeforeSubroutineCall,
            theSubroutinesOperandsArray, theSubroutinesLocalsArray
        )

        /*
         * The first PC of each element of the list is the pc of the first instruction of
         * the subroutine <=> the subroutine id
         */
        type SubroutineMemoryLayouts = List[(PC, theDomain.OperandsArray, theDomain.LocalsArray)]
        // The entire state of the computation is (from the perspective of the AI)
        // encapsulated by the following data-structures:
        /* 1 */ var operandsArray = theOperandsArray
        /* 2 */ var localsArray = theLocalsArray
        /* 3 */ var worklist = initialWorkList
        /* 4 */ var evaluated = alreadyEvaluated
        /* 5 */ var memoryLayoutBeforeSubroutineCall: SubroutineMemoryLayouts = theMemoryLayoutBeforeSubroutineCall
        /* 6 */ var subroutinesOperandsArray = theSubroutinesOperandsArray
        /* 7 */ var subroutinesLocalsArray = theSubroutinesLocalsArray

        def throwInterpretationFailedException(cause: Throwable, pc: PC): Nothing = {
            throw InterpretationFailedException(
                cause, theDomain
            )(
                this,
                pc, cfJoins, worklist, evaluated,
                operandsArray, localsArray, memoryLayoutBeforeSubroutineCall
            )
        }

        // Integrates the abstract state related to the execution of the subroutines with the main
        // operands/locals array. The abstract state is the state computed across all executions
        // of the respective subroutines!
        def integrateSubroutineInformation(): Unit = {
            if (subroutinesOperandsArray ne null) {
                foreachNonNullValue(subroutinesOperandsArray) { (pc, value) ⇒
                    // It is possible to have a method that only has jsr instructions, but no
                    // ret instruction(s); e.g. com.sun.mail.imap.IMAPStore compiled with Java 1.4.
                    // In this case the subroutine and the normal functionality are overlapping.
                    // In this case, the "normal" operands/locals array already contains the
                    // combined state.
                    if ((operandsArray(pc) eq null) /* && (localsArray(pc) eq null)*/ ) {
                        val subroutineOperands = subroutinesOperandsArray(pc)
                        val subroutineLocals = subroutinesLocalsArray(pc)
                        operandsArray(pc) = subroutineOperands
                        localsArray(pc) = subroutineLocals
                    }
                }
            }
        }

        def abstractInterpretationEnded(): AIResult { val domain: theDomain.type } = {

            integrateSubroutineInformation()
            val result =
                AIResultBuilder.completed(
                    code, cfJoins, liveVariables, theDomain
                )(
                    evaluated, operandsArray, localsArray
                )
            try {
                theDomain.abstractInterpretationEnded(result)
            } catch {
                case ct: ControlThrowable ⇒ throw ct
                case t: Throwable         ⇒ throwInterpretationFailedException(t, instructions.length)
            }
            if (tracer.isDefined) tracer.get.result(result)
            result
        }

        // -------------------------------------------------------------------------------
        //
        // Main loop of the abstract interpreter
        //
        // -------------------------------------------------------------------------------

        /*
         * The information to which subroutine an instruction belongs is only needed
         * if a subroutine is ever abruptly terminated. In case of code generated by
         * a Java 6 or newer compiler no subroutines are generated. However,
         * even the JDK 8 still contains a few classes that were compiled with Java 1.1!
         */
        lazy val belongsToSubroutine = code.belongsToSubroutine()

        /*
         * Updates the state of the abstract interpreter to make it possible to
         * continue (at some time in the future) the abstract interpretation with the
         * instruction at the given target (`targetPC`).
         *
         * Basically, the operand stack and the local variables are updated using the
         * given ones and the target program counter is added to the worklist.
         *
         * In case of an abrupt termination of a subroutine the operands and locals
         * array of the catching method are also updated.
         */
        def gotoTarget(
            sourcePC:                 PC,
            sourceInstruction:        Instruction,
            sourceOperands:           Operands,
            sourceLocals:             Locals,
            targetPC:                 PC,
            isExceptionalControlFlow: Boolean,
            newOperands:              Operands,
            newLocals:                Locals
        ): Unit = {

            val (operands, locals) =
                theDomain.afterEvaluation(
                    sourcePC, sourceInstruction,
                    sourceOperands, sourceLocals,
                    targetPC, isExceptionalControlFlow,
                    newOperands, newLocals
                )

            var isTargetScheduled: Answer = Unknown

            // Determine the number of subroutines that are abruptly terminated by
            // an exception that is not handled by the subroutine itself.
            val abruptSubroutineTerminationCount: Int = {
                if (isExceptionalControlFlow && memoryLayoutBeforeSubroutineCall.nonEmpty) {
                    val jumpToSubroutineId = belongsToSubroutine(targetPC)
                    if (jumpToSubroutineId != memoryLayoutBeforeSubroutineCall.head._1) {
                        var subroutinesToTerminate = 1
                        val it = memoryLayoutBeforeSubroutineCall.toIterator
                        it.next()
                        while (it.hasNext && it.next()._1 != jumpToSubroutineId) {
                            subroutinesToTerminate += 1
                        }
                        subroutinesToTerminate
                    } else {
                        0
                    }
                } else {
                    0
                }
            }

            // When we have an abrupt termination of a subroutine, we have to make
            // sure that we are actually scheduling the operation in the handling
            // (sub)routine and use the handling subroutine's operands and locals array.
            var targetOperandsArray: theDomain.OperandsArray = null
            var targetLocalsArray: theDomain.LocalsArray = null
            if (abruptSubroutineTerminationCount == 0) {
                targetOperandsArray = operandsArray
                targetLocalsArray = localsArray
            } else {
                val (_, operandsArray, localsArray) =
                    memoryLayoutBeforeSubroutineCall(abruptSubroutineTerminationCount - 1)
                targetOperandsArray = operandsArray
                targetLocalsArray = localsArray
            }

            /* Handles the case that a subroutine (jsr/ret) is abruptly terminated due
             * to an exception.
             *
             * Schedules the evaluation of the exception handler in the context of the
             * (calling) subroutine to which the handler belongs.
             *
             * @param forceSchedule If `false` the instruction will not be scheduled if
             *      it is not already scheduled. In this case we will basically just test
             *      if the instruction was scheduled.
             *
             * @return `true` if the target instruction was (re)scheduled.
             *      Hence, if
             *      - `doSchedule` is false and true is returned, then the
             *          instruction was already scheduled.
             *      - `doSchedule` is false and false is returned, then the target
             *          instruction will not be executed.
             *      - `doSchedule` is true and false is returned, then the target
             *          instruction was newly scheduled.
             *      - `doSchedule` is true and true is returned, then the target
             *          instruction was already scheduled.
             */
            def handleAbruptSubroutineTermination(forceSchedule: Boolean): Boolean = {
                val jumpToSubroutineId = belongsToSubroutine(targetPC)

                var subroutinesToTerminate = abruptSubroutineTerminationCount
                // We now know the number of subroutines that are terminated (at most
                // all active real subroutines) now let's remove the elements of those
                // subroutines from the worklist, schedule the instruction (if necessary)
                // and re-add the child subroutines.
                var header: List[PC] = Nil
                var remainingWorklist: List[PC] = worklist
                while (subroutinesToTerminate > 0) {
                    val pc = remainingWorklist.head
                    header = pc :&: header
                    remainingWorklist = remainingWorklist.tail
                    if (pc == SUBROUTINE)
                        subroutinesToTerminate -= 1
                }

                if (remainingWorklist.nonEmpty && remainingWorklist.head == targetPC) {
                    // The instruction was already scheduled.
                    if (tracer.isDefined) {
                        tracer.get.abruptSubroutineTermination(theDomain)(
                            sourcePC, targetPC, jumpToSubroutineId,
                            abruptSubroutineTerminationCount,
                            oldWorklist = worklist,
                            newWorklist = worklist
                        )
                    }
                    return true;
                }

                val filteredRemainingWorkList = removeFirstUnless(remainingWorklist, targetPC) { _ < 0 }
                val rescheduled = filteredRemainingWorkList ne remainingWorklist
                if (rescheduled || forceSchedule) {
                    remainingWorklist = targetPC :&: filteredRemainingWorkList
                }
                while (header.nonEmpty) {
                    remainingWorklist = header.head :&: remainingWorklist
                    header = header.tail
                }

                if (tracer.isDefined) {
                    tracer.get.abruptSubroutineTermination(theDomain)(
                        sourcePC, targetPC, jumpToSubroutineId,
                        abruptSubroutineTerminationCount,
                        oldWorklist = worklist,
                        newWorklist = remainingWorklist
                    )
                }

                worklist = remainingWorklist

                rescheduled
            }

            // The worklist containing the PC is manipulated ...:
            // - here (by this method)
            // - by the JSR / RET instructions
            // - the main loop that processes the worklist

            val currentOperands = targetOperandsArray(targetPC)
            val wasJoinPerformed =
                if (currentOperands eq null) {
                    // We analyze the instruction for the first time.
                    isTargetScheduled = Yes // it is already or will be scheduled...
                    targetOperandsArray(targetPC) = operands
                    if (IdentifyDeadVariables && cfJoins.contains(targetPC)) {
                        var i = 0
                        val theLiveVariables = liveVariables(targetPC)
                        val newLocals = locals.mapConserve { v: theDomain.DomainValue ⇒
                            val lvIndex = i
                            i += 1
                            if ((v eq null) ||
                                (v eq theDomain.TheIllegalValue) ||
                                theLiveVariables.contains(lvIndex)) {
                                v
                            } else {
                                theDomain.TheIllegalValue
                            }
                        }
                        targetLocalsArray(targetPC) = newLocals
                    } else {
                        targetLocalsArray(targetPC) = locals
                    }

                    if (abruptSubroutineTerminationCount > 0) {
                        handleAbruptSubroutineTermination(forceSchedule = true)
                    } else if (worklist.nonEmpty && cfJoins.contains(targetPC)) {
                        worklist = insertBefore(worklist, targetPC, SUBROUTINE_START)
                    } else {
                        worklist = targetPC :&: worklist
                    }

                    if (tracer.isDefined)
                        tracer.get.flow(theDomain)(sourcePC, targetPC, isExceptionalControlFlow)

                    /* join: */ false

                } else if (!cfJoins.contains(targetPC)) {
                    // The instruction is not an instruction where multiple control-flow
                    // paths join; however, we may have a dangling computation.
                    // E.g., imagine the following code:
                    //
                    // 0: int i = 0
                    // 1: do {
                    // 2:     if (UNDECIDED) "A" else "B"
                    // 3: } while (i<5)
                    //
                    // In this case, it may happen that (we are primarily doing
                    // depth-first evaluation) we are reanalyzing the loop (1-3)
                    // before we analyze the second branch of the if (e.g., case "B").
                    // However, the "pc" of the second branch is already scheduled
                    // and we don't want/need to reschedule it again.
                    isTargetScheduled = Yes // it is already or will be scheduled...
                    targetOperandsArray(targetPC) = operands
                    targetLocalsArray(targetPC) = locals
                    if (!containsInPrefix(worklist, targetPC, SUBROUTINE_START)) {
                        worklist = targetPC :&: worklist
                    }
                    if (tracer.isDefined) {
                        tracer.get.flow(theDomain)(sourcePC, targetPC, isExceptionalControlFlow)
                    }

                    /* join: */ false

                } else {
                    // we already evaluated the target (join) instruction ...
                    val currentLocals = targetLocalsArray(targetPC)
                    val mergeResult =
                        theDomain.join(targetPC, currentOperands, currentLocals, operands, locals)
                    if (tracer.isDefined) tracer.get.join(theDomain)(
                        targetPC,
                        currentOperands, currentLocals, operands, locals,
                        mergeResult
                    )
                    mergeResult match {
                        case NoUpdate ⇒ /* nothing to do*/
                            // Keep default: isTargetScheduled = Unknown
                            if (tracer.isDefined) {
                                tracer.get.noFlow(theDomain)(sourcePC, targetPC)
                            }

                        case StructuralUpdate((updatedOperands, updatedLocals)) ⇒
                            isTargetScheduled = Yes
                            targetOperandsArray(targetPC) = updatedOperands
                            targetLocalsArray(targetPC) = updatedLocals
                            if (abruptSubroutineTerminationCount > 0) {
                                if (handleAbruptSubroutineTermination(forceSchedule = true)) {
                                    // the instruction was just moved to the beginning
                                    if (tracer.isDefined) {
                                        tracer.get.rescheduled(theDomain)(
                                            sourcePC, targetPC, isExceptionalControlFlow, worklist
                                        )
                                    }
                                } else {
                                    if (tracer.isDefined) {
                                        tracer.get.flow(theDomain)(
                                            sourcePC, targetPC, isExceptionalControlFlow
                                        )
                                    }
                                }
                            } else {
                                // we want depth-first evaluation (, but we do not want to
                                // reschedule instructions that do not belong to the current
                                // evaluation context/(sub-)routine.)
                                val updatedWorklist =
                                    insertBeforeIfNew(worklist, targetPC, SUBROUTINE_START)
                                if (tracer.isDefined) {
                                    if (updatedWorklist ne worklist)
                                        // the instruction was not yet scheduled for
                                        // another evaluation
                                        tracer.get.flow(theDomain)(
                                            sourcePC, targetPC, isExceptionalControlFlow
                                        )
                                    else {
                                        // the instruction was just moved to the beginning
                                        tracer.get.rescheduled(theDomain)(
                                            sourcePC, targetPC, isExceptionalControlFlow,
                                            updatedWorklist
                                        )
                                    }
                                }
                                worklist = updatedWorklist
                            }

                        case MetaInformationUpdate((updatedOperands, updatedLocals)) ⇒
                            targetOperandsArray(targetPC) = updatedOperands
                            targetLocalsArray(targetPC) = updatedLocals

                            if (abruptSubroutineTerminationCount > 0) {
                                if (handleAbruptSubroutineTermination(forceSchedule = false)) {
                                    isTargetScheduled = Yes
                                    if (tracer.isDefined) {
                                        // the instruction was just moved to the beginning
                                        tracer.get.flow(theDomain)(
                                            sourcePC, targetPC, isExceptionalControlFlow
                                        )
                                    }
                                } else {
                                    // keep default: isTargetScheduled = Unknown
                                    // (We just know that the instruction is not
                                    // scheduled in the context of the calling parent
                                    // routine, but it may be scheduled in a different
                                    // context!)
                                    if (tracer.isDefined) {
                                        tracer.get.noFlow(theDomain)(sourcePC, targetPC)
                                    }
                                }
                            } else {
                                // we want depth-first evaluation (, but we do not want to
                                // reschedule instructions that do not belong to the current
                                // evaluation context/(sub-)routine.), but not for
                                // instructions where multiple paths join...
                                if (containsInPrefix(worklist, targetPC, SUBROUTINE)) {
                                    isTargetScheduled = Yes
                                    if (tracer.isDefined) {
                                        tracer.get.rescheduled(theDomain)(
                                            sourcePC, targetPC, isExceptionalControlFlow, worklist
                                        )
                                    }
                                } else {
                                    // keep default: isTargetScheduled = Unknown
                                    // (We just know that the instruction is not
                                    // scheduled in the context of the calling parent
                                    // routine, but it may be scheduled in a different
                                    // context!)
                                    // isTargetScheduled = No if we don't have subroutines..
                                    if (tracer.isDefined) {
                                        tracer.get.noFlow(theDomain)(sourcePC, targetPC)
                                    }
                                }
                            }
                    }

                    /*join: */ true
                }

            assert(
                worklist.exists(_ == targetPC) == isTargetScheduled.isYesOrUnknown ||
                    worklist.forall(_ != targetPC) == isTargetScheduled.isNoOrUnknown,
                s"worklist=$worklist; target=$targetPC; scheduled=$isTargetScheduled "+
                    s"(join=$wasJoinPerformed,exceptional=$isExceptionalControlFlow)"
            )

            worklist =
                theDomain.flow(
                    sourcePC, sourceOperands, sourceLocals,
                    targetPC, isTargetScheduled,
                    isExceptionalControlFlow, abruptSubroutineTerminationCount,
                    wasJoinPerformed,
                    worklist,
                    targetOperandsArray, targetLocalsArray,
                    tracer
                )

            // assert(
            //   abruptSubroutineTerminationCount == 0 ||
            //      !containsInPrefix(worklist, targetPC, SUBROUTINE_START),
            //   "an exception handler that handles the abrupt termination of a subroutine "+
            //      "is scheduled to be executed as part of the abruptly terminated subroutine")
        }

        // THIS IS THE MAIN INTERPRETER LOOP
        while (worklist.nonEmpty) {
            if (isInterrupted) {
                val result =
                    AIResultBuilder.aborted(
                        code, cfJoins, liveVariables, theDomain
                    )(
                        worklist, evaluated,
                        operandsArray, localsArray,
                        memoryLayoutBeforeSubroutineCall,
                        subroutinesOperandsArray, subroutinesLocalsArray
                    )

                if (tracer.isDefined) {
                    tracer.get.result(result)
                }

                return result;
            }

            // The central worklist is manipulated at the following places:
            // - here
            // - by the JSR / RET instructions
            // - by the "gotoTarget" method
            val pc: PC = {
                // Check if we we have a return from the evaluation of a subroutine.
                // I.e., all paths in a subroutine are explored and we know all
                // exit points; we will now schedule the jump to the return
                // address and reset the subroutine's computation context
                while (worklist.head < 0) { // while we may return from multiple nested subroutines
                    evaluated :&:= SUBROUTINE_END
                    // the structure is:
                    //      SUBROUTINE_START :&:
                    //          (
                    //              (RET_PC :&: )+
                    //              SUBROUTINE_RETURN_ADDRESS_LOCAL_VARIABLE  :&: lvIndex :&:
                    //          )?
                    //      SUBROUTINE_RETURN_TO_TARGET :&: returnTarget :&:
                    //      SUBROUTINE :&:
                    //      remaining worklist
                    worklist = worklist.tail // remove SUBROUTINE_START
                    var retPCs = Set.empty[PC]
                    while (worklist.head >= SUBROUTINE_INFORMATION_BLOCK_SEPARATOR_BOUND) {
                        // In case that a subroutine always throws a (non-caught) exception,
                        // we will not have encountered a single ret instruction.
                        retPCs += worklist.head
                        worklist = worklist.tail
                    }
                    // We don't know the local variable in case that the subroutine
                    // never returned normally and we were not able to fetch the
                    // information eagerly... (which is, however, the case for all known
                    // compilers)
                    val lvIndex =
                        if (worklist.head == SUBROUTINE_RETURN_ADDRESS_LOCAL_VARIABLE) {
                            worklist = worklist.tail
                            val lvIndex = worklist.head
                            worklist = worklist.tail
                            Some(lvIndex)
                        } else {
                            None
                        }

                    worklist = worklist.tail // remove SUBROUTINE_RETURN_TO_TARGET
                    val returnAddress = worklist.head
                    worklist = worklist.tail.tail // remove the subroutine marker

                    // We need to merge the results of the execution of the subroutines to get
                    // abstract interpretation time information about the operands/locals across
                    // all subroutine calls; we have to make sure that we extract the information
                    // belonging to the correct subroutine (if we have nested subroutine calls)
                    var subroutineLevel = 0
                    var trace = evaluated.tail
                    var subroutine: List[PC] = Nil
                    while (trace.head != SUBROUTINE_START || subroutineLevel != 0) {
                        trace.head match {
                            case SUBROUTINE_START ⇒ subroutineLevel -= 1
                            case SUBROUTINE_END   ⇒ subroutineLevel += 1
                            case pc               ⇒ if (subroutineLevel == 0) subroutine :&:= pc
                        }
                        trace = trace.tail
                    }
                    if (subroutinesOperandsArray eq null) {
                        // ... we finished the analysis of a subroutine for the first time.
                        subroutinesOperandsArray = new Array(instructions.length)
                        subroutinesLocalsArray = new Array(instructions.length)
                        subroutine foreach { pc ⇒
                            if (pc >= 0) {
                                subroutinesOperandsArray(pc) = operandsArray(pc)
                                subroutinesLocalsArray(pc) = localsArray(pc)
                            }
                        }
                    } else {
                        subroutine foreach { pc ⇒
                            if (pc >= 0) {
                                val currentOperands = operandsArray(pc)
                                val currentLocals = localsArray(pc)
                                assert(currentOperands ne null)

                                val mergedOperands = subroutinesOperandsArray(pc)
                                if (mergedOperands eq null) {
                                    subroutinesOperandsArray(pc) = currentOperands
                                    subroutinesLocalsArray(pc) = currentLocals
                                } else {
                                    // we have to merge the results from a previous execution of the
                                    // subroutine with the current results
                                    val mergedLocals = subroutinesLocalsArray(pc)
                                    theDomain.join(
                                        pc,
                                        mergedOperands, mergedLocals,
                                        currentOperands, currentLocals
                                    ) match {
                                        case NoUpdate ⇒ /*nothing to do...*/
                                        case SomeUpdate((newOperands, newLocals)) ⇒
                                            subroutinesOperandsArray(pc) = newOperands
                                            subroutinesLocalsArray(pc) = newLocals
                                    }
                                }
                            }
                        }
                    }

                    val targets = retPCs.map { retPC ⇒
                        if (tracer.isDefined) {
                            tracer.get.returnFromSubroutine(theDomain)(
                                retPC,
                                returnAddress,
                                subroutine
                            )
                        }
                        // Reset the local variable that stores the return address
                        // to avoid conflicts on merge in case of a nested subroutine
                        // that is evaluated in a loop.
                        val operands = operandsArray(retPC)
                        val locals = localsArray(retPC)
                        val updatedLocals =
                            lvIndex.map(locals.updated(_, theDomain.Null)).getOrElse(locals)
                        (retPC, operands, updatedLocals)
                    }
                    // Clear all computations to make this subroutine callable again.
                    val (_ /*subroutineId*/ , oldOperandsArray, oldLocalsArray) =
                        memoryLayoutBeforeSubroutineCall.head
                    operandsArray = oldOperandsArray
                    localsArray = oldLocalsArray
                    memoryLayoutBeforeSubroutineCall = memoryLayoutBeforeSubroutineCall.tail
                    targets.foreach { target ⇒
                        val (retPC, operands, updatedLocals) = target
                        gotoTarget(
                            retPC, instructions(retPC),
                            operandsArray(retPC), localsArray(retPC),
                            returnAddress, isExceptionalControlFlow = false,
                            operands, updatedLocals
                        )
                    }

                    // It may be possible that – after the return from a
                    // call to a subroutine – we have nothing further to do and
                    // the interpretation ends (in the bytecode there is at least
                    // one further instruction, but we may have evaluated that one
                    // already and the evaluation context didn't change).
                    if (worklist.isEmpty) {
                        return abstractInterpretationEnded();
                    }
                }
                // [THE DEFAULT CASE] the PC of the next instruction...
                worklist.head
            }

            try {
                worklist = worklist.tail
                evaluated :&:= pc
                val instruction = instructions(pc)
                // the memory layout before executing the instruction with the given pc
                val operands = operandsArray(pc)
                val locals = localsArray(pc)

                if (tracer.isDefined) {
                    tracer.get.instructionEvalution(theDomain)(pc, instruction, operands, locals)
                }

                @inline def pcOfNextInstruction = code.pcOfNextInstruction(pc)

                def checkDefinitivePath(nextPC: PC, altPC: PC, qualifier: String): Unit = {
                    /*    if (worklist.isEmpty &&
                        liveVariables(nextPC) != liveVariables(altPC) &&
                        evaluated.exists(cfJoins.contains) // if it works out we should use something more efficient than the evaluated set (e.g. evaluatedCFJoins)
                        )
                        println(s"$pc > $nextPC(alt: $altPC):definitive path -$qualifier")
                        */
                }

                /*
                 * Handles all '''if''' instructions that perform a comparison with a fixed
                 * value.
                 */
                def ifXX(
                    domainTest:    SingleValueDomainTest,
                    yesConstraint: SingleValueConstraint,
                    noConstraint:  SingleValueConstraint
                ): Unit = {

                    val branchInstruction = as[SimpleConditionalBranchInstruction](instruction)
                    val operand = operands.head
                    val rest = operands.tail
                    val nextPC = pcOfNextInstruction
                    val branchTargetPC = pc + branchInstruction.branchoffset

                    domainTest(pc, operand) match {
                        case Yes ⇒
                            checkDefinitivePath(branchTargetPC, nextPC, "ifXX-YES")
                            gotoTarget(
                                pc, instruction, operands, locals,
                                branchTargetPC, isExceptionalControlFlow = false,
                                rest, locals
                            )
                        case No ⇒
                            checkDefinitivePath(nextPC, branchTargetPC, "ifXX-NO")
                            gotoTarget(
                                pc, instruction, operands, locals,
                                nextPC, isExceptionalControlFlow = false,
                                rest, locals
                            )
                        case Unknown ⇒
                            // BT = Branch Target
                            val (newBTOperands, newBTLocals) =
                                yesConstraint(branchTargetPC, operand, rest, locals)
                            if (tracer.isDefined &&
                                ((rest ne newBTOperands) || (locals ne newBTLocals))) {
                                tracer.get.establishedConstraint(theDomain)(
                                    pc, branchTargetPC, rest, locals, newBTOperands, newBTLocals
                                )
                            }
                            // FT = Fall Through
                            val (newFTOperands, newFTLocals) =
                                noConstraint(nextPC, operand, rest, locals)
                            if (tracer.isDefined &&
                                ((rest ne newFTOperands) || (locals ne newFTLocals))) {
                                tracer.get.establishedConstraint(theDomain)(
                                    pc, nextPC, rest, locals, newFTOperands, newFTLocals
                                )
                            }

                            // We have empirically evaluated which strategy leads to the minimal
                            // number of instruction evaluations and the following test does:
                            if (branchTargetPC > pc) {
                                gotoTarget(
                                    pc, instruction, operands, locals,
                                    nextPC, isExceptionalControlFlow = false,
                                    newFTOperands, newFTLocals
                                )
                                gotoTarget(
                                    pc, instruction, operands, locals,
                                    branchTargetPC, isExceptionalControlFlow = false,
                                    newBTOperands, newBTLocals
                                )
                            } else {
                                gotoTarget(
                                    pc, instruction, operands, locals,
                                    branchTargetPC, isExceptionalControlFlow = false,
                                    newBTOperands, newBTLocals
                                )
                                gotoTarget(
                                    pc, instruction, operands, locals,
                                    nextPC, isExceptionalControlFlow = false,
                                    newFTOperands, newFTLocals
                                )
                            }
                    }
                }

                /*
                 * Handles all '''if''' instructions that perform a comparison of two
                 * stack based values.
                 */
                def ifTcmpXX(
                    domainTest:    TwoValuesDomainTest,
                    yesConstraint: TwoValuesConstraint,
                    noConstraint:  TwoValuesConstraint
                ): Unit = {

                    val branchInstruction = as[SimpleConditionalBranchInstruction](instruction)
                    val right = operands.head
                    val remainingOperands = operands.tail
                    val left = remainingOperands.head
                    val rest = remainingOperands.tail
                    val branchTargetPC = pc + branchInstruction.branchoffset
                    val nextPC = code.pcOfNextInstruction(pc)
                    val testResult = domainTest(pc, left, right)
                    testResult match {
                        case Yes ⇒
                            checkDefinitivePath(branchTargetPC, nextPC, "ifTcmpXX-YES")
                            gotoTarget(
                                pc, instruction, operands, locals,
                                branchTargetPC, isExceptionalControlFlow = false,
                                rest, locals
                            )
                        case No ⇒
                            checkDefinitivePath(nextPC, branchTargetPC, "ifTcmpXX-NO")
                            gotoTarget(
                                pc, instruction, operands, locals,
                                nextPC, isExceptionalControlFlow = false,
                                rest, locals
                            )
                        case Unknown ⇒
                            // BT = Branch Target
                            // FT = Fall Through
                            // See ifXX for details regarding the heuristic for selecting the next
                            // instruction to evaluate.
                            val (newBTOperands, newBTLocals) =
                                yesConstraint(branchTargetPC, left, right, rest, locals)
                            if (tracer.isDefined &&
                                ((rest ne newBTOperands) || (locals ne newBTLocals))) {
                                tracer.get.establishedConstraint(theDomain)(
                                    pc, branchTargetPC, rest, locals, newBTOperands, newBTLocals
                                )
                            }
                            val (newFTOperands, newFTLocals) =
                                noConstraint(nextPC, left, right, rest, locals)
                            if (tracer.isDefined &&
                                ((rest ne newFTOperands) || (locals ne newFTLocals))) {
                                tracer.get.establishedConstraint(theDomain)(
                                    pc, nextPC, rest, locals, newFTOperands, newFTLocals
                                )
                            }
                            if (branchTargetPC > pc) {
                                gotoTarget(
                                    pc, instruction, operands, locals,
                                    nextPC, isExceptionalControlFlow = false,
                                    newFTOperands, newFTLocals
                                )
                                gotoTarget(
                                    pc, instruction, operands, locals,
                                    branchTargetPC, isExceptionalControlFlow = false,
                                    newBTOperands, newBTLocals
                                )
                            } else {
                                gotoTarget(
                                    pc, instruction, operands, locals,
                                    branchTargetPC, isExceptionalControlFlow = false,
                                    newBTOperands, newBTLocals
                                )
                                gotoTarget(
                                    pc, instruction, operands, locals,
                                    nextPC, isExceptionalControlFlow = false,
                                    newFTOperands, newFTLocals
                                )
                            }
                    }
                }

                /*
                 * Handles the control-flow when an exception was raised.
                 *
                 * Called when an exception was explicitly (by means of an athrow
                 * instruction) or implicitly raised as a side effect of
                 * evaluating the current instruction. In this case, the corresponding
                 * handler is searched and the control is transferred to it.
                 * If no handler is found the domain is
                 * informed that the method invocation completed abruptly.
                 *
                 * @note The operand stack will only contain the raised exception.
                 *
                 * @param exceptionValue A value that represents an instance of
                 *      an object that is a subtype of `java.lang.Throwable`.
                 */
                def doHandleTheException(
                    exceptionValue:   ExceptionValue,
                    establishNonNull: Boolean
                ): PCs = {

                    var targetPCs = IntSet.empty
                    def gotoExceptionHandler(
                        pc:             PC,
                        branchTargetPC: PC,
                        upperBound:     Option[ObjectType]
                    ): Unit = {
                        val newOperands = Chain.singleton(exceptionValue)
                        val memoryLayout1 @ (updatedOperands1, updatedLocals1) =
                            if (establishNonNull)
                                theDomain.refEstablishIsNonNull(
                                    pc, exceptionValue, newOperands, locals
                                )
                            else
                                (newOperands, locals)

                        val (updatedOperands2, updatedLocals2) =
                            if (upperBound.isDefined)
                                theDomain.refSetUpperTypeBoundOfTopOperand(
                                    branchTargetPC,
                                    upperBound.get,
                                    updatedOperands1,
                                    updatedLocals1
                                )
                            else
                                memoryLayout1

                        gotoTarget(
                            pc, instruction, operands, locals,
                            branchTargetPC, isExceptionalControlFlow = true,
                            updatedOperands2, updatedLocals2
                        )
                        targetPCs += branchTargetPC
                    }

                    val isHandled = code.handlersFor(pc) exists { eh ⇒
                        // find the exception handler that matches the given exception
                        val branchTarget = eh.handlerPC
                        val catchTypeOption = eh.catchType
                        if (catchTypeOption.isEmpty) { // this is a finally handler
                            gotoExceptionHandler(pc, branchTarget, None)
                            true
                        } else {
                            theDomain.isValueSubtypeOf(exceptionValue, catchTypeOption.get) match {
                                case No ⇒
                                    false
                                case Yes ⇒
                                    gotoExceptionHandler(pc, branchTarget, None)
                                    true
                                case Unknown ⇒
                                    gotoExceptionHandler(pc, branchTarget, catchTypeOption)
                                    false
                            }
                        }
                    }
                    // If "isHandled" is true, we are sure that at least one
                    // handler caught the exception... hence this method
                    // invocation will not complete abruptly.
                    if (!isHandled) abruptMethodExecution(pc, exceptionValue)

                    targetPCs
                }

                def handleException(exceptionValue: ExceptionValue): PCs = {
                    // iterating over the individual exceptions is potentially
                    // more precise than just iterating over the "abstraction"
                    val baseValues = exceptionValue.baseValues
                    if (baseValues.isEmpty) {
                        exceptionValue.isNull match {
                            case No ⇒ // just forward
                                doHandleTheException(exceptionValue, false)
                            case Unknown ⇒
                                val npeHandlerPC =
                                    if (theDomain.throwNullPointerExceptionOnThrow) {
                                        val npe = theDomain.VMNullPointerException(pc)
                                        doHandleTheException(npe, false)
                                    } else
                                        IntSet.empty
                                npeHandlerPC ++ doHandleTheException(exceptionValue, true)
                            case Yes ⇒
                                val npe = theDomain.VMNullPointerException(pc)
                                doHandleTheException(npe, false)
                        }
                    } else {
                        handleExceptions(baseValues)
                    }
                }

                def handleExceptions(exceptions: Traversable[ExceptionValue]): PCs = {
                    exceptions.foldLeft(IntSet.empty)(_ ++ handleException(_))
                }

                def abruptMethodExecution(pc: Int, exception: ExceptionValue): Unit = {
                    if (tracer.isDefined)
                        tracer.get.abruptMethodExecution(theDomain)(pc, exception)

                    theDomain.abruptMethodExecution(pc, exception)
                }

                def fallThrough(
                    newOperands: Operands = operands,
                    newLocals:   Locals   = locals
                ): PC = {
                    val nextPC = pcOfNextInstruction
                    gotoTarget(
                        pc, instruction, operands, locals,
                        nextPC, isExceptionalControlFlow = false,
                        newOperands, newLocals
                    )
                    nextPC
                }

                def handleReturn(computation: Computation[Nothing, ExceptionValue]): Unit = {
                    if (computation.throwsException) handleException(computation.exceptions)
                }

                def computationWithException(
                    computation: Computation[Nothing, ExceptionValue],
                    rest:        Operands
                ): Unit = {
                    //TODO val regPC =
                    if (computation.returnsNormally) fallThrough(rest) // else -1
                    //TODO val exPCs =
                    if (computation.throwsException) handleException(computation.exceptions) // else IntSet.empty

                    //TODO if (computation.returnsNormally != computation.throwsException)
                    //TODO    println(s"$pc: DEFINITIVE PATH $regPC of ${exPCs} - $instruction")
                }

                def computationWithExceptions(
                    computation: Computation[Nothing, ExceptionValues],
                    rest:        Operands
                ): Unit = {

                    if (computation.returnsNormally) fallThrough(rest)
                    if (computation.throwsException) handleExceptions(computation.exceptions)
                }

                def computationWithReturnValueAndException(
                    computation: Computation[DomainValue, ExceptionValue],
                    rest:        Operands
                ): Unit = {
                    //TODOval regPC =
                    if (computation.hasResult) fallThrough(computation.result :&: rest) // else -1
                    //TODO val exPCs =
                    if (computation.throwsException) handleException(computation.exceptions) // else IntSet.empty

                    //TODO if (computation.returnsNormally != computation.throwsException)
                    //TODO    println(s"$pc: DEFINITIVE PATH $regPC of ${exPCs} in {$exPCs} - $instruction")
                }

                def computationWithReturnValueAndExceptions(
                    computation: Computation[DomainValue, ExceptionValues],
                    rest:        Operands
                ): Unit = {

                    if (computation.hasResult) fallThrough(computation.result :&: rest)
                    if (computation.throwsException) handleExceptions(computation.exceptions)
                }

                def computationWithOptionalReturnValueAndExceptions(
                    computation: Computation[DomainValue, ExceptionValues],
                    rest:        Operands
                ): Unit = {

                    if (computation.returnsNormally) {
                        if (computation.hasResult)
                            fallThrough(computation.result :&: rest)
                        else
                            fallThrough(rest)
                    }
                    if (computation.throwsException) handleExceptions(computation.exceptions)

                    //TODOif (computation.hasResult != computation.throwsException)
                    //TODO    println(s"$pc: DEFINITIVE PATH - $instruction")
                }

                // Small helper method to make type casts shorter.
                @inline def as[I <: Instruction](i: Instruction): I = i.asInstanceOf[I]

                (instruction.opcode: @annotation.switch) match {
                    //
                    // UNCONDITIONAL TRANSFER OF CONTROL
                    //

                    case 167 /*goto*/ | 200 /*goto_w*/ ⇒
                        val goto = as[UnconditionalBranchInstruction](instruction)
                        val offset = goto.branchoffset
                        val branchtarget = pc + offset
                        gotoTarget(
                            pc, instruction, operands, locals,
                            branchtarget, isExceptionalControlFlow = false,
                            operands, locals
                        )

                    // Fundamental idea: we treat a "jump to subroutine" similar to
                    // the call of a method. I.e., we make sure that the operand
                    // stack and the registers are empty for all instructions that
                    // potentially belong to the subroutine.
                    // Basically, we clear all information when the exploration of
                    // all paths of the subroutine is finished
                    // and before we return from the subroutine.
                    // Semantics (from the JVM Spec):
                    // - The instruction following each jsr(_w) instruction may be
                    //      returned to only by a single ret instruction.
                    // - No jsr(_w) instruction that is returned to may be used to
                    //      recursively call a subroutine if that subroutine is already
                    //      present in the subroutine call chain. (Subroutines can be
                    //      nested when using try-finally constructs from within a
                    //      finally clause.)
                    //      HOWEVER, if a subroutine is terminated by a thrown exception
                    //      it may be the case that we call the same subroutine again
                    //      even though it appears as if the subroutine was not finished.
                    // - Each instance of type return address can be returned to at most
                    //      once.
                    case 168 /*jsr*/ | 201 /*jsr_w*/ ⇒
                        val returnTarget = pcOfNextInstruction
                        val branchTarget = pc + as[JSRInstruction](instruction).branchoffset
                        evaluated :&:= SUBROUTINE_START
                        memoryLayoutBeforeSubroutineCall :&:= (
                            (branchTarget, operandsArray.clone, localsArray.clone)
                        )

                        // let's check if we can eagerly fetch the information where the
                        // return address is stored!
                        worklist = instructions(branchTarget) match {
                            case AStoreInstruction(lvIndex) ⇒
                                SUBROUTINE_START :&:
                                    SUBROUTINE_RETURN_ADDRESS_LOCAL_VARIABLE :&: lvIndex :&:
                                    SUBROUTINE_RETURN_TO_TARGET :&: returnTarget :&:
                                    SUBROUTINE :&: worklist
                            case _ ⇒
                                SUBROUTINE_START :&:
                                    SUBROUTINE_RETURN_TO_TARGET :&: returnTarget :&:
                                    SUBROUTINE :&: worklist
                        }
                        val newOperands = theDomain.ReturnAddressValue(returnTarget) :&: operands
                        gotoTarget(
                            pc, instruction, operands, locals,
                            branchTarget, isExceptionalControlFlow = false,
                            newOperands, locals
                        )

                        theDomain.jumpToSubroutine(pc, branchTarget, returnTarget)

                        if (tracer.isDefined) {
                            tracer.get.jumpToSubroutine(theDomain)(
                                pc, branchTarget, memoryLayoutBeforeSubroutineCall.size
                            )
                        }

                    case 169 /*ret*/ ⇒
                        val lvIndex = as[RET](instruction).lvIndex
                        // we now know the local variable that is used and
                        // (one of) the ret instruction(s), we store this for later usage
                        val oldWorklist: List[PC] = worklist

                        // IMPROVE Use ChainBuilder to enable efficient creation of the chain.
                        var subroutineWorklist = List.empty[PC] // after the next steps...
                        var tail = worklist
                        while (tail.head >= 0) {
                            subroutineWorklist :&:= tail.head
                            tail = tail.tail
                        }
                        subroutineWorklist = subroutineWorklist.reverse // reestablish the correct order
                        tail = tail.tail // remove SUBROUTINE_START marker

                        var dynamicSubroutineInformation = List.empty[PC]
                        while (tail.head != SUBROUTINE_RETURN_TO_TARGET) {
                            dynamicSubroutineInformation :&:= tail.head
                            tail = tail.tail
                        }
                        // let's check if we already know the used local variable
                        if (dynamicSubroutineInformation.isEmpty) {
                            // let's store the local variable
                            worklist =
                                subroutineWorklist :&::
                                    (SUBROUTINE_START :&: pc :&:
                                        SUBROUTINE_RETURN_ADDRESS_LOCAL_VARIABLE :&: lvIndex :&:
                                        tail)
                        } else {
                            // just let's store this ret instruction
                            worklist =
                                subroutineWorklist :&::
                                    (SUBROUTINE_START :&: pc :&: dynamicSubroutineInformation.reverse) :&::
                                    tail
                        }

                        theDomain.returnFromSubroutine(pc, lvIndex)

                        if (tracer.isDefined) {
                            tracer.get.ret(theDomain)(
                                pc,
                                locals(lvIndex).asReturnAddressValue,
                                oldWorklist,
                                worklist
                            )
                        }

                    //
                    // CONDITIONAL TRANSFER OF CONTROL
                    //

                    case 165 /*if_acmpeq*/ ⇒
                        ifTcmpXX(refAreEqual _, RefAreEqual, RefAreNotEqual)
                    case 166 /*if_acmpne*/ ⇒
                        ifTcmpXX(refAreNotEqual _, RefAreNotEqual, RefAreEqual)
                    case 198 /*ifnull*/ ⇒
                        ifXX(refIsNull _, RefIsNull, RefIsNonNull)
                    case 199 /*ifnonnull*/ ⇒
                        ifXX(refIsNonNull _, RefIsNonNull, RefIsNull)

                    case 159 /*if_icmpeq*/ ⇒
                        ifTcmpXX(intAreEqual _, IntAreEqual, IntAreNotEqual)
                    case 160 /*if_icmpne*/ ⇒
                        ifTcmpXX(intAreNotEqual _, IntAreNotEqual, IntAreEqual)
                    case 161 /*if_icmplt*/ ⇒
                        ifTcmpXX(intIsLessThan _, IntIsLessThan, IntIsGreaterThanOrEqualTo)
                    case 162 /*if_icmpge*/ ⇒
                        ifTcmpXX(intIsGreaterThanOrEqualTo _, IntIsGreaterThanOrEqualTo, IntIsLessThan)
                    case 163 /*if_icmpgt*/ ⇒
                        ifTcmpXX(intIsGreaterThan _, IntIsGreaterThan, IntIsLessThanOrEqualTo)
                    case 164 /*if_icmple*/ ⇒
                        ifTcmpXX(intIsLessThanOrEqualTo _, IntIsLessThanOrEqualTo, IntIsGreaterThan)
                    case 153 /*ifeq*/ ⇒
                        ifXX(intIs0 _, IntIs0, IntIsNot0)
                    case 154 /*ifne*/ ⇒
                        ifXX(intIsNot0 _, IntIsNot0, IntIs0)
                    case 155 /*iflt*/ ⇒
                        ifXX(intIsLessThan0 _, IntIsLessThan0, IntIsGreaterThanOrEqualTo0)
                    case 156 /*ifge*/ ⇒
                        ifXX(intIsGreaterThanOrEqualTo0 _, IntIsGreaterThanOrEqualTo0, IntIsLessThan0)
                    case 157 /*ifgt*/ ⇒
                        ifXX(intIsGreaterThan0 _, IntIsGreaterThan0, IntIsLessThanOrEqualTo0)
                    case 158 /*ifle */ ⇒
                        ifXX(intIsLessThanOrEqualTo0 _, IntIsLessThanOrEqualTo0, IntIsGreaterThan0)

                    case 171 /*lookupswitch*/ ⇒
                        val switch = as[LOOKUPSWITCH](instruction)
                        val index = operands.head
                        val remainingOperands = operands.tail
                        if (switch.npairs.isEmpty) {
                            // in the Java 7 JDK 45 we actually found a lookupswitch
                            // that just had a defaultBranch (glorified "goto")
                            gotoTarget(
                                pc, instruction, operands, locals,
                                pc + switch.defaultOffset,
                                isExceptionalControlFlow = false,
                                remainingOperands, locals
                            )
                        } else {
                            import theDomain.intIsSomeValueInRange
                            import theDomain.intIsSomeValueNotInRange
                            var branchToDefaultRequired = false
                            val npairs = switch.npairs
                            val firstKey = npairs(0)._1
                            var previousKey = firstKey
                            for ((key, offset) ← npairs) {
                                if (!branchToDefaultRequired && (key - previousKey) > 1) {
                                    // there is a hole in the switch table...
                                    val nonCaseValue =
                                        intIsSomeValueInRange(pc, index, previousKey + 1, key - 1)
                                    if (nonCaseValue.isYesOrUnknown) {
                                        branchToDefaultRequired = true
                                    }
                                }
                                previousKey = key

                                if (intIsSomeValueInRange(pc, index, key, key).isYesOrUnknown) {
                                    val branchTargetPC = pc + offset
                                    val (updatedOperands, updatedLocals) =
                                        theDomain.intEstablishValue(
                                            branchTargetPC, key, index, remainingOperands, locals
                                        )
                                    if (tracer.isDefined &&
                                        (
                                            (remainingOperands ne updatedOperands) ||
                                            (locals ne updatedLocals)
                                        )) {
                                        tracer.get.establishedConstraint(theDomain)(
                                            pc, branchTargetPC, remainingOperands, locals,
                                            updatedOperands, updatedLocals
                                        )
                                    }
                                    gotoTarget(
                                        pc, instruction, operands, locals,
                                        branchTargetPC, isExceptionalControlFlow = false,
                                        updatedOperands, updatedLocals
                                    )
                                }
                            }

                            if (branchToDefaultRequired ||
                                intIsSomeValueNotInRange(
                                    pc, index, firstKey, npairs.last._1
                                ).isYesOrUnknown) {
                                val defaultBranchTargetPC = pc + switch.defaultOffset

                                gotoTarget(
                                    pc, instruction, operands, locals,
                                    defaultBranchTargetPC, isExceptionalControlFlow = false,
                                    remainingOperands, locals
                                )
                            }
                        }

                    case 170 /*tableswitch*/ ⇒
                        import theDomain.intIsSomeValueInRange
                        import theDomain.intIsSomeValueNotInRange
                        val tableswitch = instruction.asInstanceOf[TABLESWITCH]
                        val index = operands.head
                        val remainingOperands = operands.tail
                        val low = tableswitch.low
                        val high = tableswitch.high
                        var v = low
                        while (v <= high) {
                            if (intIsSomeValueInRange(pc, index, v, v).isYesOrUnknown) {
                                val branchTargetPC = pc + tableswitch.jumpOffsets(v - low)
                                val (updatedOperands, updatedLocals) =
                                    theDomain.intEstablishValue(
                                        branchTargetPC, v, index, remainingOperands, locals
                                    )
                                if (tracer.isDefined &&
                                    (
                                        (remainingOperands ne updatedOperands) ||
                                        (locals ne updatedLocals)
                                    )) {
                                    tracer.get.establishedConstraint(theDomain)(
                                        pc, branchTargetPC, remainingOperands, locals,
                                        updatedOperands, updatedLocals
                                    )
                                }
                                gotoTarget(
                                    pc, instruction, operands, locals,
                                    branchTargetPC, isExceptionalControlFlow = false,
                                    updatedOperands, updatedLocals
                                )
                            }
                            v = v + 1
                        }
                        if (intIsSomeValueNotInRange(pc, index, low, high).isYesOrUnknown) {

                            val defaultBranchTargetPC = pc + tableswitch.defaultOffset
                            gotoTarget(
                                pc, instruction, operands, locals,
                                defaultBranchTargetPC, isExceptionalControlFlow = false,
                                remainingOperands, locals
                            )
                        }

                    //
                    // STATEMENTS THAT CAN CAUSE EXCEPTIONELL TRANSFER OF CONTROL FLOW
                    //

                    case 191 /*athrow*/ ⇒
                        // In general, we either have a control flow to an exception handler
                        // or we abort the method.
                        // EXCERPT FROM THE SPEC:
                        // Within a class file the exception handlers for each method are
                        // stored in a table. At runtime the Java virtual machine searches
                        // the exception handlers of the current method in the order that
                        // they appear in the corresponding exception handler table.
                        val theDomain.DomainReferenceValue(exceptionValue) = operands.head
                        handleException(exceptionValue)

                    //
                    // CREATE ARRAY
                    //

                    case 188 /*newarray*/ ⇒
                        val count :&: rest = operands
                        val atype = instruction.asInstanceOf[NEWARRAY].atype
                        val computation = (atype: @annotation.switch) match {
                            case BooleanType.atype ⇒
                                theDomain.newarray(pc, count, BooleanType)
                            case CharType.atype ⇒
                                theDomain.newarray(pc, count, CharType)
                            case FloatType.atype ⇒
                                theDomain.newarray(pc, count, FloatType)
                            case DoubleType.atype ⇒
                                theDomain.newarray(pc, count, DoubleType)
                            case ByteType.atype ⇒
                                theDomain.newarray(pc, count, ByteType)
                            case ShortType.atype ⇒
                                theDomain.newarray(pc, count, ShortType)
                            case IntegerType.atype ⇒
                                theDomain.newarray(pc, count, IntegerType)
                            case LongType.atype ⇒
                                theDomain.newarray(pc, count, LongType)
                        }
                        computationWithReturnValueAndException(computation, rest)

                    case 189 /*anewarray*/ ⇒
                        val count :&: rest = operands
                        val componentType = instruction.asInstanceOf[ANEWARRAY].componentType
                        val computation = theDomain.newarray(pc, count, componentType)
                        computationWithReturnValueAndException(computation, rest)

                    case 197 /*multianewarray*/ ⇒
                        val multianewarray = instruction.asInstanceOf[MULTIANEWARRAY]
                        val dimensions = multianewarray.dimensions
                        val dimensionSizes = operands.take(multianewarray.dimensions)
                        val arrayType = multianewarray.arrayType
                        val computation = theDomain.multianewarray(pc, dimensionSizes, arrayType)
                        computationWithReturnValueAndException(computation, operands.drop(dimensions))

                    //
                    // LOAD FROM AND STORE VALUE IN ARRAYS
                    //

                    case 50 /*aaload*/ ⇒
                        val index :&: arrayref :&: rest = operands
                        // TODO propagate constraints if the index may be invalid...
                        val computation = theDomain.aaload(pc, index, arrayref)
                        computationWithReturnValueAndExceptions(computation, rest)
                    case 83 /*aastore*/ ⇒
                        val value :&: index :&: arrayref :&: rest = operands
                        val computation = theDomain.aastore(pc, value, index, arrayref)
                        computationWithExceptions(computation, rest)

                    case 51 /*baload*/ ⇒
                        val index :&: arrayref :&: rest = operands
                        val computation = theDomain.baload(pc, index, arrayref)
                        computationWithReturnValueAndExceptions(computation, rest)
                    case 84 /*bastore*/ ⇒
                        val value :&: index :&: arrayref :&: rest = operands
                        val computation = theDomain.bastore(pc, value, index, arrayref)
                        computationWithExceptions(computation, rest)

                    case 52 /*caload*/ ⇒
                        val index :&: arrayref :&: rest = operands
                        val computation = theDomain.caload(pc, index, arrayref)
                        computationWithReturnValueAndExceptions(computation, rest)
                    case 85 /*castore*/ ⇒
                        val value :&: index :&: arrayref :&: rest = operands
                        val computation = theDomain.castore(pc, value, index, arrayref)
                        computationWithExceptions(computation, rest)

                    case 49 /*daload*/ ⇒
                        val index :&: arrayref :&: rest = operands
                        val computation = theDomain.daload(pc, index, arrayref)
                        computationWithReturnValueAndExceptions(computation, rest)
                    case 82 /*dastore*/ ⇒
                        val value :&: index :&: arrayref :&: rest = operands
                        val computation = theDomain.dastore(pc, value, index, arrayref)
                        computationWithExceptions(computation, rest)

                    case 48 /*faload*/ ⇒
                        val index :&: arrayref :&: rest = operands
                        val computation = theDomain.faload(pc, index, arrayref)
                        computationWithReturnValueAndExceptions(computation, rest)
                    case 81 /*fastore*/ ⇒
                        val value :&: index :&: arrayref :&: rest = operands
                        val computation = theDomain.fastore(pc, value, index, arrayref)
                        computationWithExceptions(computation, rest)

                    case 46 /*iaload*/ ⇒
                        val index :&: arrayref :&: rest = operands
                        val computation = theDomain.iaload(pc, index, arrayref)
                        computationWithReturnValueAndExceptions(computation, rest)
                    case 79 /*iastore*/ ⇒
                        val value :&: index :&: arrayref :&: rest = operands
                        val computation = theDomain.iastore(pc, value, index, arrayref)
                        computationWithExceptions(computation, rest)

                    case 47 /*laload*/ ⇒
                        val index :&: arrayref :&: rest = operands
                        val computation = theDomain.laload(pc, index, arrayref)
                        computationWithReturnValueAndExceptions(computation, rest)
                    case 80 /*lastore*/ ⇒
                        val value :&: index :&: arrayref :&: rest = operands
                        val computation = theDomain.lastore(pc, value, index, arrayref)
                        computationWithExceptions(computation, rest)

                    case 53 /*saload*/ ⇒
                        val index :&: arrayref :&: rest = operands
                        val computation = theDomain.saload(pc, index, arrayref)
                        computationWithReturnValueAndExceptions(computation, rest)
                    case 86 /*sastore*/ ⇒
                        val value :&: index :&: arrayref :&: rest = operands
                        val computation = theDomain.sastore(pc, value, index, arrayref)
                        computationWithExceptions(computation, rest)

                    //
                    // LENGTH OF AN ARRAY
                    //

                    case 190 /*arraylength*/ ⇒
                        val arrayref = operands.head
                        val computation = theDomain.arraylength(pc, arrayref)
                        computationWithReturnValueAndException(computation, operands.tail)

                    //
                    // ACCESSING FIELDS
                    //
                    case 180 /*getfield*/ ⇒
                        val GETFIELD(declaringClass, name, fieldType) = instruction
                        val receiver = operands.head
                        computationWithReturnValueAndException(
                            theDomain.getfield(pc, receiver, declaringClass, name, fieldType),
                            operands.tail
                        )

                    case 178 /*getstatic*/ ⇒
                        val getstatic = instruction.asInstanceOf[GETSTATIC]
                        computationWithReturnValueAndException(
                            theDomain.getstatic(
                                pc,
                                getstatic.declaringClass,
                                getstatic.name,
                                getstatic.fieldType
                            ),
                            operands
                        )

                    case 181 /*putfield*/ ⇒
                        val putfield = instruction.asInstanceOf[PUTFIELD]
                        val value :&: objectref :&: rest = operands
                        computationWithException(
                            theDomain.putfield(
                                pc,
                                objectref,
                                value,
                                putfield.declaringClass,
                                putfield.name,
                                putfield.fieldType
                            ),
                            rest
                        )

                    case 179 /*putstatic*/ ⇒
                        val putstatic = instruction.asInstanceOf[PUTSTATIC]
                        val value :&: rest = operands
                        computationWithException(
                            theDomain.putstatic(
                                pc,
                                value,
                                putstatic.declaringClass,
                                putstatic.name,
                                putstatic.fieldType
                            ),
                            rest
                        )

                    //
                    // METHOD INVOCATIONS
                    //
                    case 186 /*invokedynamic*/ ⇒
                        val invoke = instruction.asInstanceOf[INVOKEDYNAMIC]
                        val argsCount = invoke.methodDescriptor.parametersCount
                        val computation =
                            theDomain.invokedynamic(
                                pc,
                                invoke.bootstrapMethod,
                                invoke.name,
                                invoke.methodDescriptor,
                                operands.take(argsCount)
                            )
                        computationWithReturnValueAndExceptions(
                            computation,
                            operands.drop(argsCount)
                        )

                    case 185 /*invokeinterface*/ ⇒
                        val invoke = instruction.asInstanceOf[INVOKEINTERFACE]
                        val argsCount = invoke.methodDescriptor.parametersCount
                        val computation =
                            theDomain.invokeinterface(
                                pc,
                                invoke.declaringClass,
                                invoke.name,
                                invoke.methodDescriptor,
                                operands.take(argsCount + 1)
                            )
                        computationWithOptionalReturnValueAndExceptions(
                            computation,
                            operands.drop(argsCount + 1)
                        )

                    case 183 /*invokespecial*/ ⇒
                        val invoke = instruction.asInstanceOf[INVOKESPECIAL]
                        val argsCount = invoke.methodDescriptor.parametersCount
                        val computation =
                            theDomain.invokespecial(
                                pc,
                                invoke.declaringClass,
                                invoke.isInterface,
                                invoke.name,
                                invoke.methodDescriptor,
                                operands.take(argsCount + 1)
                            )
                        computationWithOptionalReturnValueAndExceptions(
                            computation,
                            operands.drop(argsCount + 1)
                        )

                    case 184 /*invokestatic*/ ⇒
                        val invoke = instruction.asInstanceOf[INVOKESTATIC]
                        val argsCount = invoke.methodDescriptor.parametersCount
                        val computation =
                            theDomain.invokestatic(
                                pc,
                                invoke.declaringClass,
                                invoke.isInterface,
                                invoke.name,
                                invoke.methodDescriptor,
                                operands.take(argsCount)
                            )
                        computationWithOptionalReturnValueAndExceptions(
                            computation,
                            operands.drop(argsCount)
                        )

                    case 182 /*invokevirtual*/ ⇒
                        val invoke = instruction.asInstanceOf[INVOKEVIRTUAL]
                        val argsCount = invoke.methodDescriptor.parametersCount
                        val computation =
                            theDomain.invokevirtual(
                                pc,
                                invoke.declaringClass,
                                invoke.name,
                                invoke.methodDescriptor,
                                operands.take(argsCount + 1)
                            )
                        computationWithOptionalReturnValueAndExceptions(
                            computation,
                            operands.drop(argsCount + 1)
                        )

                    case 194 /*monitorenter*/ ⇒
                        val computation = theDomain.monitorenter(pc, operands.head)
                        computationWithException(computation, operands.tail)

                    case 195 /*monitorexit*/ ⇒
                        val computation = theDomain.monitorexit(pc, operands.head)
                        computationWithExceptions(computation, operands.tail)

                    //
                    // RETURN FROM METHOD
                    //
                    case 176 /*areturn*/ ⇒ handleReturn(theDomain.areturn(pc, operands.head))
                    case 175 /*dreturn*/ ⇒ handleReturn(theDomain.dreturn(pc, operands.head))
                    case 174 /*freturn*/ ⇒ handleReturn(theDomain.freturn(pc, operands.head))
                    case 172 /*ireturn*/ ⇒ handleReturn(theDomain.ireturn(pc, operands.head))
                    case 173 /*lreturn*/ ⇒ handleReturn(theDomain.lreturn(pc, operands.head))
                    case 177 /*return*/  ⇒ handleReturn(theDomain.returnVoid(pc))

                    // -----------------------------------------------------------------------
                    //
                    // INSTRUCTIONS THAT ALWAYS JUST FALL THROUGH AND WILL
                    // NEVER THROW AN EXCEPTION
                    //
                    // -----------------------------------------------------------------------

                    //
                    // PUT LOCAL VARIABLE VALUE ONTO STACK
                    //
                    case 25 /*aload*/
                        | 24 /*dload*/
                        | 23 /*fload*/
                        | 21 /*iload*/
                        | 22 /*lload*/ ⇒
                        val lvIndex = as[LoadLocalVariableInstruction](instruction).lvIndex
                        fallThrough(locals(lvIndex) :&: operands)
                    case 42 /*aload_0*/
                        | 38 /*dload_0*/
                        | 34 /*fload_0*/
                        | 26 /*iload_0*/
                        | 30 /*lload_0*/ ⇒
                        fallThrough(locals(0) :&: operands)
                    case 43 /*aload_1*/
                        | 39 /*dload_1*/
                        | 35 /*fload_1*/
                        | 27 /*iload_1*/
                        | 31 /*lload_1*/ ⇒
                        fallThrough(locals(1) :&: operands)
                    case 44 /*aload_2*/
                        | 40 /*dload_2*/
                        | 36 /*fload_2*/
                        | 28 /*iload_2*/
                        | 32 /*lload_2*/ ⇒
                        fallThrough(locals(2) :&: operands)
                    case 45 /*aload_3*/
                        | 41 /*dload_3*/
                        | 37 /*fload_3*/
                        | 29 /*iload_3*/
                        | 33 /*lload_3*/ ⇒
                        fallThrough(locals(3) :&: operands)

                    //
                    // STORE OPERAND IN LOCAL VARIABLE
                    //
                    case 58 /*astore*/
                        | 57 /*dstore*/
                        | 56 /*fstore*/
                        | 54 /*istore*/
                        | 55 /*lstore*/ ⇒
                        val lvIndex = as[StoreLocalVariableInstruction](instruction).lvIndex
                        fallThrough(operands.tail, locals.updated(lvIndex, operands.head))
                    case 75 /*astore_0*/
                        | 71 /*dstore_0*/
                        | 67 /*fstore_0*/
                        | 63 /*lstore_0*/
                        | 59 /*istore_0*/ ⇒
                        fallThrough(operands.tail, locals.updated(0, operands.head))
                    case 76 /*astore_1*/
                        | 72 /*dstore_1*/
                        | 68 /*fstore_1*/
                        | 64 /*lstore_1*/
                        | 60 /*istore_1*/ ⇒
                        fallThrough(operands.tail, locals.updated(1, operands.head))
                    case 77 /*astore_2*/
                        | 73 /*dstore_2*/
                        | 69 /*fstore_2*/
                        | 65 /*lstore_2*/
                        | 61 /*istore_2*/ ⇒
                        fallThrough(operands.tail, locals.updated(2, operands.head))
                    case 78 /*astore_3*/
                        | 74 /*dstore_3*/
                        | 70 /*fstore_3*/
                        | 66 /*lstore_3*/
                        | 62 /*istore_3*/ ⇒
                        fallThrough(operands.tail, locals.updated(3, operands.head))

                    //
                    // PUSH CONSTANT VALUE
                    //

                    case 1 /*aconst_null*/ ⇒ fallThrough(theDomain.NullValue(pc) :&: operands)

                    case 16 /*bipush*/ ⇒
                        val value = instruction.asInstanceOf[BIPUSH].value.toByte
                        fallThrough(theDomain.ByteValue(pc, value) :&: operands)

                    case 14 /*dconst_0*/ ⇒ fallThrough(theDomain.DoubleValue(pc, 0.0d) :&: operands)
                    case 15 /*dconst_1*/ ⇒ fallThrough(theDomain.DoubleValue(pc, 1.0d) :&: operands)

                    case 11 /*fconst_0*/ ⇒ fallThrough(theDomain.FloatValue(pc, 0.0f) :&: operands)
                    case 12 /*fconst_1*/ ⇒ fallThrough(theDomain.FloatValue(pc, 1.0f) :&: operands)
                    case 13 /*fconst_2*/ ⇒ fallThrough(theDomain.FloatValue(pc, 2.0f) :&: operands)

                    case 2 /*iconst_m1*/ ⇒ fallThrough(theDomain.IntegerValue(pc, -1) :&: operands)
                    case 3 /*iconst_0*/  ⇒ fallThrough(theDomain.IntegerValue(pc, 0) :&: operands)
                    case 4 /*iconst_1*/  ⇒ fallThrough(theDomain.IntegerValue(pc, 1) :&: operands)
                    case 5 /*iconst_2*/  ⇒ fallThrough(theDomain.IntegerValue(pc, 2) :&: operands)
                    case 6 /*iconst_3*/  ⇒ fallThrough(theDomain.IntegerValue(pc, 3) :&: operands)
                    case 7 /*iconst_4*/  ⇒ fallThrough(theDomain.IntegerValue(pc, 4) :&: operands)
                    case 8 /*iconst_5*/  ⇒ fallThrough(theDomain.IntegerValue(pc, 5) :&: operands)

                    case 9 /*lconst_0*/  ⇒ fallThrough(theDomain.LongValue(pc, 0L) :&: operands)
                    case 10 /*lconst_1*/ ⇒ fallThrough(theDomain.LongValue(pc, 1L) :&: operands)

                    case 18 /*ldc*/ ⇒ instruction match {
                        case LoadInt(v) ⇒
                            fallThrough(theDomain.IntegerValue(pc, v) :&: operands)
                        case LoadFloat(v) ⇒
                            fallThrough(theDomain.FloatValue(pc, v) :&: operands)
                        case LoadString(v) ⇒
                            fallThrough(theDomain.StringValue(pc, v) :&: operands)
                        case LoadClass(v) ⇒
                            fallThrough(theDomain.ClassValue(pc, v) :&: operands)
                        case LoadMethodHandle(v) ⇒
                            fallThrough(theDomain.MethodHandle(pc, v) :&: operands)
                        case LoadMethodType(v) ⇒
                            fallThrough(theDomain.MethodType(pc, v) :&: operands)

                    }
                    case 19 /*ldc_w*/ ⇒ instruction match {
                        case LoadInt_W(v) ⇒
                            fallThrough(theDomain.IntegerValue(pc, v) :&: operands)
                        case LoadFloat_W(v) ⇒
                            fallThrough(theDomain.FloatValue(pc, v) :&: operands)
                        case LoadString_W(v) ⇒
                            fallThrough(theDomain.StringValue(pc, v) :&: operands)
                        case LoadClass_W(v) ⇒
                            fallThrough(theDomain.ClassValue(pc, v) :&: operands)
                        case LoadMethodHandle_W(v) ⇒
                            fallThrough(theDomain.MethodHandle(pc, v) :&: operands)
                        case LoadMethodType_W(v) ⇒
                            fallThrough(theDomain.MethodType(pc, v) :&: operands)
                    }
                    case 20 /*ldc2_w*/ ⇒ instruction match {
                        case LoadLong(v) ⇒
                            fallThrough(theDomain.LongValue(pc, v) :&: operands)
                        case LoadDouble(v) ⇒
                            fallThrough(theDomain.DoubleValue(pc, v) :&: operands)
                    }

                    case 17 /*sipush*/ ⇒
                        val value = instruction.asInstanceOf[SIPUSH].value.toShort
                        fallThrough(theDomain.ShortValue(pc, value) :&: operands)

                    //
                    // RELATIONAL OPERATORS
                    //
                    case 150 /*fcmpg*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.fcmpg(pc, value1, value2) :&: rest)
                    case 149 /*fcmpl*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.fcmpl(pc, value1, value2) :&: rest)

                    case 152 /*dcmpg*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.dcmpg(pc, value1, value2) :&: rest)
                    case 151 /*dcmpl*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.dcmpl(pc, value1, value2) :&: rest)

                    case 148 /*lcmp*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.lcmp(pc, value1, value2) :&: rest)

                    //
                    // UNARY EXPRESSIONS
                    //
                    case 119 /*dneg*/ ⇒
                        fallThrough(theDomain.dneg(pc, operands.head) :&: operands.tail)
                    case 118 /*fneg*/ ⇒
                        fallThrough(theDomain.fneg(pc, operands.head) :&: operands.tail)
                    case 117 /*lneg*/ ⇒
                        fallThrough(theDomain.lneg(pc, operands.head) :&: operands.tail)
                    case 116 /*ineg*/ ⇒
                        fallThrough(theDomain.ineg(pc, operands.head) :&: operands.tail)

                    //
                    // BINARY EXPRESSIONS
                    //

                    case 99 /*dadd*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.dadd(pc, value1, value2) :&: rest)
                    case 111 /*ddiv*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.ddiv(pc, value1, value2) :&: rest)
                    case 107 /*dmul*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.dmul(pc, value1, value2) :&: rest)
                    case 115 /*drem*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.drem(pc, value1, value2) :&: rest)
                    case 103 /*dsub*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.dsub(pc, value1, value2) :&: rest)

                    case 98 /*fadd*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.fadd(pc, value1, value2) :&: rest)
                    case 110 /*fdiv*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.fdiv(pc, value1, value2) :&: rest)
                    case 106 /*fmul*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.fmul(pc, value1, value2) :&: rest)
                    case 114 /*frem*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.frem(pc, value1, value2) :&: rest)
                    case 102 /*fsub*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.fsub(pc, value1, value2) :&: rest)

                    case 96 /*iadd*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.iadd(pc, value1, value2) :&: rest)
                    case 126 /*iand*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.iand(pc, value1, value2) :&: rest)
                    case 108 /*idiv*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        val computation = theDomain.idiv(pc, value1, value2)
                        computationWithReturnValueAndException(computation, rest)
                    case 104 /*imul*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.imul(pc, value1, value2) :&: rest)
                    case 128 /*ior*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.ior(pc, value1, value2) :&: rest)
                    case 112 /*irem*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        val computation = theDomain.irem(pc, value1, value2)
                        computationWithReturnValueAndException(computation, rest)
                    case 120 /*ishl*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.ishl(pc, value1, value2) :&: rest)
                    case 122 /*ishr*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.ishr(pc, value1, value2) :&: rest)
                    case 100 /*isub*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.isub(pc, value1, value2) :&: rest)
                    case 124 /*iushr*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.iushr(pc, value1, value2) :&: rest)
                    case 130 /*ixor*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.ixor(pc, value1, value2) :&: rest)

                    case 97 /*ladd*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.ladd(pc, value1, value2) :&: rest)
                    case 127 /*land*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.land(pc, value1, value2) :&: rest)
                    case 109 /*ldiv*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        val computation = theDomain.ldiv(pc, value1, value2)
                        computationWithReturnValueAndException(computation, rest)
                    case 105 /*lmul*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.lmul(pc, value1, value2) :&: rest)
                    case 129 /*lor*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.lor(pc, value1, value2) :&: rest)
                    case 113 /*lrem*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        val computation = theDomain.lrem(pc, value1, value2)
                        computationWithReturnValueAndException(computation, rest)
                    case 121 /*lshl*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.lshl(pc, value1, value2) :&: rest)
                    case 123 /*lshr*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.lshr(pc, value1, value2) :&: rest)
                    case 101 /*lsub*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.lsub(pc, value1, value2) :&: rest)
                    case 125 /*lushr*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.lushr(pc, value1, value2) :&: rest)
                    case 131 /*lxor*/ ⇒
                        val value2 :&: value1 :&: rest = operands
                        fallThrough(theDomain.lxor(pc, value1, value2) :&: rest)
                    //
                    // GENERIC STACK MANIPULATION
                    //
                    case 89 /*dup*/ ⇒
                        fallThrough(operands.head :&: operands)
                    case 90 /*dup_x1*/ ⇒
                        val v1 :&: v2 :&: rest = operands
                        fallThrough(v1 :&: v2 :&: v1 :&: rest)
                    case 91 /*dup_x2*/ ⇒
                        operands match {
                            case (v1 /*CTC1*/ ) :&: (v2 @ CTC1()) :&: (v3 /*CTC1*/ ) :&: rest ⇒
                                fallThrough(v1 :&: v2 :&: v3 :&: v1 :&: rest)
                            case (v1 /*@ CTC1()*/ ) :&: v2 /* @ CTC2()*/ :&: rest ⇒
                                fallThrough(v1 :&: v2 :&: v1 :&: rest)
                        }
                    case 92 /*dup2*/ ⇒
                        operands match {
                            case (v1 @ CTC1()) :&: (v2 /*CTC1!*/ ) :&: _ ⇒
                                fallThrough(v1 :&: v2 :&: operands)
                            case (v /*CTC2!*/ ) :&: _ ⇒
                                fallThrough(v :&: operands)
                        }
                    case 93 /*dup2_x1*/ ⇒
                        operands match {
                            case (v1 @ CTC1()) :&: (v2 /*CTC1!*/ ) :&: (v3 /*CTC1!*/ ) :&: rest ⇒
                                fallThrough(v1 :&: v2 :&: v3 :&: v1 :&: v2 :&: rest)
                            case (v1 @ CTC2()) :&: (v2 /*@ CTC1()*/ ) :&: rest ⇒
                                fallThrough(v1 :&: v2 :&: v1 :&: rest)
                        }
                    case 94 /*dup2_x2*/ ⇒ operands match {
                        case (v1 @ CTC1()) :&: (v2 @ CTC1()) :&: (v3 @ CTC1()) :&: v4 :&: rest ⇒
                            fallThrough(v1 :&: v2 :&: v3 :&: v4 :&: v1 :&: v2 :&: rest)
                        case (v1 @ CTC2()) :&: (v2 @ CTC1()) :&: (v3 @ CTC1()) :&: rest ⇒
                            fallThrough(v1 :&: v2 :&: v3 :&: v1 :&: rest)
                        case (v1 @ CTC1()) :&: (v2 @ CTC1()) :&: (v3 @ CTC2()) :&: rest ⇒
                            fallThrough(v1 :&: v2 :&: v3 :&: v1 :&: v2 :&: rest)
                        case (v1 /*@ CTC2()*/ ) :&: (v2 /*@ CTC1()*/ ) :&: rest ⇒
                            fallThrough(v1 :&: v2 :&: v1 :&: rest)
                    }

                    case 87 /*pop*/ ⇒
                        fallThrough(operands.tail)
                    case 88 /*pop2*/ ⇒
                        if (operands.head.computationalType.operandSize == 1)
                            fallThrough(operands.drop(2))
                        else
                            fallThrough(operands.tail)

                    case 95 /*swap*/ ⇒
                        val v1 :&: v2 :&: rest = operands
                        fallThrough(v2 :&: v1 :&: rest)

                    //
                    // TYPE CONVERSION
                    //
                    case 144 /*d2f*/ ⇒
                        fallThrough(theDomain.d2f(pc, operands.head) :&: operands.tail)
                    case 142 /*d2i*/ ⇒
                        fallThrough(theDomain.d2i(pc, operands.head) :&: operands.tail)
                    case 143 /*d2l*/ ⇒
                        fallThrough(theDomain.d2l(pc, operands.head) :&: operands.tail)

                    case 141 /*f2d*/ ⇒
                        fallThrough(theDomain.f2d(pc, operands.head) :&: operands.tail)
                    case 139 /*f2i*/ ⇒
                        fallThrough(theDomain.f2i(pc, operands.head) :&: operands.tail)
                    case 140 /*f2l*/ ⇒
                        fallThrough(theDomain.f2l(pc, operands.head) :&: operands.tail)

                    case 145 /*i2b*/ ⇒
                        fallThrough(theDomain.i2b(pc, operands.head) :&: operands.tail)
                    case 146 /*i2c*/ ⇒
                        fallThrough(theDomain.i2c(pc, operands.head) :&: operands.tail)
                    case 135 /*i2d*/ ⇒
                        fallThrough(theDomain.i2d(pc, operands.head) :&: operands.tail)
                    case 134 /*i2f*/ ⇒
                        fallThrough(theDomain.i2f(pc, operands.head) :&: operands.tail)
                    case 133 /*i2l*/ ⇒
                        fallThrough(theDomain.i2l(pc, operands.head) :&: operands.tail)
                    case 147 /*i2s*/ ⇒
                        fallThrough(theDomain.i2s(pc, operands.head) :&: operands.tail)

                    case 138 /*l2d*/ ⇒
                        fallThrough(theDomain.l2d(pc, operands.head) :&: operands.tail)
                    case 137 /*l2f*/ ⇒
                        fallThrough(theDomain.l2f(pc, operands.head) :&: operands.tail)
                    case 136 /*l2i*/ ⇒
                        fallThrough(theDomain.l2i(pc, operands.head) :&: operands.tail)

                    case 192 /*checkcast*/ ⇒
                        val objectref = operands.head
                        val supertype = instruction.asInstanceOf[CHECKCAST].referenceType
                        val isNull = theDomain.refIsNull(pc, objectref)
                        if (isNull.isYes)
                            // if objectref is null => UNCHANGED (see spec. for details)
                            fallThrough()
                        else {
                            theDomain.isValueSubtypeOf(objectref, supertype) match {
                                case Yes ⇒
                                    // if objectref is a subtype or if null is Unknown => UNCHANGED
                                    fallThrough()

                                case No ⇒
                                    if (isNull.isNo)
                                        handleException(theDomain.VMClassCastException(pc))
                                    else { // isNull is unknown
                                        val (newOperands, newLocals) =
                                            theDomain.refTopOperandIsNull(pc, operands, locals)
                                        fallThrough(newOperands, newLocals)

                                        if (theDomain.throwClassCastException)
                                            handleException(theDomain.VMClassCastException(pc))
                                    }

                                case Unknown ⇒
                                    val (newOperands, newLocals) =
                                        theDomain.refSetUpperTypeBoundOfTopOperand(
                                            pc,
                                            supertype,
                                            operands, locals
                                        )
                                    // The following assert may catch bugs in the
                                    // implementation of domains!
                                    assert(
                                        {
                                            val castedValue = newOperands.head
                                            theDomain.isValueSubtypeOf(castedValue, supertype).isYes
                                        },
                                        s"the cast of $objectref to ${supertype.toJava} failed: "+
                                            s"the subtyping relation between "+
                                            s"${newOperands.head} and ${supertype.toJava} is "+
                                            theDomain.isValueSubtypeOf(newOperands.head, supertype)
                                    )
                                    fallThrough(newOperands, newLocals)

                                    if (theDomain.throwClassCastException)
                                        handleException(theDomain.VMClassCastException(pc))
                            }
                        }

                    //
                    // "OTHER" INSTRUCTIONS
                    //

                    case 193 /*instanceof*/ ⇒
                        val value :&: rest = operands
                        val referenceType = as[INSTANCEOF](instruction).referenceType

                        val valueIsNull = theDomain.refIsNull(pc, value)
                        val result =
                            if (valueIsNull.isYes)
                                theDomain.BooleanValue(pc, false)
                            else
                                theDomain.isValueSubtypeOf(value, referenceType) match {
                                    case No ⇒
                                        theDomain.BooleanValue(pc, false)

                                    case Yes if valueIsNull.isNo ⇒
                                        // null instanceOf[X] is always false...
                                        // TODO [Dependent Values] add a constraint that – if the value is 1 then the value is non-null and if is 0 then the value is null
                                        theDomain.BooleanValue(pc, true)

                                    case _ /*Unknown or valueIsNull === Unknown*/ ⇒
                                        theDomain.BooleanValue(pc)
                                }
                        fallThrough(result :&: rest)

                    case 132 /*iinc*/ ⇒
                        val iinc = as[IINC](instruction)
                        val newValue = theDomain.iinc(pc, locals(iinc.lvIndex), iinc.constValue)
                        fallThrough(operandsArray(pc), locals.updated(iinc.lvIndex, newValue))

                    case 187 /*new*/ ⇒
                        val newObject = as[NEW](instruction)
                        fallThrough(theDomain.NewObject(pc, newObject.objectType) :&: operands)

                    case 0 /*nop*/    ⇒ fallThrough()
                    case 196 /*wide*/ ⇒ fallThrough()

                    case opcode ⇒
                        val message = s"unknown opcode: $opcode"
                        throw BytecodeProcessingFailedException(message)
                }

                theDomain.evaluationCompleted(
                    pc, worklist, evaluated, operandsArray, localsArray, tracer
                )

            } catch {
                case ct: ControlThrowable ⇒ throw ct
                case t: Throwable         ⇒ throwInterpretationFailedException(t, pc)
            }
        }

        abstractInterpretationEnded()
    }
}

/**
 * The `AI` trait's companion object that primarily defines instance independent values.
 *
 * @author Michael Eichberg
 */
private object AI {

    /**
     * The list of program counters (`List(0)`) that is used when we analysis a method
     * right from the beginning.
     */
    final val initialWorkList: List[PC] = Chain.singleton(0)

}

/**
 * Facilitates matching against values of computational type category 1.
 *
 * @example
 * {{{
 * case v @ CTC1() => ...
 * }}}
 *
 * @author Michael Eichberg
 */
object CTC1 {
    def unapply(value: Domain#DomainValue): Boolean = value.computationalType.categoryId == 1
}

/**
 * Facilitates matching against values of computational type category 2.
 *
 * @example
 * {{{
 * case v @ CTC2() => ...
 * }}}
 *
 * @author Michael Eichberg
 */
object CTC2 {
    def unapply(value: Domain#DomainValue): Boolean = value.computationalType.categoryId == 2
}
