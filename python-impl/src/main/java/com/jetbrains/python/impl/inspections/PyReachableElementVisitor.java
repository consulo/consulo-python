// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.controlflow.PyDataFlowKt;
import com.jetbrains.python.impl.codeInsight.controlflow.Reachability;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.types.TypeEvalContext;

public class PyReachableElementVisitor extends PyElementVisitor {
    private final PyElementVisitor delegate;
    private final TypeEvalContext context;

    public PyReachableElementVisitor(PyElementVisitor delegate, TypeEvalContext context) {
        this.delegate = delegate;
        this.context = context;
    }

    @Override
    public void visitPyElement(PyElement node) {
        if (PyDataFlowKt.getReachabilityForInspection(node, context) == Reachability.REACHABLE) {
            node.accept(delegate);
        }
    }
}
