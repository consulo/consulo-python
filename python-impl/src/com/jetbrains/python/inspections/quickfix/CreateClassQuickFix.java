package com.jetbrains.python.inspections.quickfix;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.template.TemplateBuilder;
import com.intellij.codeInsight.template.TemplateBuilderFactory;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class CreateClassQuickFix implements LocalQuickFix {
  private final String myClassName;
  private final PsiElement myAnchor;

  public CreateClassQuickFix(String className, PsiElement anchor) {
    myClassName = className;
    myAnchor = anchor;
  }

  @NotNull
  public String getName() {
    if (myAnchor instanceof PyFile) {
      return "Create class '" + myClassName + "' in module " + ((PyFile)myAnchor).getName();
    }
    return "Create class '" + myClassName + "'";
  }

  @NotNull
  public String getFamilyName() {
    return "Create Class";
  }

  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement anchor = myAnchor;
    if (!anchor.isValid()) {
      return;
    }
    if (!(anchor instanceof PyFile)) {
      while(!(anchor.getParent() instanceof PyFile)) {
        anchor = anchor.getParent();
      }
    }
    PyClass pyClass = PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyClass.class,
                                                                                           "class " + myClassName + "(object):\n    pass");
    if (anchor instanceof PyFile) {
      pyClass = (PyClass) anchor.add(pyClass);
    }
    else {
      pyClass = (PyClass) anchor.getParent().addBefore(pyClass, anchor);
    }
    pyClass = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(pyClass);
    TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(pyClass);
    builder.replaceElement(pyClass.getSuperClassExpressions() [0], "object");
    builder.replaceElement(pyClass.getStatementList(), PyNames.PASS);
    builder.run();
  }

}
