// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.console;

/**
 * The {@link PyConsoleProcessFinishedException} is thrown when IDE is waiting for
 * the reply from Python console and the Python console process is discovered
 * to be finished.
 *
 * @see PythonConsoleClientUtil#synchronizedPythonConsoleClient
 */
class PyConsoleProcessFinishedException extends RuntimeException {
    PyConsoleProcessFinishedException(int exitValue) {
        super("Console already exited with value: " + exitValue + " while waiting for an answer.");
    }
}
