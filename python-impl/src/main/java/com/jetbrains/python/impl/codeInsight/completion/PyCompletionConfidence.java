// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyStringElement;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.editor.completion.CompletionConfidence;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiFile;
import consulo.util.lang.ThreeState;
import one.util.streamex.StreamEx;

import java.util.Objects;

import static com.jetbrains.python.impl.psi.PyUtil.as;


@ExtensionImpl
public final class PyCompletionConfidence extends CompletionConfidence {

    // TODO consulo: CompletionConfidence#shouldSkipAutopopup(Editor, PsiElement, PsiFile, int) missing, Editor parameter dropped
    @Override
    public ThreeState shouldSkipAutopopup(PsiElement contextElement, PsiFile psiFile, int offset) {
        ASTNode node = contextElement.getNode();
        if (node != null) {
            IElementType elementType = node.getElementType();
            if (elementType == PyTokenTypes.FLOAT_LITERAL || elementType == PyTokenTypes.INTEGER_LITERAL) {
                return ThreeState.YES;
            }
            if (isSequenceOfDots(contextElement)) {
                return ThreeState.YES;
            }
            if (elementType == PyTokenTypes.FSTRING_START) {
                return ThreeState.YES;
            }
            if (PyTokenTypes.STRING_NODES.contains(elementType)) {
                PyStringElement stringElement = as(contextElement, PyStringElement.class);
                if (stringElement != null) {
                    int relativeOffset = offset - stringElement.getTextRange().getStartOffset();
                    if (relativeOffset < stringElement.getContentRange().getStartOffset()) {
                        return ThreeState.YES;
                    }
                }
            }
        }
        return ThreeState.UNSURE;
    }

    private static boolean isSequenceOfDots(PsiElement contextElement) {
        return StreamEx
            .iterate(contextElement, PsiElement::getPrevSibling)
            .takeWhile(Objects::nonNull)
            .limit(3)
            .allMatch(psi -> psi instanceof PsiErrorElement || psi.textMatches("."));
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
