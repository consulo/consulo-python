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

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.*;
import consulo.ide.impl.idea.util.FunctionUtil;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import java.util.function.Function;

/**
 * User: ktisha
 */
public class PyMoveAttributeToInitQuickFix implements LocalQuickFix {

  public PyMoveAttributeToInitQuickFix() {
  }

  @Nonnull
  public String getName() {
    return PyBundle.message("QFIX.move.attribute");
  }

  @NonNls
  @Nonnull
  public String getFamilyName() {
    return getName();
  }

  public void applyFix(@Nonnull final Project project, @Nonnull final ProblemDescriptor descriptor) {
    final PsiElement element = descriptor.getPsiElement();
    if (!(element instanceof PyTargetExpression)) return;
    final PyTargetExpression targetExpression = (PyTargetExpression)element;

    final PyClass containingClass = targetExpression.getContainingClass();
    final PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(element, PyAssignmentStatement.class);
    if (containingClass == null || assignment == null) return;

    final Function<String, PyStatement> callback = FunctionUtil.<String, PyStatement>constant(assignment);
    AddFieldQuickFix.addFieldToInit(project, containingClass, ((PyTargetExpression)element).getName(), callback);
    removeDefinition(assignment);
  }

  private static boolean removeDefinition(PyAssignmentStatement assignment) {
    final PyStatementList statementList = PsiTreeUtil.getParentOfType(assignment, PyStatementList.class);
    if (statementList == null) return false;

    if (statementList.getStatements().length == 1) {
      final PyPassStatement passStatement = PyElementGenerator.getInstance(assignment.getProject()).createPassStatement();
      statementList.addBefore(passStatement, assignment);
    }
    assignment.delete();
    return true;
  }
}
