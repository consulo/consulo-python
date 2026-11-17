// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

/**
 * {@link PyVarianceIntermediateResult} provides the same values as {@link PyVariance} but with the additional value {@link RECURSIVE}.
 */
public sealed interface PyVarianceIntermediateResult extends PyBaseVariance permits PyVarianceIntermediateResult.RECURSIVE, PyVariance {
    /**
     * {@link RECURSIVE} is used to indicate that the variance of a type variable is based on itself, hence recursive.
     * Mind the following illustrating examples:
     *
     * <pre>{@code
     * class C[U]:
     *     def f(self, a: C[U]): pass
     * }</pre>
     * To determine the variance of {@code U}, we need to check all its uses. Since one use is in {@code C} itself, the result is recursive.
     *
     * <pre>{@code
     * class M[S]:
     *     def foo(self, b: N[S]): pass
     * class N[T]:
     *     def bar(self, a: M[T]): pass
     * }</pre>
     * To determine the variance of {@code M}, we need to check all its uses. One use is in {@code N} which leads to determining the variance of {@code T}.
     * However, this in turn, has a use in {@code M} again, which makes the result recursive.
     */
    final class RECURSIVE implements PyVarianceIntermediateResult {
        public static final RECURSIVE INSTANCE = new RECURSIVE();

        private RECURSIVE() {
        }

        @Override
        public String getName() {
            return "RECURSIVE";
        }
    }

    @Override
    default PyVarianceIntermediateResult not() {
        return (PyVarianceIntermediateResult)PyVarianceKt.invertVariance(this);
    }

    default PyVarianceIntermediateResult times(PyVarianceIntermediateResult other) {
        return (PyVarianceIntermediateResult)PyVarianceKt.multiplyVariance(this, other);
    }

    default PyVariance times(PyVariance other) {
        return (PyVariance)PyVarianceKt.multiplyVariance(this, other);
    }
}
