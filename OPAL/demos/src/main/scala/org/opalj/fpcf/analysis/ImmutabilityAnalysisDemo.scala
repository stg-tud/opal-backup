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
package analysis

import java.net.URL

import org.opalj.util.gc
import org.opalj.util.Nanoseconds
import org.opalj.util.PerformanceEvaluation.time

import org.opalj.br.ClassFile
import org.opalj.br.analyses.PropertyStoreKey
import org.opalj.br.analyses.DefaultOneStepAnalysis
import org.opalj.br.analyses.Project
import org.opalj.br.analyses.BasicReport

import org.opalj.fpcf.properties.ClassImmutability
import org.opalj.fpcf.properties.TypeImmutability
import org.opalj.fpcf.properties.ExtensibleType

/**
 * Determines the immutability of the classes of a project.
 *
 * @author Michael Eichberg
 */
object ImmutabilityAnalysisDemo extends DefaultOneStepAnalysis {

    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!//
    //                                                                                            //
    // THIS CODE CONTAINS THE PERFORMANCE MEASUREMENT CODE AS USED FOR THE "REACTIVE PAPER"!      //
    //                                                                                            //
    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!//

    override def title: String = "determines the immutability of objects and types"

    override def description: String = "determines the immutability of objects and types"

    private[this] var setupTime = Nanoseconds.None
    private[this] var analysisTime = Nanoseconds.None
    private[this] var performanceData: Map[Nanoseconds, List[Nanoseconds]] = Map.empty

    override def doAnalyze(
        project:       Project[URL],
        parameters:    Seq[String],
        isInterrupted: () ⇒ Boolean
    ): BasicReport = {
        var r: () ⇒ String = null

        def handleResults(t: Nanoseconds, ts: Seq[Nanoseconds]) = {
            performanceData += ((t, List(setupTime, analysisTime)))
            performanceData = performanceData.filter((t_ts) ⇒ ts.contains(t_ts._1))
        }

        List(1, 2, 4, 8, 16, 32, 64).foreach { parallelismLevel ⇒
            performanceData = Map.empty
            gc()

            println(s"\nRunning analysis with $parallelismLevel thread(s):")
            r = time[() ⇒ String](10, 50, 15, analyze(project, parallelismLevel))(handleResults)
            println(
                s"Results with $parallelismLevel threads:\n"+
                    performanceData.values.
                    map(v ⇒ v.map(_.toSeconds.toString(false))).
                    map(v ⇒ List("setup\t", "analysis\t").zip(v).map(e ⇒ e._1 + e._2).mkString("", "\n", "\n")).
                    mkString("\n")
            )

            gc()
        }
        BasicReport(r())
    }

    def analyze(theProject: Project[URL], parallelismLevel: Int): () ⇒ String = {
        val project = Project.recreate(theProject) // We need an empty project(!)

        import project.get

        // The following measurements (t) are done such that the results are comparable with the
        // reactive async approach developed by P. Haller and Simon Gries.
        val projectStore = time {
            PropertyStoreKey.parallelismLevel = parallelismLevel
            get(PropertyStoreKey)
        } { r ⇒ setupTime = r }
        //projectStore.debug = true

        val manager = project.get(FPCFAnalysesManagerKey)
        manager.runAll(TypeExtensibilityAnalysis, FieldMutabilityAnalysis)

        time {
            manager.runAll(ClassImmutabilityAnalysis, TypeImmutabilityAnalysis)
        } { r ⇒ analysisTime = r }

        println(s"\nsetup: ${setupTime.toSeconds}; analysis: ${analysisTime.toSeconds}")

        () ⇒ {
            projectStore.validate(None)

            val extensibleClasses =
                projectStore.entities(ExtensibleType).
                    map(_.asInstanceOf[ClassFile].thisType.toJava).toList.sorted.
                    mkString("\n\t\t\t", "\n\t\t\t", "\n")

            val immutableClasses =
                projectStore.entities(ClassImmutability.key).
                    filter(ep ⇒ !ep.e.asInstanceOf[ClassFile].isInterfaceDeclaration).
                    groupBy { _.p }.map { kv ⇒
                        (
                            kv._1,
                            kv._2.toList.sortWith { (a, b) ⇒
                                val cfA = a.e.asInstanceOf[ClassFile]
                                val cfB = b.e.asInstanceOf[ClassFile]
                                cfA.thisType.toJava < cfB.thisType.toJava
                            }
                        )
                    }

            val immutableClassesPerCategory =
                immutableClasses.map(kv ⇒ "\t\t"+kv._1+": "+kv._2.size).toList.sorted.mkString("\n")

            val immutableTypes =
                projectStore.entities(TypeImmutability.key).
                    filter(ep ⇒ !ep.e.asInstanceOf[ClassFile].isInterfaceDeclaration).
                    groupBy { _.p }.map { kv ⇒
                        (kv._1, kv._2.size)
                    }
            val immutableTypesPerCategory =
                immutableTypes.map(kv ⇒ "\t\t"+kv._1+": "+kv._2).toList.sorted.mkString("\n")

            val immutableClassesInfo =
                immutableClasses.values.flatten.filter { ep ⇒
                    !ep.e.asInstanceOf[ClassFile].isInterfaceDeclaration
                }.map { ep ⇒
                    ep.e.asInstanceOf[ClassFile].thisType.toJava+
                        " => "+ep.p+
                        " => "+projectStore(ep.e, TypeImmutability.key).p
                }.mkString("\tImmutability:\n\t\t", "\n\t\t", "\n")

            "Details:\n"+
                extensibleClasses.mkString("\tExtensible Classes:\n\t\t", "\n\t\t", "\n") +
                immutableClassesInfo+
                "\nSummary (w.r.t classes):\n"+
                "\tObject Immutability:\n"+
                immutableClassesPerCategory+"\n"+
                "\tType Immutability:\n"+
                immutableTypesPerCategory+"\n"+
                "\n"+projectStore.toString(false)
        }
    }
}
