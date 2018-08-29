/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package bi
package reader

import java.io.DataInputStream

/**
 * Generic infrastructure for reading the "@deprecated" attribute.
 */
trait Deprecated_attributeReader extends AttributeReader {

    //
    // TYPE DEFINITIONS AND FACTORY METHODS
    //

    type Deprecated_attribute <: Attribute

    def Deprecated_attribute(
        cp:                   Constant_Pool,
        attribute_name_index: Constant_Pool_Index
    ): Deprecated_attribute

    //
    // IMPLEMENTATION
    //

    /**
     * <pre>
     * Deprecated_attribute {
     *  u2 attribute_name_index;
     *  u4 attribute_length;
     * }
     * </pre>
     */
    private[this] def parserFactory() = (
        ap: AttributeParent,
        cp: Constant_Pool,
        attribute_name_index: Constant_Pool_Index,
        in: DataInputStream
    ) ⇒ {
        /*val attribute_length =*/ in.readInt
        Deprecated_attribute(cp, attribute_name_index)
    }

    registerAttributeReader(DeprecatedAttribute.Name → parserFactory())
}
