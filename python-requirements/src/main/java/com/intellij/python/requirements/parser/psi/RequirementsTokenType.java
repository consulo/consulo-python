// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.requirements.parser.psi;

import com.intellij.python.requirements.RequirementsLanguage;
import consulo.language.ast.IElementType;

public class RequirementsTokenType extends IElementType {
    public RequirementsTokenType(String debugName) {
        super(debugName, RequirementsLanguage.INSTANCE);
    }

    @Override
    public String toString() {
        return "RequirementsTokenType." + super.toString();
    }
}
