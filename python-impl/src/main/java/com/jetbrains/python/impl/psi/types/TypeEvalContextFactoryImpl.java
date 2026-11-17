// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.psi.types.TypeEvalContextCache;
import com.jetbrains.python.psi.types.TypeEvalContextFactory;
import consulo.annotation.component.ServiceImpl;
import consulo.application.util.registry.Registry;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

@ServiceImpl
@Singleton
public class TypeEvalContextFactoryImpl implements TypeEvalContextFactory {
    @Override
    public TypeEvalContext codeCompletion(Project project, @Nullable PsiFile origin) {
        return getContextFromCache(project, new TypeEvalContextImpl(true, true, true, false, origin));
    }

    @Override
    public TypeEvalContext userInitiated(Project project, @Nullable PsiFile origin) {
        return getContextFromCache(project, new TypeEvalContextImpl(true, true, false, false, origin));
    }

    @Override
    public TypeEvalContext codeAnalysis(Project project, @Nullable PsiFile origin) {
        return getContextFromCache(project, buildCodeAnalysisContext(origin));
    }

    @Override
    public TypeEvalContext codeInsightFallback(@Nullable Project project) {
        TypeEvalContextImpl anchor = new TypeEvalContextImpl(false, false, false, false, null);
        if (project != null) {
            return getContextFromCache(project, anchor);
        }
        return anchor;
    }

    @Override
    public TypeEvalContext externalContext(Project project) {
        TypeEvalContextImpl anchor = new TypeEvalContextImpl(false, false, false, true, null);
        return getContextFromCache(project, anchor);
    }

    @Override
    public TypeEvalContext deepCodeInsight(Project project) {
        return getContextFromCache(project, new TypeEvalContextImpl(false, true, false, false, null));
    }

    /**
     * Moves context through cache returning one from cache (if exists).
     *
     * @param project current project
     * @param context context to fetch from cache
     * @return context to use
     * @see TypeEvalContextCache#getContext
     */
    private static TypeEvalContext getContextFromCache(Project project, TypeEvalContext context) {
        return project.getInstance(TypeEvalContextCache.class).getContext(context);
    }

    private static TypeEvalContext buildCodeAnalysisContext(@Nullable PsiFile origin) {
        if (Registry.is("python.optimized.type.eval.context", true)) {
            return new TypeEvalContextImpl.OptimizedTypeEvalContext(false, false, false, origin);
        }
        return new TypeEvalContextImpl(false, false, false, false, origin);
    }
}
