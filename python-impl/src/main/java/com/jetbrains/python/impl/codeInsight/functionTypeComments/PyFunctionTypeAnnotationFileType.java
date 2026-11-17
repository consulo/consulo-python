// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.functionTypeComments;

import com.jetbrains.python.PythonFileType;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

/**
 * @author Mikhail Golubev
 */
public class PyFunctionTypeAnnotationFileType extends PythonFileType {
    public static final PyFunctionTypeAnnotationFileType INSTANCE = new PyFunctionTypeAnnotationFileType();

    private PyFunctionTypeAnnotationFileType() {
        super(PyFunctionTypeAnnotationDialect.INSTANCE);
    }

    @Override
    public String getId() {
        return "PythonFunctionTypeComment";
    }

    @Override
    public LocalizeValue getDescription() {
        return PyLocalize.filetypePythonFunctionTypeAnnotationDescription();
    }

    @Override
    public String getDefaultExtension() {
        return "functionTypeComment";
    }
}
