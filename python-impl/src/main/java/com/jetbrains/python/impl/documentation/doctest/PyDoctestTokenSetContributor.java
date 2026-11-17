// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.doctest;

import com.jetbrains.python.PythonDialectsTokenSetContributorBase;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.TokenSet;

/**
 * User : ktisha
 */
@ExtensionImpl
public final class PyDoctestTokenSetContributor extends PythonDialectsTokenSetContributorBase {
    public static final TokenSet DOCTEST_REFERENCE_EXPRESSIONS = TokenSet.create(PyDoctestTokenTypes.DOC_REFERENCE);

    @Override
    public TokenSet getExpressionTokens() {
        return DOCTEST_REFERENCE_EXPRESSIONS;
    }

    @Override
    public TokenSet getReferenceExpressionTokens() {
        return DOCTEST_REFERENCE_EXPRESSIONS;
    }
}
