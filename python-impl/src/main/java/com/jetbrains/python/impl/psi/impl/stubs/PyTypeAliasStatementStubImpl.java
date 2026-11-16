// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.stubs.PyTypeAliasStatementStub;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.util.lang.Version;
import org.jspecify.annotations.Nullable;

public class PyTypeAliasStatementStubImpl extends PyVersionSpecificStubBase<PyTypeAliasStatement> implements PyTypeAliasStatementStub {

    private final @Nullable String myName;
    private final @Nullable String myTypeExpressionText;

    public PyTypeAliasStatementStubImpl(@Nullable String name,
                                        @Nullable String typeExpressionText,
                                        @Nullable StubElement parent,
                                        IStubElementType stubElementType,
                                        RangeSet<Version> versions) {
        super(parent, stubElementType, versions);
        myName = name;
        myTypeExpressionText = typeExpressionText;
    }

    @Override
    public @Nullable String getName() {
        return myName;
    }

    @Override
    public @Nullable String getTypeExpressionText() {
        return myTypeExpressionText;
    }

    @Override
    public String toString() {
        return "PyTypeAliasStatementStubImpl{" +
            "myName='" + myName + '\'' +
            ", myTypeExpressionText='" + myTypeExpressionText + '\'' +
            '}';
    }
}
