// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.pyi;

import com.jetbrains.python.impl.psi.PyFileElementType;
import consulo.language.Language;

public class PyiFileElementType extends PyFileElementType {
    protected PyiFileElementType(Language language) {
        super(language);
    }

    @Override
    public String getExternalId() {
        return "PythonStub.FILE";
    }
}
