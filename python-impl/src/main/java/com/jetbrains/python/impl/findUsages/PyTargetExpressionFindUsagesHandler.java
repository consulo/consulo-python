// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.findUsages;

import com.jetbrains.python.psi.PyTargetExpression;

/**
 * Important note: please update PyFindUsagesHandlerFactory#proxy on any changes here.
 */
public class PyTargetExpressionFindUsagesHandler extends PyFindUsagesHandler {
    public PyTargetExpressionFindUsagesHandler(PyTargetExpression psiElement) {
        super(psiElement);
    }
}
