// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import consulo.annotation.access.RequiredReadAction;
import com.jetbrains.python.ast.PyAstOrPattern;

import java.util.List;

public interface PyOrPattern extends PyAstOrPattern, PyPattern {
    @Override
    @RequiredReadAction
    default List<PyPattern> getAlternatives() {
        //noinspection unchecked
        return (List<PyPattern>)PyAstOrPattern.super.getAlternatives();
    }
}
