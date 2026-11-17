// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.extractmethod;

import com.jetbrains.python.psi.types.PyType;
import consulo.language.editor.refactoring.extractMethod.AbstractVariableData;
import org.jspecify.annotations.Nullable;

public class PyVariableData extends AbstractVariableData {
    public @Nullable String typeName;
    public @Nullable PyType type;

    public @Nullable String getTypeName() {
        return typeName;
    }

    public @Nullable PyType getType() {
        return type;
    }
}
