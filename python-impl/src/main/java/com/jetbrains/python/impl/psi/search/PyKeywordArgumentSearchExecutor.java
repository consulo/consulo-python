// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNamedParameter;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.ReferencesSearchQueryExecutor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.util.query.QueryExecutorBase;

import java.util.function.Predicate;


@ExtensionImpl
public final class PyKeywordArgumentSearchExecutor extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>
    implements ReferencesSearchQueryExecutor {
    @Override
    public void processQuery(ReferencesSearch.SearchParameters queryParameters,
                             Predicate<? super PsiReference> consumer) {
        PsiElement element = queryParameters.getElementToSearch();
        if (!(element instanceof PyNamedParameter)) {
            return;
        }
        ScopeOwner owner = ReadAction.compute(() -> ScopeUtil.getScopeOwner(element));
        if (!(owner instanceof PyFunction)) {
            return;
        }
        ReferencesSearch.search(owner, queryParameters.getScopeDeterminedByUser()).forEach(reference -> {
            PsiElement refElement = reference.getElement();
            PyCallExpression call = PsiTreeUtil.getParentOfType(refElement, PyCallExpression.class);
            if (call != null && PsiTreeUtil.isAncestor(call.getCallee(), refElement, false)) {
                PyArgumentList argumentList = call.getArgumentList();
                if (argumentList != null) {
                    PyKeywordArgument keywordArgument = argumentList.getKeywordArgument(((PyNamedParameter)element).getName());
                    if (keywordArgument != null) {
                        return consumer.test(keywordArgument.getReference());
                    }
                }
            }
            return true;
        });
    }
}
