// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.magicLiteral;

import com.jetbrains.python.psi.StringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.ReferencesSearchQueryExecutor;
import consulo.project.util.query.QueryExecutorBase;
import consulo.util.lang.StringUtil;

import java.util.function.Predicate;

/**
 * Searches for string usages on magic literals.
 * <strong>Install it</strong> as "referencesSearch" !
 * @author Ilya.Kazakevich
 */
@ExtensionImpl
public final class PyMagicLiteralReferenceSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>
    implements ReferencesSearchQueryExecutor {

    @Override
    public void processQuery(ReferencesSearch.SearchParameters queryParameters, Predicate<? super PsiReference> consumer) {
        ReadAction.run(() -> {
            PsiElement refElement = queryParameters.getElementToSearch();
            if (PyMagicLiteralTools.couldBeMagicLiteral(refElement)) {
                String refText = ((StringLiteralExpression) refElement).getStringValue();
                if (!StringUtil.isEmpty(refText)) {
                    SearchScope searchScope = queryParameters.getEffectiveSearchScope();
                    queryParameters.getOptimizer().searchWord(refText, searchScope, true, refElement);
                }
            }
        });
    }
}
