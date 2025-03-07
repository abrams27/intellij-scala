package org.jetbrains.plugins.scala.annotator.element

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.annotator.ScalaAnnotationHolder
import org.jetbrains.plugins.scala.annotator.element.ScExpressionAnnotator.checkExpressionType
import org.jetbrains.plugins.scala.annotator.element.ScTypedExpressionAnnotator.mismatchRangesIn
import org.jetbrains.plugins.scala.annotator.quickfix.ReportHighlightingErrorQuickFix
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.expr.{ScBlockExpr, ScFunctionExpr, ScParenthesisedExpr, ScTypedExpression}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameter
import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.{FunctionType, TupleType}
import org.jetbrains.plugins.scala.lang.psi.types.api.FunctionType.isFunctionType
import org.jetbrains.plugins.scala.project.ProjectPsiElementExt
import org.jetbrains.plugins.scala.util.SAMUtil.toSAMType

object ScFunctionExprAnnotator extends ElementAnnotator[ScFunctionExpr] {

  override def annotate(literal: ScFunctionExpr, typeAware: Boolean)
                       (implicit holder: ScalaAnnotationHolder): Unit =
    annotateImpl(literal, typeAware)


  private[annotator] def annotateImpl(literal: ScFunctionExpr, typeAware: Boolean, fromBlock: Boolean = false)
                                     (implicit holder: ScalaAnnotationHolder): Unit = {
    if (!typeAware || isImplicitlyConverted(literal))
      return

    if (!fromBlock && annotatedByBlockExpr(literal))
      return

    val parameters = literal.parameters

    val problemWithParameters = expectedFunctionTypeOf(literal).exists {
      case FunctionType(_, expectedTypes) =>
        missingParametersIn(literal, parameters, expectedTypes) ||
          tooManyParametersIn(literal, parameters, expectedTypes) ||
          parameterTypeMismatchIn(literal, parameters, expectedTypes)
      case _ => false
    } || missingParameterTypeIn(parameters)

    if (!problemWithParameters) {
      resultTypeMismatchIn(literal)
    }
  }

  private def isImplicitlyConverted(literal: ScFunctionExpr) =
    (literal.`type`().toOption, literal.getTypeWithoutImplicits().toOption) match {
      case (Some(t1), Some(t2)) if t1.equiv(t2) => false
      case _ => true
    }

  private def expectedFunctionTypeOf(literal: ScFunctionExpr) = literal.expectedType() match {
    case Some(t @ FunctionType(_, _)) => Some(t)
    case Some(t) => toSAMType(t, literal)
    case _ => None
  }

  private def missingParametersIn(literal: ScFunctionExpr, parameters: Iterable[ScParameter], expectedTypes: Iterable[ScType])
                                 (implicit holder: ScalaAnnotationHolder): Boolean = {
    val missing = parameters.size < expectedTypes.size
    if (missing) {
      val startElement = if (parameters.isEmpty) literal.leftParen.getOrElse(literal.params) else parameters.last

      val errorRange = startElement.nextElementNotWhitespace
        .map(nextElement => new TextRange(startElement.getTextRange.getEndOffset - 1, nextElement.getTextOffset + 1))
        .getOrElse(startElement.getTextRange)

      val message = (if (expectedTypes.size - parameters.size == 1) "Missing parameter: " else "Missing parameters: ") +
        expectedTypes.drop(parameters.size).map(_.presentableText(literal)).mkString(", ")

      holder.createErrorAnnotation(errorRange, message)
    }
    missing
  }

  private def untupledExpectedType(
    ctx:           PsiElement,
    parameters:    Iterable[ScParameter],
    expectedTypes: Iterable[ScType]
  ): Option[Seq[ScType]] =
    if (ctx.isInScala3Module && expectedTypes.size == 1)
      expectedTypes.head match {
        case TupleType(components) if components.size == parameters.size => Option(components)
        case _                                                           => None
      }
    else None

  private def tooManyParametersIn(
    literal: ScFunctionExpr,
    parameters: Seq[ScParameter],
    expectedTypes: Iterable[ScType]
  )(implicit
    holder: ScalaAnnotationHolder
  ): Boolean = {
    val tooMany =
      parameters.size > expectedTypes.size &&
        untupledExpectedType(literal, parameters, expectedTypes).isEmpty

    if (tooMany) {
      val message = ScalaBundle.message("annotator.error.too.many.parameters")
      if (!literal.hasParentheses) {
        holder.createErrorAnnotation(parameters.head, message)
      } else {
        val firstExcessiveParameter = parameters(expectedTypes.size)

        val range = new TextRange(
          firstExcessiveParameter.prevElementNotWhitespace.getOrElse(literal.params).getTextRange.getEndOffset - 1,
          firstExcessiveParameter.getTextOffset + 1)

        holder.createErrorAnnotation(range, message)
      }
    }
    tooMany
  }

  private def parameterTypeMismatchIn(
    ctx:           PsiElement,
    parameters:    Iterable[ScParameter],
    expectedTypes: Iterable[ScType]
  )(implicit
    holder: ScalaAnnotationHolder
  ): Boolean = {
    var typeMismatch                = false
    val expectedTypesAfterUntupling = untupledExpectedType(ctx, parameters, expectedTypes).getOrElse(expectedTypes)

    parameters.zip(expectedTypesAfterUntupling).iterator.takeWhile(_ => !typeMismatch).foreach {
      case (parameter, expectedType) =>
        parameter.typeElement.flatMap(_.`type`().toOption).filter(!expectedType.conforms(_)).foreach { _ =>
          val message = ScalaBundle.message(
            "type.mismatch.expected",
            expectedType.presentableText(parameter),
            parameter.typeElement.get.getText
          )

          val ranges = mismatchRangesIn(parameter.typeElement.get, expectedType)(parameter)
          ranges.foreach(holder.createErrorAnnotation(_, message, ReportHighlightingErrorQuickFix))
          typeMismatch = true
        }
    }
    typeMismatch
  }

  private def missingParameterTypeIn(parameters: Seq[ScParameter])
                                    (implicit holder: ScalaAnnotationHolder): Boolean = {
    var missing = false
    parameters.iterator.takeWhile(_ => !missing).foreach { parameter =>
      if (parameter.typeElement.isEmpty && parameter.expectedParamType.isEmpty) {
        holder.createErrorAnnotation(parameter, ScalaBundle.message("annotator.error.missing.parameter.type"))
        missing = true
      }
    }
    missing
  }

  private def resultTypeMismatchIn(literal: ScFunctionExpr)(implicit holder: ScalaAnnotationHolder): Unit = {
    val typeAscription = literal match {
      case Parent((_: ScParenthesisedExpr | _: ScBlockExpr) && Parent(ta: ScTypedExpression)) => Some(ta)
      case _ => None
    }

    typeAscription match {
      case Some(ascription) => ScTypedExpressionAnnotator.doAnnotate(ascription)
      case None =>
        val inMultilineBlock = literal match {
          case Parent(b: ScBlockExpr) => b.textContains('\n')
          case _ => false
        }
        if (!inMultilineBlock && literal.expectedType().exists(isFunctionType)) {
          literal.result.foreach(checkExpressionType(_, typeAware = true, fromFunctionLiteral = true))
        } else {
          checkExpressionType(literal, typeAware = true, fromFunctionLiteral = true)
        }
    }
  }
}
