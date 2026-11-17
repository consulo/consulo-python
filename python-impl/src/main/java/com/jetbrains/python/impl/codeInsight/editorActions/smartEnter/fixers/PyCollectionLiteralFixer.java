// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers;

import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiErrorElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.util.collection.ArrayUtil;
import consulo.python.psi.localize.PyParsingLocalize;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.SmartEnterUtil;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyDoubleStarExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;

import static com.jetbrains.python.impl.psi.PyUtil.as;

public class PyCollectionLiteralFixer extends PyFixer<PySequenceExpression> {
    public PyCollectionLiteralFixer() {
        super(PySequenceExpression.class);
    }

    @Override
    protected boolean isApplicable(Editor editor, PySequenceExpression element) {
        return true;
    }

    @Override
    protected void doApply(Editor editor, PySmartEnterProcessor processor, PySequenceExpression collection) {
        int caretOffset = editor.getCaretModel().getOffset();
        PsiElement collectionItemAnchor = collection.getContainingFile().findElementAt(caretOffset);
        if (collectionItemAnchor == null) {
            return;
        }
        if (collectionItemAnchor instanceof PsiWhiteSpace) {
            collectionItemAnchor = PyPsiUtils.getPrevNonWhitespaceSiblingOnSameLine(collectionItemAnchor);
        }
        if (collectionItemAnchor != null && isMissingColonError(collectionItemAnchor)) {
            collectionItemAnchor = collectionItemAnchor.getPrevSibling();
        }
        // Since surrounding parentheses don't belong to PyTupleExpression expression,
        // whitespace after its last element is a sibling of the tuple itself
        if (collection instanceof PyTupleExpression && collectionItemAnchor == collection) {
            collectionItemAnchor = ArrayUtil.getLastElement(collection.getElements());
        }
        if (collectionItemAnchor == null) {
            return;
        }
        PsiElement collectionItem = PyPsiUtils.getParentRightBefore(collectionItemAnchor, collection);
        if (!(collectionItem instanceof PyExpression)) {
            return;
        }

        Document document = editor.getDocument();
        int collectionItemLastLine = document.getLineNumber(collectionItem.getTextRange().getEndOffset());
        int caretLine = document.getLineNumber(caretOffset);
        if (collectionItemLastLine != caretLine) {
            return;
        }

        PsiElement nextOnSameLine = PyPsiUtilsCore.getNextNonWhitespaceSiblingOnSameLine(collectionItem);
        if (nextOnSameLine != null && isMissingColonError(nextOnSameLine)) {
            nextOnSameLine = PyPsiUtilsCore.getNextNonWhitespaceSiblingOnSameLine(nextOnSameLine);
        }
        if (nextOnSameLine == null || nextOnSameLine instanceof PsiComment) {
            int separatorOffset = collectionItem.getTextRange().getEndOffset();
            PyKeyValueExpression keyValuePair = as(collectionItem, PyKeyValueExpression.class);
            if (collection instanceof PyDictLiteralExpression && keyValuePair == null && !(collectionItem instanceof PyDoubleStarExpression)) {
                document.insertString(separatorOffset, ": ");
                processor.registerUnresolvedError(separatorOffset + 2);
            }
            // Our parser can't handle "'key': ," sequence
            else if (!(keyValuePair != null && keyValuePair.getValue() == null)) {
                document.insertString(separatorOffset, ",");
                editor.getCaretModel().moveToOffset(separatorOffset + 1);
                SmartEnterUtil.plainEnter(editor);
                // The default behavior is to move the caret after the containing statement, unless other caretOffset is specified explicitly
                processor.registerUnresolvedError(editor.getCaretModel().getOffset());
                // Forcibly commit the document to prevent other fixers and enter processors from being run
                PsiDocumentManager.getInstance(collection.getProject()).commitDocument(document);
            }
        }
    }

    private static boolean isMissingColonError(PsiElement element) {
        PsiErrorElement errorElement = as(element, PsiErrorElement.class);
        return errorElement != null && PyParsingLocalize.parseExpectedColon().equals(errorElement.getErrorDescriptionValue());
    }
}
