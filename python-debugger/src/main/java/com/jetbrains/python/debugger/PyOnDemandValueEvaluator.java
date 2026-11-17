// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import consulo.execution.debug.frame.XValueNode;
import consulo.localize.LocalizeValue;
import consulo.python.debugger.localize.PydevLocalize;

import java.util.List;

public class PyOnDemandValueEvaluator extends PyFullValueEvaluator {
    private PyDebugValue myDebugValue;
    private XValueNode myNode;

    public PyOnDemandValueEvaluator(LocalizeValue linkText,
                                    PyFrameAccessor debugProcess,
                                    PyDebugValue debugValue,
                                    XValueNode node) {
        super(linkText, debugProcess, debugValue.getEvaluationExpression());
        myDebugValue = debugValue;
        myNode = node;
    }

    public PyDebugValue getDebugValue() {
        return myDebugValue;
    }

    public void setDebugValue(PyDebugValue debugValue) {
        myDebugValue = debugValue;
    }

    public XValueNode getNode() {
        return myNode;
    }

    public void setNode(XValueNode node) {
        myNode = node;
    }

    @Override
    public void startEvaluation(XFullValueEvaluationCallback callback) {
        myNode.setFullValueEvaluator(new PyLoadingValueEvaluator(PydevLocalize.pydevLoadingValue(), myDebugProcess, myExpression));
        callback.evaluated("... Loading Value");
        PyFrameAccessor.PyAsyncValue<String> pyAsyncValue =
            new PyFrameAccessor.PyAsyncValue<>(myDebugValue, myDebugValue.createDebugValueCallback());
        myDebugProcess.loadAsyncVariablesValues(null, List.of(pyAsyncValue));
    }

    @Override
    public boolean isShowValuePopup() {
        return false;
    }
}
