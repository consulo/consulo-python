// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyCallableParameter;
import org.jspecify.annotations.Nullable;

/**
 * A resolved, constructor-relevant dataclass field.
 * <p>
 * This is the shared, framework-agnostic field model: type providers, completion and inspections consume it instead of
 * each re-deriving field facts (name, alias, kw-only-ness, generated parameter) from the field's {@link PyDataclassFieldParameters}.
 * <p>
 * Declared in JB {@code PyDataclasses.kt}.
 *
 * @param parameter The generated constructor parameter, or {@code null} when the field is excluded from {@code __init__} (e.g. {@code init=False}).
 */
public record PyDataclassField(PyTargetExpression declaration,
                               String name,
                               String parameterName,
                               @Nullable Boolean kwOnly,
                               @Nullable PyCallableParameter parameter) {
    public PyTargetExpression getDeclaration() {
        return declaration;
    }

    public String getName() {
        return name;
    }

    public String getParameterName() {
        return parameterName;
    }

    public @Nullable Boolean getKwOnly() {
        return kwOnly;
    }

    public @Nullable PyCallableParameter getParameter() {
        return parameter;
    }
}
