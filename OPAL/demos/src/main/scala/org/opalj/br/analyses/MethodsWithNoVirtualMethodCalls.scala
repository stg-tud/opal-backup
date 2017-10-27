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
package br
package analyses

import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

import org.opalj.util.PerformanceEvaluation.time
import org.opalj.br.instructions.VirtualMethodInvocationInstruction
import org.opalj.ai.analyses.{MethodReturnValuesAnalysis ⇒ TheAnalysis}

/**
 * A shallow analysis that identifies methods that do not perform virtual method
 * calls.
 *
 * @author Michael Eichberg
 */
object MethodsWithNoVirtualMethodCalls extends DefaultOneStepAnalysis {

    override def title: String = "identifies methods that perform no virtual method calls"

    override def description: String = TheAnalysis.description

    override def doAnalyze(
        theProject:    Project[URL],
        parameters:    Seq[String],
        isInterrupted: () ⇒ Boolean
    ): BasicReport = {

        val allMethods = new AtomicInteger(0)

        val methodsWithoutVirtualMethodCalls =
            time {
                for {
                    classFile ← theProject.allClassFiles.par
                    (method, body) ← classFile.methodsWithBody
                    if { allMethods.incrementAndGet(); true }
                    if body.instructions.exists { i ⇒ i.isInstanceOf[VirtualMethodInvocationInstruction] }
                } yield {
                    method.toJava
                }
            } { t ⇒ println(s"Analysis time: $t") }

        BasicReport(
            methodsWithoutVirtualMethodCalls.map(_.toString()).seq.toSeq.sorted.mkString(
                s"${methodsWithoutVirtualMethodCalls.size} methods out of ${allMethods.get} methods with a body perfom no virtual method call\n",
                "\n",
                "\n"
            )
        )
    }
}
