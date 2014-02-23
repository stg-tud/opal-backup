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
package analyses

import instructions._
import java.net.URL

/**
 * Finds classes that define only a co-variant `equals` method (an equals method
 * where the parameter type is a subtype of `java.lang.Object`), but which do not
 * also define a "standard" `equals` method.
 *
 * ==Implementation Note==
 * This analysis is implemented using a traditional approach where each analysis
 * analyzes the project's resources on its own and fully controls the process.
 *
 * @author Michael Eichberg
 */
class CovariantEqualsMethodDefined[Source]
        extends MultipleResultsAnalysis[Source, ClassBasedReport[Source]] {

    //
    // Meta-data
    //

    def description: String = "Finds classes that define a co-variant equals method."

    // 
    // Implementation
    //

    def analyze(
        project: Project[Source],
        parameters: Seq[String] = List.empty): Iterable[ClassBasedReport[Source]] = {

        val mutex = new Object
        var reports = List[ClassBasedReport[Source]]()

        for (classFile ← project.classFiles.par) yield {

            var definesEqualsMethod = false
            var definesCovariantEqualsMethod = false
            for (Method(_, "equals", MethodDescriptor(Seq(ot), BooleanType)) ← classFile.methods)
                if (ot == ObjectType.Object)
                    definesEqualsMethod = true
                else
                    definesCovariantEqualsMethod = true

            if (definesCovariantEqualsMethod && !definesEqualsMethod) {
                mutex.synchronized {
                    reports = ClassBasedReport(
                        project.source(classFile.thisType),
                        Severity.Warning,
                        classFile.thisType,
                        "Defines a covariant equals method, but does not also define the standard equals method.") :: reports
                }
            }
        }
        reports
    }
}

/**
 * Enables the stand alone execution of this analysis.
 */
object CovariantEqualsMethodDefinedAnalysis extends AnalysisExecutor {
    val analysis = urlBasedAnalysisToAnalysisWithReportableResults(
        new CovariantEqualsMethodDefined[URL]
    )
}
