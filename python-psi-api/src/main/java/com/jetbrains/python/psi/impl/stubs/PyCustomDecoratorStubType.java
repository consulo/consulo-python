// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import com.jetbrains.python.psi.PyDecorator;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;

@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyCustomDecoratorStubType<T extends PyCustomDecoratorStub> extends PyCustomStubType<PyDecorator, T> {
    ExtensionPointName<PyCustomDecoratorStubType<? extends PyCustomDecoratorStub>> EP_NAME =
        ExtensionPointName.create((Class<PyCustomDecoratorStubType<? extends PyCustomDecoratorStub>>) (Class<?>) PyCustomDecoratorStubType.class);
}
