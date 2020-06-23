/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj.fpcf.properties.reference_immutability;

import org.opalj.br.fpcf.FPCFAnalysis;
import org.opalj.fpcf.properties.PropertyValidator;
import org.opalj.tac.fpcf.analyses.immutability.reference.L0ReferenceImmutabilityAnalysis;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Annotation to state that the annotated reference is mutable
 *
 * @author Tobias Peter Roth
 */
@PropertyValidator(key = "ReferenceImmutability",validator = MutableReferenceMatcher.class)
@Documented
@Retention(RetentionPolicy.CLASS)
public @interface MutableReferenceAnnotation {


    /**
     * True if the field is non-final because it is read prematurely.
     * Tests may ignore @NonFinal annotations if the FieldPrematurelyRead property for the field
     * did not identify the premature read.
     */
    boolean prematurelyRead() default false;
    /**
     * A short reasoning of this property.
     */
    String value()  default "N/A";

    Class<? extends FPCFAnalysis>[] analyses() default {L0ReferenceImmutabilityAnalysis.class};

}