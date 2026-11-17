// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger.values;

public class CannotRetrieveColumnDataException extends Exception {
    public CannotRetrieveColumnDataException(String message) {
        super(message);
    }
}
