// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.ProtectionLevel;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyParameter;
import consulo.language.psi.PsiElement;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.function.Predicate;

public interface PyCallableParameter {
    @Nullable
    String getName();

    /**
     * @param context type evaluation context
     * @return type of the parameter.
     */
    @Nullable
    PyType getType(TypeEvalContext context);

    /**
     * @return underneath psi element if exists.
     */
    @Nullable
    PyParameter getParameter();

    default @Nullable PsiElement getDeclarationElement() {
        return getParameter();
    }

    @Nullable
    PyExpression getDefaultValue();

    boolean hasDefaultValue();

    @Nullable
    String getDefaultValueText();

    boolean isPositionalContainer();

    boolean isKeywordContainer();

    boolean isSelf();

    boolean isPositionOnlySeparator();

    boolean isKeywordOnlySeparator();

    default ProtectionLevel getProtectionLevel() {
        return ProtectionLevel.forName(StringUtil.notNullize(getName()));
    }

    /**
     * @param includeDefaultValue if true, include the default value after an "=".
     * @return canonical representation of parameter.
     * Includes asterisks for *param and **param.
     */
    default String getPresentableText(boolean includeDefaultValue) {
        return getPresentableText(includeDefaultValue, null);
    }

    /**
     * @param includeDefaultValue if true, include the default value after an "=".
     * @param context             context to be used to resolve argument type
     * @return canonical representation of parameter.
     * Includes asterisks for *param and **param.
     * Also includes argument type if {@code context} is not null and resolved type is not unknown.
     */
    String getPresentableText(boolean includeDefaultValue, @Nullable TypeEvalContext context);

    /**
     * @param includeDefaultValue if true, include the default value after an "=".
     * @param context             context to be used to resolve argument type
     * @param typeFilter          predicate to be used to ignore resolved argument type
     * @return canonical representation of parameter.
     * Includes asterisks for *param and **param.
     * Also includes argument type if {@code context} is not null and filter returns {@code false} for it.
     */
    default String getPresentableText(
        boolean includeDefaultValue,
        @Nullable TypeEvalContext context,
        Predicate<@Nullable PyType> typeFilter
    ) {
        return getPresentableText(includeDefaultValue, context, Collections.emptySet(), typeFilter);
    }

    /**
     * @param includeDefaultValue  if true, include the default value after an "=".
     * @param context              context to be used to resolve argument type
     * @param typeRendererFeatures rendering features to enable when formatting the argument type, e.g.
     *                             {@link PyTypeRendererFeature#TYPE_VAR_BOUNDS} to render `bar: T ≤: str` instead of just `bar: T`.
     * @param typeFilter           predicate to be used to ignore resolved argument type
     * @return canonical representation of parameter.
     * Includes asterisks for *param and **param.
     * Also includes argument type if {@code context} is not null and filter returns {@code false} for it.
     */
    String getPresentableText(
        boolean includeDefaultValue,
        @Nullable TypeEvalContext context,
        Set<PyTypeRendererFeature> typeRendererFeatures,
        Predicate<@Nullable PyType> typeFilter
    );

    /**
     * @param context context to be used to resolve argument type
     * @return argument type. Returns element type for *param and value type for **param.
     */
    @Nullable
    PyType getArgumentType(TypeEvalContext context);
}
