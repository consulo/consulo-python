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
package com.jetbrains.python.inspections;

import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveProcessor;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.util.PsiTreeUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Warns about shadowing names defined in outer scopes.
 *
 * @author vlan
 */
@ExtensionImpl
public class PyShadowingNamesInspection extends PyInspection {
  @Nonnull
  @Override
  public String getDisplayName() {
    return "Shadowing names from outer scopes";
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
    public void visitPyClass(@Nonnull PyClass node) {
      processElement(node);
    }

    @Override
    public void visitPyFunction(@Nonnull PyFunction node) {
      processElement(node);
    }

    @Override
    public void visitPyNamedParameter(@Nonnull PyNamedParameter node) {
      if (node.isSelf()) {
        return;
      }
      processElement(node);
    }

    @Override
    public void visitPyTargetExpression(@Nonnull PyTargetExpression node) {
      if (!node.isQualified()) {
        processElement(node);
      }
    }

    private void processElement(@Nonnull PsiNameIdentifierOwner element) {
      final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
      if (owner instanceof PyClass) {
        return;
      }
      final String name = element.getName();
      if (name != null) {
        final PsiElement identifier = element.getNameIdentifier();
        final PsiElement problemElement = identifier != null ? identifier : element;
        if ("_".equals(name)) {
          return;
        }
        if (owner != null) {
          final ScopeOwner nextOwner = ScopeUtil.getScopeOwner(owner);
          if (nextOwner != null) {
            final PyResolveProcessor processor = new PyResolveProcessor(name);
            PyResolveUtil.scopeCrawlUp(processor, nextOwner, null, name, null);
            for (PsiElement resolved : processor.getElements()) {
              if (resolved != null) {
                final PyComprehensionElement comprehension = PsiTreeUtil.getParentOfType(resolved, PyComprehensionElement.class);
                if (comprehension != null && PyUtil.isOwnScopeComprehension(comprehension)) {
                  return;
                }
                final Scope scope = ControlFlowCache.getScope(owner);
                if (scope.isGlobal(name) || scope.isNonlocal(name)) {
                  return;
                }
                registerProblem(problemElement,
                                String.format("Shadows name '%s' from outer scope", name),
                                ProblemHighlightType.WEAK_WARNING,
                                null,
                                new PyRenameElementQuickFix());
                return;
              }
            }
          }
        }
      }
    }
  }
}

