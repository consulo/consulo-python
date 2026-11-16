// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import com.jetbrains.python.psi.PyElement;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.stub.StringStubIndexExtension;
import consulo.language.psi.stub.StubIndexKey;

@ExtensionImpl
public final class PyExportedModuleAttributeIndex extends StringStubIndexExtension<PyElement> {
    public static final StubIndexKey<String, PyElement> KEY = StubIndexKey.createIndexKey("Py.module.attribute");

    @Override
    public StubIndexKey<String, PyElement> getKey() {
        return KEY;
    }

    @Override
    public boolean traceKeyHashToVirtualFileMapping() {
        return true;
    }
}
