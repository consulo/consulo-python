/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.inspections.quickfix;

import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.editor.intention.IntentionAction;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
public class RemoveTrailingBlankLinesFix implements LocalQuickFix, IntentionAction, HighPriorityAction {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return LocalizeValue.localizeTODO("Remove trailing blank lines");
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return getText();
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        return true;
    }

    @Override
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        removeTrailingBlankLines(file);
    }

    @Override
    public boolean startInWriteAction() {
        return true;
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        removeTrailingBlankLines(descriptor.getPsiElement().getContainingFile());
    }

    private static void removeTrailingBlankLines(PsiFile file) {
        Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
        if (document == null) {
            return;
        }
        int lastBlankLineOffset = -1;
        for (int i = document.getLineCount() - 1; i >= 0; i--) {
            int lineStart = document.getLineStartOffset(i);
            String trimmed = document.getCharsSequence().subSequence(lineStart, document.getLineEndOffset(i)).toString().trim();
            if (trimmed.length() > 0) {
                break;
            }
            lastBlankLineOffset = lineStart;
        }
        if (lastBlankLineOffset > 0) {
            document.deleteString(lastBlankLineOffset, document.getTextLength());
        }
    }
}
