// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;

/**
 * Lets a framework accept a dict literal as a value of a class type.
 * A framework uses it when it converts a dict to an instance of the class at runtime.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyDictLiteralPromotionExtension {
    ExtensionPointName<PyDictLiteralPromotionExtension> EP_NAME = ExtensionPointName.create(PyDictLiteralPromotionExtension.class);

    /**
     * Returns {@code true} if {@code dictLiteral} is a valid value of the instance type {@code expectedType}.
     * {@code promoteValue} gives the type of a nested value expression for its expected type.
     */
    boolean acceptsDictLiteral(PyClassType expectedType,
                               PyDictLiteralExpression dictLiteral,
                               BiFunction<@Nullable PyType, PyExpression, @Nullable PyType> promoteValue,
                               TypeEvalContext context);
}
