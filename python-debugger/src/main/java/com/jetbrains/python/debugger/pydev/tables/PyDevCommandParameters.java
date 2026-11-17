// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.pydev.tables;

import com.jetbrains.python.tables.TableCommandParameters;
import org.jspecify.annotations.Nullable;

public class PyDevCommandParameters implements TableCommandParameters {
    private final int myStart;
    private final int myEnd;
    private final @Nullable String myFormat;

    public PyDevCommandParameters(int start, int end, @Nullable String format) {
        myStart = start;
        myEnd = end;
        myFormat = format;
    }

    public int getStart() {
        return myStart;
    }

    public int getEnd() {
        return myEnd;
    }

    public @Nullable String getFormat() {
        return myFormat;
    }
}
