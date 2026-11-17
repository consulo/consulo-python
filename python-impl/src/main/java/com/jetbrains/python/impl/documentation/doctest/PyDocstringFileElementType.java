// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.doctest;

import com.jetbrains.python.impl.psi.PyFileElementType;
import consulo.language.Language;

/**
 * User : ktisha
 */
public class PyDocstringFileElementType extends PyFileElementType {
    public PyDocstringFileElementType(Language language) {
        super(language);
    }

    @Override
    public String getExternalId() {
        return "PyDocstring.FILE";
    }
}
