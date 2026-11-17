// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeRepresentation;

import com.jetbrains.python.PythonDialectsTokenSetContributorBase;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.TokenSet;

@ExtensionImpl
public class PyTypeRepresentationTokenSetContributor extends PythonDialectsTokenSetContributorBase {
    @Override
    public TokenSet getExpressionTokens() {
        return TokenSet.create(PyTypeRepresentationElementTypes.FUNCTION_SIGNATURE);
    }
}
