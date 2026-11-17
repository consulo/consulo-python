// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFormattedStringElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * Intention to convert between f-strings and t-strings (PEP-750)
 */
// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyFStringToTStringIntention", categories = "Python", fileExtensions = "py")
public class PyFStringToTStringIntention extends PsiElementBaseIntentionAction {
    public PyFStringToTStringIntention() {
        setText(PyLocalize.intnNameConvertBetweenFStringTString());
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement element) {
        PyFormattedStringElement stringElement = PsiTreeUtil.getParentOfType(element, PyFormattedStringElement.class, false);
        if (stringElement == null) {
            return false;
        }
        LocalizeValue text = getPresentation(stringElement);
        if (text == null) {
            return false;
        }
        setText(text);
        return true;
    }

    private static @Nullable LocalizeValue getPresentation(PyFormattedStringElement stringElement) {
        if (LanguageLevel.forElement(stringElement).isOlderThan(LanguageLevel.PYTHON314)) {
            return null;
        }

        if (!(stringElement.getParent() instanceof PyStringLiteralExpression stringLiteral)) {
            return null;
        }
        PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(stringLiteral, PyDocStringOwner.class);
        if (docStringOwner != null && docStringOwner.getDocStringExpression() == stringLiteral) {
            return null;
        }

        if (stringElement.isFormatted()) {
            return PyLocalize.intnConvertFStringToTString();
        }
        else if (stringElement.isTemplate()) {
            return PyLocalize.intnConvertTStringToFString();
        }
        return null;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
        PyFormattedStringElement stringElement = PsiTreeUtil.getParentOfType(element, PyFormattedStringElement.class, false);
        if (stringElement == null) {
            return;
        }
        int prefixOffset = stringElement.getTextOffset();
        String oldPrefix = stringElement.getPrefix();
        String newPrefix;
        if (oldPrefix.contains("f") || oldPrefix.contains("F")) {
            newPrefix = oldPrefix.replace("f", "t").replace("F", "T");
        }
        else if (oldPrefix.contains("t") || oldPrefix.contains("T")) {
            newPrefix = oldPrefix.replace("t", "f").replace("T", "F");
        }
        else {
            return;
        }
        PyUtil.updateDocumentUnblockedAndCommitted(stringElement, (Document document) -> {
            document.replaceString(prefixOffset, prefixOffset + oldPrefix.length(), newPrefix);
        });
    }
}
