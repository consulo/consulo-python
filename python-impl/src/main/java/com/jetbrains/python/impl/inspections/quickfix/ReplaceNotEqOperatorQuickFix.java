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
import consulo.language.impl.psi.LeafPsiElement;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   06.03.2010
 * Time:   16:16:52
 */
public class ReplaceNotEqOperatorQuickFix implements LocalQuickFix {
  @Nonnull
  @Override
  public String getName() {
    return PyBundle.message("INTN.replace.noteq.operator");
  }

  @Nonnull
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiElement binaryExpression = descriptor.getPsiElement();

    if (binaryExpression instanceof PyBinaryExpression) {
      PsiElement operator = ((PyBinaryExpression)binaryExpression).getPsiOperator();
      if (operator != null) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        operator.replace(elementGenerator.createFromText(LanguageLevel.forElement(binaryExpression), LeafPsiElement.class, "!="));
      }
    }
  }
}
