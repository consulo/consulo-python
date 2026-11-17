// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types.engine;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.module.Module;
import org.jspecify.annotations.Nullable;

/**
 * An external type provider invoked by {@link com.jetbrains.python.psi.types.TypeEvalContext} to obtain a type from a separate engine.
 * Implementations may return {@code null} if they cannot provide a type for the given element.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyTypeEngineProvider {
    ExtensionPointName<PyTypeEngineProvider> EP_NAME = ExtensionPointName.create(PyTypeEngineProvider.class);

    default @Nullable PyTypeEngine createTypeEngine(Module module) {
        return null;
    }

    static @Nullable PyTypeEngine createTypeEngineFor(Module module) {
        return EP_NAME.computeSafeIfAny(it -> it.createTypeEngine(module));
    }
}
