// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.StubBasedPsiElement;
import com.jetbrains.python.ast.PyAstTupleParameter;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;
import org.jspecify.annotations.Nullable;

/**
 * Tuple parameter. Defines nothing; this interface is only needed for stub creation.
 */
public interface PyTupleParameter extends PyAstTupleParameter, PyParameter, StubBasedPsiElement<PyTupleParameterStub> {

    @Override
    @RequiredReadAction
    default @Nullable PyNamedParameter getAsNamed() {
        return (PyNamedParameter)PyAstTupleParameter.super.getAsNamed();
    }

    @Override
    @RequiredReadAction
    default PyTupleParameter getAsTuple() {
        return (PyTupleParameter)PyAstTupleParameter.super.getAsTuple();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getDefaultValue() {
        return (PyExpression)PyAstTupleParameter.super.getDefaultValue();
    }

    /**
     * @return the nested parameters within this tuple parameter.
     */
    @Override
    PyParameter[] getContents();
}
