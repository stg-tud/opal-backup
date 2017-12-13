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
package ba

import java.io.ByteArrayInputStream

import org.opalj.bc.Assembler
import org.opalj.br.ObjectType
import org.opalj.br.IntegerType
import org.opalj.br.MethodDescriptor.JustReturnsString
import org.opalj.br.MethodDescriptor.JustReturnsInteger
import org.opalj.br.MethodDescriptor.JustTakes
import org.opalj.br.instructions.INVOKEVIRTUAL
import org.opalj.br.instructions.DUP
import org.opalj.br.instructions.GETSTATIC
import org.opalj.br.instructions.SWAP
import org.opalj.br.reader.Java8Framework
import org.opalj.util.InMemoryClassLoader

/**
 * Demonstrates how to perform a simple instrumentation; here, we just search for toString calls
 * and print out the result to the console.
 *
 * @author Michael Eichberg
 */
object SimpleInstrumentation extends App {

    val PrintStreamType = ObjectType("java/io/PrintStream")
    val SystemType = ObjectType("java/lang/System")

    val TheType = ObjectType("org/opalj/ba/SimpleInstrumentationDemo")

    // let's load the class
    val in = () ⇒ this.getClass.getResourceAsStream("SimpleInstrumentationDemo.class")
    val cf = Java8Framework.ClassFile(in).head // in this case we don't have invokedynamic resolution
    val newMethods =
        for (m ← cf.methods) yield {
            m.body match {
                case None ⇒
                    m.copy() // these are native and abstract methods

                case Some(code) ⇒
                    // let's search all "toString" calls
                    val lCode = CODE.toLabeledCode(code)
                    for { (pc, INVOKEVIRTUAL(_, "toString", JustReturnsString)) ← code } {
                        // print out the result of toString
                        lCode.insert(
                            pc, InsertionPosition.After,
                            Seq(
                                DUP,
                                GETSTATIC(SystemType, "out", PrintStreamType),
                                SWAP,
                                INVOKEVIRTUAL(PrintStreamType, "println", JustTakes(ObjectType.String))
                            )
                        )
                        // print out the receiver's hashCode (it has to be on the stack!)
                        lCode.insert(
                            pc, InsertionPosition.Before,
                            Seq(
                                DUP,
                                INVOKEVIRTUAL(ObjectType.Object, "hashCode", JustReturnsInteger),
                                GETSTATIC(SystemType, "out", PrintStreamType),
                                SWAP,
                                INVOKEVIRTUAL(PrintStreamType, "println", JustTakes(IntegerType))
                            )
                        )
                    }
                    val (newCode, _) = lCode.toCodeAttributeBuilder(m)
                    m.copy(body = Some(newCode))
            }
        }
    val newRawCF = Assembler(toDA(cf.copy(methods = newMethods)))

    //
    // THE FOLLOWING IS NOT RELATED TO BYTECODE MANIPULATION, BUT SHOWS ASPECTS OF OPAL WHICH ARE
    // HELPFUL WHEN DOING BYTECODE INSTRUMENTATION.
    //

    // Let's see the old file...
    println("original: "+
        org.opalj.io.writeAndOpen(
            da.ClassFileReader.ClassFile(in).head.toXHTML(None), "SimpleInstrumentationDemo", ".html"
        ))

    // Let's see the new file...
    println("instrumented: "+
        org.opalj.io.writeAndOpen(
            da.ClassFileReader.ClassFile(() ⇒ new ByteArrayInputStream(newRawCF)).head.toXHTML(None),
            "NewSimpleInstrumentationDemo", ".html"
        ))

    // Let's test that the new class does what it is expected to do... (we execute the
    // instrumented method)
    val cl = new InMemoryClassLoader(Map((TheType.toJava, newRawCF)), this.getClass.getClassLoader)
    val newClass = cl.findClass(TheType.toJava)
    val instance = newClass.newInstance()
    newClass.getMethod("callsToString").invoke(instance)

}

// the following is just a simple demo class which we are going to instrument
class SimpleInstrumentationDemo {

    def main(args: Array[String]): Unit = {
        new SimpleInstrumentationDemo().callsToString()
    }

    def callsToString(): Unit = {
        println("the length of the toString representation is: "+this.toString().length())
    }
}

