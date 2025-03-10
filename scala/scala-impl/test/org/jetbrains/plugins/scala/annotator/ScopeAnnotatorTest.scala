package org.jetbrains.plugins.scala
package annotator

import org.intellij.lang.annotations.Language
import org.jetbrains.plugins.scala.base.SimpleTestCase
import org.jetbrains.plugins.scala.extensions.PsiElementExt
import org.junit.Assert
import org.junit.experimental.categories.Category

@Category(Array(classOf[TypecheckerTests]))
class ScopeAnnotatorTest extends SimpleTestCase {
  // TODO List of explicit clash groups, report scope 
  // ("Foo is already defined as class Foo, object Foo in object Holder")
  // TODO Suggest "rename" quick fix 
  
  final val Header = "class Foo; class Bar; \n "
  
  def testEmpty(): Unit = {
    assertFine("")
  }

  def testSingleDefinition(): Unit = {
    assertFine("class C")
    assertFine("case class C()")
    assertFine("trait T")
    assertFine("object O")
    assertFine("case object O")
    assertFine("package p {}")
    assertFine("val v = null")
    assertFine("val v")
    assertFine("var v = null")
    assertFine("var v")
    assertFine("type A = Any")
    assertFine("type A")
    assertFine("def f {}")
    assertFine("def f")
    assertFine("def f[T] {}")
    assertFine("def f(p: Any) {}")
    assertFine("(p: Any) => ()")
    assertFine("class C[T]")
    assertFine("class C(p: Any)")
    assertFine("class C(val p: Any)")
    assertFine("class C(var p: Any)")
    assertFine("null match { case p => }")
    assertFine("null match { case a @ _ => }")
    assertFine("for(v <- Nil) {}")
    assertFine("for(x <- Nil; v = null) {}")
    assertFine("{ (v: Any) => }")
    assertFine("class X { def this(x: Any) { this() } }")
  }
  
  def testDistinctNames(): Unit = {
    assertFine("class A; class B")
    assertFine("case class A(); case class B()")
    assertFine("trait A; trait B")
    assertFine("object A; object B")
    assertFine("case object A; case object B")
    assertFine("package a {}; package b {}")
    assertFine("val a = null; val b = null")
    assertFine("val a, b = null")
    assertFine("val (a, b) = (null, null)")
    assertFine("val a; val b")
    assertFine("var a = null; var b = null")
    assertFine("var a; var b")
    assertFine("type A = Any; type B = Any")
    assertFine("type A; type B")
    assertFine("def a {}; def b {}")
    assertFine("def a; def b")
    assertFine("def f[A, B] {}")
    assertFine("def f(a: Any, b: Any) {}")
    assertFine("def f(a: Any)(b: Any) {}")
    assertFine("(a: Any, b: Any) => ()")
    assertFine("class C[A, B]")
    assertFine("class C(a: Any, b: Any)")
    assertFine("class C(a: Any)(b: Any)")
    assertFine("class C(val a: Any, val b: Any)")
    assertFine("class C(var a: Any, var b: Any)")
    assertFine("(null, null) match { case (a, b) => }")
    assertFine("(null, null) match { case (a @ _, b @ _) => }")
    assertFine("for(a <- Nil; b <- Nil) {}")
    assertFine("for(x <- Nil; a = null; b = null) {}")
    assertFine("for(x <- Nil; b = null) {}")
    assertFine("{ a: Any, b: Any) => }")
  }

  def testNameClash(): Unit = {
    assertClashes("class C; class C", "C")
    assertClashes("case class C(); case class C()", "C")
    assertClashes("trait T; trait T", "T")
    assertClashes("object O; object O", "O")
    assertClashes("case object O; case object O", "O")
    assertFine("package p {}; package p {}")
    assertClashes("val v = null; val v = null", "v")
    assertClashes("val v, v = null", "v")
    assertClashes("val (v, v) = (null, null)", "v")
    assertClashes("val v; val v", "v")
    assertClashes("var v = null; var v = null", "v")
    assertClashes("var v; var v", "v")
    assertClashes("type A = Any; type A = Any", "A")
    assertClashes("type A; type A", "A")
    assertClashes("def f {}; def f {}", "f")
    assertClashes("def f; def f", "f")
    assertClashes("def f[T, T] {}", "T")
    assertClashes("def f(p: Any, p: Any) {}", "p")
    assertClashes("def f(p: Any)(p: Any) {}", "p")
    assertClashes("(p: Any, p: Any) => ()", "p")
    assertClashes("class C[T, T]", "T")
    assertClashes("class C(p: Any, p: Any)", "p")
    assertClashes("class C(p: Any)(p: Any)", "p")
    assertClashes("class C(val p: Any, val p: Any)", "p")
    assertClashes("class C(var p: Any, var p: Any)", "p")
    assertClashes("(null, null) match { case (p, p) => }", "p")
    assertClashes("(null, null) match { case (a @ _, a @ _) => }", "a")
    assertClashes("(null, null) match { case a @ (a @ _, _) => }", "a")
    assertFine("for(v <- Nil; v <- Nil) {}")
    assertClashes("for(x <- Nil; v = null; v = null) {}", "v")
    assertClashes("for(v <- Nil; v = null) {}", "v")
    assertClashes("{ (v: Any, v: Any) => }", "v")
    assertClashes("class X { def this(x: Any) { this() }; def this(x: Any) { this() } }", "this")
  }
  
  def testUnderscore(): Unit = {
    assertFine("val f: (Any => Unit) = { case _: Foo | _: Bar => }")
  }
  
  // TODO implement processing of distributed package declarations
//  def testNameClashInPackage {
//    assertMatches(messages("package p { class C }; package p { class C }")) { 
//      case Error("C", _) :: Error("C", _) :: Nil => 
//    }
//  }

  def testThreeClashedNames(): Unit = {
    assertMatches(messages("class C; class C; class C")) {
      case Error("C", _) :: Error("C", _) :: Error("C", _) :: Nil =>
    }
    assertMatches(messages("object O; object O; object O")) {
      case Error("O", _) :: Error("O", _) :: Error("O", _) :: Nil =>
    }
  }
  
  def testGroups(): Unit = {
    assertClashes("def f(a: Any, a: Any); def f(b: Any, b: Any)", "f", "a", "b")
  }

  def testScopeInspection(): Unit = {
    assertClashes("{ class C; class C}", "C")
    assertClashes("class X { class C; class C }", "C")
    assertClashes("case class X() { class C; class C }", "C")
    assertClashes("trait X { class C; class C }", "C")
    assertClashes("object X { class C; class C }", "C")
    assertClashes("case object X { class C; class C }", "C")
    assertClashes("package X { class C; class C }", "C")
    assertClashes("def X { class C; class C }", "C")
    assertClashes("val X = { class C; class C }", "C")
    assertClashes("var X = { class C; class C }", "C")
    assertClashes("for(x <- Nil) { class C; class C }", "C")
    assertClashes("if(true) { class C; class C }", "C")
    assertClashes("if(true) {} else { class C; class C }", "C")
    assertClashes("while(true) { class C; class C }", "C")
    assertClashes("do { class C; class C } while(true)", "C")
    assertClashes("try { class C; class C } catch { case _ => }", "C")
    assertClashes("try {} catch { case _ => } finally { class C; class C }", "C")
    assertClashes("new { class C; class C }", "C")
    assertClashes("null match { case _ => class C; class C }", "C")
    assertClashes("val x: (Any => Unit) = { case _ => class C; class C }", "C")
    assertClashes("for(x <- Nil) { class C; class C }", "C")
    assertClashes("{ (x: Any) => class C; class C }", "C")
  }

  def testScopeBoundary(): Unit = {
    assertFine("class C; { class C }")
    assertFine("class C; class X { class C }")
    assertFine("class C; case class X() { class C }")
    assertFine("class C; trait X { class C }")
    assertFine("class C; object X { class C }")
    assertFine("class C; case object X { class C }")
    assertFine("class C; package X { class C }")    
    assertFine("class C; def X { class C }")
    assertFine("class C; val X = { class C }")
    assertFine("class C; var X = { class C }")
    assertFine("val v = null; type X = { val v = null }")
    assertFine("class C; for(x <- Nil) { class C }")    
    assertFine("class C; if(true) { class C } else { class C }")    
    assertFine("class C; while(true) { class C }")    
    assertFine("class C; do { class C } while(true)")    
    assertFine("class C; try { class C } catch { case _ => } finally { class C }")
    assertFine("class C; new { class C }")
    assertFine("class C; for(x <- Nil) { class C }")
    assertFine("class C; val x: C forSome { type C <: Any } = null")
  }

  def testScopeBoundaryParameters(): Unit = {
    assertFine("class C; class X[C]")
    assertFine("val v = null; class X(v: Any)")
    assertFine("class C; def X[C] {}")
    assertFine("val v = null; def X(v: Any) {}")
    assertFine("val v = null; null match { case v => }")
    assertFine("null match { case v =>; case v => }")
    assertFine("val v = null; val x: (Any => Unit) = { case v => }")
    assertFine("val x: (Any => Unit) = { case v =>; case v => }")
    assertFine("val v = null; for(v <- Nil) {}")
    assertFine("val v = null; for(x <- Nil; v = null) {}")
  }
  
  def testSelfBoundary(): Unit = {
    assertFine("class C { class C }")
    assertFine("class C[T] { class T }")
    assertFine("def f { val f = null }")
    assertFine("def x[T] { class T }")
    assertFine("def x(p: Any) { val p = null }")
    assertFine("val v = { val v = null }")
    assertFine("var v = { val v = null }")
    assertFine("for(v <- Nil) { val v = null }")
    assertFine("for(x <- Nil; v = null) { val v = null }")
    assertFine("null match ( case v => val v = null }")
    assertFine("{ (v: Any) => val v = null }")
  }
  
  def testNestedScopes(): Unit = {
    assertFine("class C; { class C; { class C } }")
    assertClashes("class X; { class X; { class C; class C } }", "C")
  }
  
  def testSameLeveScopeBoundary(): Unit = {
    assertFine("{ class C }; { class C }")
  }
  
  def testMembers(): Unit = {
    assertClashes("class C(p: Any) { val p = null }", "p")
    assertMatches(messages("class C(a: Any, b: Any) { val a = null; val b = null }")) {
      case Error("a", _) :: Error("a", _) :: Error("b", _) :: Error("b", _) :: Nil =>
    }
    assertMatches(messages("class C(a: Any)(b: Any) { val b = null; val a = null }")) {
      case Error("a", _) :: Error("a", _) :: Error("b", _) :: Error("b", _) :: Nil =>
    }
    assertClashes("class C(val p: Any) { val p = null }", "p")
    assertClashes("class C(var p: Any) { val p = null }", "p")
    assertClashes("case class C(p: Any) { val p = null }", "p")
    assertClashes("case class C(val p: Any) { val p = null }", "p")
    assertClashes("case class C(var p: Any) { val p = null }", "p")
  }
  
  def testMembersCrossClash(): Unit = {
    assertMatches(messages("class C(p: Any, p: Any) { val p = null }")) {
      case Error("p", _) :: Error("p", _) :: Error("p", _) :: Nil =>
    }
  }  
  
  def testMemberAndIds(): Unit = {
    assertFine("class X(p: Any){ class p }")
    assertClashes("class X(p: Any){ val p = null }", "p")
    assertClashes("class X(p: Any){ object p }", "p")
    assertClashes("class X(p: Any){ case class p() }", "p")
 }

  def testTypesClash(): Unit = {
    assertClashes("class T; trait T", "T")
    assertClashes("class T; type T = Any", "T")
    assertClashes("class T; type T", "T")
    assertClashes("class T; case class T()", "T")
  }
  
  def testTermsClash(): Unit = {
    assertClashes("def v {}; def v", "v")
    assertClashes("def v {}; val v = null", "v")
    assertClashes("def v {}; val v", "v")
    assertClashes("def v {}; var v = null", "v") 
    assertClashes("def v {}; var v", "v") 
    assertClashes("def v {}; object v", "v") 
    assertClashes("def v {}; case class v()", "v")
  }

  def testTypeAndTerms(): Unit = {
    assertFine("class x; val x = null")
    assertFine("class x; val x")
    assertFine("class x; var x = null")
    assertFine("class x; var x")
    assertFine("class x; def x {}")
    assertFine("class x; def x")
    assertFine("class x; object x")
    assertFine("class x; case object x")
  }
  
  def testTypesOrTermsClash(): Unit = {
    assertClashes("class X; class X; object X", "X")
    assertClashes("object X; object X; class X", "X")
  }
  
  def testCaseClassCompanion(): Unit = {
    assertFine("case class X(); object X")
    assertFine("case class X(); case object X")
    assertClashes("case class v(); def v {}", "v")
    assertMatches(messages("case class X(); object X; def X {}")) {
      case Error("X", _) :: Error("X", _) :: Nil =>
    }
    assertClashes("case class X(); object X; object X", "X")
    assertClashes("case class X(); case class X(); object X", "X")
  }

  def testFunctionParameterNames(): Unit = {
    assertClashes("def f(foo: Any) {}; def f(bar: Any) {}", "f")
  }
  
  def testFunctionSignature(): Unit = {
    assertFine("def f() {}; def f(p: Any) {}")
    assertFine("def a(p: Any) {}; def b(p: Any) {}")
    assertFine("def f(p: Bar) {}; def f(p: Foo) {}")
    assertFine("def f(a: Any) {}; def f(a: Any, b: Any) {}")
    assertFine("def f(a: Bar)(b: Any) {}; def f(a: Foo)(b: Any) {}")
    assertFine("def f(a: Any, b: Any) {}; def f(a: Any)(b: Any) {}")
    
    assertClashes("def f {}; def f {}", "f")
    assertClashes("def f() {}; def f() {}", "f")
    assertClashes("def f {}; def f() {}", "f")
    assertClashes("def f(p: Any) {}; def f(p: Any) {}", "f")
    assertClashes("def f(a: Any) {}; def f(b: Any) {}", "f")
    assertClashes("def f(a: Any, b: Any) {}; def f(a: Any, b: Any) {}", "f")
    assertClashes("def f(a: Any)(b: Any) {}; def f(a: Any)(b: Any) {}", "f")
  }

  //TODO thoughtfully consider type parameters and return type
  // (all functions with type parameters are simply excluded now)
  def testFunctionTypeParameters(): Unit = {
    assertFine("def f[A] = new Foo; def f[A, B] = new Bar")

//    assertClashes("def f[A] = new Foo; def f[A] = new Bar", "f")
//    assertClashes("def f[A] = new Foo; def f[B] = new Bar", "f")
//    assertClashes("def f[A] = new Foo; def f[A, B] = new Foo", "f")
//
    assertFine("def f = new Foo; def f[A] = new Bar")
//    assertClashes("def f = new Foo; def f[A] = new Foo", "f")
//
    assertFine("val f = new Foo; def f[A] = new Bar")
//    assertClashes("val f = new Foo; def f[A] = new Foo", "f")
  }

  def testForStatement(): Unit = {
    assertFine("for (a <- Some(1); a <- Some(a)) {}")
    assertFine("for {a <- Some(1); a <- Some(a)} {}")
    assertFine("for {a <- Some(1); b <- Some(a); a = b} {}")
    assertClashes("for {a <- Some(1); a <- Some(a); b = a; a = b} {}", "a")
  }
  
  def testRepeatedParameter(): Unit = {
    assertFine("def f(p: Any*) {}; def f(p: Any) {}")
    assertFine("def f(p: Any) {}; def f(p: Any*) {}")
    assertClashes("def f(p: Any*) {}; def f(p: Any*) {}", "f")
  }
  
  def testConstructorSignature(): Unit = {
    assertFine("class X { def this(x: Int) = this(); def this(x: AnyVal) = this() }")
    assertFine("class X { def this(a: Any) = this(); def this(a: Any, b: Any) = this() }")
  }
  
  def testPrimaryConstructor(): Unit = {
    assertFine("class X(x: Foo) { def this(x: Bar) {} }")
    assertFine("class X(a: Any) { def this(a: Any, b: Any) {} }")
    assertFine("class X(a: Any) { def this(a: Any)(b: Any) {} }")
    
    // TODO find clashes with primary constructor
//    assertClashes("class X { def this() {} }", "this")
//    assertClashes("class X(x: Any) { def this(x: Any) {} }", "this")
//    assertClashes("class X(a: Any, b: Any) { def this(a: Any, b: Any) {} }", "this")
//    assertClashes("class X(a: Any)(b: Any) { def this(a: Any)(b: Any) {} }", "this")
  }
  
  def testFunctionHolders(): Unit = {
    assertFine("class X { def f() {}; def f(p: Any) {} }")
    assertFine("object X { def f() {}; def f(p: Any) {} }")
    assertFine("trait X { def f() {}; def f(p: Any) {} }")
    assertFine("new { def f() {}; def f(p: Any) {} }")
    
    assertClashes("def x { def f() {}; def f(p: Any) {} }", "f")
    assertClashes("if(true) { def f() {}; def f(p: Any) {} }", "f")
    assertClashes("if(true) {} else { def f() {}; def f(p: Any) {} }", "f")
    assertClashes("while(true) { def f() {}; def f(p: Any) {} }", "f")
    assertClashes("do { def f() {}; def f(p: Any) {} } while(true)", "f")
    assertClashes("for(x <- Nil) { def f() {}; def f(p: Any) {} }", "f")
  }

  def testLocalFunctionSignature(): Unit = {
    assertClashes("def x { def f() {}; def f(p: Any) {} }", "f")
    assertClashes("def x { def f(p: Foo) {}; def f(p: Bar) {} }", "f")
    assertClashes("def x { def f(a: Any) {}; def f(a: Any, b: Any) {} }", "f")
    assertClashes("def x { def f(a: Any) {}; def f(a: Any)(b: Any) {} }", "f")
    assertClashes("def x { def f(a: Any)(b: Foo) {}; def f(a: Any)(b: Bar) {} }", "f")
  }

  def testFunctionFollowingApplications(): Unit = {
    assertFine("def f(a: Any) {}; def f(a: Any)(b: Any) {}")
    assertFine("def f(a: Any)(b: Foo) {}; def f(a: Any)(b: Bar) {}")
    assertFine("def f(a: Any)(b: Any) {}; def f(a: Any)(b: Any, c: Any) {}")
    assertFine("def f(a: Any)(b: Any) {}; def f(a: Any)(b: Any)(c: Any) {}")
    
    assertFine("def f(a: Any)(b: Any) {}; def f(a: Any, b: Any) {}")
    
    assertClashes("def f(a: Any)(b: Any) {}; def f(a: Any)(b: Any) {}", "f")
    assertClashes("def f(a: Any)(b: Any)(c: Any) {}; def f(a: Any)(b: Any)(c: Any) {}", "f")
  }

  def testConstructorFollowingApplications(): Unit = {
    assertFine(
      """class X {
        |  def this(a: Any) = this()
        |  def this(a: Any)(b: Any) = this()
        |}""".stripMargin)
    assertFine(
      """class X {
        |  def this(a: Any)(b: Any) = this()
        |  def this(a: Any)(b: Any, c: Any) = this()
        |}""".stripMargin)
    assertFine(
      """class X {
        |  def this(a: Any)(b: Any) = this()
        |  def this(a: Any)(b: Any)(c: Any) = this()
        |}""".stripMargin)

    assertFine(
      """class X {
        |  def this(a: Any)(b: Any) = this()
        |  def this(a: Any, b: Any) = this()
        |}""".stripMargin)

    assertFine(
      """class X {
        |  def this(a: Any)(b: Int) = this()
        |  def this(a: Any)(b: Long) = this()
        |}""".stripMargin)

    assertClashes(
      """class X {
        |  def this(a: Any)(b: Any) = this()
        |  def this(a: Any)(b: Any) = this()
        |}""".stripMargin, "this")

    assertClashes(
      """class X {
        |  def this(a: Any)(b: Any)(c: Any) = this()
        |  def this(a: Any)(b: Any)(c: Any) = this()
        |}""".stripMargin, "this")
  }
  
  def testTypeErasure(): Unit = {
    // precheck
    assertFine("def f(a: Foo) {}; def f(a: Bar) {}")
    assertClashes("class Holder[T]; def f(a: Holder) {}; def f(a: Holder) {}", "f")
    assertClashes("class Holder[T]; def f(a: Holder[Any]) {}; def f(a: Holder[Any]) {}", "f")
    
    assertClashes("class Holder[T]; def f(a: Holder[Foo]) {}; def f(a: Holder[Bar]) {}", "f")
    assertClashes("class Holder[T]; def f(a: Holder[Holder[Foo]]) {}; def f(a: Holder[Holder[Bar]]) {}", "f")
    assertClashes("class Holder[T]; def f(a: Holder[Foo], b: Holder[Bar]) {}; def f(a: Holder[Bar], b: Holder[Foo]) {}", "f")
    assertClashes("class Holder[A, B]; def f(a: Holder[Foo, Bar]) {}; def f(a: Holder[Bar, Foo]) {}", "f")

    assertClashes("class ArrayFoo[T]; def f(a: ArrayFoo[Foo]) {}; def f(a: ArrayFoo[Bar]) {}", "f")
    assertClashes("class FooArray[T]; def f(a: FooArray[Foo]) {}; def f(a: FooArray[Bar]) {}", "f")
  } 

  def testEarlyDefinitions(): Unit = {
    assertFine("new { val a = 1} with AnyRef; new { val a = 1} with AnyRef")
    assertClashes("new { val a = 1; val a = 2} with AnyRef", "a")
  }

  def testCaseClause(): Unit = {
    assertClashes("{case (a, a) => ()}", "a")
    assertClashes("{case (a, (b, a)) => ()}", "a")
    assertClashes("{case a | a => ()}", "a")
    assertFine("{case a => val a = 1}")
    assertFine("{case a => {(); val a = 1}}")
  }
  
  def testListOfPatterns(): Unit = {
    assertClashes("val (a, a) = ()", "a")
    assertClashes("val (a, (b, a)) = ()", "a")    
  }

  def testBackticks(): Unit = {
    Assert.assertTrue(clashesOf("class Test1 { def foo = ???; def `foo` = ???}").toSet == Set("foo", "`foo`"))
    Assert.assertTrue(clashesOf("class A; trait `A`").toSet == Set("A", "`A`"))
  }
  
  def testFunctionSignatureTypeConformanceAndErasure: Unit = {
    assertMatches(messages("type Alias = Any; def f(p: Any) {}; def f(p: Alias) {}")) {
      case Error("f", _) :: Error("f", _) :: Nil =>
    }
    //Any and AnyVal are erased to Object
    assertMatches(messages("def f(p: Any) {}; def f(p: AnyVal) {}")) {
      case Error("f", _) :: Error("f", _) :: Nil =>
    }
  }
  
  def testMessages(): Unit = {
    val messageSuffix = "is already defined in the scope"

    assertMatches(messages("class Foo; class Foo")) {
      case Error("Foo", m) :: _ if m.endsWith(messageSuffix) =>
    }
    assertMatches(messages("def f(a: Any, b: Any) {}; def f(a: Any, b: Any) {}")) {
      case Error("f", m) :: _ if m.endsWith(messageSuffix) =>
    }
    assertMatches(messages("def f(a: Any)(b: Any) {}; def f(a: Any)(b: Any) {}")) {
      case Error("f", m) :: _ if m.endsWith(messageSuffix) =>
    }
    assertMatches(messages("def x { def f(p: Any) {}; def f(p: Any) {} }")) {
      case Error("f", m) :: _ if m.endsWith(messageSuffix) =>
    }                           
    assertMatches(messages("def x { def f(a: Any) {}; def f(a: Any, b: Any) {} }")) {
      case Error("f", m) :: _ if m.endsWith(messageSuffix) =>
    }
  }

  //SCL-7116
  def testDifferentReturnType(): Unit = {
    assertFine("""class Option[+X]; def foo(x: Option[Foo]): Foo = {}; def foo(y: Option[Bar]): Bar = {}""")

    assertClashes("""class Option[+X]; def foo(x: Option[Foo]): Bar = {}; def foo(y: Option[Bar]): Bar = {}""", "foo")
    assertClashes("""class Option[+X]; def foo(x: Option[Foo]): Option[Foo] = {}; def foo(y: Option[Bar]): Option[Bar] = {}""", "foo")
  }

  def testByNameParameter(): Unit = {
    //SCL-8633
    assertFine("def a(x: Int) = x; def a(x: => Int) = x")

    //SCL-15358
    assertFine(
      """
        |class Test {
        |  protected def test(i: Int, a: Any): Unit = ???
        |  def test(i: Int, a: => Any): Unit = ???
        |}
      """.stripMargin)
  }

  def testPrivateField(): Unit = {
    //SCL-2101
    assertFine(
      """class Some(name: Int) {
        |    def name {""}
        |}""".stripMargin)

    //SCL-5789
    assertFine(
      """
        |class Test {
        |  private[this] val x = 1
        |  def x(): String = this.x.toString
        |}
      """.stripMargin)

    //SCL-5869
    assertFine(
      """
        |class Test(private[this] var param:Int) {
        |  def param():Int = this.param
        |  def param_= (param:Int) {this.param = param}
        |}
      """.stripMargin)

    assertFine("class A(private[this] val param: Int) { def param(): Int = this.param }")
    assertFine("class A(param: Int) { def param(): Int = this.param }")
    assertFine("class A(param: Int) { def param: Int = 1 }")
    assertFine("class A() { private[this] val x: Int = 1; def x(): Int = x }")
    assertFine("class A() { private[this] var x: Int = 1; def x(): Int = x }")
    assertFine("class A() { private[this] var x: Int = 1; def x: Int = 1 }")

    assertClashes("class A() { private var x: Int = 1; def x(): Int = x }", "x")
    assertClashes("class A(private val param: Int) { def param(): Int = this.param }", "param")
  }

  //methods in structural types may be overloaded, but return type is ignored
  def testStructuralType(): Unit = {
    assertFine(
      """
        |object Test {
        |  trait A[T]
        |
        |  type T = {
        |    def foo(f: A[Int])
        |    def foo(f: A[Boolean])
        |  }
        |}
      """.stripMargin)

    assertClashes(
      """object Test {
        |  type MyInt = Int
        |
        |  type T = {
        |    def foo(f: Int)
        |    def foo(f: MyInt)
        |  }
        |}
      """.stripMargin, "foo")

    assertClashes(
      """
        |object Test {
        |  type T = {
        |    def foo(): Int
        |    val foo: Int
        |  }
        |}
      """.stripMargin, "foo")
  }

  def testDifferentReturnTypes(): Unit = {
    assertClashes(
      """
        |object Test {
        |  def test(a: Int): Int = 0
        |  def test(b: Int): Boolean = true
        |}
      """.stripMargin, "test")

    assertFine(
      """
        |trait Option[+X]
        |object Test {
        |  def test(a: Option[Int]): Int = 0
        |  def test(b: Option[Boolean]): Boolean = true
        |}
      """.stripMargin)

    assertClashes(
      """
        |trait Option[+X]
        |object Test {
        |  type Opt[Y] = Option[Y]
        |  def test(a: Opt[Int]): Int = 0
        |  def test(b: Option[Int]): Boolean = true
        |}
      """.stripMargin, "test")

  }

  def testExtensions(): Unit = {
    assertFine(
      """extension (n: Int)
        |  def mySpecialToString: String = n.toString
        |  def mySpecialMkString(prefix: String, separator: String, postfix: String): String =
        |    List(n).mkString(prefix, separator, postfix)
        |
        |extension (n: Long)
        |  def mySpecialToString: String = n.toString
        |  def mySpecialMkString(prefix: String, separator: String, postfix: String): String =
        |    List(n).mkString(prefix, separator, postfix)
        """.stripMargin,
      Scala3Language.INSTANCE
    )
  }

  def clashesOf(@Language(value = "Scala", prefix = Header) code: String, lang: com.intellij.lang.Language = ScalaLanguage.INSTANCE) = {
    messages(code, lang).map {
      case error: Error => error.element
      case message => Assert.fail("Unexpected message: " + message)
    }
  }

  def assertClashes(@Language(value = "Scala", prefix = Header) code: String, pairs: String*): Unit = {
    val expectation = pairs.flatMap(p => List(p, p))
    Assert.assertEquals("Incorrect clashed elements", expectation.mkString(", "), clashesOf(code).mkString(", "))
  }
  
  def assertFine(@Language(value = "Scala", prefix = Header) code: String, lang: com.intellij.lang.Language = ScalaLanguage.INSTANCE): Unit = {
    val clashes = clashesOf(code, lang)
    if(clashes.nonEmpty) Assert.fail("Unexpected clashes: " + clashes.mkString(", "))
  }
  
  def messages(@Language(value = "Scala", prefix = Header) code: String, lang: com.intellij.lang.Language = ScalaLanguage.INSTANCE): List[Message] = {
    val file = (Header + code).parse(lang)
    implicit val mock: AnnotatorHolderMock = new AnnotatorHolderMock(file)
    file.depthFirst().foreach {
      ScopeAnnotator.annotateScope(_)
    }
    
    mock.annotations
  }
}