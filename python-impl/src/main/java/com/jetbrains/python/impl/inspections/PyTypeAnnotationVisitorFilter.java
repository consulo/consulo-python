// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.functionTypeComments.PyFunctionTypeAnnotationDialect;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiFile;

/**
 * @author Mikhail Golubev
 */
// Consulo: PythonVisitorFilter is a LanguageExtension; JB registers this filter for "PyFunctionTypeComment" and "PyTypeHint",
// the latter is registered by PyTypeHintVisitorFilter
@ExtensionImpl
public class PyTypeAnnotationVisitorFilter implements PythonVisitorFilter {
    @Override
    public boolean isSupported(Class visitorClass, PsiFile file) {
        return !(visitorClass == PyIncorrectDocstringInspection.class ||
            visitorClass == PyMissingOrEmptyDocstringInspection.class ||
            visitorClass == PySingleQuotedDocstringInspection.class ||
            visitorClass == PyByteLiteralInspection.class ||
            visitorClass == PyMandatoryEncodingInspection.class ||
            visitorClass == PyNonAsciiCharInspection.class ||
            visitorClass == PyPep8Inspection.class ||
            visitorClass == PyCompatibilityInspection.class);
    }

    @Override
    public Language getLanguage() {
        return PyFunctionTypeAnnotationDialect.INSTANCE;
    }
}
