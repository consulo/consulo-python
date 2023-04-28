package com.jetbrains.python.psi.search;

import com.jetbrains.python.psi.PyClass;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.util.query.QueryExecutor;

/**
 * @author VISTALL
 * @since 25/04/2023
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyClassInheritorsSearchExecutor extends QueryExecutor<PyClass, PyClassInheritorsSearch.SearchParameters> {
}
