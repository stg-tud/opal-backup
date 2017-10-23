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
package av
package checking

import scala.collection.Set
import org.opalj.br._

/**
 * @author Marco Torsello
 */
trait AnnotationsPredicate extends (Traversable[Annotation] ⇒ Boolean)

/**
 * @author Marco Torsello
 */
case object NoAnnotations extends AnnotationsPredicate {

    def apply(others: Traversable[Annotation]): Boolean = false

}

/**
 * @author Michael Eichberg
 */
case object AnyAnnotations extends AnnotationsPredicate {

    def apply(others: Traversable[Annotation]): Boolean = true

}

/**
 * @author Marco Torsello
 */
case class HasAtLeastTheAnnotations(
        annotationPredicates: Set[_ <: AnnotationPredicate]
) extends AnnotationsPredicate {

    def apply(others: Traversable[Annotation]): Boolean = {
        annotationPredicates.forall(p ⇒ others.exists(a ⇒ p(a)))
    }
}
object HasAtLeastTheAnnotations {

    def apply(annotationPredicate: AnnotationPredicate): HasAtLeastTheAnnotations = {
        new HasAtLeastTheAnnotations(Set(annotationPredicate))
    }
}

/**
 * @author Marco Torsello
 */
case class HasTheAnnotations(
        annotationPredicates: Set[_ <: AnnotationPredicate]
) extends AnnotationsPredicate {

    def apply(others: Traversable[Annotation]): Boolean = {
        others.size == annotationPredicates.size &&
            annotationPredicates.forall(p ⇒ others.exists(a ⇒ p(a)))
    }

}
object HasTheAnnotations {

    def apply(annotationPredicate: AnnotationPredicate): HasTheAnnotations = {
        new HasTheAnnotations(Set(annotationPredicate))
    }
}

/**
 * @author Marco Torsello
 */
case class HasAtLeastOneAnnotation(
        annotationPredicates: Set[_ <: AnnotationPredicate]
) extends AnnotationsPredicate {

    def apply(annotations: Traversable[Annotation]): Boolean = {
        annotationPredicates.exists(p ⇒ annotations.exists(a ⇒ p(a)))
    }
}
