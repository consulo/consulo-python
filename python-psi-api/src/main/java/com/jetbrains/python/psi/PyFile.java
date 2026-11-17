// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstFile;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface PyFile extends PyAstFile, PyTypedElement, PsiFile, PyDocStringOwner, ScopeOwner {
    @Override
    @RequiredReadAction
    default List<PyStatement> getStatements() {
        //noinspection unchecked
        return (List<PyStatement>)PyAstFile.super.getStatements();
    }

    @RequiredReadAction
    List<PyClass> getTopLevelClasses();

    @RequiredReadAction
    List<PyFunction> getTopLevelFunctions();

    @RequiredReadAction
    List<PyTargetExpression> getTopLevelAttributes();

    @Nullable
    @RequiredReadAction
    PyFunction findTopLevelFunction(String name);

    @Nullable
    @RequiredReadAction
    PyClass findTopLevelClass(String name);

    @Nullable
    @RequiredReadAction
    PyTargetExpression findTopLevelAttribute(String name);

    List<PyTypeAliasStatement> getTypeAliasStatements();

    @Nullable
    PyTypeAliasStatement findTypeAliasStatement(String name);

    /**
     * Return the list of all 'from ... import' statements in the top-level scope of the file.
     *
     * @return the list of 'from ... import' statements.
     */
    List<PyFromImportStatement> getFromImports();

    /**
     * Return an exported PSI element defined in the file with the given name.
     */
    @Nullable
    PsiElement findExportedName(String name);

    /**
     * Iterate over exported PSI elements defined in the file.
     */
    Iterable<PyElement> iterateNames();

    /**
     * Return the resolved exported elements.
     */
    List<RatedResolveResult> multiResolveName(String name);

    /**
     * Return the resolved elements.
     *
     * @param exported found element must be exported, i.e. visible from other modules.
     */
    List<RatedResolveResult> multiResolveName(String name, boolean exported);

    /**
     * @deprecated Use {@link #multiResolveName(String)} instead.
     */
    @Deprecated(forRemoval = true)
    @Nullable
    PsiElement getElementNamed(String name);

    /**
     * Returns the list of import elements in all 'import xxx' statements in the top-level scope of the file.
     *
     * @return the list of import targets.
     */
    List<PyImportElement> getImportTargets();

    /**
     * Returns the list of names in the __all__ declaration, or null if there is no such declaration in the module.
     *
     * @return the list of names or null.
     */
    @Nullable
    List<String> getDunderAll();

    /**
     * If the function raises a DeprecationWarning or a PendingDeprecationWarning, returns the explanation text provided for the warning.
     *
     * @return the deprecation message or null if the function is not deprecated.
     */
    String getDeprecationMessage();

    /**
     * Returns the sequential list of import statements in the beginning of the file.
     */
    @Override
    @RequiredReadAction
    default List<PyImportStatementBase> getImportBlock() {
        //noinspection unchecked
        return (List<PyImportStatementBase>)PyAstFile.super.getImportBlock();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyStringLiteralExpression getDocStringExpression() {
        return (PyStringLiteralExpression)PyAstFile.super.getDocStringExpression();
    }
}
