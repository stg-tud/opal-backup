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
package tac

import org.scalatest.Matchers
import org.scalatest.FunSpec
import org.scalatest.junit.JUnitRunner
import org.scalatest.Matchers
import org.junit.runner.RunWith

import org.opalj.br._
import org.opalj.br.TestSupport.biProject

/**
 * @author Michael Eichberg
 * @author Roberts Kolosovs
 */
@RunWith(classOf[JUnitRunner])
class TACNaiveLongArithmeticTest extends FunSpec with Matchers {

    val ArithmeticExpressionsType = ObjectType("tactest/ArithmeticExpressions")

    val project = biProject("tactest-8-preserveAllLocals.jar")

    val ArithmeticExpressionsClassFile = project.classFile(ArithmeticExpressionsType).get

    import BinaryArithmeticOperators._
    import UnaryArithmeticOperators._

    val LongAddMethod = ArithmeticExpressionsClassFile.findMethod("longAdd").head
    val LongAndMethod = ArithmeticExpressionsClassFile.findMethod("longAnd").head
    val LongDivMethod = ArithmeticExpressionsClassFile.findMethod("longDiv").head
    val LongNegMethod = ArithmeticExpressionsClassFile.findMethod("longNeg").head
    val LongMulMethod = ArithmeticExpressionsClassFile.findMethod("longMul").head
    val LongOrMethod = ArithmeticExpressionsClassFile.findMethod("longOr").head
    val LongRemMethod = ArithmeticExpressionsClassFile.findMethod("longRem").head
    val LongShRMethod = ArithmeticExpressionsClassFile.findMethod("longShR").head
    val LongShLMethod = ArithmeticExpressionsClassFile.findMethod("longShL").head
    val LongSubMethod = ArithmeticExpressionsClassFile.findMethod("longSub").head
    val LongAShMethod = ArithmeticExpressionsClassFile.findMethod("longASh").head
    val LongXOrMethod = ArithmeticExpressionsClassFile.findMethod("longXOr").head

    describe("the naive TAC of long operations") {

            def binaryJLC(strg: String) = Array(
                "0: r_0 = this;",
                "1: r_1 = p_1;",
                "2: r_3 = p_2;",
                "3: op_0 = r_1;",
                "4: op_2 = r_3;",
                strg,
                "6: return op_0;"
            )

            def binaryAST(stmt: Stmt): Array[Stmt] = Array(
                Assignment(-1, SimpleVar(-1, ComputationalTypeReference), Param(ComputationalTypeReference, "this")),
                Assignment(-1, SimpleVar(-2, ComputationalTypeLong), Param(ComputationalTypeLong, "p_1")),
                Assignment(-1, SimpleVar(-4, ComputationalTypeLong), Param(ComputationalTypeLong, "p_2")),
                Assignment(0, SimpleVar(0, ComputationalTypeLong), SimpleVar(-2, ComputationalTypeLong)),
                Assignment(1, SimpleVar(2, ComputationalTypeLong), SimpleVar(-4, ComputationalTypeLong)),
                stmt,
                ReturnValue(3, SimpleVar(0, ComputationalTypeLong))
            )

            def binaryShiftAST(stmt: Stmt): Array[Stmt] = Array(
                Assignment(-1, SimpleVar(-1, ComputationalTypeReference), Param(ComputationalTypeReference, "this")),
                Assignment(-1, SimpleVar(-2, ComputationalTypeLong), Param(ComputationalTypeLong, "p_1")),
                Assignment(-1, SimpleVar(-4, ComputationalTypeInt), Param(ComputationalTypeInt, "p_2")),
                Assignment(0, SimpleVar(0, ComputationalTypeLong), SimpleVar(-2, ComputationalTypeLong)),
                Assignment(1, SimpleVar(2, ComputationalTypeInt), SimpleVar(-4, ComputationalTypeInt)),
                stmt,
                ReturnValue(3, SimpleVar(0, ComputationalTypeLong))
            )

            it("should correctly reflect addition") {
                val statements = AsQuadruples(method = LongAddMethod, classHierarchy = Code.BasicClassHierarchy)._1
                val javaLikeCode = ToJavaLike(statements, false)

                assert(statements.nonEmpty)
                assert(javaLikeCode.length > 0)
                statements.shouldEqual(binaryAST(
                    Assignment(2, SimpleVar(0, ComputationalTypeLong),
                        BinaryExpr(2, ComputationalTypeLong, Add, SimpleVar(0, ComputationalTypeLong), SimpleVar(2, ComputationalTypeLong)))
                ))
                javaLikeCode.shouldEqual(binaryJLC("5: op_0 = op_0 + op_2;"))
            }

            it("should correctly reflect logical and") {
                val statements = AsQuadruples(method = LongAndMethod, classHierarchy = Code.BasicClassHierarchy)._1
                val javaLikeCode = ToJavaLike(statements, false)

                assert(statements.nonEmpty)
                assert(javaLikeCode.length > 0)
                statements.shouldEqual(binaryAST(
                    Assignment(2, SimpleVar(0, ComputationalTypeLong),
                        BinaryExpr(2, ComputationalTypeLong, And, SimpleVar(0, ComputationalTypeLong), SimpleVar(2, ComputationalTypeLong)))
                ))
                javaLikeCode.shouldEqual(binaryJLC("5: op_0 = op_0 & op_2;"))
            }

            it("should correctly reflect division") {
                val statements = AsQuadruples(method = LongDivMethod, classHierarchy = Code.BasicClassHierarchy)._1
                val javaLikeCode = ToJavaLike(statements, false)

                assert(statements.nonEmpty)
                assert(javaLikeCode.length > 0)
                statements.shouldEqual(binaryAST(
                    Assignment(2, SimpleVar(0, ComputationalTypeLong),
                        BinaryExpr(2, ComputationalTypeLong, Divide, SimpleVar(0, ComputationalTypeLong), SimpleVar(2, ComputationalTypeLong)))
                ))
                javaLikeCode.shouldEqual(binaryJLC("5: op_0 = op_0 / op_2;"))
            }

            it("should correctly reflect negation") {
                val statements = AsQuadruples(method = LongNegMethod, classHierarchy = Code.BasicClassHierarchy)._1
                val javaLikeCode = ToJavaLike(statements, false)

                assert(statements.nonEmpty)
                assert(javaLikeCode.length > 0)
                statements.shouldEqual(Array(
                    Assignment(-1, SimpleVar(-1, ComputationalTypeReference), Param(ComputationalTypeReference, "this")),
                    Assignment(-1, SimpleVar(-2, ComputationalTypeLong), Param(ComputationalTypeLong, "p_1")),
                    Assignment(0, SimpleVar(0, ComputationalTypeLong), SimpleVar(-2, ComputationalTypeLong)),
                    Assignment(1, SimpleVar(0, ComputationalTypeLong),
                        PrefixExpr(1, ComputationalTypeLong, Negate, SimpleVar(0, ComputationalTypeLong))),
                    ReturnValue(2, SimpleVar(0, ComputationalTypeLong))
                ))
                javaLikeCode.shouldEqual(
                    Array(
                        "0: r_0 = this;",
                        "1: r_1 = p_1;",
                        "2: op_0 = r_1;",
                        "3: op_0 = - op_0;",
                        "4: return op_0;"
                    )
                )
            }

            it("should correctly reflect multiplication") {
                val statements = AsQuadruples(method = LongMulMethod, classHierarchy = Code.BasicClassHierarchy)._1
                val javaLikeCode = ToJavaLike(statements, false)

                assert(statements.nonEmpty)
                assert(javaLikeCode.length > 0)
                statements.shouldEqual(binaryAST(
                    Assignment(2, SimpleVar(0, ComputationalTypeLong),
                        BinaryExpr(2, ComputationalTypeLong, Multiply, SimpleVar(0, ComputationalTypeLong), SimpleVar(2, ComputationalTypeLong)))
                ))
                javaLikeCode.shouldEqual(binaryJLC("5: op_0 = op_0 * op_2;"))
            }

            it("should correctly reflect logical or") {
                val statements = AsQuadruples(method = LongOrMethod, classHierarchy = Code.BasicClassHierarchy)._1
                val javaLikeCode = ToJavaLike(statements, false)

                assert(statements.nonEmpty)
                assert(javaLikeCode.length > 0)
                statements.shouldEqual(binaryAST(
                    Assignment(2, SimpleVar(0, ComputationalTypeLong),
                        BinaryExpr(2, ComputationalTypeLong, Or, SimpleVar(0, ComputationalTypeLong), SimpleVar(2, ComputationalTypeLong)))
                ))
                javaLikeCode.shouldEqual(binaryJLC("5: op_0 = op_0 | op_2;"))
            }

            it("should correctly reflect modulo") {
                val statements = AsQuadruples(method = LongRemMethod, classHierarchy = Code.BasicClassHierarchy)._1
                val javaLikeCode = ToJavaLike(statements, false)

                assert(statements.nonEmpty)
                assert(javaLikeCode.length > 0)
                statements.shouldEqual(binaryAST(
                    Assignment(2, SimpleVar(0, ComputationalTypeLong),
                        BinaryExpr(2, ComputationalTypeLong, Modulo, SimpleVar(0, ComputationalTypeLong), SimpleVar(2, ComputationalTypeLong)))
                ))
                javaLikeCode.shouldEqual(binaryJLC("5: op_0 = op_0 % op_2;"))
            }

            it("should correctly reflect shift right") {
                val statements = AsQuadruples(method = LongShRMethod, classHierarchy = Code.BasicClassHierarchy)._1
                val javaLikeCode = ToJavaLike(statements, false)

                assert(statements.nonEmpty)
                assert(javaLikeCode.length > 0)
                statements.shouldEqual(binaryShiftAST(
                    Assignment(2, SimpleVar(0, ComputationalTypeLong),
                        BinaryExpr(2, ComputationalTypeLong, ShiftRight, SimpleVar(0, ComputationalTypeLong), SimpleVar(2, ComputationalTypeInt)))
                ))
                javaLikeCode.shouldEqual(binaryJLC("5: op_0 = op_0 >> op_2;"))
            }

            it("should correctly reflect shift left") {
                val statements = AsQuadruples(method = LongShLMethod, classHierarchy = Code.BasicClassHierarchy)._1
                val javaLikeCode = ToJavaLike(statements, false)

                assert(statements.nonEmpty)
                assert(javaLikeCode.length > 0)
                statements.shouldEqual(binaryShiftAST(
                    Assignment(2, SimpleVar(0, ComputationalTypeLong),
                        BinaryExpr(2, ComputationalTypeLong, ShiftLeft, SimpleVar(0, ComputationalTypeLong), SimpleVar(2, ComputationalTypeInt)))
                ))
                javaLikeCode.shouldEqual(binaryJLC("5: op_0 = op_0 << op_2;"))
            }

            it("should correctly reflect subtraction") {
                val statements = AsQuadruples(method = LongSubMethod, classHierarchy = Code.BasicClassHierarchy)._1
                val javaLikeCode = ToJavaLike(statements, false)

                assert(statements.nonEmpty)
                assert(javaLikeCode.length > 0)
                statements.shouldEqual(binaryAST(
                    Assignment(2, SimpleVar(0, ComputationalTypeLong),
                        BinaryExpr(2, ComputationalTypeLong, Subtract, SimpleVar(0, ComputationalTypeLong), SimpleVar(2, ComputationalTypeLong)))
                ))
                javaLikeCode.shouldEqual(binaryJLC("5: op_0 = op_0 - op_2;"))
            }

            it("should correctly reflect arithmetic shift right") {
                val statements = AsQuadruples(method = LongAShMethod, classHierarchy = Code.BasicClassHierarchy)._1
                val javaLikeCode = ToJavaLike(statements, false)

                assert(statements.nonEmpty)
                assert(javaLikeCode.length > 0)
                statements.shouldEqual(binaryShiftAST(
                    Assignment(2, SimpleVar(0, ComputationalTypeLong),
                        BinaryExpr(2, ComputationalTypeLong, UnsignedShiftRight, SimpleVar(0, ComputationalTypeLong), SimpleVar(2, ComputationalTypeInt)))
                ))
                javaLikeCode.shouldEqual(binaryJLC("5: op_0 = op_0 >>> op_2;"))
            }

            it("should correctly reflect logical xor") {
                val statements = AsQuadruples(method = LongXOrMethod, classHierarchy = Code.BasicClassHierarchy)._1
                val javaLikeCode = ToJavaLike(statements, false)

                assert(statements.nonEmpty)
                assert(javaLikeCode.length > 0)
                statements.shouldEqual(binaryAST(
                    Assignment(2, SimpleVar(0, ComputationalTypeLong),
                        BinaryExpr(2, ComputationalTypeLong, XOr, SimpleVar(0, ComputationalTypeLong), SimpleVar(2, ComputationalTypeLong)))
                ))
                javaLikeCode.shouldEqual(binaryJLC("5: op_0 = op_0 ^ op_2;"))
            }
    }
}
