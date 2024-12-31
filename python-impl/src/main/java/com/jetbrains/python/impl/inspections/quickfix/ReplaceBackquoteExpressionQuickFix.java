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
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyReprExpression;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   06.03.2010
 * Time:   16:50:53
 */
public class ReplaceBackquoteExpressionQuickFix implements LocalQuickFix {
  @Nonnull
  @Override
  public String getName() {
    return PyBundle.message("INTN.replace.backquote.expression");
  }

  @Nonnull
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiElement problemElement = descriptor.getPsiElement();
    if (problemElement instanceof PyReprExpression) {
      if (((PyReprExpression)problemElement).getExpression() != null) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        problemElement.replace(elementGenerator.createExpressionFromText("repr(" + ((PyReprExpression)problemElement).getExpression().getText() + ")"));
      }
    }
  }
}