// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import com.jetbrains.python.psi.impl.stubs.*;
import consulo.language.psi.stub.StubBase;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.ObjectUtil;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import org.jspecify.annotations.Nullable;

/**
 * Decorator stub storage.
 */
public class PyDecoratorStubImpl extends StubBase<PyDecorator> implements PyDecoratorStub {
    private final QualifiedName myQualifiedName;
    private final boolean myHasArgumentList;
    private final @Nullable PyCustomDecoratorStub myCustomStub;

    protected PyDecoratorStubImpl(QualifiedName qualname,
                                  boolean hasArgumentList,
                                  StubElement parent,
                                  @Nullable PyCustomDecoratorStub customStub) {
        super(parent, PyStubElementTypes.DECORATOR_CALL);
        myQualifiedName = qualname;
        myHasArgumentList = hasArgumentList;
        myCustomStub = customStub;
    }

    @Override
    public QualifiedName getQualifiedName() {
        return myQualifiedName;
    }

    @Override
    public boolean hasArgumentList() {
        return myHasArgumentList;
    }

    @Override
    public <T> @Nullable T getCustomStub(Class<T> stubClass) {
        return ObjectUtil.tryCast(myCustomStub, stubClass);
    }

    @Override
    public String toString() {
        return "PyDecoratorStubImpl{" +
               "myQualifiedName=" + myQualifiedName +
               ", myHasArgumentList=" + myHasArgumentList +
               ", myCustomStub=" + myCustomStub +
               '}';
    }
}
