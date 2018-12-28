/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.fpcf

import java.net.URL

import org.opalj.fpcf.analyses.cg.LazyCalleesAnalysis
import org.opalj.fpcf.analyses.cg.TriggeredInstantiatedTypesAnalysis
import org.opalj.fpcf.analyses.cg.TriggeredLoadedClassesAnalysis
import org.opalj.fpcf.analyses.cg.RTACallGraphAnalysisScheduler
import org.opalj.fpcf.analyses.cg.TriggeredStaticInitializerAnalysis
import org.opalj.fpcf.analyses.escape.EagerInterProceduralEscapeAnalysis
import org.opalj.fpcf.analyses.escape.EagerSimpleEscapeAnalysis
import org.opalj.fpcf.cg.properties.StandardInvokeCallees
import org.opalj.br.analyses.Project
import org.opalj.ai.domain.l2.DefaultPerformInvocationsDomainWithCFGAndDefUse
import org.opalj.ai.fpcf.analyses.LazyL0BaseAIAnalysis
import org.opalj.ai.fpcf.properties.AIDomainFactoryKey
import org.opalj.tac.fpcf.analyses.TACAITransformer

/**
 * Tests if the escape properties specified in the test project (the classes in the (sub-)package of
 * org.opalj.fpcf.fixture) and the computed ones match. The actual matching is delegated to
 * PropertyMatchers to facilitate matching arbitrary complex property specifications.
 *
 * @author Florian Kuebler
 */
class EscapeAnalysisTests extends PropertiesTest {

    val analyses: List[FPCFAnalysisScheduler] = List(
        RTACallGraphAnalysisScheduler,
        TriggeredStaticInitializerAnalysis,
        TriggeredInstantiatedTypesAnalysis,
        TriggeredLoadedClassesAnalysis,
        LazyL0BaseAIAnalysis,
        TACAITransformer,
        LazyCalleesAnalysis(Set(StandardInvokeCallees))
    )

    override def init(p: Project[URL]): Unit = {
        val performInvocationsDomain = classOf[DefaultPerformInvocationsDomainWithCFGAndDefUse[_]]

        p.updateProjectInformationKeyInitializationData(
            AIDomainFactoryKey,
            (i: Option[Set[Class[_ <: AnyRef]]]) ⇒ (i match {
                case None               ⇒ Set(performInvocationsDomain)
                case Some(requirements) ⇒ requirements + performInvocationsDomain
            }): Set[Class[_ <: AnyRef]]
        )
    }

    describe("no analysis is scheduled") {
        val as = executeAnalyses(Set.empty)
        as.propertyStore.shutdown()
        validateProperties(
            as,
            allocationSitesWithAnnotations(as.project) ++
                explicitFormalParametersWithAnnotations(as.project),
            Set("EscapeProperty")
        )
    }

    describe("the org.opalj.fpcf.analyses.escape.SimpleEscapeAnalysis is executed") {
        val as = executeAnalyses(EagerSimpleEscapeAnalysis :: analyses)
        as.propertyStore.shutdown()
        validateProperties(
            as,
            allocationSitesWithAnnotations(as.project) ++
                explicitFormalParametersWithAnnotations(as.project),
            Set("EscapeProperty")
        )
    }

    describe("the org.opalj.fpcf.analyses.escape.InterProceduralEscapeAnalysis is executed") {
        val as = executeAnalyses(EagerInterProceduralEscapeAnalysis :: analyses)
        as.propertyStore.shutdown()
        validateProperties(
            as,
            allocationSitesWithAnnotations(as.project) ++
                explicitFormalParametersWithAnnotations(as.project),
            Set("EscapeProperty")
        )
    }

}
