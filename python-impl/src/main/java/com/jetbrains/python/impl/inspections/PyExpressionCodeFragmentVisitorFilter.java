// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiFile;

@ExtensionImpl
public class PyExpressionCodeFragmentVisitorFilter implements PythonVisitorFilter {
    @Override
    public boolean isSupported(Class visitorClass, PsiFile file) {
        if (file instanceof PyExpressionCodeFragment) {
            if (visitorClass == PyPep8Inspection.class) {
                return false;
            }
        }
        return true;
    }

    // Consulo: PythonVisitorFilter is a LanguageExtension
    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
