// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyTupleParameter;
import com.jetbrains.python.psi.stubs.PyTupleParameterStub;
import org.jspecify.annotations.Nullable;

/**
 * Implementation does nothing but marking the element type.
 */
public class PyTupleParameterStubImpl extends StubBase<PyTupleParameter> implements PyTupleParameterStub {
    private final @Nullable String myDefaultValueText;

    protected PyTupleParameterStubImpl(@Nullable String defaultValueText, StubElement parent) {
        super(parent, PyStubElementTypes.TUPLE_PARAMETER);
        myDefaultValueText = defaultValueText;
    }

    @Override
    public @Nullable String getDefaultValueText() {
        return myDefaultValueText;
    }

    @Override
    public String toString() {
        return "PyTupleParameterStubImpl{" +
               "myDefaultValueText='" + myDefaultValueText + '\'' +
               '}';
    }
}
