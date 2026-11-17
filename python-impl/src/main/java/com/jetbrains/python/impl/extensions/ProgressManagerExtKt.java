// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.extensions;

import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.localize.LocalizeValue;
import consulo.util.lang.function.ThrowableSupplier;

import java.util.function.Supplier;

/**
 * Top-level declarations of {@code ProgressManagerExt.kt}.
 */
public final class ProgressManagerExtKt {
    private ProgressManagerExtKt() {
    }

    // Consulo: JB runs a Task.WithResult, which is ProgressManager.runProcessWithProgressSynchronously
    public static <T> T runUnderProgress(ProgressManager progressManager, LocalizeValue title, Supplier<T> code) {
        if (Application.get().isDispatchThread()) {
            return progressManager.runProcessWithProgressSynchronously((ThrowableSupplier<T, RuntimeException>) code::get, title, false, null);
        }
        else {
            return code.get();
        }
    }
}
