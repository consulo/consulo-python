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

import jakarta.annotation.Nonnull;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.inspections.PySetFunctionToLiteralInspection;
import com.jetbrains.python.psi.*;

/**
 * User : catherine
 * Quick Fix to replace function call of built-in function "set" with
 * set literal if applicable
 */
public class ReplaceFunctionWithSetLiteralQuickFix implements LocalQuickFix {
  @Override
  @Nonnull
  public String getName() {
    return PyBundle.message("QFIX.replace.function.set.with.literal");
  }

  @Override
  @Nonnull
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PyElement[] elements = PySetFunctionToLiteralInspection.getSetCallArguments((PyCallExpression)descriptor.getPsiElement());
    PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
    PsiElement functionCall = descriptor.getPsiElement();
    StringBuilder str = new StringBuilder("{");
    for (int i = 0; i != elements.length; ++i) {
      PyElement e = elements[i];
      str.append(e.getText());
      if (i != elements.length-1)
        str.append(", ");
    }
    str.append("}");
    functionCall.replace(elementGenerator.createFromText(LanguageLevel.forElement(functionCall), PyExpressionStatement.class,
                                                             str.toString()).getExpression());
  }
}
