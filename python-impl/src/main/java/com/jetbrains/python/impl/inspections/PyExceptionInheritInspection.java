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
import com.jetbrains.python.impl.inspections.quickfix.PyAddExceptionSuperClassQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiPolyVariantReference;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author Alexey.Ivanov
 */
@ExtensionImpl
public class PyExceptionInheritInspection extends PyInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.exception.not.inherit");
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
    public void visitPyRaiseStatement(PyRaiseStatement node) {
      PyExpression[] expressions = node.getExpressions();
      if (expressions.length == 0) {
        return;
      }
      PyExpression expression = expressions[0];
      if (expression instanceof PyCallExpression) {
        PyExpression callee = ((PyCallExpression)expression).getCallee();
        if (callee instanceof PyReferenceExpression) {
          final PsiPolyVariantReference reference = ((PyReferenceExpression)callee).getReference(getResolveContext());
          if (reference == null) {
            return;
          }
          PsiElement psiElement = reference.resolve();
          if (psiElement instanceof PyClass) {
            PyClass aClass = (PyClass)psiElement;
            for (PyClassLikeType type : aClass.getAncestorTypes(myTypeEvalContext)) {
              if (type == null) {
                return;
              }
              final String name = type.getName();
              if (name == null || "BaseException".equals(name) || "Exception".equals(name)) {
                return;
              }
            }
            registerProblem(expression, "Exception doesn't inherit from base \'Exception\' class", new PyAddExceptionSuperClassQuickFix());
          }
        }
      }
    }
  }
}
