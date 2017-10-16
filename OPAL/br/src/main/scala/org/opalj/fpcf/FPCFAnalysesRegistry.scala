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

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigObject

import org.opalj.log.GlobalLogContext
import org.opalj.log.OPALLogger

/**
 * Registry for all factories for analyses that are implemented using the fixpoint computations
 * framework ('''FPCF''').
 *
 * The registry primarily serves as a central container that can be queried
 * by subsequent tools.
 *
 * The analyses that are part of OPAL are already registered.
 *
 * @note The registry does not handle dependencies between analyses yet.
 *
 * ==Thread Safety==
 * The registry is thread safe.
 *
 * @author Michael Reif
 * @author Michael Eichberg
 */
object FPCFAnalysesRegistry {

    private[this] var idToFactory: Map[String, FPCFAnalysisRunner] = Map.empty
    private[this] var idToDescription: Map[String, String] = Map.empty

    /**
     * Registers the factory of a fixpoint analysis that can be
     * used to compute a specific (set of) property(ies).
     *
     * @param analysisDescription A short description of the analysis and the properties that the
     *                            analysis computes; in particular w.r.t. a specific set of entities.
     * @param analysisFactory     The factory.
     */
    def register(
        analysisID:          String,
        analysisDescription: String,
        analysisFactory:     String
    ): Unit = this.synchronized {
        val analysisRunner = resolveAnalysisRunner(analysisFactory)
        if (analysisRunner.nonEmpty) {
            idToFactory += ((analysisID, analysisRunner.get))
            idToDescription += ((analysisID, analysisDescription))
            OPALLogger.info(
                "OPAL Setup", s"registered analysis: $analysisID ($analysisDescription)"
            )(GlobalLogContext)
        } else {
            OPALLogger.error(
                "OPAL Setup", s"unknown analysis implementation: $analysisFactory"
            )(GlobalLogContext)
        }
    }

    private[this] def resolveAnalysisRunner(fqn: String): Option[FPCFAnalysisRunner] = {
        import scala.reflect.runtime.universe.runtimeMirror
        val mirror = runtimeMirror(getClass.getClassLoader)
        var result = Option.empty[FPCFAnalysisRunner]
        try {
            val module = mirror.staticModule(fqn)
            result = Some(mirror.reflectModule(module).instance.asInstanceOf[FPCFAnalysisRunner])
        } catch {
            case e: ScalaReflectionException ⇒
            case c: ClassCastException       ⇒
        }
        result
    }

    private[this] case class AnalysisFactory(id: String, description: String, factory: String)

    def registerFromConfig(): Unit = {
        val config = ConfigFactory.load()
        try {
            /* HANDLING FOR AN ARRAY OF ANALYSES... analyses = [ {},...,{}]
            //import com.typesafe.config.Config
            //import net.ceedubs.ficus.Ficus._
            //import net.ceedubs.ficus.readers.ArbitraryTypeReader._
            val key = "org.opalj.fpcf.registry.analyses"
            val registeredAnalyses = config.as[List[AnalysisFactory]](key)
            registeredAnalyses foreach { a ⇒
                register(a.id, a.description, a.factory)
            }
            */
            val registeredAnalyses = config.getObject("org.opalj.fpcf.registry.analyses")
            val entriesIterator = registeredAnalyses.entrySet.iterator
            while (entriesIterator.hasNext) {
                val entry = entriesIterator.next
                val id = entry.getKey
                val metaData = entry.getValue.asInstanceOf[ConfigObject]
                val description = metaData.getOrDefault("description", null).unwrapped.toString
                val factory = metaData.getOrDefault("factory", null).unwrapped.toString
                register(id, description, factory)
            }
        } catch {
            case e: Exception ⇒ OPALLogger.error(
                "OPAL Setup", "initialization of the FPCF Analysis Registry failed", e
            )(GlobalLogContext)
        }
    }

    /**
     * Returns the ids of the registered analyses.
     */
    def analysisIDs(): Iterable[String] = this.synchronized {
        idToFactory.keys
    }

    /**
     * Returns the descriptions of the registered analyses. These descriptions are
     * expected to be useful to the end-users.
     */
    def analysisDescriptions(): Iterable[String] = this.synchronized {
        idToDescription.values
    }

    /**
     * Returns the current view of the registry.
     */
    def factories: Iterable[FPCFAnalysisRunner] = this.synchronized {
        idToFactory.values
    }

    /**
     * Returns the factory for analysis with a matching description.
     */
    def factory(id: String): FPCFAnalysisRunner = this.synchronized {
        idToFactory(id)
    }

    //initialize the registry with the known default analyses
    registerFromConfig()

    def main(args: Array[String]): Unit = {
        println(idToDescription.mkString("FPCF Analysis Registry\n\t", "\n\t", "\n"))
    }
}
