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
package org.opalj.support.debug

import java.net.URL

import org.opalj.util.PerformanceEvaluation.time
import org.opalj.br.Method
import org.opalj.br.analyses.BasicReport
import org.opalj.br.analyses.Project
import org.opalj.ai.analyses.cg.CHACallGraphAlgorithmConfiguration
import org.opalj.ai.analyses.cg.CallGraphFactory
import org.opalj.ai.analyses.cg.ComputedCallGraph
import org.opalj.ai.analyses.cg.VTAWithPreAnalysisCallGraphAlgorithmConfiguration
import org.opalj.ai.analyses.cg.DefaultVTACallGraphDomain
import org.opalj.ai.analyses.cg.CallGraphDifferenceReport
import org.opalj.ai.analyses.cg.CallGraphComparison
import org.opalj.br.analyses.DefaultOneStepAnalysis

/**
 * Calculates and compares the results of two call graphs.
 *
 * @author Michael Eichberg
 */
object CallGraphDiff extends DefaultOneStepAnalysis {

    override def title: String = "identifies differences between two call graphs"

    override def description: String = {
        "identifies methods that do not have the same call graph information"
    }

    override def doAnalyze(
        project:       Project[URL],
        parameters:    Seq[String],
        isInterrupted: () ⇒ Boolean
    ): BasicReport = {
        val (unexpected, additional) = callGraphDiff(project, Console.println, isInterrupted)
        if (unexpected.nonEmpty || additional.nonEmpty) {
            var r = "Found the following difference(s):\n"
            if (additional.nonEmpty) {
                r = additional.mkString(r+"Additional:\n", "\n\n", "\n\n")
            }
            if (unexpected.nonEmpty) {
                r = unexpected.mkString(r+"Unexpected:\n", "\n\n", "\n\n")
            }
            BasicReport(r)
        } else
            BasicReport("No differences found.")
    }

    def callGraphDiff(
        project:       Project[_],
        println:       String ⇒ Unit,
        isInterrupted: () ⇒ Boolean
    ): (List[CallGraphDifferenceReport], List[CallGraphDifferenceReport]) = {
        // TODO Add support for interrupting the calculation of the control-flow graph
        import CallGraphFactory.defaultEntryPointsForLibraries
        val entryPoints = () ⇒ defaultEntryPointsForLibraries(project)
        val ComputedCallGraph(lessPreciseCG, _, _) = time {
            CallGraphFactory.create(
                project,
                entryPoints,
                new CHACallGraphAlgorithmConfiguration(project)
            )
        } { t ⇒ println("creating the less precise call graph took "+t) }

        if (isInterrupted())
            return null;

        val ComputedCallGraph(morePreciseCG, _, _) = time {
            CallGraphFactory.create(
                project,
                entryPoints,
                new VTAWithPreAnalysisCallGraphAlgorithmConfiguration(project) {
                    override def Domain(method: Method) = {
                        new DefaultVTACallGraphDomain(
                            project, fieldValueInformation, methodReturnValueInformation,
                            cache,
                            method //, 4
                        )
                    }
                }
            )
        } { ns ⇒ println("creating the more precise call graph took "+ns.toSeconds) }

        if (isInterrupted())
            return null;

        CallGraphComparison(project, lessPreciseCG, morePreciseCG)
    }
}