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
package org.opalj.fpcf

/**
 * The different types of updates distinguished by the FPCF.
 *
 * @author Michael Eichberg
 */
private[fpcf] sealed abstract class UpdateType(name: String) {
    val ID: Int
}

/**
 * The result is just an intermediate result that may be refined in the future.
 */
case object IntermediateUpdate extends UpdateType("Intermediate Update") {
    final val ID = 1
}

/**
 * The result is the final result and was computed using other information.
 */
case object FinalUpdate extends UpdateType("Final Update") {
    final val ID = 2
}

/**
 * The result is the final result and was computed without requiring any other information.
 */
case object OneStepFinalUpdate extends UpdateType("Final Updated Without Dependencies") {
    final val ID = 3
}

/**
 * The result was determined by looking up a property kind's fallback property. Hence,
 * no "real" computation was performed.
 * Furthermore, it may be the case that
 * the updated value – at the point in time when it is handled - is no longer relevant
 * and has to be dropped.
 */
case object FallbackUpdate extends UpdateType("Fallback Update") {
    final val ID = 4
}