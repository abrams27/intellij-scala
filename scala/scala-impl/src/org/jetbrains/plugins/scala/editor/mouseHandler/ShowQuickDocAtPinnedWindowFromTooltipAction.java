/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.scala.editor.mouseHandler;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.navigation.AbstractDocumentationTooltipAction;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class ShowQuickDocAtPinnedWindowFromTooltipAction extends AbstractDocumentationTooltipAction {

  ShowQuickDocAtPinnedWindowFromTooltipAction() {
//    String className = getClass().getSimpleName();
//    String actionId = className.substring(0, className.lastIndexOf("Action"));
//    getTemplatePresentation().setText(ActionsBundle.actionText(actionId));
//    getTemplatePresentation().setDescription(ActionsBundle.actionDescription(actionId));
//    getTemplatePresentation().setIcon(AllIcons.General.Pin_tab);
  }

  @Override
  protected void doActionPerformed(@NotNull DataContext context, @NotNull PsiElement docAnchor, @NotNull PsiElement originalElement) {
    Project project = CommonDataKeys.PROJECT.getData(context);
    if (project == null) {
      return;
    }

    @SuppressWarnings("deprecation")
    DocumentationManager docManager = DocumentationManager.getInstance(project);
    docManager.setAllowContentUpdateFromContext(false);
    docManager.showJavaDocInfoAtToolWindow(docAnchor, originalElement); 
  }
}
