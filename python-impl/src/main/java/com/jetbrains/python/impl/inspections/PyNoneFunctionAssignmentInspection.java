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
import com.jetbrains.python.impl.inspections.quickfix.PyRemoveAssignmentQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.impl.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.impl.psi.types.PyNoneType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * User: ktisha
 * <p>
 * pylint E1111
 * <p>
 * Used when an assignment is done on a function call but the inferred function doesn't return anything.
 */
@ExtensionImpl
public class PyNoneFunctionAssignmentInspection extends PyInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.none.function.assignment");
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
    private final Map<PyFunction, Boolean> myHasInheritors = new HashMap<>();

    public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
      final PyExpression value = node.getAssignedValue();
      if (value instanceof PyCallExpression) {
        final PyType type = myTypeEvalContext.getType(value);
        final PyCallExpression callExpr = (PyCallExpression)value;
        final PyExpression callee = callExpr.getCallee();

        if (type instanceof PyNoneType && callee != null) {
          final PyCallable callable = callExpr.resolveCalleeFunction(getResolveContext());
          if (callable != null) {
            if (PySdkUtil.isElementInSkeletons(callable)) {
              return;
            }
            if (callable instanceof PyFunction) {
              final PyFunction function = (PyFunction)callable;
              // Currently we don't infer types returned by decorators
              if (hasInheritors(function) || PyUtil.hasCustomDecorators(function)) {
                return;
              }
            }
            registerProblem(node, PyBundle.message("INSP.none.function.assignment", callee.getName()), new PyRemoveAssignmentQuickFix());
          }
        }
      }
    }

    private boolean hasInheritors(@Nonnull PyFunction function) {
      final Boolean cached = myHasInheritors.get(function);
      if (cached != null) {
        return cached;
      }
      final boolean result = PyOverridingMethodsSearch.search(function, true).findFirst() != null;
      myHasInheritors.put(function, result);
      return result;
    }
  }
}
