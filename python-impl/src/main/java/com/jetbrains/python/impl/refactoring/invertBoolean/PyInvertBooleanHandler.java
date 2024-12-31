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
import consulo.language.editor.CommonDataKeys;
import consulo.dataContext.DataContext;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.editor.refactoring.action.RefactoringActionHandler;
import consulo.language.editor.refactoring.RefactoringBundle;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyNamedParameter;

/**
 * User : ktisha
 */
public class PyInvertBooleanHandler implements RefactoringActionHandler {
  static final String REFACTORING_NAME = RefactoringBundle.message("invert.boolean.title");

  @Override
  public void invoke(@Nonnull Project project, Editor editor, PsiFile file, DataContext dataContext) {
    PsiElement element = dataContext.getData(CommonDataKeys.PSI_ELEMENT);
    if (element == null && editor != null && file != null) {
      element = file.findElementAt(editor.getCaretModel().getOffset());
    }
    final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
    if (assignmentStatement != null) {
      invoke(assignmentStatement.getTargets()[0]);
    }
    else if (element instanceof PyNamedParameter) {
      invoke(element);
    }
    else {
      CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.getCannotRefactorMessage(
          RefactoringBundle.message("error.wrong.caret.position.local.or.expression.name")), REFACTORING_NAME, "refactoring.invertBoolean");
    }
  }

  @Override
  public void invoke(@Nonnull Project project, @Nonnull PsiElement[] elements, DataContext dataContext) {
    if (elements.length == 1) {
      final PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(elements[0], PyAssignmentStatement.class);
      if (assignmentStatement != null) {
        invoke(assignmentStatement.getTargets()[0]);
      }
    }
  }

  private static void invoke(@Nonnull final PsiElement element) {
    new PyInvertBooleanDialog(element).show();
  }
}
