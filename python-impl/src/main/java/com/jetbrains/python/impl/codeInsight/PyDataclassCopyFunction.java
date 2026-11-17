// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import consulo.language.psi.util.QualifiedName;

/**
 * A framework-provided "shallow copy with overrides" free function (e.g. {@code dataclasses.replace} / {@code attrs.evolve}).
 * <p>
 * Declared in JB {@code PyDataclasses.kt}.
 */
public record PyDataclassCopyFunction(QualifiedName qualifiedName, String instanceParameterName) {
    public QualifiedName getQualifiedName() {
        return qualifiedName;
    }

    public String getInstanceParameterName() {
        return instanceParameterName;
    }
}
