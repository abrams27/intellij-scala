package org.jetbrains.plugins.scala.lang.completion

import com.intellij.codeInsight.completion._
import com.intellij.codeInsight.lookup._
import com.intellij.openapi.util.Iconable
import com.intellij.psi._
import com.intellij.psi.filters._
import com.intellij.psi.filters.position.{FilterPattern, LeftNeighbour}
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import org.jetbrains.plugins.scala.codeInspection.targetNameAnnotation.addTargetNameAnnotationIfNeeded
import org.jetbrains.plugins.scala.extensions.{ObjectExt, PsiModifierListOwnerExt}
import org.jetbrains.plugins.scala.lang.completion.filters.modifiers.ModifiersFilter
import org.jetbrains.plugins.scala.lang.psi.TypeAdjuster
import org.jetbrains.plugins.scala.lang.psi.api.statements._
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScClassParameter
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.ScModifierListOwner
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.templates.ScTemplateBody
import org.jetbrains.plugins.scala.lang.psi.api.toplevel.typedef.ScTemplateDefinition
import org.jetbrains.plugins.scala.lang.psi.impl.ScalaPsiElementFactory
import org.jetbrains.plugins.scala.overrideImplement._
import org.jetbrains.plugins.scala.util.TypeAnnotationUtil

import javax.swing.Icon
import scala.annotation.nowarn
import scala.jdk.CollectionConverters._

/**
  * contribute override/implement elements. May be called on override keyword (ove<caret>)
  * or after override/implement element definition (override def <caret>)
  * or on method/field/type name (without override) -> this will add override keyword if there is appropriate setting
  * or inside class parameters [case] class X(ove<caret>, override val/var na<caret>) extends Y
  */
// TODO: support kind of sorter
class ScalaOverrideContributor extends ScalaCompletionContributor {

  import ScalaOverrideContributor._

  extend(CompletionType.BASIC,
    identifierPattern.and(new FilterPattern(new AndFilter(new NotFilter(new LeftNeighbour(new TextContainFilter("override"))), new AndFilter(new NotFilter(new LeftNeighbour(new TextFilter("."))), new ModifiersFilter)))): @nowarn("cat=deprecation"),
    new CompletionProvider[CompletionParameters] {

      override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet): Unit = {
        // one word (simple completion throw generation all possible variants)

        val position = positionFromParameters(parameters)
        val maybeBody = Option(position.getContext.getContext).collect {
          case body: ScTemplateBody => body
        }

        maybeBody.foreach { body =>
          val (clazz, members) = membersOf(body)

          val lookupElements = members.map { member =>
            createLookupElement(member, createText(member, clazz, position, full = true), hasOverride = false)
          }

          resultSet.addAllElements(lookupElements.asJava)
        }
      }
    })

  // completion inside class parameters
  extend(CompletionType.BASIC,
    identifierWithParentPattern(classOf[ScClassParameter]),
    new CompletionProvider[CompletionParameters]() {

      override def addCompletions(completionParameters: CompletionParameters,
                                  processingContext: ProcessingContext,
                                  completionResultSet: CompletionResultSet): Unit = {
        val position = positionFromParameters(completionParameters)
        val hasOverride = position.getParent match {
          case parameter: ScClassParameter => parameter.hasModifierPropertyScala("override")
          case _ => false
        }

        val (clazz, members) = membersOf(position)

        val lookupElements = members.collect { case member @ (_: ScValueMember | _: ScVariableMember) =>
          createLookupElement(
            member,
            createText(member, clazz, position, full = !hasOverride, withBody = false),
            hasOverride
          )
        }

        completionResultSet.addAllElements(lookupElements.asJava)
      }
    })

  /**
    * handle only declarations here
    */
  extend(CompletionType.BASIC,
    identifierPattern.and(new FilterPattern(new AndFilter(new NotFilter(new OrFilter(new LeftNeighbour(new TextContainFilter(".")), new LeftNeighbour(new TextContainFilter(":"))))))): @nowarn("cat=deprecation"),
    new CompletionProvider[CompletionParameters] {

    override def addCompletions(parameters: CompletionParameters, context: ProcessingContext, resultSet: CompletionResultSet): Unit = {
      val position = positionFromParameters(parameters)

      Option(PsiTreeUtil.getContextOfType(position, classOf[ScDeclaration])).collect {
        case ml: ScModifierListOwner => ml
      }.foreach { declaration =>
        val maybeBody = Option(declaration.getContext).collect {
          case body: ScTemplateBody => body
        }

        val filterClass = declaration match {
          case _: PsiMethod => classOf[ScMethodMember]
          case _: ScValueDeclaration => classOf[ScValueMember]
          case _: ScVariableDeclaration => classOf[ScVariableMember]
          case _: ScTypeAlias => classOf[ScAliasMember]
          case _ => classOf[ScalaNamedMember]
        }

        maybeBody.foreach { body =>
          val hasOverride = declaration.hasModifierPropertyScala("override")
          val (clazz, classMembers) = membersOf(body)

          val lookupElements = classMembers.filter(filterClass.isInstance).map { member =>
            createLookupElement(member, createText(member, clazz, position), hasOverride)
          }

          resultSet.addAllElements(lookupElements.asJava)
        }
      }
    }
  })

  private def createText(classMember: ClassMember, clazz: ScTemplateDefinition, position: PsiElement, full: Boolean = false, withBody: Boolean = true): String = {
    import ScalaPsiElementFactory._
    import TypeAnnotationUtil._
    import clazz.projectContext

    val text: String = classMember match {
      case member @ ScMethodMember(signature, isOverride) =>
        val mBody = if (isOverride) ScalaGenerationInfo.getMethodBody(member, clazz, isImplement = false) else "???"
        val fun =
          if (full)
            createOverrideImplementMethod(
              signature,
              needsOverrideModifier = true,
              mBody,
              position,
              withComment = false,
              withAnnotation = false
            )
          else createMethodFromSignature(signature, mBody, position, withComment = false, withAnnotation = false)

        removeTypeAnnotationIfNeeded(fun)
        fun.getText
      case ScAliasMember(element, substitutor, _) =>
        getOverrideImplementTypeSign(element, substitutor, needsOverride = false)
      case member: ScValueMember =>
        val variable = createOverrideImplementVariable(
          member.element,
          member.substitutor,
          needsOverrideModifier = false,
          isVal = true,
          features = position,
          withBody = withBody
        )
        removeTypeAnnotationIfNeeded(variable)
        variable.getText
      case member: ScVariableMember =>
        val variable = createOverrideImplementVariable(
          member.element,
          member.substitutor,
          needsOverrideModifier = false,
          isVal = false,
          features = position,
          withBody = withBody
        )
        removeTypeAnnotationIfNeeded(variable)
        variable.getText
      case _ => " "
    }

    if (!full) text.indexOf(" ", 1) match {
      //remove val, var, def or type
      case -1 => text
      case part => text.substring(part + 1)
    } else if (classMember.is[ScMethodMember]) text else "override " + text
  }

}

object ScalaOverrideContributor {

  import PsiTreeUtil.{getContextOfType, getParentOfType}

  private def membersOf(element: PsiElement) = getParentOfType(element, classOf[ScTemplateDefinition]) match {
    case null => (null, Seq.empty)
    case clazz =>
      import ScalaOIUtil._
      (clazz, getMembersToOverride(clazz) ++ getMembersToImplement(clazz, withSelfType = true))
  }

  private def createLookupElement(member: ClassMember, lookupString: String, hasOverride: Boolean) = {
    import Iconable._

    val lookupObject = member match {
      case member: ScValueOrVariableMember[_] if member.element.is[ScClassParameter] =>
        member.element
      case _ => member.getElement
    }

    val icon = lookupObject.getIcon(ICON_FLAG_VISIBILITY | ICON_FLAG_READ_STATUS)

    LookupElementBuilder.create(lookupObject, lookupString)
      .withIcon(icon)
      .withInsertHandler(new MyInsertHandler(hasOverride))
      .withRenderer(quickRenderer(member))
      .withExpensiveRenderer(expensiveRenderer(member, icon))
  }

  private[this] class MyInsertHandler(hasOverride: Boolean) extends InsertHandler[LookupElement] {

    override def handleInsert(context: InsertionContext, item: LookupElement): Unit = {
      val startElement = context.getFile.findElementAt(context.getStartOffset)
      getContextOfType(startElement, classOf[ScModifierListOwner]) match {
        case member: PsiMember =>
          onMember(member, item)
          ScalaGenerationInfo.positionCaret(context.getEditor, member)
          context.commitDocument()
        case _ =>
      }
    }

    private def onMember(member: ScModifierListOwner with PsiMember, item: LookupElement): Unit = {
      if (!hasOverride && !member.hasModifierPropertyScala("override")) {
        member.setModifierProperty("override")
      }
      addTargetNameAnnotationIfNeeded(member, item.getObject)
      TypeAdjuster.markToAdjust(member)
    }
  }

  private def quickRenderer(member: ClassMember): LookupElementRenderer[LookupElement] = { (_, presentation) =>
    def itemText: String = "override " + (member match {
      case methodMember: ScMethodMember => methodMember.getText + " = {...}"
      case _ => member.name
    })

    presentation.setItemText(itemText)
  }

  private def expensiveRenderer(member: ClassMember, icon: Icon): LookupElementRenderer[LookupElement] = { (element, presentation) =>
    def typeText: String = {
      val maybeType = member match {
        case member: ScalaTypedMember if !member.is[JavaFieldMember] => Some(member.scType)
        case ScAliasMember(definition: ScTypeAliasDefinition, _, _) => definition.aliasedTypeElement.map(_.calcType)
        case _ => None
      }

      maybeType.map(_.presentableText(member.getPsiElement)).getOrElse("")
    }

    element.renderElement(presentation)
    presentation.setIcon(icon)
    presentation.setTypeText(typeText)
  }
}
