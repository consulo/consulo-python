// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import consulo.annotation.access.RequiredReadAction;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public interface PyAstTupleExpression<T extends PyAstExpression> extends PyAstSequenceExpression, Iterable<T> {
    @Override
    @RequiredReadAction
    default Iterator<T> iterator() {
        //noinspection unchecked,rawtypes
        return ((List)Arrays.asList(getElements())).iterator();
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyTupleExpression(this);
    }
}
