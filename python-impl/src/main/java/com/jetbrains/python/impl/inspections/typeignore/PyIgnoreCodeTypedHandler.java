// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.typeignore;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.inspections.PyIgnoreCommentUtil;
import com.jetbrains.python.psi.PyFile;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.codeEditor.EditorEx;
import consulo.codeEditor.HighlighterIterator;
import consulo.language.editor.AutoPopupController;
import consulo.language.editor.action.TypedHandlerDelegate;
import consulo.language.psi.PsiFile;
import consulo.project.Project;

/**
 * Opens the code completion after {@code [} or {@code ,} in the open code list of an ignore comment.
 */
// JB: declared in PyIgnoreCodeCompletionContributor.kt
@ExtensionImpl
public class PyIgnoreCodeTypedHandler extends TypedHandlerDelegate {
    @Override
    public Result checkAutoPopup(char charTyped, Project project, Editor editor, PsiFile file) {
        if (charTyped != '[' && charTyped != ',') {
            return Result.CONTINUE;
        }
        if (!(file instanceof PyFile)) {
            return Result.CONTINUE;
        }
        int caret = editor.getCaretModel().getOffset();
        if (caret == 0) {
            return Result.CONTINUE;
        }
        if (!(editor instanceof EditorEx editorEx)) {
            return Result.CONTINUE;
        }
        HighlighterIterator iterator = editorEx.getHighlighter().createIterator(caret - 1);
        if (iterator.atEnd() || iterator.getTokenType() != PyTokenTypes.END_OF_LINE_COMMENT) {
            return Result.CONTINUE;
        }
        String commentBeforeCaret = editor.getDocument().getCharsSequence().subSequence(iterator.getStart(), caret).toString() + charTyped;
        if (!PyIgnoreCommentUtil.UNCLOSED_CODES_PATTERN.matcher(commentBeforeCaret).matches()) {
            return Result.CONTINUE;
        }
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
        return Result.STOP;
    }
}
