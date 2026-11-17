// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionNavigator;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ReadAction;
import consulo.content.scope.SearchScope;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.ReferencesSearch;
import consulo.language.psi.search.ReferencesSearchQueryExecutor;
import consulo.language.psi.search.RequestResultProcessor;
import consulo.language.psi.search.SingleTargetRequestResultProcessor;
import consulo.language.psi.search.UsageSearchContext;
import consulo.project.util.query.QueryExecutorBase;

import java.util.function.Predicate;


@ExtensionImpl
public final class PyInitReferenceSearchExecutor extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters>
    implements ReferencesSearchQueryExecutor {
    @Override
    public void processQuery(ReferencesSearch.SearchParameters queryParameters, Predicate<? super PsiReference> consumer) {
        PsiElement element = queryParameters.getElementToSearch();
        if (!(element instanceof PyFunction)) {
            return;
        }

        ReadAction.run(() -> {
            String className;
            SearchScope searchScope;
            PyFunction function = (PyFunction)element;
            PyClass pyClass = PyUtil.turnConstructorIntoClass(function);
            if (pyClass == null) {
                return;
            }
            className = pyClass.getName();
            if (className == null) {
                return;
            }

            searchScope = queryParameters.getEffectiveSearchScope();
            if (searchScope instanceof GlobalSearchScope) {
                searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)searchScope, PythonFileType.INSTANCE);
            }


            var processor = new ClassInitializationProcessor(pyClass);
            queryParameters.getOptimizer().searchWord(className, searchScope, UsageSearchContext.IN_CODE, true, pyClass, processor);
        });
    }

    private static class ClassInitializationProcessor extends RequestResultProcessor {

        private final SingleTargetRequestResultProcessor myProcessor;

        private ClassInitializationProcessor(PyClass cls) {
            super(cls);
            myProcessor = new SingleTargetRequestResultProcessor(cls);
        }

        @Override
        public boolean processTextOccurrence(PsiElement element,
                                             int offsetInElement,
                                             Predicate<? super PsiReference> consumer) {
            if (PyCallExpressionNavigator.getPyCallExpressionByCallee(element) != null) {
                return myProcessor.processTextOccurrence(element, offsetInElement, consumer);
            }

            return true;
        }
    }
}
