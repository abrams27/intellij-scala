package org.jetbrains.plugins.scala
package lang
package psi
package impl
package base
package literals

import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.tree.java.PsiLiteralExpressionImpl
import org.jetbrains.plugins.scala.lang.psi.api.base.{ScLiteral, literals}
import org.jetbrains.plugins.scala.lang.psi.types.{ScType, api}

final class ScCharLiteralImpl(node: ASTNode,
                              override val toString: String)
  extends QuotedLiteralImplBase(node, toString)
    with literals.ScCharLiteral {

  override protected def startQuote: String = ScLiteral.CharQuote

  override protected def wrappedValue(value: Character) =
    ScCharLiteralImpl.Value(value)

  override protected def toValue(chars: String): Character = {
    val outChars = new java.lang.StringBuilder
    val success = PsiLiteralExpressionImpl.parseStringCharacters(
      chars,
      outChars,
      null
    )

    if (success && outChars.length == 1) Character.valueOf(outChars.charAt(0))
    else null
  }
}

object ScCharLiteralImpl {

  final case class Value(override val value: Character) extends ScLiteral.Value(value) {

    import ScLiteral.CharQuote

    override def presentation: String = CharQuote + super.presentation + CharQuote

    override def wideType(implicit project: Project): ScType = api.Char
  }
}
