// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import org.jspecify.annotations.Nullable;

/**
 * {@link PyExpectedVariance} provides the same values as {@link PyVariance} but with the additional value {@link NONE}.
 */
public sealed interface PyExpectedVariance extends PyBaseVariance permits PyExpectedVariance.NONE, PyVariance {
    /**
     * {@link NONE} is used to indicate that a given position in the AST does not provide an expected variance.
     * Mind the following illustrating example:
     *
     * <pre>{@code
     * from typing import Callable
     * class C[X, Y, Z]:
     *     attr: X
     *     def f(self, a: Callable[[Y], None]) -> Z|None :
     *         Not_Me = None
     * }</pre>
     * The example above shows common locations where uses of type variables (here {@code X}, {@code Y}, and {@code Z}) can occur.
     * These locations are subject to {@code PyExpectedVarianceJudgment.getExpectedVariance()}.
     * In case that method gets called with an invalid location such as {@code Not_Me}, it will return {@link NONE}.
     */
    final class NONE implements PyExpectedVariance {
        public static final NONE INSTANCE = new NONE();

        private NONE() {
        }

        @Override
        public String getName() {
            return "NONE";
        }
    }

    @Override
    default PyExpectedVariance not() {
        return (PyExpectedVariance)PyVarianceKt.invertVariance(this);
    }

    @Override
    default PyExpectedVariance times(PyBaseVariance other) {
        return (PyExpectedVariance)PyVarianceKt.multiplyVariance(this, other);
    }

    default @Nullable PyVariance toPyVariance() {
        if (this instanceof NONE) {
            return null;
        }
        return (PyVariance)this;
    }
}
