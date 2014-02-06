/* License (BSD Style License):
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
package project

import org.scalatest.FlatSpec
import org.scalatest.Matchers

import reader.Java7Framework

/**
 * Tests if we the same same call graph is created if we run the call graph
 * algorithm multiple times. Given that the call graph algorithm is heavily
 * parallelized, this "somehow" tests if the algorithm is thread safe.
 *
 * @author Michael Eichberg
 */
class CallGraphThreadSafetyTest extends FlatSpec with Matchers {

    behavior of "the BATAICALLGRAPH"

    def testFileName = "classfiles/callgraph.jar"
    def testFilePath = "ext/ai"
    def testCallGraphAlgorithm = new CHACallGraphAlgorithmConfiguration()

    //
    // PROJECT SETUP
    //
    def file = TestSupport.locateTestResources(testFileName, testFilePath)
    val classFiles = Java7Framework.ClassFiles(file)
    val project = bat.resolved.analyses.IndexBasedProject(classFiles)

    //
    // GRAPH CONSTRUCTION
    //
    val callGraphs =
        (0 to 150).par map { i ⇒
            CallGraphFactory.create(
                project,
                CallGraphFactory.defaultEntryPointsForLibraries(project),
                new CHACallGraphAlgorithmConfiguration())
        }

    //
    // TESTS
    //

    import scala.language.existentials

    // Validate every method against the callgraph defined by annotations
    it should "always calculate the same call graph" in {
        println(callGraphs.head._1.callsStatistics(50))
        println(callGraphs.head._1.calledByStatistics(50))
        
        callGraphs.seq.reduce[(CallGraph[_], List[UnresolvedMethodCall], List[CallGraphConstructionException])] { (l, r) ⇒
            val (lcallGraph, lunresolvedMethodCalls, lexceptions) = l
            val (rcallGraph, runresolvedMethodCalls, rexceptions) = r
            println("comparing: "+System.identityHashCode(lcallGraph)+"  "+System.identityHashCode(rcallGraph))

            lcallGraph.calledByCount should be(rcallGraph.calledByCount)
            lcallGraph.callsCount should be(rcallGraph.callsCount)

            var lcalledBySet: Set[(Method, scala.collection.Map[Method, PCs])] = Set.empty
            lcallGraph.foreachCalledByMethod((method, callers) ⇒ lcalledBySet += ((method, callers)))
            var rcalledBySet: Set[(Method, scala.collection.Map[Method, PCs])] = Set.empty
            rcallGraph.foreachCalledByMethod((method, callers) ⇒ rcalledBySet += ((method, callers)))
            lcalledBySet should equal(rcalledBySet)

            var lcallsSet: Set[(Method, scala.collection.Map[PC, Iterable[Method]])] = Set.empty
            lcallGraph.foreachCallingMethod((method, callees) ⇒ lcallsSet += ((method, callees)))
            var rcallsSet: Set[(Method, scala.collection.Map[PC, Iterable[Method]])] = Set.empty
            rcallGraph.foreachCallingMethod((method, callees) ⇒ rcallsSet += ((method, callees)))
            
            lunresolvedMethodCalls.toSet should equal(runresolvedMethodCalls.toSet)
            lexceptions.toSet should equal(rexceptions.toSet)

            l
        }

    }

}