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

import javax.annotation.Nonnull;

import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.project.Project;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.*;

/**
 * User: catherine
 *
 * QuickFix to move misplaced docstring
 */
public class StatementEffectDocstringQuickFix implements LocalQuickFix {
  @Nonnull
  public String getName() {
    return PyBundle.message("QFIX.statement.effect.move.docstring");
  }

  @Nonnull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
    PsiElement expression = descriptor.getPsiElement();
    if (expression instanceof PyStringLiteralExpression) {
      PyStatement st = PsiTreeUtil.getParentOfType(expression, PyStatement.class);
      if (st != null) {
        PyDocStringOwner parent = PsiTreeUtil.getParentOfType(expression, PyDocStringOwner.class);

        if (parent instanceof PyClass || parent instanceof PyFunction) {
          PyStatementList statementList = PsiTreeUtil.findChildOfType(parent, PyStatementList.class);
          if (statementList != null) {
            PyStatement[] statements = statementList.getStatements();
            if (statements.length > 0) {
              statementList.addBefore(st, statements[0]);
              st.delete();
            }
          }
        }
      }
    }
  }

}
