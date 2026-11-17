// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.application.ApplicationManager;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.psi.PsiManager;
import consulo.project.Project;
import consulo.project.ProjectManager;

public final class PyTypeAnalysisRestartKt {
    private PyTypeAnalysisRestartKt() {
    }

    /**
     * Runs the analysis again in each open project after a setting that changes the result of the type inference.
     * <p>
     * The cached types must go before the analysis runs again. {@link PsiManager#dropPsiCaches} drops them, and
     * {@link DaemonCodeAnalyzer#restart} then highlights the open files with the new setting.
     */
    static void restartTypeAnalysis(String reason) {
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            return;
        }

        ApplicationManager.getApplication().invokeLater(() -> {
            for (Project project : ProjectManager.getInstance().getOpenProjects()) {
                if (project.isDisposed()) {
                    continue;
                }
                PsiManager.getInstance(project).dropPsiCaches();
                DaemonCodeAnalyzer.getInstance(project).restart(reason);
            }
        });
    }
}
