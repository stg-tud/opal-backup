/* License (BSD Style License):
 *  Copyright (c) 2009:
 *  Software Technology Group,
 *  Department of Computer Science
 *  Darmstadt University of Technology
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
package de.tud.cs.st.bat.canonical


/**
 * <pre>
 * InnerClasses_attribute { 
 * u2 attribute_name_index; 
 * u4 attribute_length; 
 * u2 number_of_classes; // => Seq[InnerClasses_attribute.Class]
 * {	u2 inner_class_info_index; 
 * 	u2 outer_class_info_index; 
 * 	u2 inner_name_index; 
 * 	u2 inner_class_access_flags; 
 * 	} classes[number_of_classes]; 
 * } 
 * </pre>

 * @author Michael Eichberg
 */
trait InnerClasses_attribute extends Attribute{

	//
	// ABSTRACT DEFINITIONS
	//
	
	type Class

	val attribute_name_index : Int
	
	val classes : InnerClassesEntries
	
	 
	//
	// IMPLEMENTATION
	//
	
	type InnerClassesEntries = Seq[Class]
	
	def attribute_name = InnerClasses_attribute.name
	
	def attribute_length = 2 + (classes.size * 8)

}
object InnerClasses_attribute {
	val name = "InnerClasses"
}



/**
* u2 inner_class_info_index; 
* u2 outer_class_info_index; 
* u2 inner_name_index; 
* u2 inner_class_access_flags;
 */
trait InnerClassesEntry{

	/**
	 * Every CONSTANT_Class_info entry in the constant_pool table 
	 * which represents a class or interface C that is not a package 
	 * member must have exactly one corresponding entry in the 
	 * classes array. 
	 * If a class has members that are classes or interfaces, its 
	 * constant_pool table (and hence its InnerClasses attribute) 
	 * must refer to each such member, even if that member is not 
	 * otherwise mentioned by the class. These rules imply that a nested 
	 * class or interface member will have InnerClasses information 
	 * for each enclosing class and for each immediate member. 
	 */

	val inner_class_info_index : Int
	val outer_class_info_index : Int
	val inner_name_index : Int
	val inner_class_access_flags : Int
}


