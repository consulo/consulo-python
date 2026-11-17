// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.extensions;

import com.jetbrains.python.psi.PyCaseClause;
import com.jetbrains.python.psi.PyMatchStatement;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.types.TypeEvalContext;

public final class PyMatchStatementExtKt {
    private PyMatchStatementExtKt() {
    }

    public static boolean isExhaustive(PyMatchStatement statement, TypeEvalContext context) {
        // TODO: placeholder until exhaustive match is introduced
        for (PyCaseClause it : statement.getCaseClauses()) {
            PyPattern pattern = it.getPattern();
            if (pattern != null && pattern.isIrrefutable()) {
                return true;
            }
        }
        return false;
    }
}
