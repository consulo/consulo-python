// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.smartstepinto;

import com.jetbrains.python.impl.debugger.PyStackFrame;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyDictCompExpression;
import com.jetbrains.python.psi.PyGeneratorExpression;
import com.jetbrains.python.psi.PyListCompExpression;

public class PySmartStepIntoVariantComprehension extends PySmartStepIntoVariant {

    protected PySmartStepIntoVariantComprehension(PyComprehensionElement element, int callOrder,
                                                  PySmartStepIntoContext context) {
        super(element, callOrder, context);
    }

    @Override
    public String getFunctionName() {
        return getText();
    }

    @Override
    public String getText() {
        if (myElement instanceof PyGeneratorExpression)
          return "<genexpr>";
        else if (myElement instanceof PyListCompExpression)
          return "<listcomp>";
        else if (myElement instanceof PyDictCompExpression)
          return "<dictcomp>";
        else
          return "<setcomp>";
    }

    public static boolean isComprehensionName(String name) {
        return PyStackFrame.COMPREHENSION_NAMES.contains(name);
    }
}
