package org.jetbrains.plugins.scala.lang.psi.stubs.index

import com.intellij.psi.PsiClass
import com.intellij.psi.stubs.StubIndexKey

class ScFullClassNameIndex extends ScIntStubIndexExtension[PsiClass] {
  override def getKey: StubIndexKey[Integer, PsiClass] =
    ScalaIndexKeys.FQN_KEY
}
