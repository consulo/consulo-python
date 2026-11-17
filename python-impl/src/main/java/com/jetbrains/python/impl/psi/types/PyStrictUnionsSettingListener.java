// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

/**
 * Runs the analysis again in each open project when the user changes {@link PyUnionType#STRICT_UNIONS_SETTING}.
 *
 * @see PyTypeAnalysisRestartKt#restartTypeAnalysis
 */
// TODO consulo: AdvancedSettingsChangeListener missing
class PyStrictUnionsSettingListener {
    public void advancedSettingChanged(String id, Object oldValue, Object newValue) {
        if (!id.equals(PyUnionType.STRICT_UNIONS_SETTING)) {
            return;
        }
        PyTypeAnalysisRestartKt.restartTypeAnalysis("PyStrictUnionsSettingListener: " + PyUnionType.STRICT_UNIONS_SETTING + " changed");
    }
}
