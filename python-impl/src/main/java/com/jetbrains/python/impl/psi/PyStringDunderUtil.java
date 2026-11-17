// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

public final class PyStringDunderUtil {
    private PyStringDunderUtil() {
    }

    /**
     * A string conversion of one of these types is not useful. Either the type keeps the `__str__` and the `__repr__`
     * of `object`, or its own `__repr__` prints only the name of the type and an address.
     * <p>
     * A type stub cannot tell a type that overrides `__str__` from a type that does not, because the flake8-pyi rule
     * Y029 removes a `__str__` or a `__repr__` that only overrides the `object` one. This set gives the answer for the
     * types above, and lets an inspection report them when no runtime module and no skeleton is available.
     */
    public static final Set<String> TYPES_WITHOUT_USEFUL_STRING_CONVERSION = Set.of(
        "builtins.zip",
        "builtins.map",
        "builtins.filter",
        "builtins.enumerate",
        "builtins.reversed",
        PyTypingTypeProvider.GENERATOR,
        PyTypingTypeProvider.ASYNC_GENERATOR
    );

    public static final Set<String> KNOWN_INT_TYPES = Set.of(PyNames.FQN.INT, "numpy.int8", "numpy.int16", "numpy.int32", "numpy.int64");
    public static final Set<String> KNOWN_DECIMAL_TYPES = plus(KNOWN_INT_TYPES, Set.of(
        PyNames.FQN.FLOAT, "decimal.Decimal", "fractions.Fraction",
        "numpy.float16", "numpy.float32", "numpy.float64"
    ));
    public static final Set<String> KNOWN_COMPLEX_TYPES = plus(KNOWN_DECIMAL_TYPES, Set.of(
        PyNames.FQN.COMPLEX, "numpy.complex64", "numpy.complex128"
    ));

    public static final Set<String> KNOWN_FORMAT_MINI_LANGUAGE_TYPES = plus(KNOWN_COMPLEX_TYPES, Set.of(PyNames.FQN.STR));

    public static boolean isAllowedFormatOverride(PyType type, Set<String> allowedQNames, TypeEvalContext context) {
        if (!(type instanceof PyClassType classType)) {
            return false;
        }
        Set<@Nullable String> actualQNames = new HashSet<>();
        for (PyClassLikeType ancestor : classType.getPyClass().getAncestorTypes(context)) {
            actualQNames.add(ancestor != null ? ancestor.getClassQName() : null);
        }
        actualQNames.add(classType.getClassQName());
        for (String qName : allowedQNames) {
            if (actualQNames.contains(qName)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> plus(Set<String> first, Set<String> second) {
        Set<String> result = new LinkedHashSet<>(first);
        result.addAll(second);
        return result;
    }
}
