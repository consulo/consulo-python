// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.search;

import com.jetbrains.python.psi.PyClass;
import consulo.application.util.query.ExtensibleQueryFactory;
import consulo.application.util.query.Query;


public final class PyClassInheritorsSearch extends ExtensibleQueryFactory<PyClass, PyClassInheritorsSearch.SearchParameters> {
    public static final PyClassInheritorsSearch INSTANCE = new PyClassInheritorsSearch();

    public static class SearchParameters {
        private final PyClass mySuperClass;
        private final boolean myCheckDeepInheritance;

        public SearchParameters(PyClass superClass, boolean checkDeepInheritance) {
            mySuperClass = superClass;
            myCheckDeepInheritance = checkDeepInheritance;
        }

        public PyClass getSuperClass() {
            return mySuperClass;
        }

        public boolean isCheckDeepInheritance() {
            return myCheckDeepInheritance;
        }
    }

    private PyClassInheritorsSearch() {
        super(PyClassInheritorsSearchExecutor.class);
    }

    public static Query<PyClass> search(PyClass superClass, boolean checkDeepInheritance) {
        SearchParameters parameters = new SearchParameters(superClass, checkDeepInheritance);
        return INSTANCE.createUniqueResultsQuery(parameters);
    }
}
