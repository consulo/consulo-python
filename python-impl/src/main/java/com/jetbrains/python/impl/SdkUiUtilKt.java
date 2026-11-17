// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.impl.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.impl.sdk.flavors.VirtualEnvSdkFlavor;
import consulo.content.bundle.Sdk;

/**
 * Top-level declarations of {@code SdkUiUtil.kt}.
 */
// These functions get SDK type without touching IO, hence fast and save to be called from EDT
public final class SdkUiUtilKt {
    private SdkUiUtilKt() {
    }

    public static boolean isNonToolVirtualEnv(Sdk sdk) {
        return sdk.getSdkAdditionalData() instanceof PythonSdkAdditionalData data && data.getFlavor() instanceof VirtualEnvSdkFlavor;
    }

    public static boolean isCondaVirtualEnv(Sdk sdk) {
        // TODO consulo: com.jetbrains.python.sdk.flavors.conda.CondaEnvSdkFlavor missing
        return false;
    }

    // Consulo: JB PythonInterpreter.isCondaVirtualEnv is not ported (PythonInterpreter maps to Sdk)
}
