// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.impl.stubs;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import com.jetbrains.python.psi.PyClass;

@ExtensionAPI(ComponentScope.APPLICATION)
public abstract class PyCustomClassStubType<T extends PyCustomClassStub> implements PyCustomStubType<PyClass, T> {

    public static final ExtensionPointName<PyCustomClassStubType<? extends PyCustomClassStub>> EP_NAME =
        ExtensionPointName.create((Class<PyCustomClassStubType<? extends PyCustomClassStub>>) (Class<?>) PyCustomClassStubType.class);
}
