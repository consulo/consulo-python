// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.numpy.codeInsight;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.types.PyLegacyDocstringTypeParser;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.impl.PyResolveResultRaterBase;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;

@ExtensionImpl
public final class NumpyResolveRater extends PyResolveResultRaterBase {

    @Override
    public int getMemberRate(PsiElement member, PyType type, TypeEvalContext context) {
        if (member instanceof PsiNamedElement) {
            PyType ndArray = PyLegacyDocstringTypeParser.getTypeByName(member, NumpyDocStringTypeProvider.NDARRAY, context);
            if (ndArray != null && PyTypeChecker.match(ndArray, type, context) &&
                PyNames.isRightOperatorName(((PsiNamedElement) member).getName())) {
                return 100;
            }
        }
        return 0;
    }
}
