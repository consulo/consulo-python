// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.ParenthesesInsertHandler;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * Inserts square brackets after names of parameterized types inside type hints.
 * It includes special constructs from typings, such as {@code typing.Optional} or {@code typing.Union},
 * and regular generic classes, such as {@code list} or {@code ContextManager}.
 */
public class PyParameterizedTypeInsertHandler extends ParenthesesInsertHandler<LookupElement> {
    public static boolean isCompletingParameterizedType(PsiElement definition,
                                                        PsiElement completionPosition,
                                                        TypeEvalContext context) {
        return PyTypingTypeProvider.isInsideTypeHint(completionPosition, context) && isParameterizableType(definition, context);
    }

    public static final PyParameterizedTypeInsertHandler INSTANCE = new PyParameterizedTypeInsertHandler();

    private PyParameterizedTypeInsertHandler() {
        super(false, false, true, false, '[', ']');
    }

    @Override
    protected boolean placeCaretInsideParentheses(InsertionContext context, LookupElement item) {
        return true;
    }

    private static boolean isParameterizableType(PsiElement element, TypeEvalContext typeEvalContext) {
        return element instanceof PyQualifiedNameOwner qnOwner &&
               PyTypingTypeProvider.GENERIC_CLASSES.contains(qnOwner.getQualifiedName()) ||
               element instanceof PyClass pyClass && new PyTypingTypeProvider().getGenericType(pyClass, typeEvalContext) != null;
    }
}
