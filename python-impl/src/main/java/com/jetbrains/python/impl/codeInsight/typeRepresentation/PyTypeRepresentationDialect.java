// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeRepresentation;

import com.jetbrains.python.PythonLanguage;
import consulo.language.DependentLanguage;
import consulo.language.Language;

/**
 * Used to represent types that are not possible to express in the language like callable types
 * <p>
 * This is used to serialize types, and for type engine communication
 */
public final class PyTypeRepresentationDialect extends Language implements DependentLanguage {
    public static final PyTypeRepresentationDialect INSTANCE = new PyTypeRepresentationDialect();

    private PyTypeRepresentationDialect() {
        super(PythonLanguage.getInstance(), "PyTypeRepresentation");
    }
}
