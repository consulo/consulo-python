// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.inspections.PyEncodingUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import consulo.annotation.access.RequiredWriteAction;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

/**
 * add missing encoding declaration
 * # -*- coding: <encoding name> -*-
 * to the source file
 * <p/>
 * User: catherine
 */
public class AddEncodingQuickFix implements LocalQuickFix {

    private final String myDefaultEncoding;
    private final int myEncodingFormatIndex;

    public AddEncodingQuickFix(String defaultEncoding, int encodingFormatIndex) {
        myDefaultEncoding = defaultEncoding;
        myEncodingFormatIndex = encodingFormatIndex;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixAddEncoding();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return;
        }
        PsiElement firstLine = file.getFirstChild();
        if (firstLine instanceof PsiComment && firstLine.getText().startsWith("#!")) {
            firstLine = firstLine.getNextSibling();
        }
        LanguageLevel languageLevel = LanguageLevel.forElement(file);
        String commentText = String.format(PyEncodingUtil.ENCODING_FORMAT_PATTERN[myEncodingFormatIndex], myDefaultEncoding);
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        PsiComment encodingComment = elementGenerator.createFromText(languageLevel, PsiComment.class, commentText);
        encodingComment = (PsiComment)file.addBefore(encodingComment, firstLine);

        if (encodingComment.getNextSibling() == null || !encodingComment.getNextSibling().textContains('\n')) {
            file.addAfter(elementGenerator.createFromText(languageLevel, PsiWhiteSpace.class, "\n"), encodingComment);
        }
        Document document = file.getFileDocument();
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);
        int insertedLineNumber = document.getLineNumber(encodingComment.getTextOffset());
        // Consulo: no ModPsiUpdater.moveCaretTo, the caret is moved in the editor directly
        Editor editor = PyQuickFixUtil.getEditor(file);
        if (editor != null) {
            editor.getCaretModel().moveToOffset(document.getLineStartOffset(insertedLineNumber + 1));
        }
    }
}
