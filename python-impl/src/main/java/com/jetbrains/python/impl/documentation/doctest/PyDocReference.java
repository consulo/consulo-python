/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.documentation.doctest;

import com.google.common.collect.Lists;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.resolve.CompletionVariantsProcessor;
import com.jetbrains.python.impl.psi.resolve.PyResolveProcessor;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.impl.psi.PyUtil.StringNodeInfo;
import com.jetbrains.python.impl.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.psi.resolve.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author ktisha
 */
public class PyDocReference extends PyReferenceImpl {
    public PyDocReference(PyQualifiedExpression element, @Nonnull PyResolveContext context) {
        super(element, context);
    }

    @Override
    public HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
        return HighlightSeverity.WARNING;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        ResolveResult[] results = super.multiResolve(incompleteCode);
        if (results.length == 0) {
            InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myElement.getProject());
            PsiLanguageInjectionHost host = languageManager.getInjectionHost(myElement);
            String referencedName = myElement.getReferencedName();
            if (referencedName == null) {
                return ResolveResult.EMPTY_ARRAY;
            }

            if (host != null) {
                List<Pair<PsiElement, TextRange>> files = languageManager.getInjectedPsiFiles(host);
                if (files != null) {
                    for (Pair<PsiElement, TextRange> pair : files) {
                        PyResolveProcessor processor = new PyResolveProcessor(referencedName);

                        PyResolveUtil.scopeCrawlUp(processor, (ScopeOwner) pair.getFirst(), referencedName, pair.getFirst());
                        List<RatedResolveResult> resultList =
                            getResultsFromProcessor(referencedName, processor, pair.getFirst(), pair.getFirst());
                        if (resultList.size() > 0) {
                            List<RatedResolveResult> ret = RatedResolveResult.sorted(resultList);
                            return ret.toArray(new RatedResolveResult[ret.size()]);
                        }
                    }
                }
                PyResolveProcessor processor = new PyResolveProcessor(referencedName);
                ScopeOwner scopeOwner = getHostScopeOwner();
                if (scopeOwner != null) {
                    PsiFile topLevel = scopeOwner.getContainingFile();
                    PyResolveUtil.scopeCrawlUp(processor, scopeOwner, referencedName, topLevel);
                    PsiElement referenceAnchor = getScopeControlFlowAnchor(host);
                    List<RatedResolveResult> resultList =
                        getResultsFromProcessor(referencedName, processor, referenceAnchor, topLevel);
                    if (resultList.size() > 0) {
                        List<RatedResolveResult> ret = RatedResolveResult.sorted(resultList);
                        return ret.toArray(new RatedResolveResult[ret.size()]);
                    }
                }
            }
        }
        return results;
    }

    @Nullable
    @RequiredReadAction
    private PsiElement getScopeControlFlowAnchor(@Nonnull PsiLanguageInjectionHost host) {
        return isInsideFormattedStringNode(host) ? PsiTreeUtil.getParentOfType(host, PyStatement.class) : null;
    }

    @RequiredReadAction
    private boolean isInsideFormattedStringNode(@Nonnull PsiLanguageInjectionHost host) {
        if (host instanceof PyStringLiteralExpression stringLiteral) {
            ASTNode node = findContainingStringNode(getElement(), stringLiteral);
            return node != null && new StringNodeInfo(node).isFormatted();
        }
        return false;
    }

    @Nullable
    @RequiredReadAction
    private static ASTNode findContainingStringNode(@Nonnull PsiElement injectedElement, @Nonnull PyStringLiteralExpression host) {
        InjectedLanguageManager manager = InjectedLanguageManager.getInstance(host.getProject());
        List<Pair<PsiElement, TextRange>> files = manager.getInjectedPsiFiles(host);
        if (files != null) {
            PsiFile injectedFile = injectedElement.getContainingFile();
            Pair<PsiElement, TextRange> first = ContainerUtil.find(files, pair -> pair.getFirst() == injectedFile);
            if (first != null) {
                int hostOffset = -host.getTextRange().getStartOffset();
                for (ASTNode node : host.getStringNodes()) {
                    TextRange relativeNodeRange = node.getTextRange().shiftRight(hostOffset);
                    if (relativeNodeRange.contains(first.getSecond())) {
                        return node;
                    }
                }
            }
        }
        return null;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public Object[] getVariants() {
        ArrayList<Object> ret = Lists.newArrayList(super.getVariants());
        PsiElement originalElement = CompletionUtilCore.getOriginalElement(myElement);
        PyQualifiedExpression element = originalElement instanceof PyQualifiedExpression qualifiedExpr ? qualifiedExpr : myElement;

        ScopeOwner scopeOwner = getHostScopeOwner();
        if (scopeOwner != null) {
            CompletionVariantsProcessor processor = new CompletionVariantsProcessor(element);
            PyResolveUtil.scopeCrawlUp(processor, scopeOwner, null, null);
            ret.addAll(processor.getResultList());
        }
        return ret.toArray();
    }

    @Nullable
    private ScopeOwner getHostScopeOwner() {
        InjectedLanguageManager languageManager = InjectedLanguageManager.getInstance(myElement.getProject());
        PsiLanguageInjectionHost host = languageManager.getInjectionHost(myElement);
        if (host != null) {
            PsiFile file = host.getContainingFile();
            ScopeOwner result = ScopeUtil.getScopeOwner(host);
            if (result == null && file instanceof ScopeOwner scopeOwner) {
                result = scopeOwner;
            }
            return result;
        }
        return null;
    }
}
