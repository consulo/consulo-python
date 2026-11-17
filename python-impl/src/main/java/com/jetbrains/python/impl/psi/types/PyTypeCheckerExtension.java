// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Extension to make additional check of {@link PyType} in {@link PyTypeChecker}
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyTypeCheckerExtension {

    ExtensionPointName<PyTypeCheckerExtension> EP_NAME = ExtensionPointName.create(PyTypeCheckerExtension.class);

    Optional<Boolean> match(@Nullable PyType expected,
                            @Nullable PyType actual,
                            TypeEvalContext context,
                            PyTypeChecker.GenericSubstitutions substitutions);
}
