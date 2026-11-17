// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstTypeParameter;
import com.jetbrains.python.psi.stubs.PyTypeParameterStub;
import org.jspecify.annotations.Nullable;

/**
 * Represents a Type Parameter that can be a part of {@link PyTypeParameterList}<br>
 * For more information see <a href="https://peps.python.org/pep-0695/">PEP 695</a>
 */
public interface PyTypeParameter extends PyAstTypeParameter, PyElement, PsiNameIdentifierOwner, PyTypedElement, PyQualifiedNameOwner,
        StubBasedPsiElement<PyTypeParameterStub> {

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getBoundExpression() {
        return (PyExpression)PyAstTypeParameter.super.getBoundExpression();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getDefaultExpression() {
        return (PyExpression)PyAstTypeParameter.super.getDefaultExpression();
    }
}
