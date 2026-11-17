// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.debugger;

import consulo.application.Application;
import consulo.execution.debug.frame.XFullValueEvaluator;
import consulo.localize.LocalizeValue;
import consulo.python.debugger.localize.PydevLocalize;

public class PyFullValueEvaluator extends XFullValueEvaluator {
    protected final PyFrameAccessor myDebugProcess;
    protected final String myExpression;

    /**
     * @param linkText     text of the link what will be appended to a variables tree node text
     */
    protected PyFullValueEvaluator(LocalizeValue linkText, PyFrameAccessor debugProcess, String expression) {
        super(linkText);
        myDebugProcess = debugProcess;
        myExpression = expression;
    }


    protected PyFullValueEvaluator(PyFrameAccessor debugProcess, String expression) {
        myDebugProcess = debugProcess;
        myExpression = expression;
    }


    @Override
    public void startEvaluation(XFullValueEvaluationCallback callback) {
        doEvaluate(callback, false);
    }

    protected boolean isCopyValueCallback(XFullValueEvaluationCallback callback) {
        // Copy Value does not go through this evaluator - PyDebugProcess copies by HasCopyValue
        return false;
    }

    protected void doEvaluate(XFullValueEvaluationCallback callback, boolean doTrunc) {
        String expression = myExpression.trim();
        if (expression.isEmpty()) {
            callback.evaluated("");
            return;
        }

        Application.get().executeOnPooledThread(() -> {
            try {
                final PyDebugValue value = myDebugProcess.evaluate(expression, false, doTrunc);
                if (value == null) {
                    callback.evaluated(PydevLocalize.pydevSessionFinished().get());
                    return;
                }
                if (value.getValue() == null) {
                    throw new PyDebuggerException("Failed to Load Value");
                }
                callback.evaluated(value.getValue());
                if (!isCopyValueCallback(callback)) {
                    Application.get().invokeLater(() -> showCustomPopup(myDebugProcess, value));
                }
            }
            catch (PyDebuggerException e) {
                callback.errorOccurred(e.getTracebackError());
            }
        });
    }

    protected void showCustomPopup(PyFrameAccessor debugProcess, PyDebugValue debugValue) {

    }
}
