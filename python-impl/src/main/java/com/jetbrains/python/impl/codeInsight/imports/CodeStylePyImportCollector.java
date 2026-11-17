// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.formatter.PyCodeStyleSettings;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import consulo.language.codeStyle.CodeStyle;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import org.jspecify.annotations.Nullable;

public class CodeStylePyImportCollector extends PyImportCollector {
    public CodeStylePyImportCollector(PyElement node, PsiReference reference, String refText) {
        super(node, reference, refText);
    }

    @Override
    protected @Nullable PsiFile addCandidatesViaFromImports(@Nullable PsiFile existingImportFile, PyFile pyFile) {
        PyCodeStyleSettings pySettings = CodeStyle.getCustomSettings(getNode().getContainingFile(), PyCodeStyleSettings.class);
        return pySettings.OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS
            ? existingImportFile
            : super.addCandidatesViaFromImports(existingImportFile, pyFile);
    }
}
