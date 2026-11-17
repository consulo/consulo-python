// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.tensorFlow;

import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.pyi.PyiFile;
import com.jetbrains.python.impl.pyi.PyiStubSuppressor;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.util.registry.Registry;
import consulo.language.psi.util.QualifiedName;

@ExtensionImpl
public class TensorflowPyiStubsSuppressor implements PyiStubSuppressor {

    @Override
    public boolean isIgnoredStub(PyiFile file) {
        if (Registry.is("enable.tensorflow.pyi.stubs", false)) {
            return false;
        }
        QualifiedName qName = QualifiedNameFinder.findShortestImportableQName(file);
        return qName != null && "tensorflow".equals(qName.getFirstComponent());
    }
}
