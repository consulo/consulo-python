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

package com.jetbrains.python.impl.inspections;

import com.google.common.collect.ImmutableList;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.inspections.quickfix.SimplifyBooleanCheckQuickFix;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyConditionalStatementPart;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PySimplifyBooleanCheckInspection extends PyInspection {
  private static List<String> COMPARISON_LITERALS = ImmutableList.of("True", "False", "[]");

  @Nonnull
  @Override
  public InspectionToolState<?> createStateProvider() {
    return new PySimplifyBooleanCheckInspectionState();
  }

  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.check.can.be.simplified");
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @Nonnull LocalInspectionToolSession session,
                                        Object state) {
    PySimplifyBooleanCheckInspectionState inspectionState = (PySimplifyBooleanCheckInspectionState)state;
    return new Visitor(holder, session, inspectionState.ignoreComparisonToZero);
  }

  private static class Visitor extends PyInspectionVisitor {
    private final boolean myIgnoreComparisonToZero;

    public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session, boolean ignoreComparisonToZero) {
      super(holder, session);
      myIgnoreComparisonToZero = ignoreComparisonToZero;
    }

    @Override
    public void visitPyConditionalStatementPart(PyConditionalStatementPart node) {
      super.visitPyConditionalStatementPart(node);
      final PyExpression condition = node.getCondition();
      if (condition != null) {
        condition.accept(new PyBinaryExpressionVisitor(getHolder(), getSession(), myIgnoreComparisonToZero));
      }
    }
  }

  private static class PyBinaryExpressionVisitor extends PyInspectionVisitor {
    private final boolean myIgnoreComparisonToZero;

    public PyBinaryExpressionVisitor(@Nullable ProblemsHolder holder,
                                     @Nonnull LocalInspectionToolSession session,
                                     boolean ignoreComparisonToZero) {
      super(holder, session);
      myIgnoreComparisonToZero = ignoreComparisonToZero;
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
      super.visitPyBinaryExpression(node);
      final PyElementType operator = node.getOperator();
      final PyExpression rightExpression = node.getRightExpression();
      if (rightExpression == null || rightExpression instanceof PyBinaryExpression ||
        node.getLeftExpression() instanceof PyBinaryExpression) {
        return;
      }
      if (PyTokenTypes.EQUALITY_OPERATIONS.contains(operator)) {
        if (operandsEqualTo(node, COMPARISON_LITERALS) ||
          (!myIgnoreComparisonToZero && operandsEqualTo(node, Collections.singleton("0")))) {
          registerProblem(node);
        }
      }
    }

    private static boolean operandsEqualTo(@Nonnull PyBinaryExpression expr, @Nonnull Collection<String> literals) {
      final String leftExpressionText = expr.getLeftExpression().getText();
      final PyExpression rightExpression = expr.getRightExpression();
      final String rightExpressionText = rightExpression != null ? rightExpression.getText() : null;
      for (String literal : literals) {
        if (literal.equals(leftExpressionText) || literal.equals(rightExpressionText)) {
          return true;
        }
      }
      return false;
    }

    private void registerProblem(PyBinaryExpression binaryExpression) {
      registerProblem(binaryExpression,
                      PyBundle.message("INSP.expression.can.be.simplified"),
                      new SimplifyBooleanCheckQuickFix(binaryExpression));
    }
  }
}
