/* License (BSD Style License):
*  Copyright (c) 2009, 2011
*  Software Technology Group
*  Department of Computer Science
*  Technische Universität Darmstadt
*  All rights reserved.
*
*  Redistribution and use in source and binary forms, with or without
*  modification, are permitted provided that the following conditions are met:
*
*  - Redistributions of source code must retain the above copyright notice,
*    this list of conditions and the following disclaimer.
*  - Redistributions in binary form must reproduce the above copyright notice,
*    this list of conditions and the following disclaimer in the documentation
*    and/or other materials provided with the distribution.
*  - Neither the name of the Software Technology Group or Technische 
*    Universität Darmstadt nor the names of its contributors may be used to 
*    endorse or promote products derived from this software without specific 
*    prior written permission.
*
*  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
*  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
*  IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
*  ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
*  LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
*  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
*  SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
*  INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
*  CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
*  ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
*  POSSIBILITY OF SUCH DAMAGE.
*/
package de.tud.cs.st.bat.resolved.reader

import de.tud.cs.st.bat.generic.reader.MethodsReader
import de.tud.cs.st.bat.generic.reader.FieldsReader
import de.tud.cs.st.bat.generic.reader.AttributesReader
import de.tud.cs.st.bat.generic.reader.SkipUnknown_attributeReader
import de.tud.cs.st.bat.generic.reader.CodeReader


/**
 * <p>
 * This class is currently not intended to be directly (re-)used; it is rather a template how to construct a 
 * special reader.
 * </p>
 *
 * @author Michael Eichberg
 */
object Java6Framework
	extends Constant_PoolResolver
		with ClassFileBinding	
		with MethodsReader
		with FieldsReader
		with AttributesReader
		//with Unknown_attributeBinding
		with SkipUnknown_attributeReader
		with AnnotationsBinding	
		with StackMapTable_attributeBinding 
		with InnerClasses_attributeBinding
		with EnclosingMethod_attributeBinding
		with SourceFile_attributeBinding 
		with SourceDebugExtension_attributeBinding
		with Deprecated_attributeBinding
		with Signature_attributeBinding
		with Synthetic_attributeBinding
		with LineNumberTable_attributeBinding
		with LocalVariableTable_attributeBinding
		with LocalVariableTypeTable_attributeBinding
		with Exceptions_attributeBinding
		with ConstantValue_attributeBinding
		with BytecodeReaderAndBinding
		with Code_attributeBinding
			with CodeReader
{

}


