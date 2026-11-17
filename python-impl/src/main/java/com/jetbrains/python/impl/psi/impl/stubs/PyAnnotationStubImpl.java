// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.stubs.PyAnnotationStub;

public class PyAnnotationStubImpl extends StubBase<PyAnnotation> implements PyAnnotationStub {
    protected PyAnnotationStubImpl(StubElement parent, IStubElementType elementType) {
        super(parent, elementType);
    }
}
