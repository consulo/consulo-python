package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.util.PsiTreeUtil;
import com.jetbrains.python.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.ResolveProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Warns about shadowing names defined in outer scopes.
 *
 * @author vlan
 */
public class PyShadowingNamesInspection extends PyInspection {
  @NotNull
  @Override
  public String getDisplayName() {
    return "Shadowing names from outer scopes";
  }

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder,
                                        boolean isOnTheFly,
                                        @NotNull LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  private static class Visitor extends PyInspectionVisitor {
    public Visitor(@Nullable ProblemsHolder holder, @NotNull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyClass(@NotNull PyClass node) {
      processElement(node);
    }

    @Override
    public void visitPyFunction(@NotNull PyFunction node) {
      processElement(node);
    }

    @Override
    public void visitPyNamedParameter(@NotNull PyNamedParameter node) {
      if (node.isSelf()) {
        return;
      }
      processElement(node);
    }

    @Override
    public void visitPyTargetExpression(@NotNull PyTargetExpression node) {
      if (node.getQualifier() == null) {
        processElement(node);
      }
    }

    private void processElement(@NotNull PsiNameIdentifierOwner element) {
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
            final ResolveProcessor processor = new ResolveProcessor(name);
            PyResolveUtil.scopeCrawlUp(processor, nextOwner, null, name, null);
            final PsiElement resolved = processor.getResult();
            if (resolved != null) {
              final PyComprehensionElement comprehension = PsiTreeUtil.getParentOfType(resolved, PyComprehensionElement.class);
              if (comprehension != null && PyUtil.isOwnScopeComprehension(comprehension)) {
                return;
              }
              final Scope scope = ControlFlowCache.getScope(owner);
              if (scope.isGlobal(name) || scope.isNonlocal(name)) {
                return;
              }
              registerProblem(problemElement, String.format("Shadows name '%s' from outer scope", name),
                              ProblemHighlightType.WEAK_WARNING, null, new PyRenameElementQuickFix());
            }
          }
        }
      }
    }
  }
}
