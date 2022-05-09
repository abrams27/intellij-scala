package org.jetbrains.plugins.scala.debugger.evaluation

import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.evaluation.expression._
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.debugger.evaluation.evaluator._
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScLiteral
import org.jetbrains.plugins.scala.lang.psi.api.base.patterns.ScReferencePattern
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScFunctionExpr, ScReferenceExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.source.ScalaCodeFragment

private object ExpressionEvaluatorBuilder extends EvaluatorBuilder {
  override def build(codeFragment: PsiElement, position: SourcePosition): ExpressionEvaluator = {
    val element = codeFragment.asInstanceOf[ScalaCodeFragment].children.toList.head
    new ExpressionEvaluatorImpl(buildEvaluator(element, position))
  }

  private def buildEvaluator(element: PsiElement, position: SourcePosition): Evaluator = element match {
    case _: ScFunctionExpr => new LambdaExpressionEvaluator(position.getElementAt)
    case lit: ScLiteral => LiteralEvaluator.fromLiteral(lit)
    case ref: ScReferenceExpression =>
      ref.resolve() match {
        case FunctionLocalVariable(name, funName, _) => new LocalVariableEvaluator(name, funName)
      }
  }

  private object FunctionLocalVariable {
    def unapply(element: PsiElement): Option[(String, String, Boolean)] =
      Option(element)
        .collect { case rp: ScReferencePattern if !rp.isClassMember => rp }
        .flatMap(rp => rp.parentOfType[ScFunctionDefinition].map(fd => (rp.name, fd.name, rp.isVar)))
  }
}
