package org.jetbrains.plugins.scala.compiler.charts.ui

sealed trait HAlign

object HAlign {

  final case object Center extends HAlign
  final case object Left extends HAlign
  final case object Right extends HAlign
}
