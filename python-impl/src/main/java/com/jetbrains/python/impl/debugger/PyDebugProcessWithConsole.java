// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.debugger.pydev.PyDebugCallback;
import org.jspecify.annotations.Nullable;

import java.io.OutputStream;
import java.util.List;


/**
 * Interface representing a Python debug process with an integrated console interface
 * for executing commands and retrieving debug-related information.
 */
public interface PyDebugProcessWithConsole {
    List<@Nullable PydevCompletionVariant> getCompletions(@Nullable String prefix) throws Exception;

    String getDescription(@Nullable String prefix) throws Exception;

    void consoleExec(@Nullable String command, PyDebugCallback<@Nullable String> callback);

    void interruptDebugConsole();

    // Needs for input(...) command
    default @Nullable OutputStream getDebugConsoleInputStream() {
        return null;
    }
}
