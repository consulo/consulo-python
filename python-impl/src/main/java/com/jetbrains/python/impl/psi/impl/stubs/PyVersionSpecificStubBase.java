// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import com.jetbrains.python.psi.stubs.PyVersionSpecificStub;
import consulo.language.psi.PsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import consulo.util.lang.Version;
import org.jspecify.annotations.Nullable;

public abstract class PyVersionSpecificStubBase<T extends PsiElement> extends StubBase<T> implements PyVersionSpecificStub {
    private final RangeSet<Version> myVersions;

    protected PyVersionSpecificStubBase(@Nullable StubElement parent,
                                        @Nullable IStubElementType elementType,
                                        RangeSet<Version> versions) {
        super(parent, elementType);
        myVersions = versions;
    }

    @Override
    public RangeSet<Version> getVersions() {
        return myVersions;
    }
}
