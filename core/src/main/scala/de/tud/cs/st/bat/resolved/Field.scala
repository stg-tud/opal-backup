/* License (BSD Style License):
 * Copyright (c) 2009 - 2013
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
 *  - Neither the name of the Software Technology Group or Technische
 *    Universität Darmstadt nor the names of its contributors may be used to
 *    endorse or promote products derived from this software without specific
 *    prior written permission.
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
package de.tud.cs.st
package bat
package resolved

/**
 * Represents a single field declaration/definition.
 *
 * @param accessFlags This field's access flags. To analyze the access flags
 *  bit vector use [[de.tud.cs.st.bat.AccessFlag]] or
 *  [[de.tud.cs.st.bat.AccessFlagsIterator]] or use pattern matching.
 * @param name The name of this field. Note, that this name is not required to be a valid
 *  Java programming language identifier.
 * @param fieldType The (erased) type of this field.
 * @param attributes The defined attributes. The JVM 7 specification defines the following
 * 	  attributes for fields:
 *    * [[de.tud.cs.st.bat.resolved.ConstantValue]],
 *    * [[de.tud.cs.st.bat.resolved.Synthetic]],
 *    * [[de.tud.cs.st.bat.resolved.Signature]],
 * 	  * [[de.tud.cs.st.bat.resolved.Deprecated]],
 *    * [[de.tud.cs.st.bat.resolved.RuntimeVisibleAnnotationTable]] and
 *    * [[de.tud.cs.st.bat.resolved.RuntimeInvisibleAnnotationTable]].
 *
 * @author Michael Eichberg
 */
final class Field private (
    val id: Int,
    val accessFlags: Int,
    val name: String,
    val fieldType: FieldType,
    val attributes: Attributes)
        extends ClassMember
        with UniqueID {

    override def isField = true

    override def asField = this

    def isTransient: Boolean = ACC_TRANSIENT isElementOf accessFlags

    def isVolatile: Boolean = ACC_VOLATILE isElementOf accessFlags

    /**
     * Returns this field's type signature.
     */
    def fieldTypeSignature: Option[FieldTypeSignature] =
        attributes collectFirst { case s: FieldTypeSignature ⇒ s }

    /**
     * Returns this field's constant value.
     */
    def constantValue: Option[ConstantValue[_]] =
        attributes collectFirst { case cv: ConstantValue[_] ⇒ cv }

    def toJavaSignature: String = fieldType.toJava+" "+name

    override def hashCode: Int = id

    override def equals(other: Any): Boolean =
        other match {
            case that: AnyRef ⇒ this eq that
            case _            ⇒ false
        }

    override def toString(): String = {
        AccessFlags.toStrings(accessFlags, AccessFlagsContexts.FIELD).mkString("", " ", " ") +
            fieldType.toJava+" "+name +
            attributes.view.map(_.getClass().getSimpleName()).mkString(" « ", ", ", " »")
    }
}

/**
 * Defines factory and extractor methods for `Field` objects.
 */
object Field {

    private val nextId = new java.util.concurrent.atomic.AtomicInteger(0)

    def fieldsCount = nextId.get

    def apply(
        accessFlags: Int,
        name: String,
        fieldType: FieldType,
        attributes: Attributes): Field = {
        new Field(
            nextId.getAndIncrement(),
            accessFlags,
            name,
            fieldType,
            attributes)
    }

    def unapply(field: Field): Option[(Int, String, FieldType, Attributes)] =
        Some((
            field.accessFlags,
            field.name,
            field.fieldType,
            field.attributes
        ))
}
