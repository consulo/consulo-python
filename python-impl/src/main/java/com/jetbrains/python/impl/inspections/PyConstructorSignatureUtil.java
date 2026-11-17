// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.Application;

import java.util.Collections;
import java.util.List;

public final class PyConstructorSignatureUtil {
    private PyConstructorSignatureUtil() {
    }

    public static List<PyFunction> findComplementaryConstructors(PyFunction function, TypeEvalContext context) {
        PyClass containingClass = PyUtil.turnConstructorIntoClass(function);
        if (containingClass == null) {
            return Collections.emptyList();
        }
        String complementaryName = PyUtil.isNewMethod(function) ? PyNames.INIT : PyNames.NEW;
        List<PyFunction> complementaryMethods = containingClass.multiFindMethodByName(complementaryName, true, context);

        for (PyFunction complementaryMethod : complementaryMethods) {
            PyClass complementaryMethodClass = complementaryMethod.getContainingClass();
            if (complementaryMethodClass == null ||
                PyUtil.isObjectClass(complementaryMethodClass) ||
                PyInspectionExtension.EP_NAME.findFirstSafe(Application.get(),
                                                            it -> it.ignoreInitNewSignatures(function, complementaryMethod)) != null) {
                return Collections.emptyList();
            }
        }

        return complementaryMethods;
    }
}
