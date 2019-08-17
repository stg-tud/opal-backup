/* BSD 2-Clause License - see OPAL/LICENSE for details. */
package org.opalj
package tac
package fpcf
package analyses
package cg
package xta

import org.opalj.br.ArrayType
import org.opalj.br.DefinedMethod
import org.opalj.br.Field
import org.opalj.br.VirtualDeclaredMethod
import org.opalj.fpcf.Entity

/**
 * Selects the corresponding set entity for each entity.
 *
 * This function is the only aspect XTA, MTA, FTA and CTA are distinguished by.
 *
 * @author Andreas Bauer
 */
trait SetEntitySelector extends (Entity ⇒ SetEntity)

/**
 * XTA type propagation uses a separate set for each method and each field.
 *
 * @author Andreas Bauer
 */
object XTASetEntitySelector extends SetEntitySelector {
    override def apply(e: Entity): SetEntity = e match {
        case dm: DefinedMethod          ⇒ dm
        case vdm: VirtualDeclaredMethod ⇒ ExternalWorld
        case f: Field                   ⇒ f
        case ef: ExternalField          ⇒ ExternalWorld
        case at: ArrayType              ⇒ at
        case _                          ⇒ sys.error("unexpected entity: "+e)

    }
}

/**
 * MTA type propagation uses a separate set each field and a single set for all methods in a class.
 *
 * @author Andreas Bauer
 */
object MTASetEntitySelector extends SetEntitySelector {
    override def apply(e: Entity): SetEntity = e match {
        case dm: DefinedMethod          ⇒ dm.definedMethod.classFile
        case vdm: VirtualDeclaredMethod ⇒ ExternalWorld
        case f: Field                   ⇒ f
        case ef: ExternalField          ⇒ ExternalWorld
        case at: ArrayType              ⇒ at
        case _                          ⇒ sys.error("unexpected entity: "+e)

    }
}

/**
 * FTA type propagation uses a separate set for each method and a single set for all fields in a class.
 *
 * @author Andreas Bauer
 */
object FTASetEntitySelector extends SetEntitySelector {
    override def apply(e: Entity): SetEntity = e match {
        case dm: DefinedMethod          ⇒ dm
        case vdm: VirtualDeclaredMethod ⇒ ExternalWorld
        case f: Field                   ⇒ f.classFile
        case ef: ExternalField          ⇒ ExternalWorld
        case at: ArrayType              ⇒ at
        case _                          ⇒ sys.error("unexpected entity: "+e)

    }
}

/**
 * CTA type propagation uses a single set for all methods and field in a class.
 *
 * @author Andreas Bauer
 */
object CTASetEntitySelector extends SetEntitySelector {
    override def apply(e: Entity): SetEntity = e match {
        case dm: DefinedMethod          ⇒ dm.definedMethod.classFile
        case vdm: VirtualDeclaredMethod ⇒ ExternalWorld
        case f: Field                   ⇒ f.classFile
        case ef: ExternalField          ⇒ ExternalWorld
        case at: ArrayType              ⇒ at
        case _                          ⇒ sys.error("unexpected entity: "+e)
    }
}