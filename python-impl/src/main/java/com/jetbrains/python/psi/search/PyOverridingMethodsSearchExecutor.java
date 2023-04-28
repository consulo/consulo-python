package com.jetbrains.python.psi.search;

import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.util.query.QueryExecutor;

/**
 * @author VISTALL
 * @since 23/04/2023
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyOverridingMethodsSearchExecutor extends QueryExecutor<PyFunction, PyOverridingMethodsSearch.SearchParameters> {
}
