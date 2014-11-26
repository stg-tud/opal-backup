/* BSD 2-Clause License:
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
package ai
package domain
package l1

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSpec
import org.scalatest.Matchers
import org.scalatest.concurrent.TimeLimitedTests
import org.scalatest.time._
import org.scalatest.BeforeAndAfterAll
import org.scalatest.ParallelTestExecution
import org.opalj.bi.TestSupport.locateTestResources
import org.opalj.util.{ Answer, Yes, No, Unknown }
import org.opalj.br.ObjectType
import org.opalj.br.analyses.Project
import org.opalj.br.reader.Java8Framework.ClassFiles
import org.opalj.collection.mutable.Locals
import org.opalj.collection.immutable.UIDSet

/**
 * @author Michael Eichberg
 */
@RunWith(classOf[JUnitRunner])
class DefaultReferenceValuesTest extends FunSpec with Matchers with ParallelTestExecution {

    object TheDomain
        extends CorrelationalDomain
        with DefaultDomainValueBinding
        with ThrowAllPotentialExceptionsConfiguration
        with PredefinedClassHierarchy
        with PerInstructionPostProcessing
        with DefaultHandlingOfMethodResults
        with IgnoreSynchronization
        with l0.DefaultTypeLevelFloatValues
        with l0.DefaultTypeLevelDoubleValues
        with l0.DefaultTypeLevelLongValues
        with l0.TypeLevelFieldAccessInstructions
        with l0.SimpleTypeLevelInvokeInstructions
        with l1.DefaultReferenceValuesBinding // <- PRIMARY GOAL!
        with l0.DefaultTypeLevelIntegerValues
        with l0.DefaultPrimitiveValuesConversions

    import TheDomain._

    //
    // TESTS
    //

    describe("the DefaultReferenceValues domain") {

        //
        // FACTORY METHODS
        //

        describe("using the factory methods") {

            it("it should be possible to create a representation for a non-null object "+
                "with a specific type") {
                val ref = ReferenceValue(444, No, true, ObjectType.Object)
                if (!ref.isNull.isNo || ref.origin != 444 || !ref.isPrecise)
                    fail("expected a precise, non-null reference value with pc 444;"+
                        " actual: "+ref)
            }

        }

        //
        // SIMPLE REFINEMENT
        //

        describe("refining a DomainValue that represents a reference value") {

            it("it should be able to correctly update the upper bound of the corresponding value") {

                val File = ObjectType("java/io/File")
                val Serializable = ObjectType.Serializable

                val theObjectValue = ObjectValue(-1, No, false, ObjectType.Object)
                val theFileValue = ObjectValue(-1, No, false, File)

                {
                    val (updatedOperands, updatedLocals) =
                        theObjectValue.refineUpperTypeBound(
                            -1, Serializable,
                            List(theObjectValue),
                            Locals(IndexedSeq(theFileValue, theObjectValue)))
                    updatedOperands.head.asInstanceOf[ReferenceValue].upperTypeBound.first should be(Serializable)
                    updatedLocals(0).asInstanceOf[ReferenceValue].upperTypeBound.first should be(File)
                    updatedLocals(1).asInstanceOf[ReferenceValue].upperTypeBound.first should be(Serializable)
                }

                {
                    // there is nothing to refine...
                    val (updatedOperands, _) =
                        theFileValue.refineUpperTypeBound(-1, Serializable, List(theObjectValue), Locals.empty)
                    updatedOperands.head should be(theObjectValue)
                }
            }
        }

        //
        // SUMMARIES
        //

        describe("the summarize function") {

            it("it should calculate a meaningful upper type bound given "+
                "multiple different types of reference values") {
                summarize(
                    -1,
                    List(
                        ObjectValue(444, No, true, ObjectType.Object),
                        NullValue(444),
                        ObjectValue(668, No, true, ObjectType("java/io/File"))
                    )
                ) should be(ObjectValue(-1, Unknown, false, ObjectType.Object))
            }
        }

        //
        // JOIN
        //

        describe("joining two DomainValues that represent reference values") {

            val ref1 = ObjectValue(444, No, true, ObjectType.Object)

            val ref1Alt = ObjectValue(444, No, true, ObjectType.Object)

            val ref2 = ObjectValue(668, No, true, ObjectType.String)

            val ref2Alt = ObjectValue(668, No, true, ObjectType.String)

            val ref3 = ObjectValue(732, No, true, ObjectType.String)

            val ref1MergeRef2 = ref1.join(-1, ref2).value

            val ref1AltMergeRef2Alt = ref1Alt.join(-1, ref2Alt).value

            val ref1MergeRef2MergeRef3 = ref1MergeRef2.join(-1, ref3).value

            val ref3MergeRef1MergeRef2 = ref3.join(-1, ref1MergeRef2).value

            it("it should keep the old value when we merge a value with an identical value") {
                ref1.join(-1, ref1Alt) should be(MetaInformationUpdate(ref1))
            }

            it("it should represent both values after a merge of two independent values") {
                val IsReferenceValue(values) = typeOfValue(ref1MergeRef2)
                values.exists(_ == ref1) should be(true)
                values.exists(_ == ref2) should be(true)
            }

            it("it should represent all three values when we merge a MultipleReferenceValue with an ObjectValue if all three values are independent") {
                val IsReferenceValue(values) = typeOfValue(ref1MergeRef2MergeRef3)
                values.exists(_ == ref1) should be(true)
                values.exists(_ == ref2) should be(true)
                values.exists(_ == ref3) should be(true)
            }

            it("it should be able to merge two value sets that contain (reference) identical values") {
                val IsReferenceValue(values312) = typeOfValue(ref3MergeRef1MergeRef2)
                val IsReferenceValue(values123) = typeOfValue(ref1MergeRef2MergeRef3)
                values312.toSet should be(values123.toSet)
            }

            it("it should be able to merge two value sets where the original set is a superset of the second set") {
                // the values represent different values in time...
                val update = ref1MergeRef2MergeRef3.join(-1, ref1AltMergeRef2Alt)
                if (!update.isMetaInformationUpdate)
                    fail("expected: MetaInformationUpdate; actual: "+update)
            }

            it("it should be able to merge two value sets where the original set is a subset of the second set") {
                ref1AltMergeRef2Alt.join(-1, ref1MergeRef2MergeRef3) should be(StructuralUpdate(ref1MergeRef2MergeRef3))
            }
        }

        //
        // USAGE
        //

        describe("using the DefaultReferenceValues domain") {

            it("it should be able to handle the case where we throw a \"null\" value or some other value") {

                val classFiles = ClassFiles(locateTestResources("classfiles/cornercases.jar", "ai"))
                val classFile = classFiles.find(_._1.thisType.fqn == "cornercases/ThrowsNullValue").get._1
                val method = classFile.methods.find(_.name == "main").get

                val result = BaseAI(classFile, method, TheDomain)
                val exception = result.operandsArray(20)
                TheDomain.refIsNull(-1, exception.head) should be(No)
            }

            val theProject = Project(locateTestResources("classfiles/ai.jar", "ai"))
            val targetType = ObjectType("ai/domain/ReferenceValuesFrenzy")
            val ReferenceValuesFrenzy = theProject.classFile(targetType).get

            it("it should be able to handle basic aliasing (method: \"aliases\"") {
                val method = ReferenceValuesFrenzy.methods.find(_.name == "aliases").get
                val result = BaseAI(ReferenceValuesFrenzy, method, TheDomain)
                import result.operandsArray
                import result.domain.IsNull

                val IsNull(v95) = asReferenceValue(operandsArray(14).head)
                v95 should be(Unknown)

                val IsNull(v99) = result.operandsArray(20).head
                v99 should be(No)

                val IsNull(v111) = result.operandsArray(57).head
                v111 should be(No)

                val IsNull(v113) = result.operandsArray(59).head
                v113 should be(No)

                val IsNull(v117) = result.operandsArray(61).head
                v117 should be(Yes)
            }

            it("it should be possible to get precise information about a method's return values (method: \"maybeNull\")") {

                val method = ReferenceValuesFrenzy.methods.find(_.name == "maybeNull").get
                val result = BaseAI(ReferenceValuesFrenzy, method, TheDomain)

                val IsNull(firstReturn) = result.operandsArray(15).head
                firstReturn should be(Yes)

                val IsNull(secondReturn) = result.operandsArray(23).head
                secondReturn should be(No)
            }

            it("it should be able to correctly track a MultipleReferenceValue's values in the presence of aliasing (method: \"complexAliasing\")") {
                val method = ReferenceValuesFrenzy.methods.find(_.name == "complexAliasing").get
                val result = BaseAI(ReferenceValuesFrenzy, method, TheDomain)

                val IsNull(doItCallParameter) = result.operandsArray(23).head
                doItCallParameter should be(Unknown)

                val IsNull(secondReturn) = result.operandsArray(27).head
                secondReturn should be(Unknown)

                val IsReferenceValue(values) = result.operandsArray(27).head
                values.head.isNull should be(Unknown)
                values.tail.head.isNull should be(Unknown)

            }

            it("it should be able to correctly determine the value's properties in the presence of aliasing (method: \"iterativelyUpdated\")") {
                val method = ReferenceValuesFrenzy.methods.find(_.name == "iterativelyUpdated").get
                val result = BaseAI(ReferenceValuesFrenzy, method, TheDomain)

                val value @ IsReferenceValue(values) = result.operandsArray(5).head
                value.isNull should be(No)
                values.head.isNull should be(Unknown)
                values.tail.head.isNull should be(Unknown)

                val returnValue @ IsReferenceValue(returnValues) = result.operandsArray(25).head
                returnValue.isNull should be(Unknown)
                returnValues.head.isNull should be(Unknown)
                returnValues.tail.head.isNull should be(Unknown)
            }

            it("it should be able to handle conditional aliasing (method: \"cfDependentValues\")") {
                val method = ReferenceValuesFrenzy.methods.find(_.name == "cfDependentValues").get
                val result = BaseAI(ReferenceValuesFrenzy, method, TheDomain)

                val IsReferenceValue(values1) = result.operandsArray(43).head
                values1.head.isNull should be(Yes) // original value
                values1.tail.head.isNull should be(Unknown)

                val IsReferenceValue(values2) = result.operandsArray(47).head
                values2.head.isNull should be(Yes) // original value
                values2.tail.head.isNull should be(Unknown)

                asReferenceValue(result.operandsArray(58).head).isNull should be(No)

                asReferenceValue(result.operandsArray(62).head).isNull should be(No)
            }

            it("it should be able to correctly refine a MultipleReferenceValues") {
                val method = ReferenceValuesFrenzy.methods.find(_.name == "multipleReferenceValues").get
                val result = BaseAI(ReferenceValuesFrenzy, method, TheDomain)
                import result.domain.asReferenceValue

                // u != null test
                asReferenceValue(result.operandsArray(26).head).isNull should be(Unknown)

                // first "doIt" call
                asReferenceValue(result.operandsArray(30).head).isNull should be(Unknown)

                // last "doIt" call
                asReferenceValue(result.operandsArray(47).head).isNull should be(No)

                // the "last return" is not dead
                result.operandsArray(45) should not be (null)
            }

            it("it should be able to correctly refine related MultipleReferenceValues (method: relatedMultipleReferenceValues)") {
                val methods = ReferenceValuesFrenzy.methods
                val method = methods.find(_.name == "relatedMultipleReferenceValues").get
                val result = BaseAI(ReferenceValuesFrenzy, method, TheDomain)

                val IsReferenceValue(values1) = result.operandsArray(77).head
                values1.size should be(3)
                values1.head.isNull should be(Unknown) // b is o
                values1.drop(1).head.isNull should be(Unknown) // b is p
                values1.drop(2).head.isNull should be(Yes) // the original value

                val IsReferenceValue(values2) = result.operandsArray(87).head
                values2.size should be(3)
                values2.head.isNull should be(Unknown) // a is o
                values2.drop(1).head.isNull should be(Unknown) // a is p
                values2.drop(2).head.isNull should be(Unknown) // a is p

                val IsReferenceValue(values3) = result.operandsArray(95).head
                values3.size should be <= (3)
                values3.foreach(_.isNull should be(Unknown))

                val IsReferenceValue(values4) = result.operandsArray(104).head
                values4.size should be(3)
                values4.head.isNull should be(No) // a is o
                values4.drop(1).head.isNull should be(Unknown)
                values4.drop(2).head.isNull should be(Unknown)

                val IsReferenceValue(values5) = result.operandsArray(109).head
                values5.size should be(3)
                values5.head.isNull should be(No) // b is o
                values5.drop(1).head.isNull should be(Unknown)
                values5.drop(2).head.isNull should be(Yes) // the original value
            }

            it("it should be able to correctly refine the nullness property of MultipleReferenceValues (method: refiningNullnessOfMultipleReferenceValues)") {

                val methods = ReferenceValuesFrenzy.methods
                val method = methods.find(_.name == "refiningNullnessOfMultipleReferenceValues").get
                val result = BaseAI(ReferenceValuesFrenzy, method, TheDomain)

                val value35 @ IsReferenceValue(values35) = result.operandsArray(35).head
                value35.isNull should be(Yes)
                values35.size should be(2)
                values35.head.isNull should be(Unknown)
                values35.tail.head.isNull should be(Unknown)

                val value51 @ IsReferenceValue(values51) = result.operandsArray(51).head
                value51.isNull should be(Yes)
                values51.size should be(1)
                values51.head.isNull should be(Yes)
            }

            it("it should be able to correctly refine the upper type bound of MultipleReferenceValues (method: refiningTypeBoundOfMultipleReferenceValues)") {
                val methods = ReferenceValuesFrenzy.methods
                val method = methods.find(_.name == "refiningTypeBoundOfMultipleReferenceValues").get
                val result = BaseAI(ReferenceValuesFrenzy, method, TheDomain)

                assert(
                    TheDomain.isSubtypeOf(
                        ObjectType("java/lang/Exception"),
                        ObjectType("java/lang/Throwable")).isYes)

                val value78 @ IsReferenceValue(values78) = result.operandsArray(78).head
                values78.size should be(1)
                value78.upperTypeBound should be(UIDSet(ObjectType.Exception))
            }
        }
    }
}
