// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev.tables;

import com.jetbrains.python.tables.TableCommandParameters;
import org.jspecify.annotations.Nullable;

public class PyDevImageCommandParameters implements TableCommandParameters {
    private final @Nullable Integer myOffset;
    private final @Nullable String myImageId;

    public PyDevImageCommandParameters(@Nullable Integer offset, @Nullable String imageId) {
        myOffset = offset;
        myImageId = imageId;
    }

    public @Nullable Integer getOffset() {
        return myOffset;
    }

    public @Nullable String getImageId() {
        return myImageId;
    }
}
