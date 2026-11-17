// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.highlighting;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyNonlocalStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.DefaultLanguageHighlighterColors;
import consulo.colorScheme.TextAttributesKey;
import consulo.language.editor.rawHighlight.HighlightVisitor;
import consulo.language.editor.rawHighlight.RainbowVisitor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Consulo: registered through {@link PyRainbowVisitorFactory}.
 */
public class PyRainbowVisitor extends RainbowVisitor {

    public static final class Holder {
        public static final Set<String> IGNORED_NAMES = Set.of(PyNames.NONE, PyNames.TRUE, PyNames.FALSE);
        public static final TextAttributesKey DEFAULT_HIGHLIGHTING_KEY = DefaultLanguageHighlighterColors.LOCAL_VARIABLE;

        public static final Set<TextAttributesKey> HIGHLIGHTING_KEYS = Set.of(PyHighlighter.PY_PARAMETER, DEFAULT_HIGHLIGHTING_KEY);

        private Holder() {
        }

        public static Set<TextAttributesKey> getHIGHLIGHTING_KEYS() {
            return HIGHLIGHTING_KEYS;
        }
    }

    public boolean suitableForFile(PsiFile psiFile) {
        return psiFile instanceof PyFile;
    }

    @Override
    @RequiredReadAction
    public void visit(PsiElement element) {
        if (element instanceof PyReferenceExpression referenceExpression) {
            processReference(referenceExpression);
        }
        else if (element instanceof PyTargetExpression targetExpression) {
            processTarget(targetExpression);
        }
        else if (element instanceof PyNamedParameter namedParameter) {
            processNamedParameter(namedParameter);
        }
    }

    @Override
    public HighlightVisitor clone() {
        return new PyRainbowVisitor();
    }

    @RequiredReadAction
    private void processReference(PyReferenceExpression referenceExpression) {
        PsiElement context = getReferenceContext(referenceExpression, new HashSet<>());
        if (context == null) {
            return;
        }
        String name = updateNameIfGlobal(context, referenceExpression.getName());
        if (name == null) {
            return;
        }

        addInfo(context, referenceExpression, name);
    }

    @RequiredReadAction
    private void processTarget(PyTargetExpression targetExpression) {
        PsiElement context = getTargetContext(targetExpression);
        if (context == null) {
            return;
        }
        String name = updateNameIfGlobal(context, targetExpression.getName());
        if (name == null) {
            return;
        }

        addInfo(context, targetExpression, name);
    }

    @RequiredReadAction
    private void processNamedParameter(PyNamedParameter namedParameter) {
        PsiElement context = getNamedParameterContext(namedParameter);
        if (context == null) {
            return;
        }
        String name = namedParameter.getName();
        if (name == null) {
            return;
        }
        PsiElement element = namedParameter.getNameIdentifier();
        if (element == null) {
            return;
        }

        addInfo(context, element, name, PyHighlighter.PY_PARAMETER);
    }

    @RequiredReadAction
    private static @Nullable PsiElement getReferenceContext(PyReferenceExpression referenceExpression,
                                                           Set<PyReferenceExpression> visitedReferenceExpressions) {
        if (referenceExpression.isQualified() || Holder.IGNORED_NAMES.contains(referenceExpression.getName())) {
            return null;
        }

        PsiElement resolved = referenceExpression.getReference().resolve();
        if (resolved instanceof PyTargetExpression targetExpression) {
            return getTargetContext(targetExpression);
        }
        else if (resolved instanceof PyNamedParameter namedParameter) {
            return getNamedParameterContext(namedParameter);
        }
        else if (resolved instanceof PyReferenceExpression resolvedReference) {
            if (!visitedReferenceExpressions.add(resolvedReference)) {
                return getLeastCommonScope(visitedReferenceExpressions);
            }
            return resolvedReference.getParent() instanceof PyAugAssignmentStatement
                ? getReferenceContext(resolvedReference, visitedReferenceExpressions)
                : null;
        }
        else {
            return null;
        }
    }

    @RequiredReadAction
    private static @Nullable PsiElement getTargetContext(PyTargetExpression targetExpression) {
        if (targetExpression.isQualified() || Holder.IGNORED_NAMES.contains(targetExpression.getName())) {
            return null;
        }

        PsiElement parent = targetExpression.getParent();
        if (parent instanceof PyGlobalStatement) {
            return targetExpression.getContainingFile();
        }
        if (parent instanceof PyNonlocalStatement) {
            PsiElement outerResolved = targetExpression.getReference().resolve();
            return outerResolved instanceof PyTargetExpression outerTarget && outerResolved != targetExpression
                ? getTargetContext(outerTarget)
                : null;
        }

        TypeEvalContext context = TypeEvalContext.codeInsightFallback(targetExpression.getProject());
        ResolveResult[] resolveResults = targetExpression.getReference(PyResolveContext.defaultContext(context)).multiResolve(false);

        for (ResolveResult resolveResult : resolveResults) {
            PsiElement element = resolveResult.getElement();
            if (element instanceof PyTargetExpression && element.getParent() instanceof PyGlobalStatement) {
                return targetExpression.getContainingFile();
            }
        }

        for (ResolveResult resolveResult : resolveResults) {
            if (resolveResult.getElement() instanceof PyTargetExpression resolvedNonLocal &&
                resolvedNonLocal.getParent() instanceof PyNonlocalStatement) {
                return getTargetContext(resolvedNonLocal);
            }
        }

        ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(targetExpression);
        return scopeOwner instanceof PyFile || scopeOwner instanceof PyFunction || scopeOwner instanceof PyLambdaExpression ? scopeOwner : null;
    }

    private static @Nullable PsiElement getNamedParameterContext(PyNamedParameter namedParameter) {
        if (namedParameter.isSelf()) {
            return null;
        }

        ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(namedParameter);
        return scopeOwner instanceof PyLambdaExpression || scopeOwner instanceof PyFunction ? scopeOwner : null;
    }

    private static @Nullable String updateNameIfGlobal(PsiElement context, @Nullable String name) {
        return context instanceof PyFile && name != null ? "global_" + name : name;
    }

    @RequiredReadAction
    private void addInfo(PsiElement context, PsiElement rainbowElement, String name) {
        addInfo(context, rainbowElement, name, Holder.DEFAULT_HIGHLIGHTING_KEY);
    }

    @RequiredReadAction
    private void addInfo(PsiElement context, PsiElement rainbowElement, String name, @Nullable TextAttributesKey key) {
        addInfo(getInfo(context, rainbowElement, name, key));
    }

    private static @Nullable ScopeOwner getLeastCommonScope(Collection<? extends PsiElement> elements) {
        ScopeOwner result = null;

        for (PsiElement it : elements) {
            ScopeOwner currentScopeOwner = ScopeUtil.getScopeOwner(it);
            if (result == null) {
                result = currentScopeOwner;
            }
            else if (result != currentScopeOwner && currentScopeOwner != null && PsiTreeUtil.isAncestor(result, currentScopeOwner, true)) {
                result = currentScopeOwner;
            }
        }

        return result;
    }
}
