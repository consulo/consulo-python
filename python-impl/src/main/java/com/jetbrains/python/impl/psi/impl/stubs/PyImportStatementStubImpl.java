// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import consulo.util.lang.Version;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;


public class PyImportStatementStubImpl extends PyVersionSpecificStubBase<PyImportStatement> implements PyImportStatementStub {
    public PyImportStatementStubImpl(StubElement parentStub, IStubElementType elementType, RangeSet<Version> versions) {
        super(parentStub, elementType, versions);
    }

    @Override
    public String toString() {
        return "PyImportStatementStub()";
    }
}
