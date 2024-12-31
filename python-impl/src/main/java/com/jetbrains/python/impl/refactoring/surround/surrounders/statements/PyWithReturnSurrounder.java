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

package com.jetbrains.python.impl.refactoring.surround.surrounders.statements;

import jakarta.annotation.Nonnull;

import consulo.language.editor.CodeInsightUtilCore;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.*;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nullable;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 28, 2009
 * Time: 6:00:47 PM
 */
public class PyWithReturnSurrounder extends PyStatementSurrounder {
  public boolean isApplicable(@Nonnull PsiElement[] elements) {
    return (elements.length == 1) &&
           (elements[0] instanceof PyExpressionStatement) &&
           (PsiTreeUtil.getParentOfType(elements[0], PyFunction.class) != null);
  }

  @Override
  @Nullable
  protected TextRange surroundStatement(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PsiElement[] elements)
    throws IncorrectOperationException
  {
    PyReturnStatement returnStatement =
      PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyReturnStatement.class, "return a");
    PyExpression expression = returnStatement.getExpression();
    assert expression != null;
    PsiElement element = elements[0];
    expression.replace(element);
    element = element.replace(returnStatement);
    element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element);
    return element.getTextRange();
  }

  public String getTemplateDescription() {
    return PyBundle.message("surround.with.return.template");
  }
}
