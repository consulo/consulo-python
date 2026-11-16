// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.disposer.Disposable;
import consulo.language.ast.TokenSet;
import consulo.util.collection.ContainerUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

/**
 * Provides element types of various kinds for known Python dialects.
 *
 */
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public final class PythonDialectsTokenSetProvider implements Disposable {

    private final ConcurrentMap<String, TokenSet> myCache = new ConcurrentHashMap<>(7); // number of token types

    @Inject
    public PythonDialectsTokenSetProvider() {
        // TODO consulo: com.intellij.openapi.extensions.ExtensionPointName.addChangeListener missing
    }

    public static PythonDialectsTokenSetProvider getInstance() {
        return Application.get().getInstance(PythonDialectsTokenSetProvider.class);
    }

    /**
     * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyStatement}.
     */
    public TokenSet getStatementTokens() {
        return getTokenSet("statement", PythonDialectsTokenSetContributor::getStatementTokens);
    }

    /**
     * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyExpression}.
     */
    public TokenSet getExpressionTokens() {
        return getTokenSet("expression", PythonDialectsTokenSetContributor::getExpressionTokens);
    }

    /**
     * Returns all element types of Python dialects that are language keywords.
     */
    public TokenSet getKeywordTokens() {
        return getTokenSet("keyword", PythonDialectsTokenSetContributor::getKeywordTokens);
    }

    /**
     * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyParameter}.
     */
    public TokenSet getParameterTokens() {
        return getTokenSet("parameter", PythonDialectsTokenSetContributor::getParameterTokens);
    }

    /**
     * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyFunction}.
     */
    public TokenSet getFunctionDeclarationTokens() {
        return getTokenSet("functionDeclaration", PythonDialectsTokenSetContributor::getFunctionDeclarationTokens);
    }

    /**
     * Returns all element types of Python dialects that can be used as unbalanced braces recovery tokens in the lexer.
     */
    public TokenSet getUnbalancedBracesRecoveryTokens() {
        return getTokenSet("unbalancedBracesRecovery", PythonDialectsTokenSetContributor::getUnbalancedBracesRecoveryTokens);
    }

    /**
     * Returns all element types of Python dialects that are subclasses of {@link com.jetbrains.python.psi.PyReferenceExpression}.
     */
    public TokenSet getReferenceExpressionTokens() {
        return getTokenSet("referenceExpression", PythonDialectsTokenSetContributor::getReferenceExpressionTokens);
    }

    @Override
    public void dispose() {
        myCache.clear();
    }

    private TokenSet getTokenSet(String key, Function<? super PythonDialectsTokenSetContributor, ? extends TokenSet> getter) {
        return myCache.computeIfAbsent(key, ignored -> orSets(getter));
    }

    private static TokenSet orSets(Function<? super PythonDialectsTokenSetContributor, ? extends TokenSet> getter) {
        return TokenSet.orSet(
            ContainerUtil.map2Array(PythonDialectsTokenSetContributor.EP_NAME.getExtensionList(), TokenSet.class, getter::apply)
        );
    }
}
