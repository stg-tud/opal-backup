/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.collection.immutable

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FunSpec
import org.scalatest.Matchers

import org.opalj.util.PerformanceEvaluation

/**
 * Tests Long2List.
 *
 * @author Michael Eichberg
 */
@RunWith(classOf[JUnitRunner])
class Long2ListTest extends FunSpec with Matchers {

    describe("properties") {

        it("forFirstN(N=0)") {
            var sum = 0L
            Long2List(1L).forFirstN(0)(sum += _)
            assert(sum == 0L)
        }

        it("forFirstN(N=1)") {
            var sum = 0L
            Long2List(1L).forFirstN(1)(sum += _)
            assert(sum == 1L)
        }

        it("forFirstN(N=2)") {
            var sum = 0L
            Long2List(1L, 2L).forFirstN(2)(sum += _)
            assert(sum == 3L)
        }

        it("forFirstN(N=3)") {
            var sum = 0L
            (4L +: Long2List(1L, 2L)).forFirstN(3)(sum += _)
            assert(sum == 7L)
        }

        it("forFirstN(N=4)") {
            var sum = 0L
            (5L +: 4L +: Long2List(1L, 2L)).forFirstN(4)(sum += _)
            assert(sum == 12L)
        }

        it("forFirstN(N=4) of a larger set") {
            var sum = 0L
            (10L +: 10L +: 5L +: 4L +: Long2List(1L, 2L)).forFirstN(4)(sum += _)
            assert(sum == 29L)
        }

    }

    describe("performance") {

        it("memory usage and runtime performance") {
            val Elements = 3000000
            val seed = 123456789L
            val rngGen = new java.util.Random(seed)

            for { i ← 1 to 10 } {
                var l = Long2List.empty

                PerformanceEvaluation.memory {
                    PerformanceEvaluation.time {
                        var i = Elements
                        do {
                            l = Math.abs(rngGen.nextLong()) +: l
                            i -= 1
                        } while (i > 0)
                    } { t ⇒ info(s"creation took ${t.toSeconds}") }
                } { mu ⇒ info(s"required $mu bytes") }

                var sumForeach = 0L
                PerformanceEvaluation.time {
                    l.foreach(sumForeach += _)
                } { t ⇒ info(s"foreach sum took ${t.toSeconds}") }

                var sumForFirstThird = 0L
                PerformanceEvaluation.time {
                    l.forFirstN(Elements / 3)(sumForFirstThird += _)
                } { t ⇒ info(s"forFirstN(1/3*Elements) sum took ${t.toSeconds}") }

                val sumIterator =
                    PerformanceEvaluation.time {
                        l.iterator.sum
                    } { t ⇒ info(s"iterator sum took ${t.toSeconds}") }

                assert(sumForeach == sumIterator)
                info(s"summarized value: ${sumIterator}")
            }

        }
    }
}
