/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.psi.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.ReferencesSearchQueryExecutor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.util.query.QueryExecutorBase;
import jakarta.annotation.Nonnull;

import java.util.function.Predicate;

/**
 * @author yole
 */
@ExtensionImpl
public class PyKeywordArgumentSearchExecutor extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>
    implements ReferencesSearchQueryExecutor {
    @Override
    public void processQuery(
        @Nonnull ReferencesSearch.SearchParameters queryParameters,
        @Nonnull Predicate<? super PsiReference> consumer
    ) {
        PsiElement element = queryParameters.getElementToSearch();
        if (!(element instanceof PyNamedParameter)) {
            return;
        }
        PyFunction owner = PsiTreeUtil.getParentOfType(element, PyFunction.class);
        if (owner == null) {
            return;
        }
        ReferencesSearch.search(owner, queryParameters.getScope()).forEach(reference -> {
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
