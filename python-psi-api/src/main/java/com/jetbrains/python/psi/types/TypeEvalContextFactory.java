// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

@ServiceAPI(ComponentScope.APPLICATION)
public interface TypeEvalContextFactory {
    TypeEvalContext codeCompletion(Project project, @Nullable PsiFile origin);

    TypeEvalContext userInitiated(Project project, @Nullable PsiFile origin);

    TypeEvalContext codeAnalysis(Project project, @Nullable PsiFile origin);

    TypeEvalContext codeInsightFallback(@Nullable Project project);

    TypeEvalContext externalContext(Project project);

    TypeEvalContext deepCodeInsight(Project project);

    static TypeEvalContextFactory getInstance() {
        return Application.get().getInstance(TypeEvalContextFactory.class);
    }
}
