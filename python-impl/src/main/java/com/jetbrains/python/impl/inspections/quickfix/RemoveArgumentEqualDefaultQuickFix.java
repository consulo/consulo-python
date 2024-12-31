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

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.*;
import jakarta.annotation.Nonnull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * User: catherine
 *
 * QuickFix to remove redundant argument equal default
 */
public class RemoveArgumentEqualDefaultQuickFix implements LocalQuickFix {
  Set<PyExpression> myProblemElements;
  public RemoveArgumentEqualDefaultQuickFix(Set<PyExpression> problemElements) {
    myProblemElements = problemElements;
  }

  @Nonnull
  public String getName() {
    return PyBundle.message("QFIX.remove.argument.equal.default");
  }

  @Nonnull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();

    PyArgumentList argumentList = PsiTreeUtil.getParentOfType(element, PyArgumentList.class);
    if (argumentList == null) return;
    StringBuilder newArgumentList = new StringBuilder("foo(");

    PyExpression[] arguments = argumentList.getArguments();
    List<String> newArgs = new ArrayList<String>();
    for (int i = 0; i != arguments.length; ++i) {
      if (!myProblemElements.contains(arguments[i])) {
        newArgs.add(arguments[i].getText());
      }
    }

    newArgumentList.append(StringUtil.join(newArgs, ", ")).append(")");
    PyExpression expression = PyElementGenerator.getInstance(project).createFromText(
      LanguageLevel.forElement(argumentList), PyExpressionStatement.class, newArgumentList.toString()).getExpression();
    if (expression instanceof PyCallExpression)
      argumentList.replace(((PyCallExpression)expression).getArgumentList());
  }
}
