// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiExternalReferenceHost;
import consulo.language.psi.PsiNamedElement;
import com.jetbrains.python.ast.PyAstKeywordArgument;
import org.jspecify.annotations.Nullable;

public interface PyKeywordArgument extends PyAstKeywordArgument, PyExpression, PsiNamedElement, PsiExternalReferenceHost {
    @Override
    @RequiredReadAction
    default @Nullable PyExpression getValueExpression() {
        return (PyExpression)PyAstKeywordArgument.super.getValueExpression();
    }
}
