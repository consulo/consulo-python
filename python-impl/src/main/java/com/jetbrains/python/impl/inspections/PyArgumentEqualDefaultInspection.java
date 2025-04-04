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
import com.jetbrains.python.impl.inspections.quickfix.RemoveArgumentEqualDefaultQuickFix;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyClassType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiReference;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: catherine
 * <p>
 * Inspection to detect situations, where argument passed to function
 * is equal to default parameter value
 * for instance,
 * dict().get(x, None) --> None is default value for second param in dict().get function
 */
@ExtensionImpl
public class PyArgumentEqualDefaultInspection extends PyInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.argument.equal.default");
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
  public boolean isEnabledByDefault() {
    return false;
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node) {
      PyArgumentList list = node.getArgumentList();
      if (list == null) {
        return;
      }
      PyCallable func = node.resolveCalleeFunction(getResolveContext());
      if (func != null && hasSpecialCasedDefaults(func, node)) {
        return;
      }
      checkArguments(node, node.getArguments());
    }

    @Override
    public void visitPyDecoratorList(final PyDecoratorList node) {
      PyDecorator[] decorators = node.getDecorators();

      for (PyDecorator decorator : decorators) {
        if (decorator.hasArgumentList()) {
          PyExpression[] arguments = decorator.getArguments();
          checkArguments(decorator, arguments);
        }
      }
    }

    private static boolean hasSpecialCasedDefaults(PyCallable callable, PsiElement anchor) {
      final String name = callable.getName();
      final PyBuiltinCache cache = PyBuiltinCache.getInstance(anchor);
      if ("getattr".equals(name) && cache.isBuiltin(callable)) {
        return true;
      }
      else if ("get".equals(name) || "pop".equals(name)) {
        final PyFunction method = callable.asMethod();
        final PyClassType dictType = cache.getDictType();
        if (method != null && dictType != null && method.getContainingClass() == dictType.getPyClass()) {
          return true;
        }
      }
      return false;
    }

    private void checkArguments(PyCallExpression callExpr, PyExpression[] arguments) {
      final PyCallExpression.PyArgumentsMapping mapping = callExpr.mapArguments(getResolveContext());
      Set<PyExpression> problemElements = new HashSet<>();
      for (Map.Entry<PyExpression, PyNamedParameter> e : mapping.getMappedParameters().entrySet()) {
        PyExpression defaultValue = e.getValue().getDefaultValue();
        if (defaultValue != null) {
          PyExpression key = e.getKey();
          if (key instanceof PyKeywordArgument && ((PyKeywordArgument)key).getValueExpression() != null) {
            key = ((PyKeywordArgument)key).getValueExpression();
          }
          if (isEqual(key, defaultValue)) {
            problemElements.add(e.getKey());
          }
        }
      }
      boolean canDelete = true;
      for (int i = arguments.length - 1; i != -1; --i) {
        if (problemElements.contains(arguments[i])) {
          if (canDelete) {
            registerProblem(arguments[i],
                            PyBundle.message("INSP.argument.equals.to.default"),
                            new RemoveArgumentEqualDefaultQuickFix(problemElements));
          }
          else {
            registerProblem(arguments[i], PyBundle.message("INSP.argument.equals.to.default"));
          }

        }
        else if (!(arguments[i] instanceof PyKeywordArgument)) {
          canDelete = false;
        }
      }
    }

    private boolean isEqual(PyExpression key, PyExpression defaultValue) {
      if (isBothInstanceOf(key, defaultValue, PyNumericLiteralExpression.class) ||
        isBothInstanceOf(key, defaultValue, PyPrefixExpression.class) || isBothInstanceOf(key, defaultValue, PyBinaryExpression.class)) {
        if (key.getText().equals(defaultValue.getText())) {
          return true;
        }
      }
      else if (key instanceof PyStringLiteralExpression && defaultValue instanceof PyStringLiteralExpression) {
        if (((PyStringLiteralExpression)key).getStringValue().equals(((PyStringLiteralExpression)defaultValue).getStringValue())) {
          return true;
        }
      }
      else {
        PsiReference keyRef =
          key instanceof PyReferenceExpression ? ((PyReferenceExpression)key).getReference(getResolveContext()) : key.getReference();
        PsiReference defRef =
          defaultValue instanceof PyReferenceExpression ? ((PyReferenceExpression)defaultValue).getReference(getResolveContext()) : defaultValue
            .getReference();
        if (keyRef != null && defRef != null) {
          PsiElement keyResolve = keyRef.resolve();
          PsiElement defResolve = defRef.resolve();
          if (keyResolve != null && keyResolve.equals(defResolve)) {
            return true;
          }
        }
      }
      return false;
    }

    private static boolean isBothInstanceOf(@Nonnull final PyExpression key,
                                            @Nonnull final PyExpression defaultValue,
                                            @Nonnull final Class clazz) {
      return clazz.isInstance(key) && clazz.isInstance(defaultValue);
    }
  }
}
