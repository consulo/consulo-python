// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.liveTemplates;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.WriteAction;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.editor.template.TemplatePreprocessor;
import consulo.language.psi.PsiFile;
import consulo.util.lang.CharArrayUtil;

@ExtensionImpl
public class PyDecoratorTemplatePreprocessor implements TemplatePreprocessor {
    @Override
    public void preprocessTemplate(Editor editor, PsiFile file, int caretOffset, String textToInsert, String templateText) {
        if (!templateText.startsWith("@")) {
            return;
        }

        Document document = editor.getDocument();
        if (caretOffset <= 0) {
            return;
        }

        CharSequence text = document.getCharsSequence();
        int checkPos = CharArrayUtil.shiftBackward(text, caretOffset - 1, " \t");

        if (checkPos >= 0 && text.charAt(checkPos) == '@') {
            WriteAction.run(() -> document.deleteString(checkPos, checkPos + 1));
        }
    }
}
