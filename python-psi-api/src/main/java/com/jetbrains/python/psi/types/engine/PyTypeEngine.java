// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types.engine;

import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyType;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

public interface PyTypeEngine {
    String getName();

    /**
     * Whether the engine answers a request now.
     * <p>
     * An engine that runs a server exists before the server runs, and it returns false until then.
     */
    default boolean isReady() {
        return true;
    }

    /**
     * Whether the engine can see {@code pyTypedElement} at all.
     * <p>
     * The built-in engine answers for an element this engine cannot see.
     */
    boolean isSupportedForResolve(PyTypedElement pyTypedElement);

    @Nullable
    Ref<@Nullable PyType> resolveType(PyTypedElement pyTypedElement, boolean isUserInitiated);
}
