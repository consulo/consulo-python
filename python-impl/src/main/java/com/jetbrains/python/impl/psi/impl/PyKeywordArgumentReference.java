// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementResolveResult;
import consulo.language.psi.PsiReferenceBase;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.impl.references.KeywordArgumentCompletionUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public class PyKeywordArgumentReference extends PsiReferenceBase.Poly<PyKeywordArgument> {
    public PyKeywordArgumentReference(PyKeywordArgument element, TextRange textRange) {
        super(element, textRange, true);
    }

    @Override
    public Object[] getVariants() {
        PyKeywordArgument originalElement = CompletionUtilCore.getOriginalElement(myElement);
        if (originalElement != null) {
            List<LookupElement> ret = new ArrayList<>();
            TypeEvalContext evalCtx = TypeEvalContext.codeCompletion(originalElement.getProject(), originalElement.getContainingFile());
            KeywordArgumentCompletionUtil.collectFunctionArgNames(originalElement, ret, evalCtx, false);
            return ret.toArray();
        }

        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        String keyword = myElement.getKeyword();
        if (keyword == null) {
            return ResolveResult.EMPTY_ARRAY;
        }
        PsiElement call = PsiTreeUtil.getParentOfType(myElement, PyCallExpression.class, PyClass.class);
        if (!(call instanceof PyCallExpression)) {
            return ResolveResult.EMPTY_ARRAY;
        }

        List<ResolveResult> resultList = new ArrayList<>();
        TypeEvalContext evalCtx = TypeEvalContext.codeInsightFallback(call.getProject());
        ((PyCallExpression)call).multiResolveCallee(PyResolveContext.defaultContext(evalCtx)).stream()
            .map(it -> it.getParameters(evalCtx))
            .filter(Objects::nonNull)
            .flatMap(it -> it.stream())
            .filter(it -> keyword.equals(it.getName()))
            .map(it -> it.getDeclarationElement())
            .filter(Objects::nonNull)
            .forEach(it -> resultList.add(new PsiElementResolveResult(it)));

        return resultList.toArray(ResolveResult.EMPTY_ARRAY);
    }

    private static @Nullable PyFunction resolveToFunction(PsiElement element, HashSet<PsiElement> visited) {
        if (visited.contains(element)) {
            return null;
        }
        visited.add(element);
        if (element instanceof PyFunction) {
            return (PyFunction)element;
        }
        if (element instanceof PyTargetExpression) {
            PyExpression assignedValue = ((PyTargetExpression)element).findAssignedValue();
            return resolveToFunction(assignedValue, visited);
        }
        if (element instanceof PyReferenceExpression) {
            PsiElement resolveResult = ((PyReferenceExpression)element).getReference().resolve();
            return resolveToFunction(resolveResult, visited);
        }
        return null;
    }
}
