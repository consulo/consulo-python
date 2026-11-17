// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import consulo.ide.impl.idea.openapi.editor.actions.ConvertIndentsActionBase;
import com.jetbrains.python.PythonCodeStyleService;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class ConvertIndentsFix implements LocalQuickFix {
    private final boolean myToSpaces;

    public ConvertIndentsFix(boolean toSpaces) {
        myToSpaces = toSpaces;
    }

    @Override
    public LocalizeValue getName() {
        return myToSpaces ? PyLocalize.qfixConvertIndentsToSpaces() : PyLocalize.qfixConvertIndentsToTabs();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiFile file = descriptor.getPsiElement().getContainingFile();
        Document document = PsiDocumentManager.getInstance(project).getDocument(file);
        if (document != null) {
            int tabSize = PythonCodeStyleService.getInstance().getIndentSize(file);
            TextRange allDoc = new TextRange(0, document.getTextLength());
            if (myToSpaces) {
                // TODO consulo: ConvertIndentsUtil missing, ConvertIndentsActionBase used
                ConvertIndentsActionBase.convertIndentsToSpaces(document, tabSize, allDoc);
            }
            else {
                // TODO consulo: ConvertIndentsUtil missing, ConvertIndentsActionBase used
                ConvertIndentsActionBase.convertIndentsToTabs(document, tabSize, allDoc);
            }
        }
    }
}
