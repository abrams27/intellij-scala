package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.StubIndexKey

class ScPackageObjectIndex extends ScIntStubIndexExtension[PsiClass] {

  override def getKey: StubIndexKey[Integer, PsiClass] =
    ScalaIndexKeys.PACKAGE_OBJECT_KEY
}
