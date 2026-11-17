// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import static com.jetbrains.python.psi.types.PyVariance.BIVARIANT;
import static com.jetbrains.python.psi.types.PyVariance.CONTRAVARIANT;
import static com.jetbrains.python.psi.types.PyVariance.COVARIANT;
import static com.jetbrains.python.psi.types.PyVariance.INFER_VARIANCE;
import static com.jetbrains.python.psi.types.PyVariance.INVARIANT;

/**
 * Top-level helpers of {@code PyVariance.kt}.
 */
final class PyVarianceKt {
    private PyVarianceKt() {
    }

    static PyBaseVariance invertVariance(PyBaseVariance variance) {
        if (variance == COVARIANT) {
            return CONTRAVARIANT;
        }
        if (variance == CONTRAVARIANT) {
            return COVARIANT;
        }
        return variance;
    }


    /**
     * The rules are:
     * - Invariant dominates
     * - Covariant × covariant = covariant
     * - Covariant × contravariant = contravariant
     * - Contravariant × contravariant = covariant
     * - Infer_Variance = invariant
     * - Recursive × contravariant = invariant // stable solution
     * - Recursive × other = other
     * <p>
     * Multiplying variances of subtypes of {@link PyBaseVariance} can narrow the variance type as follows:
     * - {@link PyExpectedVariance} * {@link PyBaseVariance} -> {@link PyExpectedVariance}
     * - {@link PyVariance} * {@link PyVarianceIntermediateResult} -> {@link PyVariance}
     * The related signatures are represented by various overrides in {@link PyBaseVariance} and its subtypes.
     */
    static PyBaseVariance multiplyVariance(PyBaseVariance first, PyBaseVariance second) {
        PyExpectedVariance none = PyExpectedVariance.NONE.INSTANCE;
        PyVarianceIntermediateResult recursive = PyVarianceIntermediateResult.RECURSIVE.INSTANCE;
        if (first == none || second == none) {
            return none;
        }
        if (first == INVARIANT || second == INVARIANT) {
            return INVARIANT;
        }
        if (first == INFER_VARIANCE || second == INFER_VARIANCE) {
            return INVARIANT;
        }
        if (first == recursive && second == CONTRAVARIANT) {
            return INVARIANT;
        }
        if (first == recursive) {
            return second;
        }
        if (second == recursive && first == CONTRAVARIANT) {
            return INVARIANT;
        }
        if (second == recursive) {
            return first;
        }
        if (first == BIVARIANT) {
            return second;
        }
        if (second == BIVARIANT) {
            return first;
        }
        if (first == second) {
            return COVARIANT;
        }
        return CONTRAVARIANT;
    }
}
