// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.doctest;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.impl.psi.resolve.CompletionVariantsProcessor;
import com.jetbrains.python.impl.psi.resolve.PyResolveProcessor;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.language.psi.ResolveResult;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.Pair;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * User : ktisha
 */
public class PyDoctestReference extends PyReferenceImpl {
    public PyDoctestReference(PyQualifiedExpression element, PyResolveContext context) {
        super(element, context);
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
        final HighlightSeverity severity = super.getUnresolvedHighlightSeverity(context);
        return severity != null ? HighlightSeverity.WARNING : null;
    }

    @Override
    @RequiredReadAction
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        ResolveResult[] results = super.multiResolve(incompleteCode);
        if (results.length == 0) {
            final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myElement.getProject());
            final PsiLanguageInjectionHost host = languageManager.getInjectionHost(myElement);
            final String referencedName = myElement.getReferencedName();
            if (referencedName == null) {
                return ResolveResult.EMPTY_ARRAY;
            }

            if (host != null) {
                final List<Pair<PsiElement, TextRange>> files = languageManager.getInjectedPsiFiles(host);
                if (files != null) {
                    for (Pair<PsiElement, TextRange> pair : files) {
                        if (pair.getFirst() instanceof PyFile) {
                            final PyResolveProcessor processor = new PyResolveProcessor(referencedName);

                            PyResolveUtil.scopeCrawlUp(processor, (ScopeOwner) pair.getFirst(), referencedName, pair.getFirst());
                            final List<RatedResolveResult> resultList = getResultsFromProcessor(referencedName, processor, pair.getFirst(),
                                pair.getFirst());
                            if (!resultList.isEmpty()) {
                                List<RatedResolveResult> ret = RatedResolveResult.sorted(resultList);
                                return ret.toArray(RatedResolveResult.EMPTY_ARRAY);
                            }
                        }
                    }
                }
                final PyResolveProcessor processor = new PyResolveProcessor(referencedName);
                final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(host);
                if (scopeOwner != null) {
                    final PsiFile topLevel = scopeOwner.getContainingFile();
                    PyResolveUtil.scopeCrawlUp(processor, scopeOwner, referencedName, topLevel);
                    final List<RatedResolveResult> resultList = getResultsFromProcessor(referencedName, processor, null, topLevel);
                    if (!resultList.isEmpty()) {
                        final List<RatedResolveResult> ret = RatedResolveResult.sorted(resultList);
                        return ret.toArray(RatedResolveResult.EMPTY_ARRAY);
                    }
                }
            }
        }
        return results;
    }

    @Override
    @RequiredReadAction
    public Object[] getVariants() {
        final Object[] results = super.getVariants();

        final InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(getElement().getProject());
        final PsiLanguageInjectionHost host = languageManager.getInjectionHost(getElement());
        if (host == null) {
            return results;
        }

        final PyQualifiedExpression originalElement = CompletionUtilCore.getOriginalElement(getElement());
        final PyQualifiedExpression element = originalElement != null ? originalElement : getElement();
        final CompletionVariantsProcessor processor = new CompletionVariantsProcessor(element, null, filterForPresentedNames(results));

        final List<Pair<PsiElement, TextRange>> files = languageManager.getInjectedPsiFiles(host);
        if (files != null) {
            for (Pair<PsiElement, TextRange> pair : files) {
                if (pair.getFirst() instanceof PyFile) {
                    PyResolveUtil.scopeCrawlUp(processor, (ScopeOwner) pair.getFirst(), null, pair.getFirst());
                }
            }
        }
        final ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(host);
        if (scopeOwner != null) {
            PyResolveUtil.scopeCrawlUp(processor, scopeOwner, null, scopeOwner.getContainingFile());
        }

        return ArrayUtil.mergeArrayAndCollection(results, new ArrayList<Object>(processor.getResultList()), Object[]::new);
    }

    private static @Nullable Predicate<String> filterForPresentedNames(Object[] variants) {
        if (variants.length == 0) {
            return null;
        }
        final Set<String> seenNames = StreamEx.of(variants).select(LookupElement.class).map(LookupElement::getLookupString).toSet();
        return s -> !seenNames.contains(s);
    }
}
