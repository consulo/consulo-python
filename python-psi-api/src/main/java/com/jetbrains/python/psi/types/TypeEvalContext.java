// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyTypedElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ProcessingContext;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

public abstract class TypeEvalContext {
    protected static final Logger logger = Logger.getInstance(TypeEvalContext.class);

    protected TypeEvalContext() {
    }

    public abstract boolean getUsesExternalTypeEngine();

    public abstract ProcessingContext getProcessingContext();

    public abstract @Nullable PsiFile getOrigin();

    public abstract boolean allowDataFlow(PsiElement element);

    public abstract boolean allowReturnTypes(PsiElement element);

    public abstract boolean allowCallContext(PsiElement element);

    public abstract boolean maySwitchToAST(PsiElement element);

    public boolean isExternal() {
        return false;
    }

    public abstract TypeEvalContext withTracing();

    public abstract void trace(String message, @Nullable Object... args);

    public abstract void traceIndent();

    public abstract void traceUnindent();

    public abstract String printTrace();

    public abstract boolean tracing();

    public abstract void traceWithIndent(String message, Runnable block);

    public abstract <R> @Nullable R assumeType(
        PyTypedElement element,
        @Nullable PyType type,
        Function<@Nullable TypeEvalContext, R> func
    );

    public abstract boolean hasAssumptions();

    public abstract @Nullable PyType getType(PyTypedElement element);

    public abstract @Nullable PyType getReturnType(PyCallable callable);

    public abstract Map<Pair<Object, Object>, @Nullable PyType> getContextTypeCache();

    public abstract Map<PyTypeParameterType, PyVariance> getVarianceCache();

    public abstract @Nullable PyType getKnownType(PyTypedElement element);

    public abstract @Nullable PyType getKnownReturnType(PyCallable callable);

    /**
     * This class ensures that only {@link TypeEvalContext} instances can directly invoke
     * {@link PyTypedElement#getType} and everybody else has to
     * access its result though {@link #getType} or {@link #getReturnType}.
     * Hence, the inferred type information cannot bypass caching in {@link TypeEvalContext}.
     */
    public abstract static sealed class Key permits KeyImpl {
        private Key() {
        }
    }

    protected static final class KeyImpl extends Key {
        public static final KeyImpl INSTANCE = new KeyImpl();

        private KeyImpl() {
        }
    }

    /**
     * Create a context for code completion.
     * <p>
     * <p>
     * It is as detailed as {@link TypeEvalContext#userInitiated}, but allows inferring types based on the context in which
     * the analyzed code was called or may be called. Since this is basically guesswork, the results should be used only for code completion.
     */
    public static TypeEvalContext codeCompletion(Project project, @Nullable PsiFile origin) {
        return TypeEvalContextFactory.getInstance().codeCompletion(project, origin);
    }

    /**
     * Create the most detailed type evaluation context for user-initiated actions.
     * <p>
     * <p>
     * Should be used for "goto definition", "find usages", refactorings, documentation.
     * <p>
     * <p>
     * For code completion see {@link TypeEvalContext#codeCompletion}.
     */
    public static TypeEvalContext userInitiated(Project project, @Nullable PsiFile origin) {
        return TypeEvalContextFactory.getInstance().userInitiated(project, origin);
    }

    /**
     * Create a type evaluation context for performing analysis operations on the specified file which is currently open in the editor,
     * without accessing stubs. For such a file, additional slow operations are allowed.
     * <p>
     * <p>
     * Inspections should not create a new type evaluation context. They should re-use the context of the inspection session.
     */
    public static TypeEvalContext codeAnalysis(Project project, @Nullable PsiFile origin) {
        return TypeEvalContextFactory.getInstance().codeAnalysis(project, origin);
    }

    /**
     * Create the most shallow type evaluation context for code insight purposes when other more detailed contexts are not available.
     * It's use should be minimized.
     *
     * @param project pass project here to enable cache. Pass null if you do not have any project.
     *                <b>Always</b> do your best to pass project here: it increases performance!
     */
    public static TypeEvalContext codeInsightFallback(@Nullable Project project) {
        return TypeEvalContextFactory.getInstance().codeInsightFallback(project);
    }


    /**
     * Special context to converting types from an external type checker, more aggressive assumptions can be made.
     */
    public static TypeEvalContext externalContext(Project project) {
        return TypeEvalContextFactory.getInstance().externalContext(project);
    }

    /**
     * Create a type evaluation context for deeper and slower code insight.
     * <p>
     * <p>
     * Should be used only when normal code insight context is not enough for getting good results.
     */
    public static TypeEvalContext deepCodeInsight(Project project) {
        return TypeEvalContextFactory.getInstance().deepCodeInsight(project);
    }
}
