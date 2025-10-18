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

package com.jetbrains.python.impl.refactoring.surround.surrounders.expressions;

import com.jetbrains.python.psi.*;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.localize.CodeInsightLocalize;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * Created by IntelliJ IDEA.
 * User: Alexey.Ivanov
 * Date: Aug 27, 2009
 * Time: 6:03:59 PM
 */
public class PyWithParenthesesSurrounder extends PyExpressionSurrounder {
  public LocalizeValue getTemplateDescription() {
    return CodeInsightLocalize.surroundWithParenthesisTemplate();
  }

  @Override
  public boolean isApplicable(@Nonnull PyExpression elements) {
    return true;
  }

  @Override
  public TextRange surroundExpression(@Nonnull Project project, @Nonnull Editor editor, @Nonnull PyExpression element)
    throws IncorrectOperationException {
    PyParenthesizedExpression parenthesesExpression = (PyParenthesizedExpression)PyElementGenerator.getInstance(project)
      .createFromText(LanguageLevel.getDefault(), PyExpressionStatement.class, "(a)").getExpression();
    PyExpression expression = parenthesesExpression.getContainedExpression();
    assert expression != null;
    expression.replace(element);
    element = (PyExpression) element.replace(parenthesesExpression);
    element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element);
    return element.getTextRange();
  }
}
