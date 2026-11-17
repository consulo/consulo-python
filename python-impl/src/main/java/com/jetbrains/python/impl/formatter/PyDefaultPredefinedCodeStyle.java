// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.formatter;

import com.jetbrains.python.formatter.PyDefaultStyleGuide;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.codeStyle.CodeStyleSettings;

@ExtensionImpl
public class PyDefaultPredefinedCodeStyle extends PyPredefinedCodeStyle {
    public PyDefaultPredefinedCodeStyle() {
        super(PyDefaultStyleGuide.getCODE_STYLE_TITLE().get());
    }

    @Override
    public String getCodeStyleId() {
        return PyDefaultStyleGuide.CODE_STYLE_ID;
    }

    @Override
    public void apply(CodeStyleSettings settings) {
        PyDefaultStyleGuide.apply(settings);
    }
}
