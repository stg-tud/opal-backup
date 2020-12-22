/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses

import org.opalj.fpcf.EPK
import org.opalj.fpcf.InterimPartialResult
import org.opalj.fpcf.ProperPropertyComputationResult
import org.opalj.fpcf.Results
import org.opalj.fpcf.SomeEOptionP
import org.opalj.fpcf.UBP
import org.opalj.br.DeclaredMethod
import org.opalj.br.analyses.DeclaredMethodsKey
import org.opalj.br.DefinedMethod
import org.opalj.br.analyses.DeclaredMethods
import org.opalj.br.fpcf.FPCFAnalysis
import org.opalj.br.fpcf.properties.cg.Callers

/**
 * A trait for analyses that model the result of the invocation of a specific
 * `apiMethod`.
 *
 * Each time a new caller of the [[apiMethod*]] is found in the
 * [[Callers]] property, [[handleNewCaller*]]
 * gets called.
 *
 * @note When `handleNewCaller` gets invoked, there is no guarantee that the caller's three-address
 *       code ([[org.opalj.tac.fpcf.properties.TACAI]]) is present in the property store, nor that
 *       it is final. If this is required, use the [[TACAIBasedAPIBasedAnalysis]]
 *       sub-trait.
 *
 * @author Florian Kuebler
 */
trait APIBasedAnalysis extends FPCFAnalysis {
    val apiMethod: DeclaredMethod

    implicit val declaredMethods: DeclaredMethods = p.get(DeclaredMethodsKey)

    def handleNewCaller(
        caller: DefinedMethod, pc: Int, isDirect: Boolean
    ): ProperPropertyComputationResult

    final def registerAPIMethod(): ProperPropertyComputationResult = {
        val seenCallers = Set.empty[(DeclaredMethod, Int, Boolean)]
        val callersEOptP = ps(apiMethod, Callers.key)
        c(seenCallers)(callersEOptP)
    }

    private[this] def c(
        seenCallers: Set[(DeclaredMethod, Int, Boolean)]
    )(callersEOptP: SomeEOptionP): ProperPropertyComputationResult =
        (callersEOptP: @unchecked) match {
            case UBP(callersUB: Callers) ⇒
                // IMPROVE: use better design in order to get new callers
                var newSeenCallers = seenCallers
                var results: List[ProperPropertyComputationResult] = Nil
                if (callersUB.nonEmpty) {
                    for ((caller, pc, isDirect) ← callersUB.callers) {
                        // the call graph is only computed for virtual and single defined methods
                        assert(caller.isVirtualOrHasSingleDefinedMethod)

                        // we can not analyze virtual methods, as we do not have their bytecode
                        if (!newSeenCallers.contains((caller, pc, isDirect)) &&
                            caller.hasSingleDefinedMethod) {
                            newSeenCallers += ((caller, pc, isDirect))

                            results ::= handleNewCaller(caller.asDefinedMethod, pc, isDirect)
                        }
                    }
                }

                if (callersEOptP.isRefinable)
                    results ::= InterimPartialResult(Set(callersEOptP), c(newSeenCallers))

                Results(results)

            case _: EPK[_, _] ⇒ InterimPartialResult(Set(callersEOptP), c(seenCallers))
        }
}
