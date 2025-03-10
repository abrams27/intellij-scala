package org.jetbrains.plugins.scala.lang.psi.api.base.types

import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScNamedElement
import org.jetbrains.plugins.scala.lang.psi.types.result.TypeResult

trait ScTypeVariableTypeElement extends ScTypeElement with ScNamedElement {
  override protected val typeName = "TypeVariable"

  def inferredType: TypeResult

  override protected def acceptScala(visitor: ScalaElementVisitor): Unit = visitor.visitTypeVariableTypeElement(this)
}
