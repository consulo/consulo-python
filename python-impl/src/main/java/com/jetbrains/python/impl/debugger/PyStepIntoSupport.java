// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import org.jspecify.annotations.Nullable;

public interface PyStepIntoSupport {
    /**
     * True when stepping into user code only is currently possible.
     */
    boolean isStepIntoMyCodeAvailable();

    /**
     * Localized tooltip explaining why stepIntoMyCode is unavailable (null if available).
     */
    @Nullable
    String getStepIntoMyCodeUnavailableReason();

    /**
     * Execute a "step into my code" for the current suspend context.
     */
    void performStepIntoMyCode();

    /**
     * True when stepping into all code (including libraries) is available.
     * Defaults to true; override to false when the session was launched with justMyCode=true.
     */
    default boolean isStepIntoAllCodeAvailable() {
        return true;
    }

    /**
     * Localized tooltip explaining why stepIntoAllCode is unavailable (null if available or not applicable).
     * Shown in the toolbar tooltip when {@link #isStepIntoAllCodeAvailable()} is false.
     */
    default @Nullable String getStepIntoAllCodeUnavailableReason() {
        return null;
    }

    /**
     * Execute a "step into all code" (including libraries) for the current suspend context.
     */
    default void performStepIntoAllCode() {
    }

    /**
     * Whether {@link #applyJustMyCodeChange} can currently be invoked (e.g. false when the session is already stopped).
     * Controls visibility of the "Switch to My code" / "Switch to all code" tooltip quickfix link.
     */
    default boolean getCanApplyJustMyCodeChange() {
        return true;
    }

    /**
     * Whether {@link #applyJustMyCodeChange} will automatically restart the debug session.
     * When false, the tooltip link text should not promise a restart.
     */
    default boolean getWillRestartOnJustMyCodeChange() {
        return true;
    }

    /**
     * Applies {@code enableJustMyCode} to the active run configuration and opens the run configuration editor
     * so the user can verify and then restart the session manually.
     * Default is a no-op (used by pydevd, which does not have a justMyCode session-level flag).
     */
    default void applyJustMyCodeChange(boolean enableJustMyCode) {
    }
}
