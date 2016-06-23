/* BSD 2-Clause License:
 * Copyright (c) 2009 - 2016
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
package br

import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import org.opalj.log.GlobalLogContext
import org.opalj.br.analyses.Project

/**
 * Basic tests of the TypesSet class.
 *
 * @author Michael Eichberg
 */
@RunWith(classOf[JUnitRunner])
class TypesSetTest extends FlatSpec with Matchers {

    //
    // Setup
    //
    val jlsCHFile = "ClassHierarchyJLS.ths"
    val jlsCHCreator = List(() ⇒ classOf[Project[_]].getResourceAsStream(jlsCHFile))
    val jlsCH = ClassHierarchy(Traversable.empty, jlsCHCreator)(GlobalLogContext)

    val preInitCH = ClassHierarchy.preInitializedClassHierarchy

    val javaLangCHFile = "JavaLangClassHierarchy.ths"
    val javaLangCHCreator = List(() ⇒ classOf[Project[_]].getResourceAsStream(javaLangCHFile))
    val javaLangCH = ClassHierarchy(Traversable.empty, javaLangCHCreator)(GlobalLogContext)

    val Object = ObjectType.Object
    val Class = ObjectType.Class
    val Throwable = ObjectType.Throwable
    val Exception = ObjectType.Exception
    val Error = ObjectType.Error
    val RuntimeException = ObjectType.RuntimeException
    val ArithmeticException = ObjectType.ArithmeticException
    val Cloneable = ObjectType.Cloneable
    val Serializable = ObjectType.Serializable
    val AnUnknownType = ObjectType("myTest/AnUnknownType")

    //
    // Verify 
    //

    behavior of "the TypesSet"

    it should "be empty empty upon creation" in {
        new TypesSet(jlsCH) should be('empty)
    }

    behavior of "the add type method +="

    it should "add the type if it is not stored in the set" in {
        val ts = new TypesSet(jlsCH)
        ts += Cloneable
        ts += Serializable
        assert(ts.size === 2)
        assert(ts.nonEmpty)
        ts.types should be((Set(Cloneable, Serializable), Set.empty))
    }

    it should "add the type if it is not stored in the set and only subtypes are added as upper type bounds" in {
        val ts = new TypesSet(jlsCH)
        ts +<:= Class
        ts += Serializable
        assert(ts.size === 2)
        assert(ts.nonEmpty)
        ts.types should be((Set(Serializable), Set(Class)))
    }

    it should "not add the type if the type is already added as an upper type bound" in {
        val ts = new TypesSet(jlsCH)
        ts +<:= Cloneable
        ts += Serializable
        ts += Cloneable
        assert(ts.size === 2)
        assert(ts.nonEmpty)
        ts.types should be((Set(Serializable), Set(Cloneable)))
    }

    it should "not add a type if a super type is already added as an upper type bound" in {
        val ts = new TypesSet(jlsCH)
        ts +<:= Object
        ts += Object
        ts += Cloneable
        assert(ts.size === 1)
        assert(ts.nonEmpty)
        ts.types should be((Set.empty, Set(Object)))
    }

    behavior of "the add upper type bound method +<:="

    it should "not remove unrelated concrete types if a super type of some types is added as an upper type bound" in {
        val ts = new TypesSet(jlsCH)
        ts += Cloneable
        ts += Class
        ts +<:= Serializable
        assert(ts.size === 2)
        assert(ts.nonEmpty)
        ts.types should be((Set(Cloneable), Set(Serializable)))
    }

    it should "remove the type if the type is (later) added as an upper type bound" in {
        val ts = new TypesSet(jlsCH)
        ts += Cloneable
        ts += Serializable
        ts +<:= Cloneable
        assert(ts.size === 2)
        assert(ts.nonEmpty)
        ts.types should be((Set(Serializable), Set(Cloneable)))
    }

    it should "remove all concrete types if a super type is added as an upper type bound" in {
        val ts = new TypesSet(jlsCH)
        ts += Cloneable
        ts += Serializable
        ts +<:= Object
        assert(ts.size === 1)
        assert(ts.nonEmpty)
        ts.types should be((Set.empty, Set(Object)))
    }

    it should "add the type if it is not stored in the set" in {
        val ts = new TypesSet(jlsCH)
        ts +<:= Cloneable
        ts +<:= Serializable
        assert(ts.size === 2)
        assert(ts.nonEmpty)
        ts.types should be((Set.empty, Set(Cloneable, Serializable)))
    }

    it should "not a add the type if a super type is already in the set as an upper type bound" in {
        val ts = new TypesSet(jlsCH)
        ts +<:= Serializable
        ts +<:= Class
        assert(ts.size === 1)
        assert(ts.nonEmpty)
        ts.types should be((Set.empty, Set(Serializable)))
    }

    it should "replace a type if a super type is added" in {
        val ts = new TypesSet(jlsCH)
        ts +<:= Class
        ts +<:= Serializable
        assert(ts.size === 1)
        assert(ts.nonEmpty)
        ts.types should be((Set.empty, Set(Serializable)))
    }
}
