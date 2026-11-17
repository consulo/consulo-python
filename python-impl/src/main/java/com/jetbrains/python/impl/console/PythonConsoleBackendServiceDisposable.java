// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.console;

import com.jetbrains.python.console.protocol.PythonConsoleBackendService;

/**
 * Not really {@link consulo.disposer.Disposable} as {@link #dispose()} method is expected
 * to be called directly.
 */
interface PythonConsoleBackendServiceDisposable extends PythonConsoleBackendService.Iface {
    void dispose();
}
