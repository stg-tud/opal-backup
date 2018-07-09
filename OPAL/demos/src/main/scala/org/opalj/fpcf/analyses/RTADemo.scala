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
package analyses

import java.net.URL

import org.opalj.br.analyses.BasicReport
import org.opalj.br.analyses.DeclaredMethodsKey
import org.opalj.br.analyses.DefaultOneStepAnalysis
import org.opalj.br.analyses.Project
import org.opalj.br.analyses.ReportableAnalysisResult
import org.opalj.fpcf.analyses.cg.EagerLoadedClassesAnalysis
import org.opalj.fpcf.analyses.cg.EagerRTACallGraphAnalysisScheduler
import org.opalj.fpcf.par.PKEParallelTasksPropertyStore
import org.opalj.fpcf.par.RecordAllPropertyStoreTracer
import org.opalj.fpcf.properties.Callees
import org.opalj.fpcf.properties.CallersProperty
import org.opalj.fpcf.properties.InstantiatedTypes
import org.opalj.fpcf.properties.LoadedClasses
import org.opalj.log.OPALLogger.info
import org.opalj.tac.SimpleTACAIKey
import org.opalj.util.PerformanceEvaluation.time

object RTADemo extends DefaultOneStepAnalysis {
    override def doAnalyze(
        project: Project[URL], parameters: Seq[String], isInterrupted: () ⇒ Boolean
    ): ReportableAnalysisResult = {

        project.getOrCreateProjectInformationKeyInitializationData(
            PropertyStoreKey,
            (context: List[PropertyStoreContext[AnyRef]]) ⇒ {
                val ps = PKEParallelTasksPropertyStore.create(
                    new RecordAllPropertyStoreTracer,
                    context.iterator.map(_.asTuple).toMap
                )(project.logContext)
                PropertyStore.updateDebug(true)
                ps
            }
        )

        val ps = project.get(PropertyStoreKey)
        PropertyStore.updateDebug(true)
        //PropertyStore.updateTraceCycleResolutions(true)
        //PropertyStore.updateTraceFallbacks(true)

        implicit val logContext = project.logContext

        // Get the TAC code for all methods to make it possible to measure the time for
        // the analysis itself.
        time {
            val tac = project.get(SimpleTACAIKey)
            project.parForeachMethodWithBody() { m ⇒ tac(m.method) }
        } { t ⇒ info("progress", s"generating 3-address code took ${t.toSeconds}") }

        // Get the TAC code for all methods to make it possible to measure the time for
        // the analysis itself.
        time {
            val manager = project.get(FPCFAnalysesManagerKey)
            manager.runAll(EagerRTACallGraphAnalysisScheduler, EagerLoadedClassesAnalysis)
        } { t ⇒ info("progress", s"constructing the call graph took ${t.toSeconds}") }

        ps.waitOnPhaseCompletion()

        val declaredMethods = project.get(DeclaredMethodsKey)

        println(ps(project, InstantiatedTypes.key).ub)
        println(ps(project, LoadedClasses.key).ub)
        for (m ← project.allMethods) {
            val dm = declaredMethods(m)
            ps(dm, CallersProperty.key)
            /*if (callers.isFinal)
                println(callers)*/
            ps(dm, Callees.key)
            /*if (callees.isFinal)
                println(callees)*/
        }
        // for (m <- project.allMethods) {
        //    println(ps(m, Callees.key))
        //}

        println(ps.statistics.mkString("\n"))

        BasicReport("")
    }
}
