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
package com.jetbrains.python.impl.inspections.quickfix;

import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.ast.TokenSet;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;

/**
 * @author Alexey.Ivanov
 * @since 2010-03-17
 */
public class SimplifyBooleanCheckQuickFix implements LocalQuickFix {
  private String myReplacementText;

  public SimplifyBooleanCheckQuickFix(PyBinaryExpression binaryExpression) {
    myReplacementText = createReplacementText(binaryExpression);
  }

  private static boolean isTrue(PyExpression expression) {
    return "True".equals(expression.getText());
  }

  private static boolean isFalse(PyExpression expression) {
    return "False".equals(expression.getText());
  }

  private static boolean isNull(PyExpression expression) {
    return "0".equals(expression.getText());
  }

  private static boolean isEmpty(PyExpression expression) {
    return "[]".equals(expression.getText());
  }

  @Nonnull
  @Override
  public LocalizeValue getName() {
    return PyLocalize.qfixSimplify$0(myReplacementText);
  }

  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!element.isValid() || !(element instanceof PyBinaryExpression)) {
      return;
    }
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    element.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(element), myReplacementText));
  }

  private static String createReplacementText(PyBinaryExpression expression) {
    PyExpression resultExpression;
    final PyExpression leftExpression = expression.getLeftExpression();
    final PyExpression rightExpression = expression.getRightExpression();
    boolean positiveCondition = !TokenSet.create(PyTokenTypes.NE, PyTokenTypes.NE_OLD).contains(expression.getOperator());
    positiveCondition ^= isFalse(leftExpression) || isFalse(rightExpression) || isNull(rightExpression) || isNull(leftExpression)
                         || isEmpty(rightExpression) || isEmpty(leftExpression);
    if (isTrue(leftExpression) || isFalse(leftExpression) || isNull(leftExpression) || isEmpty(leftExpression)) {
      resultExpression = rightExpression;
    } else {
      resultExpression = leftExpression;
    }
    return ((positiveCondition) ? "" : "not ") + resultExpression.getText();
  }
}
