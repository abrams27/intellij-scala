package org.jetbrains.plugins.scala
package codeInspection.typeChecking

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import org.jetbrains.plugins.scala.codeInspection.ScalaInspectionTestBase

class TypeCheckCanBeMatchInspectionTest extends ScalaInspectionTestBase {

  import CodeInsightTestFixture.CARET_MARKER

  override protected val classOfInspection: Class[_ <: LocalInspectionTool] =
    classOf[TypeCheckCanBeMatchInspection]

  override protected val description: String = TypeCheckCanBeMatchInspection.inspectionName

  val hint: String = TypeCheckCanBeMatchInspection.inspectionName

  private def testQuickFix(text: String, result: String): Unit = {
    testQuickFix(text, result, hint)
  }

  def test_1(): Unit = {
    val selected =
      """
        |val x = 0
        |if (x.isInstanceOf[Int]) {
        |  x.toString
        |}""".stripMargin
    checkTextHasNoErrors(selected)
  }

  def test_2(): Unit = {
    val selected =
      s"""val x = 0
         |if (${START}x.isInstanceOf[Int]$END) {
         |  x.asInstanceOf[Int].toString
         |  println(x.asInstanceOf[Int])
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      """val x = 0
        |if (x.isInstanc<caret>eOf[Int]) {
        |  x.asInstanceOf[Int].toString
        |  println(x.asInstanceOf[Int])
        |}""".stripMargin
    val result =
      """val x = 0
        |x match {
        |  case i: Int =>
        |    i.toString
        |    println(i)
        |  case _ =>
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_3(): Unit = {
    val selected =
      s"""val x = 0
         |if (${START}x.isInstanceOf[Int]$END) {
         |  val y = x.asInstanceOf[Int]
         |  x.asInstanceOf[Int].toString
         |  println(y)
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      """val x = 0
        |if (x.isInstanc<caret>eOf[Int]) {
        |  val y = x.asInstanceOf[Int]
        |  x.asInstanceOf[Int].toString
        |  println(y)
        |}""".stripMargin
    val result =
      """val x = 0
        |x match {
        |  case y: Int =>
        |    y.toString
        |    println(y)
        |  case _ =>
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_4(): Unit = {
    val selected =
      s"""val x = 0
         |if (${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Int] == 1) {
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      """val x = 0
        |if (<caret>x.isInstanceOf[Int] && x.asInstanceOf[Int] == 1) {
        |  val y = x.asInstanceOf[Int]
        |  println(y)
        |}""".stripMargin
    val result =
      """val x = 0
        |x match {
        |  case y: Int if y == 1 =>
        |    println(y)
        |  case _ =>
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_5(): Unit = {
    val selected =
      s"""val x = 0
         |if (x > 0 && (${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Int] == 1)) {
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |}""".stripMargin
    checkTextHasNoErrors(selected)
  }

  def test_6(): Unit = {
    val selected =
      s"""val x = 0
         |if (${START}x.isInstanceOf[Int]$END) {
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |} else if (x.isInstanceOf[Long]) {
         |  println(x)
         |} else println()""".stripMargin
    checkTextHasError(selected)

    val text =
      """val x = 0
        |if (<caret>x.isInstanceOf[Int]) {
        |  val y = x.asInstanceOf[Int]
        |  println(y)
        |} else if (x.isInstanceOf[Long]) {
        |  println(x)
        |} else println()""".stripMargin
    val result =
      """val x = 0
        |x match {
        |  case y: Int =>
        |    println(y)
        |  case _: Long =>
        |    println(x)
        |  case _ => println()
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_7(): Unit = {
    val selected =
      s"""val x = 0
         |if (${START}x.isInstanceOf[Int]$END && x.asInstanceOf[Long] == 1) {
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |} else if (x.isInstanceOf[Long]) {
         |  val y = x.asInstanceOf[Int]
         |  println(y)
         |} else {
         |  println(x)
         |  println()
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      """val x = 0
        |if (<caret>x.isInstanceOf[Int] && x.asInstanceOf[Long] == 1) {
        |  val y = x.asInstanceOf[Int]
        |  println(y)
        |} else if (x.isInstanceOf[Long]) {
        |  val y = x.asInstanceOf[Int]
        |  println(y)
        |} else {
        |  println(x)
        |  println()
        |}""".stripMargin
    val result =
      """val x = 0
        |x match {
        |  case y: Int if x.asInstanceOf[Long] == 1 =>
        |    println(y)
        |  case _: Long =>
        |    val y = x.asInstanceOf[Int]
        |    println(y)
        |  case _ =>
        |    println(x)
        |    println()
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_8a(): Unit = {
    val selected =
      s"""val x1 = 0
         |val x2 = 0
         |if (${START}x1.isInstanceOf[Int]$END && x2.isInstanceOf[Int]) {
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      """val x1 = 0
        |val x2 = 0
        |if (x<caret>1.isInstanceOf[Int] && x2.isInstanceOf[Int]) {
        |  val y1 = x1.asInstanceOf[Int]
        |  val y2 = x2.asInstanceOf[Int]
        |  println(y1 + y2)
        |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
        |  val y1 = x1.asInstanceOf[Int]
        |  val y2 = x2.asInstanceOf[Int]
        |  println(y1 + y2)
        |}""".stripMargin
    val result =
      """val x1 = 0
        |val x2 = 0
        |x1 match {
        |  case y1: Int if x2.isInstanceOf[Int] =>
        |    val y2 = x2.asInstanceOf[Int]
        |    println(y1 + y2)
        |  case _: Long if x2.isInstanceOf[Long] =>
        |    val y1 = x1.asInstanceOf[Int]
        |    val y2 = x2.asInstanceOf[Int]
        |    println(y1 + y2)
        |  case _ =>
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_8b(): Unit = {
    val selected =
      s"""val x1 = 0
         |val x2 = 0
         |if (x1.isInstanceOf[Int] && x2.isI${CARET_MARKER}nstanceOf[Int]) {
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |} else if (x1.isInstanceOf[Long] && x2.isInstanceOf[Long]) {
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |}""".stripMargin
    checkTextHasNoErrors(selected)
  }

  def test_8c(): Unit = {
    val selected =
      s"""val x1 = 0
         |val x2 = 0
         |if (x1.isInstanceOf[Int] && x2.isInstanceOf[Int]) {
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |} else if (x1.isIn${CARET_MARKER}stanceOf[Long] && x2.isInstanceOf[Long]) {
         |  val y1 = x1.asInstanceOf[Int]
         |  val y2 = x2.asInstanceOf[Int]
         |  println(y1 + y2)
         |}""".stripMargin
    checkTextHasNoErrors(selected)
  }

  def test_9(): Unit = {
    val selected =
      s"""val x = 0
         |val i = 0
         |if (${START}x.isInstanceOf[Int]$END) {
         |  x.asInstanceOf[Int].toString
         |  println(x.asInstanceOf[Int])
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      """val x = 0
        |val i = 0
        |if (x.isInstanc<caret>eOf[Int]) {
        |  x.asInstanceOf[Int].toString
        |  println(x.asInstanceOf[Int])
        |}""".stripMargin
    val result =
      """val x = 0
        |val i = 0
        |x match {
        |  case i1: Int =>
        |    i1.toString
        |    println(i1)
        |  case _ =>
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_10(): Unit = {
    val selected =
      s"""val x = 0
         |if (${START}x.isInstanceOf[Int]$END) x else if (x.isInstanceOf[Long]) x else 0""".stripMargin
    checkTextHasError(selected)

    val text =
      """val x = 0
        |if (x.isInstance<caret>Of[Int]) x else if (x.isInstanceOf[Long]) x else 0""".stripMargin
    val result =
      """val x = 0
        |x match {
        |  case _: Int => x
        |  case _: Long => x
        |  case _ => 0
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_11(): Unit = {
    val selected =
      s"""import java.util
         |val foo = (p: AnyVal) => {
         |  if (${START}p.isInstanceOf[util.ArrayList[_]]$END) {}
         |  else if (p.isInstanceOf[scala.util.control.Breaks]) {}
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      """import java.util
        |val foo = (p: AnyVal) => {
        |  if (p.isInstanceOf[util.ArrayList[_]]) {}
        |  else if (p.isInstanceOf[scala.util.control.Breaks]) {}
        |}""".stripMargin
    val result =
      """import java.util
        |val foo = (p: AnyVal) => {
        |  p match {
        |    case _: util.ArrayList[_] =>
        |    case _: scala.util.control.Breaks =>
        |    case _ =>
        |  }
        |}""".stripMargin
    testQuickFix(text, result)
  }

  def test_12(): Unit = {
    val selected =
      s"""def test2(p: AnyVal) {
         |  if (${START}p.isInstanceOf[T forSome {type T <: Number}]$END) {}
         |  else if (p.isInstanceOf[Int]) {}
         |}""".stripMargin
    checkTextHasError(selected)

    val text =
      """def test2(p: AnyVal) {
        |  if (p.isInstanceOf[T forSome {type T <: Number}]) {}
        |  else if (p.isInstanceOf[Int]) {}
        |}""".stripMargin
    val result =
      """def test2(p: AnyVal) {
        |  p match {
        |    case _: (T forSome {type T <: Number}) =>
        |    case _: Int =>
        |    case _ =>
        |  }
        |}""".stripMargin
    testQuickFix(text, result)
  }

}
