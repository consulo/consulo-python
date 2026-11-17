// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.editor.selectWord;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstFStringFragment;
import com.jetbrains.python.ast.PyAstFStringFragmentFormatPart;
import com.jetbrains.python.ast.PyAstFormattedStringElement;
import com.jetbrains.python.ast.PyAstStringLiteralExpression;
import com.jetbrains.python.lexer.PyFStringLiteralLexer;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.ast.IElementType;
import consulo.language.editor.action.ExtendWordSelectionHandlerBase;
import consulo.language.editor.action.SelectWordUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class PyFStringSelectionHandler extends ExtendWordSelectionHandlerBase {
    @Override
    public boolean canSelect(PsiElement e) {
        ASTNode node = e.getNode();
        if (node == null) {
            return false;
        }
        IElementType elementType = node.getElementType();
        if (PyTokenTypes.FSTRING_LITERAL_TOKENS.contains(elementType)) {
            return true;
        }
        return PsiTreeUtil.getParentOfType(e, PyAstFStringFragment.class, false) != null;
    }

    @Override
    public @Nullable List<TextRange> select(PsiElement e, CharSequence editorText, int cursorOffset, Editor editor) {
        ASTNode node = e.getNode();
        if (node == null) {
            return null;
        }
        IElementType elementType = node.getElementType();

        if (PyTokenTypes.FSTRING_LITERAL_TOKENS.contains(elementType)) {
            return selectFStringContent(e, editorText, cursorOffset);
        }

        return selectFStringFragment(e);
    }

    private static @Nullable List<TextRange> selectFStringContent(PsiElement e, CharSequence editorText, int cursorOffset) {
        PyAstFormattedStringElement fStringElement = PsiTreeUtil.getParentOfType(e, PyAstFormattedStringElement.class);
        if (fStringElement == null) {
            return null;
        }

        List<TextRange> result = new ArrayList<>();

        ASTNode node = e.getNode();
        IElementType elementType = node.getElementType();
        if (PyTokenTypes.FSTRING_TEXT_TOKENS.contains(elementType)) {
            SelectWordUtil.addWordHonoringEscapeSequences(
                editorText, node.getTextRange(), cursorOffset,
                new PyFStringLiteralLexer(elementType),
                result
            );
        }

        TextRange contentRange = fStringElement.getContentRange();
        int fStringStartOffset = fStringElement.getTextRange().getStartOffset();
        TextRange absoluteContentRange = contentRange.shiftRight(fStringStartOffset);
        if (!absoluteContentRange.isEmpty()) {
            result.add(absoluteContentRange);
        }

        int prefixLength = fStringElement.getPrefixLength();
        if (prefixLength > 0) {
            TextRange withoutPrefixRange = new TextRange(
                fStringElement.getTextRange().getStartOffset() + prefixLength,
                fStringElement.getTextRange().getEndOffset()
            );
            result.add(withoutPrefixRange);
        }

        PyAstStringLiteralExpression stringLiteral = PsiTreeUtil.getParentOfType(fStringElement, PyAstStringLiteralExpression.class);
        result.add(stringLiteral != null ? stringLiteral.getTextRange() : fStringElement.getTextRange());

        return result;
    }

    private static @Nullable List<TextRange> selectFStringFragment(PsiElement e) {
        PyAstFStringFragment fragment = PsiTreeUtil.getParentOfType(e, PyAstFStringFragment.class, false);
        if (fragment == null) {
            return null;
        }

        PyAstExpression expression = fragment.getExpression();
        if (expression == null) {
            return null;
        }
        TextRange exprRange = expression.getTextRange();

        List<TextRange> result = new ArrayList<>();

        result.add(exprRange);

        PsiElement typeConversionPsi = fragment.getTypeConversion();
        if (typeConversionPsi != null) {
            TextRange typeConversionRange = typeConversionPsi.getTextRange();
            result.add(new TextRange(exprRange.getStartOffset(), typeConversionRange.getEndOffset()));
        }

        PyAstFStringFragmentFormatPart formatPartPsi = fragment.getFormatPart();
        if (formatPartPsi != null) {
            TextRange formatTextRange = formatPartPsi.getTextRange();
            result.add(new TextRange(exprRange.getStartOffset(), formatTextRange.getEndOffset()));
        }

        result.add(fragment.getTextRange());

        PsiElement parent = fragment.getParent();
        if (parent instanceof PyAstFormattedStringElement) {
            result.add(parent.getTextRange());
        }
        return result;
    }
}
