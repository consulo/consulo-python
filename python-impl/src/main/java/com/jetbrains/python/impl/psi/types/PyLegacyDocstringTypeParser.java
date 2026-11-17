// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * A more descriptively named API alias for {@link PyTypeParser}.
 */
@SuppressWarnings("deprecation")
public final class PyLegacyDocstringTypeParser {
    private PyLegacyDocstringTypeParser() {
    }

    public static @Nullable PyType getTypeByName(@Nullable PsiElement anchor, String type) {
        return PyTypeParser.getTypeByName(anchor, type);
    }

    public static @Nullable PyType getTypeByName(@Nullable PsiElement anchor, String type, TypeEvalContext context) {
        return PyTypeParser.getTypeByName(anchor, type, context);
    }

    public static @Nullable PyType getTypeByName(@Nullable PsiElement anchor, String type, TypeEvalContext context, boolean fqnOnly) {
        return PyTypeParser.getTypeByName(anchor, type, context, fqnOnly);
    }

    public static PyTypeParser.ParseResult parse(PsiElement anchor, String type) {
        return PyTypeParser.parse(anchor, type);
    }

    public static PyTypeParser.ParseResult parse(PsiElement anchor, String type, TypeEvalContext context) {
        return PyTypeParser.parse(anchor, type, context);
    }

    public static PyTypeParser.ParseResult parse(PsiElement anchor, String type, TypeEvalContext context, boolean fqnOnly) {
        return PyTypeParser.parse(anchor, type, context, fqnOnly);
    }
}
