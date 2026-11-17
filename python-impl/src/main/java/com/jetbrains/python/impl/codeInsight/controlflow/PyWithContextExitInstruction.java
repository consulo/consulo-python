// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.controlflow;

import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.controlFlow.ControlFlowBuilder;
import consulo.language.controlFlow.base.InstructionImpl;

/**
 * Each {@code with} statement surrounds its block with an implicit {@code try/except}. Depending on the return value
 * of the context manager's {@code __exit__()} method, the intercepted exception may be suppressed or propagated further.
 * <p>
 * It affects the reachability analysis. Consider the following snippet
 *
 * <pre>{@code
 * with manager():
 *     may_raise_exception()
 *     return
 * print("Reachable?")
 * }</pre>
 * <p>
 * The potential exception from {@code may_raise_exception} jumps over the return statement and invokes {@code __exit__()}.
 * If then {@code __exit__()} returns a "truthy" value, suppressing the exception, the subsequent {@code print} statement becomes reachable.
 * <p>
 * We model this behavior with this special virtual instruction in CFG for a {@code with} statement.
 * All instructions potentially raising an exception connect to it, while it connects to the next instruction after
 * the {@code with} statement. During the reachability analysis, we consider the latter edge from this instruction "passable"
 * only if the context manager can suppress exceptions.
 * <p>
 * The rules for detecting if a context manager can suppress exceptions are
 * <a href="https://typing.python.org/en/latest/spec/exceptions.html#context-managers">described in the typing specification</a>.
 * <p>
 * See also the <a href="https://docs.python.org/3/reference/datamodel.html#object.__exit__">{@code __exit__} method documentation in the language reference</a>.
 *
 * @see PyWithItem#isSuppressingExceptions
 */
public class PyWithContextExitInstruction extends InstructionImpl {
    public PyWithContextExitInstruction(ControlFlowBuilder builder, PyWithItem withItem) {
        super(builder, withItem);
    }

    @Override
    public String getElementPresentation() {
        return "exit context manager: " + getElement().getText();
    }

    @Override
    public PyWithItem getElement() {
        return (PyWithItem) super.getElement();
    }

    /**
     * While traversing CFG, use this method to know if you should let your traversal consider this node.
     * Usually, you would want it only if the context manager DOES suppress exceptions.
     */
    public boolean isSuppressingExceptions(TypeEvalContext context) {
        return getElement().isSuppressingExceptions(context);
    }
}
