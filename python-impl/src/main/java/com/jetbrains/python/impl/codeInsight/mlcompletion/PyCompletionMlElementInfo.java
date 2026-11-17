// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.mlcompletion;

import com.jetbrains.python.impl.psi.impl.PyClassImpl;
import com.jetbrains.python.impl.psi.impl.PyFileImpl;
import com.jetbrains.python.impl.psi.impl.PyFunctionImpl;
import com.jetbrains.python.impl.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import consulo.language.psi.PsiElement;
import consulo.util.dataholder.Key;

public record PyCompletionMlElementInfo(PyCompletionMlElementKind kind, boolean isBuiltins) {
    private static final Key<PyCompletionMlElementInfo> key = Key.create("py.ml.completion.element.info");

    public PyCompletionMlElementInfo(PyCompletionMlElementKind kind) {
        this(kind, false);
    }

    public static Key<PyCompletionMlElementInfo> getKey() {
        return key;
    }

    public static PyCompletionMlElementInfo fromElement(PsiElement element) {
        PyCompletionMlElementKind kind;
        if (element instanceof PyFileImpl) {
            kind = PyCompletionMlElementKind.PACKAGE_OR_MODULE;
        }
        else if (element instanceof PyTargetExpressionImpl) {
            kind = PyCompletionMlElementKind.FROM_TARGET;
        }
        else if (element instanceof PyClassImpl) {
            kind = PyCompletionMlElementKind.TYPE_OR_CLASS;
        }
        else if (element instanceof PyFunctionImpl) {
            kind = PyCompletionMlElementKind.FUNCTION;
        }
        else {
            kind = PyCompletionMlElementKind.UNKNOWN;
        }
        boolean isBuiltins = PyBuiltinCache.getInstance(element).isBuiltin(element);
        return new PyCompletionMlElementInfo(kind, isBuiltins);
    }
}
