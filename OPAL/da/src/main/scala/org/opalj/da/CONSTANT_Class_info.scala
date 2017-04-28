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
package da

import scala.xml.Node
import org.opalj.bi.ConstantPoolTag

/**
 * @param name_index Reference to a CONSTANT_Utf8_info structure.
 * @author Michael Eichberg
 */
case class CONSTANT_Class_info(name_index: Constant_Pool_Index) extends Constant_Pool_Entry {

    override def size: Int = 1 + 2

    override def Constant_Type_Value: ConstantPoolTag = bi.ConstantPoolTags.CONSTANT_Class

    override def asConstantClass: this.type = this

    /**
     * Should be called if and only if the referenced type is known not be an array type and
     * therefore the underlying descriptor does not encode a field type descriptor.
     */
    def asJavaClassOrInterfaceType(implicit cp: Constant_Pool): String = {
        asJavaObjectType(cp(name_index).asConstantUTF8.value)
    }

    // OLD CONVERSION METHODS

    def asJavaType(implicit cp: Constant_Pool): String = {
        val classInfo = cp(name_index).toString
        if (classInfo.charAt(0) == '[')
            parseFieldType(classInfo).asJavaType
        else
            classInfo
    }

    override def asCPNode(implicit cp: Constant_Pool): Node =
        <span class="cp_entry">
            CONSTANT_Class_info(name_index={ name_index }
            &laquo;
            <span class="cp_ref">{ cp(name_index).asCPNode }</span>
            &raquo;)
        </span>

    override def asInlineNode(implicit cp: Constant_Pool): Node = {
        <span class="fqn">{ toString }</span>
    }

    override def toString(implicit cp: Constant_Pool): String = {
        val classInfo = cp(name_index).toString
        if (classInfo.charAt(0) == '[')
            parseFieldType(classInfo).asJavaType
        else
            classInfo.replace('/', '.')
    }
}
