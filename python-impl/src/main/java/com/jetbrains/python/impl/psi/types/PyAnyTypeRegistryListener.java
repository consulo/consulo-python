// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.types.PyAnyType;
import consulo.application.util.registry.RegistryValue;
import consulo.application.util.registry.RegistryValueListener;

/**
 * Runs the analysis again in each open project when the user changes {@link PyAnyType#REGISTRY_KEY}.
 *
 * @see PyTypeAnalysisRestartKt#restartTypeAnalysis
 */
// TODO consulo: RegistryValueListener application listener topic missing (Consulo never invokes RegistryValueListener)
class PyAnyTypeRegistryListener implements RegistryValueListener {
    @Override
    public void afterValueChanged(RegistryValue value) {
        if (!value.getKey().equals(PyAnyType.REGISTRY_KEY)) {
            return;
        }
        PyTypeAnalysisRestartKt.restartTypeAnalysis("PyAnyTypeRegistryListener: " + PyAnyType.REGISTRY_KEY + " changed");
    }
}
