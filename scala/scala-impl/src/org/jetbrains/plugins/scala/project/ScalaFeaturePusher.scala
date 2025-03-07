package org.jetbrains.plugins.scala.project

import com.intellij.openapi.fileTypes.{FileType, LanguageFileType}
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.roots.impl.PushedFilePropertiesUpdater
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.FileAttribute
import com.intellij.psi.PsiFile
import com.intellij.util.indexing.IndexingDataKeys
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.project.ScalaFeaturePusher.{SerializedScalaFeatures, isScalaLike}

import scala.annotation.nowarn

/**
 * Used [[com.intellij.openapi.roots.impl.JavaLanguageLevelPusher]] as a reference
 */
class ScalaFeaturePusher extends com.intellij.FileIntPropertyPusher[SerializedScalaFeatures] {

  override def getAttribute: FileAttribute = ScalaFeaturePusher.Persistence

  override def toInt(features: SerializedScalaFeatures): Int = features

  override def fromInt(value: Int): SerializedScalaFeatures = value

  override def propertyChanged(project: Project, fileOrDir: VirtualFile, actualProperty: SerializedScalaFeatures): Unit = {
    PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(fileOrDir, isScalaLike)
    fileOrDir.getChildren
      .iterator
      .filter(c => !c.isDirectory && isScalaLike(c))
      .foreach { child =>
        PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(child): @nowarn("cat=deprecation")
      }
  }

  override def getFileDataKey: Key[SerializedScalaFeatures] = ScalaFeaturePusher.key

  override def pushDirectoriesOnly(): Boolean = true

  override def getDefaultValue: SerializedScalaFeatures = ScalaFeatures.default.serializeToInt

  override def getImmediateValue(module: Module): SerializedScalaFeatures = module.features.serializeToInt

  override def getImmediateValue(project: Project, file: VirtualFile): SerializedScalaFeatures = null

  override def acceptsDirectory(file: VirtualFile, project: Project): Boolean =
    ProjectFileIndex.getInstance(project).isInSourceContent(file)
}

object ScalaFeaturePusher {
  type SerializedScalaFeatures = Integer

  def getFeatures(file: PsiFile): Option[ScalaFeatures] =
    Option(file.getContainingDirectory)
      .flatMap(dir => getFeatures(dir.getVirtualFile))
      .orElse {
        // while indexing the parser will get a dummy file that only references the real file
        Option(file.getUserData(IndexingDataKeys.VIRTUAL_FILE))
          .flatMap(vFile => if (vFile.isDirectory) Some(vFile) else Option(vFile.getParent))
          .flatMap(getFeatures)
      }

  def getFeatures(file: VirtualFile): Option[ScalaFeatures] =
    Option(file.getUserData(key)).map(ScalaFeatures.deserializeFromInt(_))

  private val Persistence = new FileAttribute("scala_pushed_feature_persistence", ScalaFeatures.version, true)

  @inline
  private def isScalaLike(file: VirtualFile): Boolean =
    isScalaLike(file.getFileType)

  private def isScalaLike(fileType: FileType): Boolean =
    fileType match {
      case lft: LanguageFileType => lft.getLanguage.isKindOf(ScalaLanguage.INSTANCE)
      case _                     => false
    }

  private val key: Key[SerializedScalaFeatures] = Key.create("Pushed Scala Features")
}
