package org.jetbrains.plugins.scala.lang.parser.parsing.xml

import com.intellij.psi.xml.XmlTokenType
import org.jetbrains.plugins.scala.lang.lexer.ScalaXmlTokenTypes
import org.jetbrains.plugins.scala.lang.parser.{ErrMsg, ScalaElementType}
import org.jetbrains.plugins.scala.lang.parser.parsing.ParsingRule
import org.jetbrains.plugins.scala.lang.parser.parsing.builder.ScalaPsiBuilder

/*
 * ETag ::= </ Name [S] >
 */

object ETag extends ParsingRule {
  override def parse(implicit builder: ScalaPsiBuilder): Boolean = {
    val tagMarker = builder.mark()
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_END_TAG_START =>
        builder.advanceLexer()
      case _ =>
        tagMarker.drop()
        return false
    }
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_NAME =>
        builder.advanceLexer()
      case _ => builder error ErrMsg("xml.name.expected")
    }
    builder.getTokenType match {
      case XmlTokenType.XML_WHITE_SPACE => builder.advanceLexer()
      case _ =>
    }
    builder.getTokenType match {
      case ScalaXmlTokenTypes.XML_TAG_END =>
        builder.advanceLexer()
      case _ =>
        builder error ErrMsg("xml.tag.end.expected")
    }
    tagMarker.done(ScalaElementType.XML_END_TAG)
    true
  }
}