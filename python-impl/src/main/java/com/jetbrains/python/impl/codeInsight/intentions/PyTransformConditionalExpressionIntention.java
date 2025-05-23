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

package com.jetbrains.python.impl.codeInsight.intentions;

import jakarta.annotation.Nonnull;

import consulo.language.editor.intention.BaseIntentionAction;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.*;
import consulo.language.util.IncorrectOperationException;

/**
 * User: catherine
 * Intention to transform conditional expression into if/else statement
 * For instance,
 *
 * x = a if cond else b
 *
 * into:
 *
 * if cond:
 *    x = a
 * else:
 *    x = b
 */
public class PyTransformConditionalExpressionIntention extends BaseIntentionAction {
  @Nonnull
  public String getFamilyName() {
    return PyBundle.message("INTN.transform.into.if.else.statement");
  }

  @Nonnull
  public String getText() {
    return PyBundle.message("INTN.transform.into.if.else.statement");
  }

  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    PyAssignmentStatement expression =
      PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyAssignmentStatement.class);
    if (expression != null && expression.getAssignedValue() instanceof PyConditionalExpression) {
      return true;
    }
    return false;
  }

  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException
  {
    final PyAssignmentStatement assignmentStatement =
          PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyAssignmentStatement.class);
    assert assignmentStatement != null;
    final PyExpression assignedValue =
      assignmentStatement.getAssignedValue();
    if (assignedValue instanceof PyConditionalExpression) {
      final PyConditionalExpression expression = (PyConditionalExpression)assignedValue;
      final PyExpression condition = expression.getCondition();
      final PyExpression falsePart = expression.getFalsePart();
      if (condition != null && falsePart != null) {
        final String truePartText = expression.getTruePart().getText();
        final PyExpression leftHandSideExpression = assignmentStatement.getLeftHandSideExpression();
        if (leftHandSideExpression != null) {
          final String targetText = leftHandSideExpression.getText();
          final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
          final String text = "if " + condition.getText() + ":\n\t" + targetText + " = " + truePartText
                        + "\nelse:\n\t" + targetText + " = " + falsePart.getText();
          final PyIfStatement ifStatement = elementGenerator.createFromText(LanguageLevel.forElement(expression), PyIfStatement.class, text);
          assignmentStatement.replace(ifStatement);
        }
      }
    }
  }

}
