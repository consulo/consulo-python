// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.functionTypeComments;

import com.jetbrains.python.PythonLanguage;
import consulo.language.DependentLanguage;
import consulo.language.Language;

/**
 * @author Mikhail Golubev
 */
public class PyFunctionTypeAnnotationDialect extends Language implements DependentLanguage {
    public static final PyFunctionTypeAnnotationDialect INSTANCE = new PyFunctionTypeAnnotationDialect();

    protected PyFunctionTypeAnnotationDialect() {
        super(PythonLanguage.getInstance(), "PyFunctionTypeComment");
    }
}
