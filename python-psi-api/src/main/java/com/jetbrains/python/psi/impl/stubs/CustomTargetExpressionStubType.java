// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.stub.IndexSink;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;

@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class CustomTargetExpressionStubType<T extends CustomTargetExpressionStub>
    implements PyCustomStubType<PyTargetExpression, T> {

    public static final ExtensionPointName<CustomTargetExpressionStubType<? extends CustomTargetExpressionStub>> EP_NAME =
        ExtensionPointName.create((Class<CustomTargetExpressionStubType<? extends CustomTargetExpressionStub>>) (Class<?>) CustomTargetExpressionStubType.class);

    public void indexStub(PyTargetExpressionStub stub, IndexSink sink) {
    }
}
