/* License (BSD Style License):
 *  Copyright (c) 2009, 2011
 *  Software Technology Group
 *  Department of Computer Science
 *  Technische Universität Darmstadt
 *  All rights reserved.
 *
 *  Redistribution and use in source and binary forms, with or without
 *  modification, are permitted provided that the following conditions are met:
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
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 *  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  POSSIBILITY OF SUCH DAMAGE.
 */
package de.tud.cs.st.bat.resolved
package dependency
package checking

import reader.Java6Framework
import analyses.ClassHierarchy
import scala.collection.immutable.SortedSet

/**
 * Represents a configuration of a project's allowed/expected dependencies.
 * First define the ensembles, then the rules and at last specify the
 * class files that should be analyzed. The rules will then be automatically
 * evaluated.
 *
 * ==Typical Usage==
 * One ensemble is predefined: [[Specification.empty]] it represents an ensemble that contains no
 * source elements and which can, e.g., be used to specify that no "real" ensemble is allowed
 * to depend on a specific ensemble.
 *
 * @author Michael Eichberg
 */
class Specification extends SourceElementIDsMap with ReverseMapping with UseIDOfBaseTypeForArrayTypes with Project {

    private[this] var theClassHierarchy = new ClassHierarchy
    override def classHierarchy = theClassHierarchy

    private[this] var allClassFiles = Map[ObjectType, ClassFile]()
    override def classFiles = allClassFiles

    private[this] var matchedSourceElements = SortedSet[SourceElementID]()

    val ensembles = scala.collection.mutable.Map[Symbol, (SourceElementsMatcher, SortedSet[SourceElementID])](
    		'empty -> (NoSourceElementsMatcher,SortedSet())
    )

    val outgoingDependencies = scala.collection.mutable.Map[SourceElementID, scala.collection.mutable.Set[(SourceElementID, DependencyType)]]()

    val incomingDependencies = scala.collection.mutable.Map[SourceElementID, scala.collection.mutable.Set[(SourceElementID, DependencyType)]]()

    val dependencyExtractor = new DependencyExtractor(Specification.this) with NoSourceElementsVisitor {

        def processDependency(sourceID: SourceElementID, targetID: SourceElementID, dType: DependencyType) {
            outgoingDependencies.
                getOrElseUpdate(sourceID, { scala.collection.mutable.Set() }).
                add((targetID, dType))
            incomingDependencies.
                getOrElseUpdate(targetID, { scala.collection.mutable.Set() }).
                add((sourceID, dType))
        }
    }

    val empty = 'empty

    def ensemble(ensembleName: Symbol)(sourceElementMatcher: SourceElementsMatcher) {
        if (ensembles.contains(ensembleName))
            throw new IllegalArgumentException("Ensemble is already defined: "+ensembleName)

        ensembles.put(ensembleName, (sourceElementMatcher, SortedSet()))
    }

    @throws(classOf[SpecificationError])
    implicit def StringToSourceElementMatcher(matcher: String): SourceElementsMatcher = {
        if (matcher endsWith ".*")
            return new PackageNameBasedMatcher(matcher.substring(0, matcher.length() - 2).replace('.', '/'))
        if (matcher endsWith ".**")
            return new PackageNameBasedMatcher(matcher.substring(0, matcher.length() - 3).replace('.', '/'), true)
        if (matcher endsWith "*")
            return new ClassMatcher(matcher.substring(0, matcher.length() - 1).replace('.', '/'), true)
        if (matcher.indexOf('*') == -1)
            return new ClassMatcher(matcher.replace('.', '/'))

        throw new SpecificationError("unsupported pattern: "+matcher);
    }

    implicit def FileToClassFileProvider(file: java.io.File): Seq[ClassFile] = Java6Framework.ClassFiles(file)

    case class Violation(source: SourceElementID, target: SourceElementID, dependencyType: DependencyType, description: String) {

        override def toString(): String = {
            description+": "+
                sourceElementIDtoString(source)+" "+dependencyType+" "+sourceElementIDtoString(target)
        }

    }

    trait DependencyChecker {
        def violations(): Set[Violation]
    }

    var dependencyCheckers: List[DependencyChecker] = Nil

    case class GlobalIncomingConstraint(targetEnsemble: Symbol, sourceEnsembles: Seq[Symbol]) extends DependencyChecker {
        def violations() = {
            val sourceEnsembleElements = (SortedSet[SourceElementID]() /: sourceEnsembles)(_ ++ ensembles(_)._2)
            val (_, targetEnsembleElements) = ensembles(targetEnsemble)
            for (
                targetEnsembleElement ← targetEnsembleElements if incomingDependencies.contains(targetEnsembleElement);
                (incomingElement, dependencyType) ← incomingDependencies(targetEnsembleElement) if !(sourceEnsembleElements.contains(incomingElement) || targetEnsembleElements.contains(incomingElement))
            ) yield Violation(incomingElement, targetEnsembleElement, dependencyType, "violation of a global incoming constraint ")
        }

        override def toString =
            targetEnsemble+" is_only_to_be_used_by ("+sourceEnsembles.mkString(",")+")"
    }

    case class LocalOutgoingConstraint

    case class SpecificationFactory(ensembleSymbol: Symbol) {

        def apply(sourceElementsMatcher: SourceElementsMatcher) {
            ensemble(ensembleSymbol)(sourceElementsMatcher)
        }

        def is_only_to_be_used_by(sourceEnsembleSymbols: Symbol*) {
            dependencyCheckers = GlobalIncomingConstraint(ensembleSymbol, sourceEnsembleSymbols.toSeq) :: dependencyCheckers
        }

        def allows_incoming_dependencies_from(sourceEnsembleSymbols: Symbol*) {
            dependencyCheckers = GlobalIncomingConstraint(ensembleSymbol, sourceEnsembleSymbols.toSeq) :: dependencyCheckers
        }
    }

    implicit def EnsembleNameToSpecificationElementFactory(ensembleSymbol: Symbol): SpecificationFactory =
        SpecificationFactory(ensembleSymbol)

    /**
     * Returns a textual representation (as defined in a specification) of an ensemble.
     */
    def ensembleToString(ensembleSymbol: Symbol): String = {
        var (sourceElementsMatcher, extension) = ensembles(ensembleSymbol)
        ensembleSymbol+"{"+
            sourceElementsMatcher+"  "+
            {
                if (extension.isEmpty)
                    "/* NO ELEMENTS */ "
                else {
                    val ex = extension.toList
                    (("\n\t//"+extension.head.toString+":"+sourceElementIDtoString(extension.head)+"\n") /: extension.tail)((s, id) ⇒ s+"\t//"+id+":"+sourceElementIDtoString(id)+"\n")
                }
            }+"}"
    }

    def analyze(classFileProviders: Traversable[Traversable[ClassFile]]) {

        val performance = new de.tud.cs.st.util.perf.PerformanceEvaluation {}
        import performance._

        // 1. create and update the support data structures
        print("1. Reading class files and extracting dependencies took ")
        time(t ⇒ println(nsToSecs(t).toString+" seconds.")) {
            for (
                classFileProvider ← classFileProviders;
                classFile ← classFileProvider
            ) {
                theClassHierarchy = theClassHierarchy + classFile
                allClassFiles = allClassFiles.updated(classFile.thisClass, classFile)
                dependencyExtractor.process(classFile)
            }
        }

        // 2. calculate the extension of the ensembles
        print("2. Determing the extension of the ensembles took ")
        time(t ⇒ println(nsToSecs(t).toString+" seconds.")) {
            val instantiatedEnsembles = ensembles.par.map((ensemble) ⇒ {
                val (ensembleName, (sourceElementMatcher, _)) = ensemble
                val extension = sourceElementMatcher.extension(this)
                Specification.this.synchronized {
                    matchedSourceElements = matchedSourceElements ++ extension
                }
                (ensembleName, (sourceElementMatcher, extension))
            })
            ensembles.clear
            ensembles.++=(instantiatedEnsembles.toIterator)
            // Non-parallel implementation
            // for ((ensembleName, (sourceElementMatcher, _)) ← ensembles) {
            // 	val extension = sourceElementMatcher.extension(this)
            // 	ensembles.update(ensembleName, (sourceElementMatcher, extension))
            // }
        }

        // 2.1. check that all ensembles contain at least one source element
        for ((ensembleSymbol,(matcher,extension)) <- ensembles if extension.isEmpty && matcher != NoSourceElementsMatcher ) {
            println("Warning: "+ensembleSymbol+" did not match any source elements: "+matcher.toString)
        }

        // 3. check all rules
        println("3. Checking the specified dependency constraints:")
        time(t ⇒ println("   Checking all constraints took "+nsToSecs(t).toString+" seconds.")) {
            for (dependencyChecker ← dependencyCheckers.par) {
                println("   Checking: "+dependencyChecker)
                for (violation ← dependencyChecker.violations) println(violation)
            }
        }
    }

    @throws(classOf[SpecificationError])
    def Directory(directoryName: String): java.io.File = {
        val file = new java.io.File(directoryName)
        if (!file.exists)
            throw new SpecificationError("The specified directory does not exist: "+directoryName+".")
        if (!file.canRead)
            throw new SpecificationError("Cannot read the specified directory: "+directoryName+".")
        if (!file.isDirectory)
            throw new SpecificationError("The specified directory is not a directory: "+directoryName+".")
        file
    }

}

case class SpecificationError(val description: String) extends Exception(description)

