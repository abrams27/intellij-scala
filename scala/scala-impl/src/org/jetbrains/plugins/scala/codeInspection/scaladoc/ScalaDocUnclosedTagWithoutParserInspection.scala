package org.jetbrains.plugins.scala.codeInspection.scaladoc

import com.intellij.codeInspection._
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import org.apache.commons.lang.StringUtils
import org.jetbrains.plugins.scala.ScalaBundle
import org.jetbrains.plugins.scala.codeInspection.{AbstractFixOnPsiElement, ScalaInspectionBundle}
import org.jetbrains.plugins.scala.lang.psi.api.ScalaElementVisitor
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.ScalaDocTokenType
import org.jetbrains.plugins.scala.lang.scaladoc.lexer.docsyntax.ScalaDocSyntaxElementType
import org.jetbrains.plugins.scala.lang.scaladoc.parser.parsing.MyScaladocParsing
import org.jetbrains.plugins.scala.lang.scaladoc.psi.api.ScDocSyntaxElement

class ScalaDocUnclosedTagWithoutParserInspection extends LocalInspectionTool {
  override def isEnabledByDefault: Boolean = true

  override def getDisplayName: String = ScalaInspectionBundle.message("display.name.unclosed.tag")

  override def buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = {
    new ScalaElementVisitor {
      override def visitWikiSyntax(s: ScDocSyntaxElement): Unit = {
        val firstElementType = s.getFirstChild.getNode.getElementType
        if (!ScalaDocSyntaxElementType.canClose(firstElementType,
          s.getLastChild.getNode.getElementType) &&
          firstElementType != ScalaDocTokenType.DOC_HEADER && firstElementType != ScalaDocTokenType.VALID_DOC_HEADER) {

          holder.registerProblem(holder.getManager.createProblemDescriptor(s.getFirstChild, getDisplayName, true,
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly, new ScalaDocEscapeTagQuickFix(s)))
        }
      }
    }
  }
}


class ScalaDocEscapeTagQuickFix(s: ScDocSyntaxElement)
        extends AbstractFixOnPsiElement(ScalaBundle.message("replace.tag.with.esc.seq"), s) {

  override def getFamilyName: String = FamilyName

  override protected def doApplyFix(syntElem: ScDocSyntaxElement)
                                   (implicit project: Project): Unit = {
    val replaceText = if (syntElem.getFirstChild.getText.contains("=")) {
      StringUtils.repeat(MyScaladocParsing.escapeSequencesForWiki("="), syntElem.getFirstChild.getText.length())
    } else {
      MyScaladocParsing.escapeSequencesForWiki(syntElem.getFirstChild.getText)
    }
    val doc = FileDocumentManager.getInstance().getDocument(syntElem.getContainingFile.getVirtualFile)
    val range: TextRange = syntElem.getFirstChild.getTextRange
    doc.replaceString(range.getStartOffset, range.getEndOffset, replaceText)
  }
}