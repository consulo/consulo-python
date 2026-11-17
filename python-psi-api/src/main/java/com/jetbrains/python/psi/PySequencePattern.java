// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstSequencePattern;
import consulo.annotation.access.RequiredReadAction;

import java.util.Arrays;
import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findChildrenByClass;

public interface PySequencePattern extends PyAstSequencePattern, PyPattern {
    @RequiredReadAction
    default List<PyPattern> getElements() {
        return Arrays.asList(findChildrenByClass(this, PyPattern.class));
    }
}
