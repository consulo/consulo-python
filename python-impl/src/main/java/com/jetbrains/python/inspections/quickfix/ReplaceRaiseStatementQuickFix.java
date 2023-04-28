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

package com.jetbrains.python.inspections.quickfix;

import javax.annotation.Nonnull;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRaiseStatement;

/**
 * @author Alexey.Ivanov
 */
public class ReplaceRaiseStatementQuickFix implements LocalQuickFix {
  @Nonnull
  @Override
  public String getName() {
    return PyBundle.message("INTN.replace.raise.statement");
  }

  @Nonnull
  public String getFamilyName() {
    return getName();
  }

  @Override
  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiElement raiseStatement = descriptor.getPsiElement();
    if (raiseStatement instanceof PyRaiseStatement) {
      PyExpression[] expressions = ((PyRaiseStatement)raiseStatement).getExpressions();
      PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
      String newExpressionText = expressions[0].getText() + "(" + expressions[1].getText() + ")";
      if (expressions.length == 2) {
        raiseStatement.replace(elementGenerator.createFromText(LanguageLevel.forElement(raiseStatement), PyRaiseStatement.class, "raise " + newExpressionText));
      } else if (expressions.length == 3) {
        raiseStatement.replace(elementGenerator.createFromText(LanguageLevel.forElement(raiseStatement), PyRaiseStatement.class,
                                                               "raise " + newExpressionText + ".with_traceback(" + expressions[2].getText() + ")"));
      }
    }
  }
}