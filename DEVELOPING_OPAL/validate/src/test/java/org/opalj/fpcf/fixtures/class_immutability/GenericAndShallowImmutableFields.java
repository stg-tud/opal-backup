package org.opalj.fpcf.fixtures.class_immutability;

import org.opalj.fpcf.properties.class_immutability.ShallowImmutableClassAnnotation;
import org.opalj.fpcf.properties.field_immutability.DeepImmutableFieldAnnotation;
import org.opalj.fpcf.properties.field_immutability.DependentImmutableFieldAnnotation;
import org.opalj.fpcf.properties.field_immutability.ShallowImmutableFieldAnnotation;
import org.opalj.fpcf.properties.type_immutability.DependentImmutableTypeAnnotation;
import org.opalj.fpcf.properties.type_immutability.MutableTypeAnnotation;
import org.opalj.fpcf.properties.type_mutability.MutableType;

@MutableTypeAnnotation("")
@ShallowImmutableClassAnnotation("")
public class GenericAndShallowImmutableFields<T1, T2> {

    @DependentImmutableFieldAnnotation("")
    private T1 t1;
    @DependentImmutableFieldAnnotation("")
    private T2 t2;
    @ShallowImmutableFieldAnnotation("")
    private TrivialMutableClass  tmc;
    GenericAndShallowImmutableFields(T1 t1, T2 t2, TrivialMutableClass tmc){
        this.t1 = t1;
        this.t2 = t2;
        this.tmc = tmc;
    }

}
