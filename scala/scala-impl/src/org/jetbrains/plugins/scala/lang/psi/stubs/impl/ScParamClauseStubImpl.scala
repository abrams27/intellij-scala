
package org.jetbrains.plugins.scala.lang.psi.stubs
package impl

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.{IStubElementType, StubBase, StubElement}
import org.jetbrains.plugins.scala.lang.psi.api.statements.params.ScParameterClause

class ScParamClauseStubImpl(parent: StubElement[_ <: PsiElement],
                            elementType: IStubElementType[_ <: StubElement[_ <: PsiElement], _ <: PsiElement],
                            override val isImplicit: Boolean,
                            override val isUsing: Boolean,
                            override val isInline: Boolean
                           )
  extends StubBase[ScParameterClause](parent, elementType) with ScParamClauseStub
