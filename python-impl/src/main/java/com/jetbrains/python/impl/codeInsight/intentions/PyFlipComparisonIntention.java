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

import consulo.language.editor.intention.BaseIntentionAction;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;

import java.util.HashMap;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyFile;
import consulo.language.util.IncorrectOperationException;

import jakarta.annotation.Nonnull;

import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   26.03.2010
 * Time:   22:01:27
 */
public class PyFlipComparisonIntention extends BaseIntentionAction
{
  private static final Map<PyElementType, String> FLIPPED_OPERATORS = new HashMap<PyElementType, String>(7);

  static {
    FLIPPED_OPERATORS.put(PyTokenTypes.EQEQ, "==");
    FLIPPED_OPERATORS.put(PyTokenTypes.NE, "!=");
    FLIPPED_OPERATORS.put(PyTokenTypes.NE_OLD, "<>");
    FLIPPED_OPERATORS.put(PyTokenTypes.GE, "<=");
    FLIPPED_OPERATORS.put(PyTokenTypes.LE, ">=");
    FLIPPED_OPERATORS.put(PyTokenTypes.GT, "<");
    FLIPPED_OPERATORS.put(PyTokenTypes.LT, ">");
  }

  @Nonnull
  public String getFamilyName() {
    return PyBundle.message("INTN.flip.comparison");
  }

  public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
    if (!(file instanceof PyFile)) {
      return false;
    }

    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PyBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class, false);
    while (binaryExpression != null) {
      PyElementType operator = binaryExpression.getOperator();
      if (FLIPPED_OPERATORS.containsKey(operator)) {
        String operatorText = binaryExpression.getPsiOperator().getText();
        String flippedOperatorText = FLIPPED_OPERATORS.get(operator);
        if (flippedOperatorText.equals(operatorText)) {
          setText(PyBundle.message("INTN.flip.$0", operatorText));
        }
        else {
          setText(PyBundle.message("INTN.flip.$0.to.$1", operatorText, flippedOperatorText));
        }
        return true;
      }
      binaryExpression = PsiTreeUtil.getParentOfType(binaryExpression, PyBinaryExpression.class);
    }
    return false;
  }

  public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException
  {
    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    PyBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, PyBinaryExpression.class, false);
    while (binaryExpression != null) {
      if (FLIPPED_OPERATORS.containsKey(binaryExpression.getOperator())) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        binaryExpression.replace(elementGenerator.createBinaryExpression(FLIPPED_OPERATORS.get(binaryExpression.getOperator()),
                                                                         binaryExpression.getRightExpression(),
                                                                         binaryExpression.getLeftExpression()));
        return;
      }
      binaryExpression = PsiTreeUtil.getParentOfType(binaryExpression, PyBinaryExpression.class);
    }
  }
}
