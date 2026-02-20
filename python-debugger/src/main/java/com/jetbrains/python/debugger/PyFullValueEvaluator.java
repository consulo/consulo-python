package com.jetbrains.python.debugger;

import consulo.execution.debug.frame.XFullValueEvaluator;
import consulo.localize.LocalizeValue;
import jakarta.annotation.Nonnull;

/**
 * @author traff
 */
public class PyFullValueEvaluator extends XFullValueEvaluator {
    private final PyFrameAccessor myDebugProcess;
    private final String myExpression;

    /**
     * @param linkText     text of the link what will be appended to a variables tree node text
     * @param debugProcess
     * @param expression
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
    public void startEvaluation(@Nonnull XFullValueEvaluationCallback callback) {
        String expression = myExpression.trim();
        if ("".equals(expression)) {
            callback.evaluated("");
            return;
        }

        try {
            PyDebugValue value = myDebugProcess.evaluate(expression, false, false);
            callback.evaluated(value.getValue());
            showCustomPopup(myDebugProcess, value);
        }
        catch (PyDebuggerException e) {
            callback.errorOccurred(e.getTracebackError());
        }
    }

    protected void showCustomPopup(PyFrameAccessor debugProcess, PyDebugValue debugValue) {

    }
}
