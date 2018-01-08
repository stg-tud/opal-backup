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
package org.opalj.da

import scala.xml.Node
import scala.xml.NodeSeq

import org.opalj.bi.ConstantPoolTag
import org.opalj.bi.ConstantPoolTags

/**
 * @param name_index Reference to a CONSTANT_Utf8_info structure encoding a package name in
 *                   internal form.
 * @author Michael Eichberg
 */
case class CONSTANT_Package_info(name_index: Constant_Pool_Index) extends Constant_Pool_Entry {

    override def size: Int = 1 + 2

    override def Constant_Type_Value: ConstantPoolTag = ConstantPoolTags.CONSTANT_Package

    override def asCPNode(implicit cp: Constant_Pool): Node = {
        <span class="cp_entry">
            CONSTANT_Package_info(name_index=
            { name_index }
            &laquo;
            <span class="cp_ref">
                { cp(name_index).asCPNode }
            </span>
            &raquo;
            )
        </span>
    }

    override def toString(implicit cp: Constant_Pool): String = cp(name_index).toString(cp)

    override def asInstructionParameter(implicit cp: Constant_Pool): NodeSeq = {
        throw new UnsupportedOperationException("unexpected usage in combination with instructions")
    }

}
