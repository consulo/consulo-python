// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeHints;

import com.jetbrains.python.PythonLanguage;
import consulo.language.DependentLanguage;
import consulo.language.Language;

public class PyTypeHintDialect extends Language implements DependentLanguage {
    public static final PyTypeHintDialect INSTANCE = new PyTypeHintDialect();

    private PyTypeHintDialect() {
        super(PythonLanguage.getInstance(), "PyTypeHint");
    }
}
