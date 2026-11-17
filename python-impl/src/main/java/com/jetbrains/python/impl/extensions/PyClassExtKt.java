// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.extensions;

import com.jetbrains.python.nameResolver.FQNamesProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * @author Ilya.Kazakevich
 */
public final class PyClassExtKt {
    private PyClassExtKt() {
    }

    public static boolean inherits(PyClass cls, TypeEvalContext evalContext, Set<String> parentNames) {
        return cls.getAncestorTypes(evalContext).stream()
            .filter(Objects::nonNull)
            .map(PyClassLikeType::getClassQName)
            .filter(Objects::nonNull)
            .anyMatch(parentNames::contains);
    }

    public static boolean inherits(PyClass cls, TypeEvalContext evalContext, String... parentNames) {
        return inherits(cls, evalContext, new HashSet<>(Arrays.asList(parentNames)));
    }

    public static boolean inherits(PyClass cls, @Nullable TypeEvalContext evalContext, FQNamesProvider parentNames) {
        return cls.getAncestorClasses(evalContext).stream().anyMatch(c -> FQNamesProviderExtKt.isNameMatches(parentNames, c));
    }
}
