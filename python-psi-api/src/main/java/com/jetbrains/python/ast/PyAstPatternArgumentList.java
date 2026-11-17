// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import consulo.annotation.access.RequiredReadAction;
import java.util.Arrays;
import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findChildrenByClass;

public interface PyAstPatternArgumentList extends PyAstElement {
    @RequiredReadAction
    default List<? extends PyAstPattern> getPatterns() {
        return Arrays.asList(findChildrenByClass(this, PyAstPattern.class));
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyPatternArgumentList(this);
    }
}
