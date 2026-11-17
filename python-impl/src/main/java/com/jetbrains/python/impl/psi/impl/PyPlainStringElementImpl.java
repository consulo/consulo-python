// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.impl.psi.LeafPsiElement;
import consulo.language.ast.IElementType;
import com.jetbrains.python.psi.PyPlainStringElement;

/**
 * @author Mikhail Golubev
 */
public class PyPlainStringElementImpl extends LeafPsiElement implements PyPlainStringElement {
    public PyPlainStringElementImpl(IElementType type, CharSequence text) {
        super(type, text);
    }
}
