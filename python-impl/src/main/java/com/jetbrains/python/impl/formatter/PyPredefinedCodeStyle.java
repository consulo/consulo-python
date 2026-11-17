// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.formatter;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.formatter.PyCodeStyleDefaultsKt;
import consulo.language.Language;
import consulo.language.codeStyle.PredefinedCodeStyle;

public abstract class PyPredefinedCodeStyle extends PredefinedCodeStyle {
    protected PyPredefinedCodeStyle(String name) {
        super(name, PythonLanguage.getInstance());
    }

    public abstract String getCodeStyleId();

    // The whole PY-85946 feature is inert while the master switch is off, so the profiles must not be
    // offered in the Code Style settings either (otherwise the historical behavior isn't byte-identical).
    // TODO consulo: PredefinedCodeStyle#isApplicableToLanguage missing
    public boolean isApplicableToLanguage(Language language) {
        return PyCodeStyleDefaultsKt.isPyNewFormatterDefaultsFeatureEnabled() && getLanguage().equals(language);
    }
}
