package org.jetbrains.plugins.scala.lang.refactoring.util

import org.jetbrains.plugins.scala.lang.psi.types.ScType
import org.jetbrains.plugins.scala.lang.psi.types.api.ParameterizedType

object ScTypeUtil {

  def stripTypeArgs(tp: ScType): ScType = tp match {
    case ParameterizedType(designator, _) => designator
    case t => t
  }
}
