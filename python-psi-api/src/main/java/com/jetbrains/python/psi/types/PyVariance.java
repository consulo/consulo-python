// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

public enum PyVariance implements PyVarianceIntermediateResult, PyExpectedVariance {
    COVARIANT, CONTRAVARIANT, INVARIANT, BIVARIANT, INFER_VARIANCE;

    @Override
    public String getName() {
        return name();
    }

    @Override
    public PyVariance not() {
        return (PyVariance)PyVarianceKt.invertVariance(this);
    }

    @Override
    public PyVariance times(PyVariance other) {
        return (PyVariance)PyVarianceKt.multiplyVariance(this, other);
    }

    @Override
    public PyVariance times(PyVarianceIntermediateResult other) {
        return (PyVariance)PyVarianceKt.multiplyVariance(this, other);
    }

    /**
     * Returns true iff declared/actual variance is compatible with the required/expected variance.
     * <p>
     * Compatibility rules (typical for variance checking):
     * - INFER_VARIANCE is treated as "unknown / don't care" and is compatible with anything.
     * - INVARIANT can be used in both co- and contravariant positions (but not vice versa).
     * - COVARIANT is only compatible with a covariant position.
     * - CONTRAVARIANT is only compatible with a contravariant position.
     * - RECURSIVE does not occur here.
     */
    public boolean isCompatibleWithActual(PyVariance actual) {
        PyVariance expected = this;
        if (actual == INFER_VARIANCE || expected == INFER_VARIANCE) {
            return true;
        }

        return switch (expected) {
            case COVARIANT -> actual == COVARIANT || actual == INVARIANT;
            case CONTRAVARIANT -> actual == CONTRAVARIANT || actual == INVARIANT;
            case INVARIANT -> actual == INVARIANT;
            case BIVARIANT -> true;
            case INFER_VARIANCE -> true;
        };
    }
}
