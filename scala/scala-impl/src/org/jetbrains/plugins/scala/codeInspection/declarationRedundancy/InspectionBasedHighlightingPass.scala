package org.jetbrains.plugins.scala.codeInspection.declarationRedundancy

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.impl._
import com.intellij.codeInsight.daemon.impl.analysis.HighlightingLevelManager
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.ex.InspectionProfileImpl
import com.intellij.codeInspection.{LocalQuickFixAsIntentionAdapter, ProblemDescriptorUtil}
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi._
import org.jetbrains.plugins.scala.codeInspection.suppression.ScalaInspectionSuppressor
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.jetbrains.plugins.scala.lang.psi.api.ScalaFile

import java.util.Collections
import scala.collection.mutable
import scala.jdk.CollectionConverters._

abstract class InspectionBasedHighlightingPass(file: ScalaFile, document: Option[Document], inspection: HighlightingPassInspection)
  extends TextEditorHighlightingPass(
    file.getProject,
    document.orNull,
    //NOTE: this parameter was set to `false` among other changes within SCL-15476.
    //However in some tests we need extra intention pass to be run after this pass
    //This is needed when we want to truly test the order of actions (intention actions, quick fixes, etc...)
    //For example see: MakePrivateQuickFixIsAboveAddTypeAnnotationQuickFixTest
    //I decided to leave `runIntentionPassAfter=false` in production just because I am not aware of any issues with it in prod
    //And I am not sure whether making it `true` always can lead to some regressions
    /*runIntentionPassAfter = */ InspectionBasedHighlightingPass.isUnitTest
//    /*runIntentionPassAfter = */ InspectionBasedHighlightingPass.isUnitTest
  ) {

  private val highlightInfos = mutable.Buffer[HighlightInfo]()

  private val inspectionSuppressor = new ScalaInspectionSuppressor

  private def profile: InspectionProfileImpl = InspectionProjectProfileManager.getInstance(myProject).getCurrentProfile

  def isEnabled(element: PsiElement): Boolean = {
    profile.isToolEnabled(highlightKey, element) && !inspectionSuppressor.isSuppressedFor(element, inspection.getShortName)
  }

  def getSeverity: HighlightSeverity = {
    val severity = Option(highlightKey).map { key =>
      val errorLevel = profile.getErrorLevel(key, file)
      errorLevel.getSeverity
    }
    severity.getOrElse(HighlightSeverity.WEAK_WARNING)
  }

  def highlightKey: HighlightDisplayKey = HighlightDisplayKey.find(inspection.getShortName)

  override def doCollectInformation(progress: ProgressIndicator): Unit = {
    if (shouldHighlightFile) {
      highlightInfos.clear()
      processFile()
    }
  }

  private def shouldHighlightFile: Boolean = HighlightingLevelManager.getInstance(file.getProject).shouldInspect(file)

  override def doApplyInformationToEditor(): Unit = {
    if (shouldHighlightFile) {
      UpdateHighlightersUtil.setHighlightersToEditor(file.getProject, getDocument, 0, file.getTextLength,
        highlightInfos.asJavaCollection, getColorsScheme, getId)
    } else {
      UpdateHighlightersUtil.setHighlightersToEditor(file.getProject, getDocument, 0, file.getTextLength,
        Collections.emptyList(), getColorsScheme, getId)
    }
  }

  private def processFile(): Unit = {
    if (isEnabled(file)) {
      val infos: Iterator[ProblemInfo] = file.depthFirst().filter {
        inspection.shouldProcessElement
      }.filter {
        isEnabled
      }.flatMap {
        inspection.invoke(_, isOnTheFly = true)
      }
      highlightInfos ++= infos.map { info: ProblemInfo =>
        val range = info.element.getTextRange
        val severity: HighlightSeverity = getSeverity
        val infoType: HighlightInfoType = HighlightInfo.convertSeverity(severity)
        val textAttributes: TextAttributesKey = profile.getEditorAttributes(highlightKey.toString, file)

        val highlightingInfoBuilder = HighlightInfo
          .newHighlightInfo(infoType)
          .severity(severity)
          .range(range)
          .descriptionAndTooltip(info.message)
        if (textAttributes != null) {
          highlightingInfoBuilder.textAttributes(textAttributes)
        }
        val highlightInfo = highlightingInfoBuilder.create()

        info.fixes.foreach { fix =>
          val action = fix match {
            case intention: IntentionAction =>
              intention
            case _ =>
              val problemDescriptor = ProblemDescriptorUtil.toProblemDescriptor(file, highlightInfo)
              new LocalQuickFixAsIntentionAdapter(fix, problemDescriptor)
          }
          highlightInfo.registerFix(action, null, info.message, range, highlightKey)
        }

        highlightInfo
      }
    }
  }

  override def getInfos: java.util.List[HighlightInfo] = highlightInfos.asJava
}

object InspectionBasedHighlightingPass {
  private val isUnitTest = ApplicationManager.getApplication.isUnitTestMode
}
