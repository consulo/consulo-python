// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi;

import com.jetbrains.python.ast.PyAstDecorator;
import com.jetbrains.python.psi.stubs.PyDecoratorStub;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.psi.StubBasedPsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Covers a decorator call, e.g. <tt>@staticmethod</tt>.
 * Decorators happen contextually above the function definition, but are stored inside it for convenience.
 * <b>Note:</b>
 * In {@code @foo} form, {@code PyCallExpression}'s methods are related to invocation of {@code foo}
 * as decorator. In {@code @foo(...)} form, these very methods are related to the call that returns the decorator
 * to be applied. In either case, they are related to an invocation of {@code foo}.
 */
public interface PyDecorator extends PyAstDecorator, PyCallExpression, StubBasedPsiElement<PyDecoratorStub> {
    /**
     * @return the function being decorated, or null.
     */
    @Override
    @RequiredReadAction
    default @Nullable PyFunction getTarget() {
        return (PyFunction)PyAstDecorator.super.getTarget();
    }

    /**
     * Return the expression directly after "@" or {@code null} if there is none.
     * <p>
     * In a syntactically correct program prior Python 3.9, where the grammar restrictions for decorators
     * were relaxed in PEP 614, this method is expected to return either a plain reference expression
     * (i.e. {@code foo.bar}, not {@code foo[0].bar}) or a call on such a reference.
     */
    @Override
    @RequiredReadAction
    default @Nullable PyExpression getExpression() {
        return (PyExpression)PyAstDecorator.super.getExpression();
    }

    /**
     * True if the annotating function is a builtin, useful together with getName(). Implementation uses stub info.
     *
     * @see PyElement#getName()
     */
    boolean isBuiltin();

    /**
     * @return true if invocation has a form of {@code @foo(...)}.
     */
    boolean hasArgumentList();

    @Override
    @RequiredReadAction
    default @Nullable PyArgumentList getArgumentList() {
        return (PyArgumentList)PyAstDecorator.super.getArgumentList();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyExpression getCallee() {
        return (PyExpression)PyAstDecorator.super.getCallee();
    }
}
