// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import java.math.BigInteger;

/**
 * A Python value that {@link PyConstantFolder} computes with. {@link PyEvaluator} converts its values to this form and back.
 */
public sealed interface PyConstantValue permits PyConstantValue.Str, PyConstantValue.Int, PyConstantValue.Bool {
    /**
     * A {@code str} value.
     */
    record Str(String value) implements PyConstantValue {
    }

    /**
     * An {@code int} value that is not a {@code bool}.
     */
    record Int(BigInteger value) implements PyConstantValue {
    }

    /**
     * A {@code bool} value.
     */
    record Bool(boolean value) implements PyConstantValue {
    }
}
