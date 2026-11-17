// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

/**
 * How a TypedDict key failed to match (see {@code PyMismatchStep.TypedDictKey}).
 */
public enum TypedDictKeyProblem {
    /**
     * The key is absent on the actual TypedDict.
     */
    MISSING,

    /**
     * The key's value type is incompatible (assignability, or mutable-key invariance).
     */
    VALUE_TYPE,

    /**
     * A mutable key is required, but the actual key is read-only.
     */
    READONLY,

    /**
     * The key's required/not-required qualifier is inconsistent.
     */
    REQUIRED,
}
