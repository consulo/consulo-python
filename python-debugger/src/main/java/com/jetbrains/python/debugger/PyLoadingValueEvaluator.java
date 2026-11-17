// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.debugger;

import consulo.localize.LocalizeValue;

public class PyLoadingValueEvaluator extends PyFullValueEvaluator {
    protected PyLoadingValueEvaluator(LocalizeValue linkText, PyFrameAccessor debugProcess, String expression) {
        super(linkText, debugProcess, expression);
    }

    @Override
    public void startEvaluation(XFullValueEvaluationCallback callback) {
        callback.evaluated("... Loading value");
    }

    @Override
    public boolean isShowValuePopup() {
        return false;
    }
}
