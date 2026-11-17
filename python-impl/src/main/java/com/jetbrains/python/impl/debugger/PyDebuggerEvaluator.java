// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.debugger.PyDebugValue;
import com.jetbrains.python.debugger.PyDebuggerException;
import com.jetbrains.python.debugger.PyFrameAccessor;
import com.jetbrains.python.impl.console.PyConsoleIndentUtil;
import consulo.application.Application;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.execution.debug.XSourcePosition;
import consulo.execution.debug.evaluation.XDebuggerEvaluator;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

// TODO consulo: XDebuggerDocumentOffsetEvaluator and ValueHintType missing in execution-debug-api, JB also implements
//  evaluate(Document, int offset, ValueHintType, XEvaluationCallback) through PyDebuggerEvaluatorKt.evaluateWithOffset
class PyDebuggerEvaluator extends XDebuggerEvaluator {
    private final Project myProject;
    private final PyFrameAccessor myDebugProcess;

    PyDebuggerEvaluator(Project project, PyFrameAccessor debugProcess) {
        myProject = project;
        myDebugProcess = debugProcess;
    }

    @Override
    public void evaluate(String expression, XEvaluationCallback callback, @Nullable XSourcePosition expressionPosition) {
        doEvaluate(expression, callback, true);
    }

    private PyDebugValue getNone() {
        return new PyDebugValue("", "NoneType", null, "None", false, null, null, false, false, false, null, null, myDebugProcess);
    }

    private void doEvaluate(String expr, XEvaluationCallback callback, boolean doTrunc) {
        Application.get().executeOnPooledThread(() -> {
            String expression = expr.trim();
            if (expression.isEmpty()) {
                callback.evaluated(getNone());
                return;
            }

            boolean isExpression = PyDebugSupportUtils.isExpression(myProject, expression);
            try {
                // todo: think on getting results from EXEC
                PyDebugValue value = myDebugProcess.evaluate(expression, !isExpression, doTrunc);
                if (value.isErrorOnEval()) {
                    callback.errorOccurred("{" + value.getType() + "}" + value.getValue()); //NON-NLS
                }
                else {
                    callback.evaluated(value);
                }
            }
            catch (PyDebuggerException e) {
                callback.errorOccurred(e.getTracebackError());
            }
        });
    }

    @Override
    public @Nullable TextRange getExpressionRangeAtOffset(@Nullable Project project,
                                                          @Nullable Document document,
                                                          int offset,
                                                          boolean sideEffectsAllowed) {
        TextRange range = PyDebugSupportUtils.getExpressionRangeAtOffset(project, document, offset);
        if (range == null) {
            return null;
        }
        if (sideEffectsAllowed || project == null || document == null) {
            return range;
        }
        return PyDebuggerEvaluatorKt.filterExpressionRangeForHover(project, document, offset, range);
    }

    @Override
    public String formatTextForEvaluation(String text) {
        return PyConsoleIndentUtil.normalize(text);
    }
}
