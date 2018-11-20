/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package bi

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FlatSpec
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers

import org.opalj.av.checking.Specification

/**
 * Tests that the implemented architecture of the infrastructure project
 * is consistent with its specification/with the intended architecture.
 *
 * @author Michael Eichberg
 */
@RunWith(classOf[JUnitRunner])
class BIArchitectureConsistencyTest extends FlatSpec with Matchers with BeforeAndAfterAll {

    behavior of "the Infrastructure Project's implemented architecture"

    it should "be consistent with the specified architecture" in {
        val expected =
            new Specification(
                Specification.ProjectDirectory("OPAL/bi/target/scala-2.12/classes"),
                useAnsiColors = true
            ) {

                ensemble('Bi) {
                    "org.opalj.bi.*" except
                        classes("""org\.opalj\.bi\..+Test.*""".r)
                }

                ensemble('Reader) {
                    "org.opalj.bi.reader.*" except
                        classes("""org\.opalj\.bi\.reader\..+Test.*""".r)
                }

                'Bi is_only_allowed_to (USE, empty)

                // 'Reader is allowed to use everything

            }

        val result = expected.analyze()
        if (result.nonEmpty) {
            println("Violations:\n\t"+result.map(_.toString(useAnsiColors = true)).mkString("\n\t"))
            fail("The implemented and the specified architecture are not consistent (see the console for details).")
        }
    }
}
