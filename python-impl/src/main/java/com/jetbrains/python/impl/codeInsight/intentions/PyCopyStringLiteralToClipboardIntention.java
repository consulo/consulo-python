// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ex.awt.CopyPasteManager;
import consulo.util.lang.Pair;

import java.awt.datatransfer.StringSelection;
import java.util.List;

// TODO consulo: ModCommand API (PsiBasedModCommandAction, ModCommand.copyToClipboard) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyCopyStringLiteralToClipboardIntention", categories = "Python", fileExtensions = "py")
public class PyCopyStringLiteralToClipboardIntention extends PsiElementBaseIntentionAction {
    @Override
    public LocalizeValue getText() {
        return PyLocalize.intnNameCopyStringToClipboard();
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class, false) != null;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
        PyStringLiteralExpression expression = PsiTreeUtil.getParentOfType(element, PyStringLiteralExpression.class, false);
        if (expression == null) {
            return;
        }
        CopyPasteManager.getInstance().setContents(new StringSelection(buildStringText(expression)));
    }

    @Override
    public boolean startInWriteAction() {
        return false;
    }

    private static String buildStringText(PyStringLiteralExpression expression) {
        if (!expression.isInterpolated()) {
            return expression.getStringValue();
        }
        StringBuilder builder = new StringBuilder();
        for (PyStringElement element : expression.getStringElements()) {
            if (element instanceof PyFormattedStringElement formattedStringElement) {
                List<TextRange> literalRanges = formattedStringElement.getLiteralPartRanges();
                for (Pair<TextRange, String> fragment : formattedStringElement.getDecodedFragments()) {
                    TextRange range = fragment.getFirst();
                    if (literalRanges.stream().anyMatch(it -> it.containsRange(range.getStartOffset(), range.getEndOffset()))) {
                        builder.append(fragment.getSecond());
                    }
                    else {
                        builder.append('?');
                    }
                }
            }
            else {
                for (Pair<TextRange, String> fragment : element.getDecodedFragments()) {
                    builder.append(fragment.getSecond());
                }
            }
        }
        return builder.toString();
    }
}
