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

import org.opalj.br.Code
import org.opalj.br.instructions.NEW
import org.opalj.br.instructions.INVOKESPECIAL

/**
 * Commonly useful methods.
 *
 * @author Michael Eichberg
 */
package object l1 {

    /**
     * @note At the bytecode level, the allocation of memory and the call of the
     *      constructor are not atomic and it is possible to associate one "new"
     *      instruction with multiple constructor calls (INVOKESPECIAL(...,"<init>",...));
     *      however, such code is not generated by any known compiler so far(Dec. 2014).
     */
    def constructorCallForNewReferenceValueWithOrigin(
        code: Code,
        receiverOrigin: PC,
        domain: l1.ReferenceValues)(
            operandsArray: domain.OperandsArray): Seq[PC] = {

        val instructions = code.instructions

        assert(
            receiverOrigin >= 0 && receiverOrigin < instructions.length,
            s"the origin $receiverOrigin is outside the scope of the method ")
        assert(
            instructions(receiverOrigin).opcode == NEW.opcode,
            s"${instructions(receiverOrigin)} is not a NEW instruction")
        assert(
            operandsArray(receiverOrigin) ne null,
            s"the (new) instruction with pc=$receiverOrigin was never executed")

        // as usual in OPAL, we assume that the bytecode is valid; i.e., there will
        // be one constructor call

        (org.opalj.ai.collectPCWithOperands(domain)(code, operandsArray) {
            case (pc, constructorCall @ INVOKESPECIAL(_, "<init>", md), operands) if operands.length >= md.parametersCount &&
                domain.asObjectValue(operands(md.parametersCount)).origin == receiverOrigin ⇒ pc
        })
    }
}
