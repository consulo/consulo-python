// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.impl.HelperPackage;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import consulo.logging.Logger;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.util.ProcessOutput;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Top-level declarations of {@code PyPep8HelperBridge.kt}.
 * <p>
 * Consulo: JB runs the helper through the exec-service API ({@code ExecService.executeHelper}); here the helper command line
 * is built by {@link HelperPackage#newCommandLine(String, List)} and run with {@link PySdkUtil#getProcessOutput}.
 */
public final class PyPep8HelperBridgeKt {
    private static final Logger log = Logger.getInstance(PyPep8HelperBridgeKt.class);

    // Consulo: the exec-service has no timeout parameter here, PySdkUtil needs one
    private static final int TIMEOUT = 10000;

    /**
     * {@code PYTHONUTF8} keeps PY-37054 fixed. The helper reads stdin with {@code io.TextIOWrapper} and no explicit encoding, so it
     * falls back to the locale encoding. That is the ANSI code page on Windows, and then {@code E501} counts UTF-8 bytes
     * instead of characters. UTF-8 mode makes that fallback UTF-8, and it also makes the helper write the report in UTF-8.
     */
    private static final Map<String, String> execOptions = Map.of("PYTHONBUFFERED", "1", "PYTHONUTF8", "1");

    private PyPep8HelperBridgeKt() {
    }

    static @Nullable String execPep8Helper(HelperPackage helper, Path python, byte[] stdin, List<String> options) {
        GeneralCommandLine cmd = helper.newCommandLine(python.toString(), options);
        // Helper returns != 0, expected
        ProcessOutput res = PySdkUtil.getProcessOutput(cmd, python.getParent().toString(), execOptions, TIMEOUT, stdin, false);
        if (res.isTimeout() || res.isCancelled() || !res.isExitCodeSet()) {
            log.warn("Error running " + helper + ": " + res.getStderr());
            return null;
        }
        return res.getStdout().trim();
    }
}
