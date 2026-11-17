// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

/**
 * Optional features that tweak how a {@link PyType} is rendered into a human-readable string.
 *
 * @see com.jetbrains.python.impl.documentation.PythonDocumentationProvider#getTypeName
 */
public enum PyTypeRendererFeature {
    /**
     * Render fully qualified names of all classes and type forms, e.g. `typing.Callable[[mod.MyClass], typing.Any]`.
     */
    USE_FQN,

    /**
     * Render bounds and constraints of TypeVars.
     */
    TYPE_VAR_BOUNDS,

    /**
     * Render literal types bare, as just their value: `1` instead of `Literal[1]`, `'a' | 'b'` instead of
     * `Literal['a', 'b']`. Used where the `Literal[...]` wrapper is redundant noise, such as the structural type diff.
     */
    BARE_LITERAL,
}
