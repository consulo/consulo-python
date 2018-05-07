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

package com.jetbrains.python.inspections;

import javax.annotation.Nonnull;

import org.jetbrains.annotations.Nls;

import javax.annotation.Nullable;
import com.intellij.codeInspection.LocalInspectionToolSession;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.python.PyBundle;
import com.jetbrains.python.psi.PyFile;

/**
 *
 * User: ktisha
 */
public class PyInterpreterInspection extends PyInspection {

  @Nls
  @Nonnull
  public String getDisplayName() {
    return PyBundle.message("INSP.NAME.invalid.interpreter");
  }

  @Nonnull
  @Override
  public PsiElementVisitor buildVisitor(@Nonnull ProblemsHolder holder,
                                        final boolean isOnTheFly,
                                        @Nonnull final LocalInspectionToolSession session) {
    return new Visitor(holder, session);
  }

  public static class Visitor extends PyInspectionVisitor {

    public Visitor(@Nullable ProblemsHolder holder,
                   @Nonnull LocalInspectionToolSession session) {
      super(holder, session);
    }

    @Override
    public void visitPyFile(PyFile node) {
      super.visitPyFile(node);
      /*if (PlatformUtils.isPyCharm()) {
        final Module module = ModuleUtilCore.findModuleForPsiElement(node);
        if (module != null) {
          final Sdk sdk = PythonSdkType.findPythonSdk(module);
          if (sdk == null) {
            registerProblem(node, "No Python interpreter configured for the project", new ConfigureInterpreterFix());
          }
          else if (PythonSdkType.isInvalid(sdk)) {
            registerProblem(node, "Invalid Python interpreter selected for the project", new ConfigureInterpreterFix());
          }
        }
      } */
    }
  }

  private static class ConfigureInterpreterFix implements LocalQuickFix {
    @Nonnull
    @Override
    public String getName() {
      return "Configure Python Interpreter";
    }

    @Nonnull
    @Override
    public String getFamilyName() {
      return "Configure Python Interpreter";
    }

    @Override
    public void applyFix(@Nonnull final Project project, @Nonnull ProblemDescriptor descriptor) {
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          // outside of read action
          ShowSettingsUtil.getInstance().showSettingsDialog(project, "Project Interpreter");
        }
      });
    }
  }
}
