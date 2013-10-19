package com.jetbrains.python.inspections;

import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassLikeType;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 */
public class PyExceptionInheritInspection extends PyInspection {
  @Nls
  @NotNull
  @Override
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.exception.not.inherit");
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
    public void visitPyRaiseStatement(PyRaiseStatement node) {
      PyExpression[] expressions = node.getExpressions();
      if (expressions.length == 0) {
        return;
      }
      PyExpression expression = expressions[0];
      if (expression instanceof PyCallExpression) {
        PyExpression callee = ((PyCallExpression)expression).getCallee();
        if (callee instanceof PyReferenceExpression) {
          PsiElement psiElement = ((PyReferenceExpression)callee).getReference(resolveWithoutImplicits()).resolve();
          if (psiElement instanceof PyClass) {
            PyClass aClass = (PyClass) psiElement;
            for (PyClassLikeType type : aClass.getAncestorTypes(myTypeEvalContext)) {
              if (type == null) {
                return;
              }
              final String name = type.getName();
              if (name == null || "BaseException".equals(name) || "Exception".equals(name)) {
                return;
              }
            }
            registerProblem(expression, "Exception doesn't inherit from base \'Exception\' class");
          }
        }
      }
    }
  }
}