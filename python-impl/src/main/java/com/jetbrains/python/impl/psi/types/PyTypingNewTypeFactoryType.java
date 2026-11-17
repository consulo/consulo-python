// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Represents a type of callable object returned in runtime by {@code typing.NewType()}.
 * For type annotations {@link PyTypingNewType} is used.
 */
public class PyTypingNewTypeFactoryType extends PyCallableTypeImpl {
    private final String name;

    public PyTypingNewTypeFactoryType(PyTypingNewType type) {
        super(List.of(PyCallableParameterImpl.nonPsi(type.getClassType().toInstance())), type.toInstance());
        this.name = type.getName();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public @Nullable List<? extends RatedResolveResult> resolveMember(String name,
                                                                      @Nullable PyExpression location,
                                                                      AccessDirection direction,
                                                                      PyResolveContext resolveContext) {
        if (name.equals("__or__")) {
            return List.of(new RatedResolveResult(RatedResolveResult.RATE_NORMAL, null));
        }
        return super.resolveMember(name, location, direction, resolveContext);
    }
}
