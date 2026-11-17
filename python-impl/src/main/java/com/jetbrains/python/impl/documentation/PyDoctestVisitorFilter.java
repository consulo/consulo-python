// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation;

import com.jetbrains.python.impl.documentation.doctest.PyDoctestLanguageDialect;
import com.jetbrains.python.impl.inspections.PythonVisitorFilter;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiFile;

/**
 * User : ktisha
 * <p>
 * filter out some python inspections and annotations if we're in doctest substitution
 */
@ExtensionImpl
public final class PyDoctestVisitorFilter implements PythonVisitorFilter {
    @Override
    public boolean isSupported(final Class visitorClass, final PsiFile file) {
        return !PyInjectedPythonCodeVisitors.disabledInspections.contains(visitorClass) &&
            !PyInjectedPythonCodeVisitors.disabledAnnotators.contains(visitorClass);
    }

    @Override
    public Language getLanguage() {
        return PyDoctestLanguageDialect.getInstance();
    }
}
