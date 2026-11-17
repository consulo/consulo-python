// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.psi.PyQuotesUtil;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * User: catherine
 * Intention to convert between single-quoted and double-quoted strings
 */
// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyQuotedStringIntention", categories = "Python", fileExtensions = "py")
public final class PyQuotedStringIntention extends PsiElementBaseIntentionAction {
    public PyQuotedStringIntention() {
        setText(PyLocalize.intnQuotedString());
    }

    private static @Nullable PsiElement findElement(PsiElement leaf) {
        PsiElement current = leaf;
        while (current != null) {
            if (current instanceof PsiElement candidate) {
                return candidate;
            }
            if (current instanceof PsiFile) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement leaf) {
        PsiElement element = findElement(leaf);
        if (element == null) {
            return false;
        }
        LocalizeValue text = getPresentation(element);
        if (text == null) {
            return false;
        }
        setText(text);
        return true;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement leaf) throws IncorrectOperationException {
        PsiElement element = findElement(leaf);
        if (element == null) {
            return;
        }
        invoke(element, editor);
    }

    private @Nullable LocalizeValue getPresentation(PsiElement element) {
        if (!(element.getContainingFile() instanceof PyFile)) {
            return null;
        }

        PyStringElement stringElement = findConvertibleStringElementUnderCaret(element);
        if (stringElement == null) {
            return null;
        }
        PyStringLiteralExpression stringLiteral = as(stringElement.getParent(), PyStringLiteralExpression.class);
        if (stringLiteral == null) {
            return null;
        }
        PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(stringLiteral, PyDocStringOwner.class);
        if (docStringOwner != null && docStringOwner.getDocStringExpression() == stringLiteral) {
            return null;
        }
        String currentQuote = stringElement.getQuote();
        if (currentQuote.equals("'")) {
            return PyLocalize.intnQuotedStringSingleToDouble();
        }
        else {
            return PyLocalize.intnQuotedStringDoubleToSingle();
        }
    }


    private static @Nullable PyStringElement findConvertibleStringElementUnderCaret(PsiElement element) {
        IElementType elementType = element.getNode().getElementType();
        if (!(PyTokenTypes.STRING_NODES.contains(elementType) || PyTokenTypes.FSTRING_TOKENS.contains(elementType))) {
            return null;
        }
        PyStringElement stringElement = PsiTreeUtil.getParentOfType(element, PyStringElement.class, false, PyExpression.class);
        return stringElement != null && PyQuotesUtil.canBeConverted(stringElement, true) ? stringElement : null;
    }

    private void invoke(PsiElement element, Editor editor) {
        PyStringElement stringElement = PsiTreeUtil.getParentOfType(element, PyStringElement.class, false, PyExpression.class);
        if (stringElement == null) {
            return;
        }
        PyStringLiteralExpression stringLiteral = as(stringElement.getParent(), PyStringLiteralExpression.class);
        if (stringLiteral == null) {
            return;
        }
        String originalQuote = stringElement.getQuote();
        boolean entireLiteralCanBeConverted = ContainerUtil.all(stringLiteral.getStringElements(),
            s -> s.getQuote().equals(originalQuote) &&
                PyQuotesUtil.canBeConverted(s, true));
        if (entireLiteralCanBeConverted) {
            stringLiteral.getStringElements().forEach(PyQuotedStringIntention::convertStringElement);
        }
        else {
            convertStringElement(stringElement);
        }
    }

    // JB also declares isAvailable(Project, Editor, PsiFile) / invoke(Project, Editor, PsiFile) bridging to ModCommandExecutor;
    // in Consulo they are provided by PsiElementBaseIntentionAction

    private static void convertStringElement(PyStringElement stringElement) {
        stringElement.replace(PyQuotesUtil.createCopyWithConvertedQuotes(stringElement));
    }
}

