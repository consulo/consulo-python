/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.refactoring.invertBoolean;

import jakarta.annotation.Nonnull;

import consulo.language.Language;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.action.BaseRefactoringAction;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.*;

/**
 * User : ktisha
 */
public class PyInvertBooleanAction extends BaseRefactoringAction {
  @Override
  protected boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  protected boolean isEnabledOnElements(@Nonnull PsiElement[] elements) {
    if (elements.length == 1) {
      return isApplicable(elements[0], elements[0].getContainingFile());
    }
    return false;
  }

  private static boolean isApplicable(@Nonnull final PsiElement element, @Nonnull final PsiFile file) {
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile != null && ProjectRootManager.getInstance(element.getProject()).getFileIndex().isInLibraryClasses(virtualFile)) return false;
    if (element instanceof PyTargetExpression) {
      final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
      if (assignmentStatement != null) {
        final PyExpression assignedValue = assignmentStatement.getAssignedValue();
        if (assignedValue == null) return false;
        final String name = assignedValue.getText();
        return name != null && (PyNames.TRUE.equals(name) || PyNames.FALSE.equals(name));
      }
    }
    if (element instanceof PyNamedParameter) {
      final PyExpression defaultValue = ((PyNamedParameter)element).getDefaultValue();
      if (defaultValue instanceof PyBoolLiteralExpression) return true;
    }
    return element.getParent() instanceof PyBoolLiteralExpression;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(@Nonnull final PsiElement element, @Nonnull final Editor editor, @Nonnull PsiFile file, @Nonnull DataContext context) {
    return isApplicable(element, element.getContainingFile());
  }

  @Override
  protected RefactoringActionHandler getHandler(@Nonnull DataContext dataContext) {
    return new PyInvertBooleanHandler();
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return language.isKindOf(PythonLanguage.getInstance());
  }
}
