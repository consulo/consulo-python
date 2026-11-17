// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

/**
 * This is Kotlin acrobatics. The goal is to have
 * (1) a user-friendly enum for variance (i.e., {@link PyVariance}) and
 * (2) de-facto subclass that also provides the enum {@link PyVarianceIntermediateResult.RECURSIVE} when inferred variance is recursive, and
 * (3) de-facto subclass that also provides the enum {@link PyExpectedVariance.NONE} when no expected variance can be computed.
 * <p>
 * The subtype hierarchy looks like:
 * <pre>
 * PyBaseVariance
 *  ├─ PyVarianceIntermediateResult
 *  │   ├─ RECURSIVE
 *  │   └─ PyVariance
 *  └─ PyExpectedVariance
 *      ├─ NONE
 *      └─ PyVariance
 * </pre>
 */
public sealed interface PyBaseVariance permits PyVarianceIntermediateResult, PyExpectedVariance {
    String getName();

    default PyBaseVariance not() {
        return PyVarianceKt.invertVariance(this);
    }

    default PyBaseVariance times(PyBaseVariance other) {
        return PyVarianceKt.multiplyVariance(this, other);
    }

    default PyExpectedVariance times(PyExpectedVariance other) {
        return (PyExpectedVariance)PyVarianceKt.multiplyVariance(this, other);
    }
}
