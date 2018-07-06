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
package properties
package compile_time_constancy

import org.opalj.br.AnnotationLike
import org.opalj.br.ObjectType
import org.opalj.br.analyses.SomeProject

/**
 * Base trait for matchers that match a field's `CompileTimeConstancy` property.
 *
 * @author Dominik Helm
 */
sealed abstract class CompileTimeConstancyMatcher(val property: CompileTimeConstancy)
    extends AbstractPropertyMatcher {

    def validateProperty(
        p:          SomeProject,
        as:         Set[ObjectType],
        entity:     Entity,
        a:          AnnotationLike,
        properties: Traversable[Property]
    ): Option[String] = {
        if (!properties.exists(_ match {
            case `property` ⇒ true
            case _          ⇒ false
        })) {
            // ... when we reach this point the expected property was not found.
            Some(a.elementValuePairs.head.value.asStringValue.value)
        } else {
            None
        }
    }
}

/**
 * Matches a field's `CompileTimeConstancy` property. The match is successful if the field has the
 * property [[org.opalj.fpcf.properties.CompileTimeConstantField]].
 */
class CompileTimeConstantMatcher extends CompileTimeConstancyMatcher(CompileTimeConstantField)

/**
 * Matches a field's `CompileTimeConstancy` property. The match is successful if the field has the
 * property [[org.opalj.fpcf.properties.CompileTimeVaryingField]].
 */
class CompileTimeVaryingMatcher extends CompileTimeConstancyMatcher(CompileTimeVaryingField)
