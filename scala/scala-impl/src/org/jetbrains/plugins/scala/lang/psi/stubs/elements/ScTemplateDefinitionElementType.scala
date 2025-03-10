package org.jetbrains.plugins.scala.lang.psi.stubs.elements

import com.intellij.lang.Language
import com.intellij.psi.impl.java.stubs.index.JavaStubIndexKeys
import com.intellij.psi.stubs.{IndexSink, StubElement, StubInputStream, StubOutputStream}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiClass, PsiElement}
import com.intellij.util.ArrayUtil.EMPTY_STRING_ARRAY
import org.jetbrains.plugins.scala.ScalaLanguage
import org.jetbrains.plugins.scala.extensions._
import org.jetbrains.plugins.scala.lang.psi.api.base.ScAnnotation
import org.jetbrains.plugins.scala.lang.psi.api.statements.ScFunctionDefinition
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef._
import org.jetbrains.plugins.scala.lang.psi.stubs.{ScGivenStub, ScImplicitStub, ScTemplateDefinitionStub}
import org.jetbrains.plugins.scala.lang.psi.stubs.impl.ScTemplateDefinitionStubImpl
import org.jetbrains.plugins.scala.lang.psi.stubs.index.ScalaIndexKeys
import org.jetbrains.plugins.scala.lang.refactoring.util.ScalaNamesUtil

abstract class ScTemplateDefinitionElementType[TypeDef <: ScTemplateDefinition](debugName: String,
                                                                                language: Language = ScalaLanguage.INSTANCE)
  extends ScStubElementType.Impl[ScTemplateDefinitionStub[TypeDef], TypeDef](debugName, language) {

  override final def serialize(stub: ScTemplateDefinitionStub[TypeDef], dataStream: StubOutputStream): Unit = {
    dataStream.writeName(stub.getName)
    dataStream.writeName(stub.getQualifiedName)
    dataStream.writeName(stub.getSourceFileName)
    dataStream.writeName(stub.javaName)
    dataStream.writeName(stub.javaQualifiedName)
    dataStream.writeOptionName(stub.additionalJavaName)
    dataStream.writeBoolean(stub.isPackageObject)
    dataStream.writeBoolean(stub.isDeprecated)
    dataStream.writeBoolean(stub.isLocal)
    dataStream.writeBoolean(stub.isVisibleInJava)
    dataStream.writeBoolean(stub.isImplicitObject)
    dataStream.writeOptionName(stub.implicitConversionParameterClass)
    dataStream.writeNames(stub.implicitClassNames)
    dataStream.writeBoolean(stub.isTopLevel)
    dataStream.writeOptionName(stub.topLevelQualifier)
    dataStream.writeBoolean(stub.isGiven)
    dataStream.writeNames(stub.givenClassNames)
  }

  override final def deserialize(dataStream: StubInputStream, parentStub: StubElement[_ <: PsiElement]) =
    new ScTemplateDefinitionStubImpl(
      parentStub,
      this,
      nameRef                          = dataStream.readNameString,
      getQualifiedName                 = dataStream.readNameString,
      getSourceFileName                = dataStream.readNameString,
      javaName                         = dataStream.readNameString,
      javaQualifiedName                = dataStream.readNameString,
      additionalJavaName               = dataStream.readOptionName,
      isPackageObject                  = dataStream.readBoolean,
      isDeprecated                     = dataStream.readBoolean,
      isLocal                          = dataStream.readBoolean,
      isVisibleInJava                  = dataStream.readBoolean,
      isImplicitObject                 = dataStream.readBoolean,
      implicitConversionParameterClass = dataStream.readOptionName,
      implicitClassNames               = dataStream.readNames,
      isTopLevel                       = dataStream.readBoolean,
      topLevelQualifier                = dataStream.readOptionName,
      isGiven                          = dataStream.readBoolean,
      givenClassNames                  = dataStream.readNames,
    )

  override final def createStubImpl(definition: TypeDef,
                                    parent: StubElement[_ <: PsiElement]): ScTemplateDefinitionStub[TypeDef] = {
    val fileName = definition.containingVirtualFile.map(_.getName).orNull

    val (isDeprecated, additionalJavaName, isPackageObject) = definition match {
      case typeDefinition: ScTypeDefinition =>
        val annotations = definition.getModifierList match {
          case null => Array.empty
          case list => list.getAnnotations
        }

        val isScalaDeprecated = annotations.exists {
          case annotation: ScAnnotation =>
            val text = annotation.constructorInvocation.typeElement.getText
            text == "deprecated" || text == "scala.deprecated"
          case _ => false
        }

        (
          isScalaDeprecated,
          typeDefinition.additionalClassJavaName,
          typeDefinition.isPackageObject
        )
      case _ =>
        (false, None, false)
    }

    val isLocal = definition.containingClass == null &&
      PsiTreeUtil.getContextOfType(definition, classOf[ScTemplateDefinition], classOf[ScFunctionDefinition]) != null

    val isVisibleInJava = definition.parents.forall {
      case o: ScObject => !o.isPackageObject
      case _ => true
    }

    val isImplicit = definition.hasModifierPropertyScala("implicit")

    val (isImplicitObject, implicitConversionParamClass, implicitClassNames) = definition match {
      case obj: ScObject if isImplicit => (true, None, ScImplicitStub.superClassNames(obj))
      case c: ScClass if isImplicit    => (false, ScImplicitStub.conversionParamClass(c), EMPTY_STRING_ARRAY)
      case _                           => (false, None, EMPTY_STRING_ARRAY)
    }

    val (isTopLevel, topLevelQualifier) = definition match {
      case member: ScMember => (member.isTopLevel, member.topLevelQualifier)
      case _                => (false, None)
    }

    val (isGivenDefinition, givenDefinitionClassNames) = definition match {
      case givenDef: ScGivenDefinition => (true, ScGivenStub.givenDefinitionClassNames(givenDef))
      case _                           => (false, EMPTY_STRING_ARRAY)
    }

    new ScTemplateDefinitionStubImpl(
      parent,
      this,
      nameRef                          = definition.name,
      getQualifiedName                 = definition.qualifiedName,
      getSourceFileName                = fileName,
      javaName                         = definition.getName,
      javaQualifiedName                = definition.getQualifiedName,
      additionalJavaName               = additionalJavaName,
      isPackageObject                  = isPackageObject,
      isDeprecated                     = isDeprecated,
      isLocal                          = isLocal,
      isVisibleInJava                  = isVisibleInJava,
      isImplicitObject                 = isImplicitObject,
      implicitConversionParameterClass = implicitConversionParamClass,
      implicitClassNames               = implicitClassNames,
      isTopLevel                       = isTopLevel,
      topLevelQualifier                = topLevelQualifier,
      isGiven                          = isGivenDefinition,
      givenClassNames                  = givenDefinitionClassNames,
    )
  }

  override final def indexStub(stub: ScTemplateDefinitionStub[TypeDef], sink: IndexSink): Unit = {

    val scalaName = stub.getName
    val javaName = stub.javaName

    if (javaName != null) {
      if (stub.isVisibleInJava) {
        sink.occurrence(JavaStubIndexKeys.CLASS_SHORT_NAMES, javaName)
      }
      sink.occurrence(ScalaIndexKeys.ALL_CLASS_NAMES, javaName)
    }

    if (scalaName != null) {
      sink.occurrence(ScalaIndexKeys.SHORT_NAME_KEY, scalaName)

      if (scalaName != javaName || !stub.isVisibleInJava) {
        sink.occurrence(ScalaIndexKeys.NOT_VISIBLE_IN_JAVA_SHORT_NAME_KEY, scalaName)
      }
    }

    val additionalNames = stub.additionalJavaName
    for (name <- additionalNames) {
      sink.occurrence(ScalaIndexKeys.ALL_CLASS_NAMES, name)
    }
    val javaFqn = stub.javaQualifiedName
    if (javaFqn != null && !stub.isLocal && stub.isVisibleInJava) {
      sink.occurrence[PsiClass, CharSequence](JavaStubIndexKeys.CLASS_FQN, javaFqn)
      val i = javaFqn.lastIndexOf(".")
      val pack =
        if (i == -1) ""
        else javaFqn.substring(0, i)
      sink.occurrence(ScalaIndexKeys.JAVA_CLASS_NAME_IN_PACKAGE_KEY, pack)
    }

    val fqn = ScalaNamesUtil.cleanFqn(stub.getQualifiedName)
    if (fqn != null && !stub.isLocal) {
      sink.occurrence[PsiClass, java.lang.Integer](ScalaIndexKeys.FQN_KEY, fqn.hashCode)
      val i = fqn.lastIndexOf(".")
      val pack =
        if (i == -1) ""
        else fqn.substring(0, i)
      sink.occurrence(ScalaIndexKeys.CLASS_NAME_IN_PACKAGE_KEY, pack)
      if (stub.isImplicitObject) sink.occurrence(ScalaIndexKeys.IMPLICIT_OBJECT_KEY, pack)

      if (stub.isTopLevel && stub.implicitConversionParameterClass.nonEmpty)
        sink.occurrence(ScalaIndexKeys.TOP_LEVEL_IMPLICIT_CLASS_BY_PKG_KEY, pack)

      stub.indexImplicits(sink)
      stub.indexGivens(sink)
    }
    if (stub.isPackageObject) {
      val packageName = fqn.stripSuffix(".package")
      val shortName = {
        val index = packageName.lastIndexOf('.')
        if (index < 0) packageName else packageName.substring(index + 1, packageName.length)
      }
      sink.occurrence[PsiClass, java.lang.Integer](ScalaIndexKeys.PACKAGE_OBJECT_KEY, packageName.hashCode)
      sink.occurrence[PsiClass, String](ScalaIndexKeys.PACKAGE_OBJECT_SHORT_NAME_KEY, shortName)
    }
  }
}
