// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.stubs.PyTypeParameterStub;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import org.jspecify.annotations.Nullable;

public class PyTypeParameterStubImpl extends StubBase<PyTypeParameter> implements PyTypeParameterStub {

    private final @Nullable String myName;
    private final @Nullable String myBoundExpressionText;
    private final @Nullable String myDefaultExpressionText;

    private final PyTypeParameter.Kind myKind;


    public PyTypeParameterStubImpl(@Nullable String name,
                                   PyTypeParameter.Kind type,
                                   @Nullable String boundExpressionText,
                                   @Nullable String defaultExpressionText,
                                   @Nullable StubElement parent,
                                   IStubElementType stubElementType) {
        super(parent, stubElementType);
        myName = name;
        myKind = type;
        myBoundExpressionText = boundExpressionText;
        myDefaultExpressionText = defaultExpressionText;
    }

    @Override
    public @Nullable String getBoundExpressionText() {
        return myBoundExpressionText;
    }

    @Override
    public @Nullable String getDefaultExpressionText() {
        return myDefaultExpressionText;
    }

    @Override
    public PyTypeParameter.Kind getKind() {
        return myKind;
    }

    @Override
    public @Nullable String getName() {
        return myName;
    }

    @Override
    public String toString() {
        return "PyTypeParameterStub(name=" + myName +
            ", kind=" + myKind +
            ", bound=" + myBoundExpressionText +
            ", default=" + myDefaultExpressionText + ")";
    }
}
