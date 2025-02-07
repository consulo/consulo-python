/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.quickfix.PyMakeFunctionFromMethodQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyMakeMethodStaticQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.impl.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Locale;

/**
 * User: ktisha
 */
@ExtensionImpl
public class PyMethodMayBeStaticInspection extends PyInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.method.may.be.static");
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
    public void visitPyFunction(PyFunction node) {
      if (PyNames.getBuiltinMethods(LanguageLevel.forElement(node)).containsKey(node.getName())) {
        return;
      }
      final PyClass containingClass = node.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final PsiElement firstSuper = PySuperMethodsSearch.search(node, myTypeEvalContext).findFirst();
      if (firstSuper != null) {
        return;
      }
      final PyFunction firstOverride = PyOverridingMethodsSearch.search(node, true).findFirst();
      if (firstOverride != null) {
        return;
      }
      final PyDecoratorList decoratorList = node.getDecoratorList();
      if (decoratorList != null) {
        return;
      }
      if (node.getModifier() != null) {
        return;
      }
      final Property property = containingClass.findPropertyByCallable(node);
      if (property != null) {
        return;
      }
      final List<PyAssignmentStatement> attributes = node.findAttributes();
      if (!attributes.isEmpty()) {
        return;
      }
      if (isTestElement(node)) {
        return;
      }

      final PyStatementList statementList = node.getStatementList();
      final PyStatement[] statements = statementList.getStatements();

      if (statements.length == 1 && statements[0] instanceof PyPassStatement) {
        return;
      }

      final PyParameter[] parameters = node.getParameterList().getParameters();

      final String selfName;
      if (parameters.length > 0) {
        final String name = parameters[0].getName();
        selfName = name != null ? name : parameters[0].getText();
      }
      else {
        selfName = PyNames.CANONICAL_SELF;
      }

      final boolean[] mayBeStatic = {true};
      PyRecursiveElementVisitor visitor = new PyRecursiveElementVisitor() {
        @Override
        public void visitPyRaiseStatement(PyRaiseStatement node) {
          super.visitPyRaiseStatement(node);
          final PyExpression[] expressions = node.getExpressions();
          if (expressions.length == 1) {
            final PyExpression expression = expressions[0];
            if (expression instanceof PyCallExpression) {
              final PyExpression callee = ((PyCallExpression)expression).getCallee();
              if (callee != null && PyNames.NOT_IMPLEMENTED_ERROR.equals(callee.getText())) {
                mayBeStatic[0] = false;
              }
            }
            else if (PyNames.NOT_IMPLEMENTED_ERROR.equals(expression.getText())) {
              mayBeStatic[0] = false;
            }
          }
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
          super.visitPyReferenceExpression(node);
          if (selfName.equals(node.getName())) {
            mayBeStatic[0] = false;
          }
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
          super.visitPyCallExpression(node);
          if (LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON30) && node.isCalleeText(PyNames.SUPER)) {
            mayBeStatic[0] = false;
          }
        }
      };
      node.accept(visitor);
      final PsiElement identifier = node.getNameIdentifier();
      if (mayBeStatic[0] && identifier != null) {
        registerProblem(identifier,
                        PyBundle.message("INSP.method.may.be.static"),
                        ProblemHighlightType.WEAK_WARNING,
                        null,
                        new PyMakeMethodStaticQuickFix(),
                        new
                          PyMakeFunctionFromMethodQuickFix());
      }
    }
  }

  private static boolean isTestElement(@Nonnull PyFunction node) {
    final String methodName = node.getName();
    final PyClass pyClass = node.getContainingClass();
    final String className = pyClass == null ? null : pyClass.getName();

    return methodName != null && className != null && methodName.toLowerCase(Locale.getDefault())
                                                                .startsWith("test") && className.toLowerCase(Locale.getDefault())
                                                                                                .startsWith("test");
  }
}
