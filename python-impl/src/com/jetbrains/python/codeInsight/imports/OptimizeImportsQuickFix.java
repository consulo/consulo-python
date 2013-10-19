package com.jetbrains.python.codeInsight.imports;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.ImportOptimizer;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class OptimizeImportsQuickFix implements LocalQuickFix {
  @NotNull
  public String getName() {
    return "Optimize imports";
  }

  @NotNull
  public String getFamilyName() {
    return "Optimize imports";
  }

  public void applyFix(@NotNull final Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element == null) {  // stale PSI
      return;
    }
    final PsiFile file = element.getContainingFile();
    ImportOptimizer optimizer = new PyImportOptimizer();
    final Runnable runnable = optimizer.processFile(file);
    new WriteCommandAction.Simple(project, getFamilyName(), file) {
      @Override
      protected void run() throws Throwable {
        runnable.run();
      }
    }.execute();
  }
}
