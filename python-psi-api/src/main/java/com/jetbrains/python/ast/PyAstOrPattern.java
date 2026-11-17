// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import consulo.annotation.access.RequiredReadAction;
import consulo.util.collection.ContainerUtil;

import java.util.Arrays;
import java.util.List;

import static com.jetbrains.python.ast.PyAstElementKt.findChildrenByClass;

public interface PyAstOrPattern extends PyAstPattern {
    @RequiredReadAction
    default List<? extends PyAstPattern> getAlternatives() {
        return Arrays.asList(findChildrenByClass(this, PyAstPattern.class));
    }

    @Override
    @RequiredReadAction
    default boolean isIrrefutable() {
        return ContainerUtil.exists(getAlternatives(), PyAstPattern::isIrrefutable);
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyOrPattern(this);
    }
}
