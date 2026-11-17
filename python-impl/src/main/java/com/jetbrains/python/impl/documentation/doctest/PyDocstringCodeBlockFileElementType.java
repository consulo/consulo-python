// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.doctest;

import com.jetbrains.python.impl.psi.PyFileElementType;
import consulo.language.Language;

public class PyDocstringCodeBlockFileElementType extends PyFileElementType {
    public PyDocstringCodeBlockFileElementType(Language language) {
        super(language);
    }

    @Override
    public String getExternalId() {
        return "PyDocstringCodeBlock.FILE";
    }
}
