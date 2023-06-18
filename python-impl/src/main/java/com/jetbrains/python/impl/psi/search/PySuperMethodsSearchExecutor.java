package com.jetbrains.python.impl.psi.search;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.application.util.query.QueryExecutor;
import consulo.language.psi.PsiElement;

/**
 * @author VISTALL
 * @since 25/04/2023
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PySuperMethodsSearchExecutor extends QueryExecutor<PsiElement, PySuperMethodsSearch.SearchParameters> {
}
