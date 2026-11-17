// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi;

import consulo.language.psi.FileContextUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPsiElementPointer;
import org.jspecify.annotations.Nullable;

public final class PsiExtKt {
    private PsiExtKt() {
    }

    public static @Nullable PsiElement injectionParent(PsiFile file) {
        SmartPsiElementPointer<?> pointer = file.getUserData(FileContextUtil.INJECTED_IN_ELEMENT);
        return pointer != null ? pointer.getElement() : null;
    }

    // TODO consulo: org.toml (consulo-toml) dependency missing in python-impl
    //public static @Nullable String getStringOrNull(TomlLiteral literal) {
    //    TomlLiteralKind kind = TomlLiteralExt.getKind(literal);
    //    if (kind instanceof TomlLiteralKind.StringKind stringKind) {
    //        return stringKind.getValue();
    //    }
    //    return null;
    //}
}
