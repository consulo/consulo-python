/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.inspections.quickfix.StatementEffectFunctionCallQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.StatementEffectIntroduceVariableQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PyStatementEffectInspection extends PyInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.statement.effect");
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @Nonnull LocalInspectionToolSession session,
                                        Object state) {
    return new Visitor(holder, session);
  }

  @Override
  protected boolean isSuppressForCodeFragment() {
    return true;
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(final ProblemsHolder holder, LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyExpressionStatement(final PyExpressionStatement node) {
      final PyExpression expression = node.getExpression();
      if (PsiTreeUtil.hasErrorElements(expression)) {
        return;
      }
      if (hasEffect(expression)) {
        return;
      }

      // https://twitter.com/gvanrossum/status/112670605505077248
      if (expression instanceof PyStringLiteralExpression) {
        return;
      }

      final PyTryPart tryPart = PsiTreeUtil.getParentOfType(node, PyTryPart.class);
      if (tryPart != null) {
        final PyStatementList statementList = tryPart.getStatementList();
        if (statementList.getStatements().length == 1 && statementList.getStatements()[0] == node) {
          return;
        }
      }
      if (expression instanceof PyReferenceExpression && !((PyReferenceExpression)expression).isQualified()) {
        registerProblem(expression, PyBundle.message("INSP.NAME.statement.message"));
      }
      else {
        registerProblem(expression, PyBundle.message("INSP.NAME.statement.message"), new StatementEffectIntroduceVariableQuickFix());
      }
    }

    private boolean hasEffect(@Nullable PyExpression expression) {
      if (expression == null) {
        return false;
      }
      if (expression instanceof PyCallExpression || expression instanceof PyYieldExpression) {
        return true;
      }
      else if (expression instanceof PyListCompExpression) {
        if (hasEffect(((PyListCompExpression)expression).getResultExpression())) {
          return true;
        }
      }
      else if (expression instanceof PyBinaryExpression) {
        PyBinaryExpression binary = (PyBinaryExpression)expression;
        final PyExpression leftExpression = binary.getLeftExpression();
        final PyExpression rightExpression = binary.getRightExpression();
        if (hasEffect(leftExpression) || hasEffect(rightExpression)) {
          return true;
        }

        final PyElementType operator = binary.getOperator();
        String method = operator == null ? null : operator.getSpecialMethodName();
        if (method != null) {
          // maybe the op is overridden and may produce side effects, like cout << "hello"
          PyType type = myTypeEvalContext.getType(leftExpression);
          if (type != null &&
            !type.isBuiltin() &&
            type.resolveMember(method, null, AccessDirection.READ, getResolveContext()) != null) {
            return true;
          }
          if (rightExpression != null) {
            type = myTypeEvalContext.getType(rightExpression);
            if (type != null) {
              String rmethod = "__r" + method.substring(2); // __add__ -> __radd__
              if (!type.isBuiltin() && type.resolveMember(rmethod, null, AccessDirection.READ, getResolveContext()) != null) {
                return true;
              }
            }
          }
        }
      }
      else if (expression instanceof PyConditionalExpression) {
        PyConditionalExpression conditionalExpression = (PyConditionalExpression)expression;
        return hasEffect(conditionalExpression.getTruePart()) || hasEffect(conditionalExpression.getFalsePart());
      }
      else if (expression instanceof PyParenthesizedExpression) {
        PyParenthesizedExpression parenthesizedExpression = (PyParenthesizedExpression)expression;
        return hasEffect(parenthesizedExpression.getContainedExpression());
      }
      else if (expression instanceof PyReferenceExpression) {
        PyReferenceExpression referenceExpression = (PyReferenceExpression)expression;
        ResolveResult[] results = referenceExpression.getReference(getResolveContext()).multiResolve(true);
        for (ResolveResult res : results) {
          if (res.getElement() instanceof PyFunction) {
            registerProblem(expression,
                            "Statement seems to have no effect and can be replaced with function call to have effect",
                            new StatementEffectFunctionCallQuickFix());
            return true;
          }
        }
      }
      else if (expression instanceof PyTupleExpression) {
        PyExpression[] elements = ((PyTupleExpression)expression).getElements();
        for (PyExpression element : elements) {
          if (hasEffect(element)) {
            return true;
          }
        }
      }
      else if (expression instanceof PyPrefixExpression) {
        final PyPrefixExpression prefixExpr = (PyPrefixExpression)expression;
        return prefixExpr.getOperator() == PyTokenTypes.AWAIT_KEYWORD;
      }
      return false;
    }
  }
}
