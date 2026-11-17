// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import consulo.application.Application;
import consulo.execution.debug.frame.XValueModifier;


public class PyValueModifier extends XValueModifier {

    private final PyFrameAccessor myDebugProcess;
    private final PyDebugValue myVariable;

    public PyValueModifier(PyFrameAccessor debugProcess, PyDebugValue variable) {
        myDebugProcess = debugProcess;
        myVariable = variable;
    }

    @Override
    public void setValue(String expression, XModificationCallback callback) {
        Application.get().executeOnPooledThread(() -> {
            try {
                // Consulo: XValueModifier#setValue receives the expression text, not an XExpression
                myDebugProcess.changeVariable(myVariable, expression);
                callback.valueModified();
            }
            catch (PyDebuggerException e) {
                callback.errorOccurred(e.getTracebackError());
            }
        });
    }

    @Override
    public String getInitialValueEditorText() {
        return PyTypeHandler.format(myVariable);
    }

}
