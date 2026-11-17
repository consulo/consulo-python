// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * A marker interface for type forms that can be "unpacked" into a collection of other types, either a nameless series,
 * or associated with parameters of a callable type.
 * Normally, such constructs cannot be used on their own in type hints, and can appear only inside other generic types.
 *
 * @see PyPositionalVariadicType
 */
public sealed interface PyVariadicType extends PyType permits PyPositionalVariadicType, PyCallableParameterVariadicType,
                                                              PyKeywordVariadicType {
    @Override
    default boolean isBuiltin() {
        return false;
    }

    @Override
    default void assertValid(String message) {

    }

    @Override
    default @Nullable List<? extends RatedResolveResult> resolveMember(String name,
                                                                       @Nullable PyExpression location,
                                                                       AccessDirection direction,
                                                                       PyResolveContext resolveContext) {
        return null;
    }

    @Override
    default Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }
}
