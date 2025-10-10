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

import com.jetbrains.python.psi.PyFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author ktisha
 */
@ExtensionImpl
public class PyInterpreterInspection extends PyInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameInvalidInterpreter();
    }

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        final boolean isOnTheFly,
        @Nonnull final LocalInspectionToolSession session,
        Object state
    ) {
        return new Visitor(holder, session);
    }

    public static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, @Nonnull LocalInspectionToolSession session) {
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
        public LocalizeValue getName() {
            return LocalizeValue.localizeTODO("Configure Python Interpreter");
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
