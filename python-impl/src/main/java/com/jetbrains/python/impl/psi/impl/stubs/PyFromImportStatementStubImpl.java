// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.google.common.collect.RangeSet;
import consulo.util.lang.Version;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;


public class PyFromImportStatementStubImpl extends PyVersionSpecificStubBase<PyFromImportStatement> implements PyFromImportStatementStub {
    private final QualifiedName myImportSourceQName;
    private final boolean myStarImport;
    private final int myRelativeLevel;

    public PyFromImportStatementStubImpl(QualifiedName importSourceQName, boolean isStarImport, int relativeLevel,
                                         StubElement parent, IStubElementType elementType, RangeSet<Version> versions) {
        super(parent, elementType, versions);
        myImportSourceQName = importSourceQName;
        myStarImport = isStarImport;
        myRelativeLevel = relativeLevel;
    }

    @Override
    public QualifiedName getImportSourceQName() {
        return myImportSourceQName;
    }

    @Override
    public boolean isStarImport() {
        return myStarImport;
    }

    @Override
    public int getRelativeLevel() {
        return myRelativeLevel;
    }

    @Override
    public String toString() {
        return "PyFromImportStatementStub(source=" + myImportSourceQName + " starImport=" + myStarImport + ")";
    }
}
