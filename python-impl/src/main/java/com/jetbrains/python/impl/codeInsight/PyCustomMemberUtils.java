// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.codeInsight.PyCustomMember;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.ParenthesesInsertHandler;
import org.jspecify.annotations.Nullable;

/**
 * TODO: Move methods to {@link PyCustomMember}. Only dependency hell prevents me from doing it
 */
public final class PyCustomMemberUtils {
    private PyCustomMemberUtils() {
    }

    /**
     * Creates {@link consulo.language.editor.completion.lookup.LookupElement} to be used in cases like {@link com.jetbrains.python.psi.types.PyType#getCompletionVariants(String, consulo.language.psi.PsiElement, consulo.language.util.ProcessingContext)}
     * This method should be in {@link PyCustomMember} but it does not. We need to move it.
     *
     * @param member   custom member
     * @param typeText type text (if any)
     * @return lookup element
     */
    public static LookupElementBuilder toLookUpElement(PyCustomMember member, @Nullable String typeText) {

        LookupElementBuilder lookupElementBuilder = LookupElementBuilder.create(member.getName())
            .withIcon(member.getIcon())
            .withTypeText(typeText);
        if (member.isFunction()) {
            lookupElementBuilder = lookupElementBuilder.withInsertHandler(ParenthesesInsertHandler.NO_PARAMETERS).withLookupString("()");
        }
        return lookupElementBuilder;
    }
}
