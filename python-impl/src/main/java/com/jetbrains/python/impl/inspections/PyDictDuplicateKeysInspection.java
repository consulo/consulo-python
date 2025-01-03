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

import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import org.jetbrains.annotations.Nls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * User: catherine
 * <p>
 * Inspection to detect using the same value as dictionary key twice.
 */
@ExtensionImpl
public class PyDictDuplicateKeysInspection extends PyInspection {
  @Nls
  @Nonnull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.duplicate.keys");
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
    public void visitPyDictLiteralExpression(PyDictLiteralExpression node) {
      if (node.getElements().length != 0) {
        final Map<String, PyElement> map = new HashMap<String, PyElement>();
        for (PyExpression exp : node.getElements()) {
          final PyExpression key = ((PyKeyValueExpression)exp).getKey();
          if (key instanceof PyNumericLiteralExpression
            || key instanceof PyStringLiteralExpression || key instanceof PyReferenceExpression) {
            if (map.keySet().contains(key.getText())) {
              registerProblem(key, "Dictionary contains duplicate keys " + key.getText());
              registerProblem(map.get(key.getText()), "Dictionary contains duplicate keys " + key.getText());
            }
            map.put(key.getText(), key);
          }
        }
      }
    }

    @Override
    public void visitPyCallExpression(final PyCallExpression node) {
      if (isDict(node)) {
        final Map<String, PsiElement> map = new HashMap<String, PsiElement>();
        final PyArgumentList pyArgumentList = node.getArgumentList();
        if (pyArgumentList == null) return;
        final PyExpression[] arguments = pyArgumentList.getArguments();
        for (PyExpression argument : arguments) {
          if (argument instanceof PyParenthesizedExpression)
            argument = ((PyParenthesizedExpression)argument).getContainedExpression();
          if (argument instanceof PySequenceExpression) {
            for (PyElement el : ((PySequenceExpression)argument).getElements()) {
              final PsiElement key = getKey(el);
              checkKey(map, key);
            }
          }
          else {
            final PsiElement key = getKey(argument);
            checkKey(map, key);
          }
        }
      }
    }

    private void checkKey(final Map<String, PsiElement> map, final PsiElement node) {
      if (node == null) return;
      String key = node.getText();
      if (node instanceof PyStringLiteralExpression)
        key = ((PyStringLiteralExpression)node).getStringValue();
      if (map.keySet().contains(key)) {
        registerProblem(node, "Dictionary contains duplicate keys " + key);
        registerProblem(map.get(key), "Dictionary contains duplicate keys " + key);
      }
      map.put(key, node);
    }

    @Nullable
    private static PsiElement getKey(final PyElement argument) {
      if (argument instanceof PyParenthesizedExpression) {
        final PyExpression expr = ((PyParenthesizedExpression)argument).getContainedExpression();
        if (expr instanceof PyTupleExpression) {
          return ((PyTupleExpression)expr).getElements()[0];
        }
      }
      if (argument instanceof PyKeywordArgument) {
        ASTNode keyWord = ((PyKeywordArgument)argument).getKeywordNode();
        if (keyWord != null) return keyWord.getPsi();
      }
      return null;
    }

    private static boolean isDict(final PyCallExpression expression) {
      final PyExpression callee = expression.getCallee();
      if (callee == null) return false;
      final String name = callee.getText();
      if ("dict".equals(name)) {
        return true;
      }
      return false;
    }

  }
}
