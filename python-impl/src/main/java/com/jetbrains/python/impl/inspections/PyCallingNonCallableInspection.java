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

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class PyCallingNonCallableInspection extends PyInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return "Trying to call a non-callable object";
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @Nonnull LocalInspectionToolSession session,
                                        Object state) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
      super.visitPyCallExpression(node);
      checkCallable(node, node.getCallee(), null);
    }

    @Override
    public void visitPyDecoratorList(PyDecoratorList node) {
      super.visitPyDecoratorList(node);
      for (PyDecorator decorator : node.getDecorators()) {
        final PyExpression callee = decorator.getCallee();
        checkCallable(decorator, callee, null);
        if (decorator.hasArgumentList()) {
          checkCallable(decorator, decorator, null);
        }
      }
    }

    private void checkCallable(@Nonnull PyElement node, @Nullable PyExpression callee, @Nullable PyType type) {
      final Boolean callable = callee != null ? isCallable(callee, myTypeEvalContext) : PyTypeChecker.isCallable(type);
      if (callable == null) {
        return;
      }
      if (!callable) {
        final PyType calleeType = callee != null ? myTypeEvalContext.getType(callee) : type;
        if (calleeType instanceof PyClassType) {
          registerProblem(node, String.format("'%s' object is not callable", calleeType.getName()));
        }
        else if (callee != null) {
          registerProblem(node, String.format("'%s' is not callable", callee.getName()));
        }
        else {
          registerProblem(node, "Expression is not callable");
        }
      }
    }
  }

  @Nullable
  private static Boolean isCallable(@Nonnull PyExpression element, @Nonnull TypeEvalContext context) {
    if (element instanceof PyQualifiedExpression && PyNames.CLASS.equals(element.getName())) {
      return true;
    }
    return PyTypeChecker.isCallable(context.getType(element));
  }
}
