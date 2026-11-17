// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstDecoratorList;
import com.jetbrains.python.psi.stubs.PyDecoratorListStub;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.StubBasedPsiElement;
import org.jspecify.annotations.Nullable;

/**
 * A list of function decorators.
 */
public interface PyDecoratorList extends PyAstDecoratorList, PyElement, StubBasedPsiElement<PyDecoratorListStub> {
    /**
     * @return decorators of function, in order of declaration (outermost first).
     */
    @Override
    PyDecorator[] getDecorators();

    @Override
    @RequiredReadAction
    default @Nullable PyDecorator findDecorator(String name) {
        return (PyDecorator)PyAstDecoratorList.super.findDecorator(name);
    }
}
