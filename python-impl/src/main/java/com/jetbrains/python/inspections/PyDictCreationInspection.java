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

package com.jetbrains.python.inspections;

import com.jetbrains.python.PyBundle;
import com.jetbrains.python.inspections.quickfix.DictCreationQuickFix;
import com.jetbrains.python.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.Pair;
import org.jetbrains.annotations.Nls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PyDictCreationInspection extends PyInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.dict.creation");
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @Nonnull LocalInspectionToolSession session,
                                        Object state) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      if (node.getAssignedValue() instanceof PyDictLiteralExpression) {
        if (node.getTargets().length != 1) {
          return;
        }
        final PyExpression target = node.getTargets()[0];
        final String name = target.getName();
        if (name == null) {
          return;
        }

        PyStatement statement = PsiTreeUtil.getNextSiblingOfType(node, PyStatement.class);

        while (statement instanceof PyAssignmentStatement) {
          final PyAssignmentStatement assignmentStatement = (PyAssignmentStatement)statement;
          final List<Pair<PyExpression, PyExpression>> targets = getDictTargets(target, name, assignmentStatement);
          if (targets == null)
            return;
          if (!targets.isEmpty()) {
            registerProblem(node, "This dictionary creation could be rewritten as a dictionary literal", new DictCreationQuickFix(node));
            break;
          }
          statement = PsiTreeUtil.getNextSiblingOfType(assignmentStatement, PyStatement.class);
        }
      }
    }
  }

  @Nullable
  public static List<Pair<PyExpression, PyExpression>> getDictTargets(@Nonnull final PyExpression target,
                                                                      @Nonnull final String name,
                                                                      @Nonnull final PyAssignmentStatement assignmentStatement) {
    final List<Pair<PyExpression, PyExpression>> targets = new ArrayList<Pair<PyExpression, PyExpression>>();
    for (Pair<PyExpression, PyExpression> targetToValue : assignmentStatement.getTargetsToValuesMapping()) {
      if (targetToValue.first instanceof PySubscriptionExpression) {
        final PySubscriptionExpression subscriptionExpression = (PySubscriptionExpression)targetToValue.first;
        if (name.equals(subscriptionExpression.getOperand().getName()) &&
          subscriptionExpression.getIndexExpression() != null &&
          !referencesTarget(targetToValue.second, target)) {
          targets.add(targetToValue);
        }
      }
      else
        return null;
    }
    return targets;
  }

  private static boolean referencesTarget(@Nonnull final PyExpression expression, @Nonnull final PsiElement target) {
    final List<PsiElement> refs = new ArrayList<PsiElement>();
    expression.accept(new PyRecursiveElementVisitor() {
      @Override
      public void visitPyReferenceExpression(PyReferenceExpression node) {
        super.visitPyReferenceExpression(node);
        final PsiPolyVariantReference ref = node.getReference();
        if (ref.isReferenceTo(target)) {
          refs.add(node);
        }
      }
    });
    return !refs.isEmpty();
  }
}
