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

import com.jetbrains.python.psi.PyFunction;
import consulo.application.util.query.EmptyQuery;
import consulo.application.util.query.ExtensibleQueryFactory;
import consulo.application.util.query.Query;

/**
 * @author yole
 */
public class PyOverridingMethodsSearch extends ExtensibleQueryFactory<PyFunction, PyOverridingMethodsSearch.SearchParameters> {
  public static final PyOverridingMethodsSearch INSTANCE = new PyOverridingMethodsSearch();

  public static class SearchParameters {
    private final PyFunction myFunction;
    private final boolean myCheckDeep;

    public SearchParameters(PyFunction function, boolean checkDeep) {
      myFunction = function;
      myCheckDeep = checkDeep;
    }

    public PyFunction getFunction() {
      return myFunction;
    }

    public boolean isCheckDeep() {
      return myCheckDeep;
    }
  }

  private PyOverridingMethodsSearch() {
    super(PyOverridingMethodsSearchExecutor.class);
  }

  public static Query<PyFunction> search(PyFunction function, boolean checkDeep) {
    if (function.getContainingClass() == null) return EmptyQuery.getEmptyQuery();
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(function, checkDeep));
  }
}

