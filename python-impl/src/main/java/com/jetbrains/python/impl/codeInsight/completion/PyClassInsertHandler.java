// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.editor.AutoPopupController;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.ui.annotation.RequiredUIAccess;


public final class PyClassInsertHandler implements InsertHandler<LookupElement> {
    public static PyClassInsertHandler INSTANCE = new PyClassInsertHandler();

    private PyClassInsertHandler() {
    }

    @Override
    @RequiredUIAccess
    public void handleInsert(InsertionContext context, LookupElement item) {
        Editor editor = context.getEditor();
        Document document = editor.getDocument();
        if (context.getCompletionChar() == '(') {
            context.setAddCompletionChar(false);
            int offset = context.getTailOffset();
            document.insertString(offset, "()");

            PyClass pyClass = PyUtil.as(item.getPsiElement(), PyClass.class);
            PyFunction init = pyClass != null ? pyClass.findInitOrNew(true, null) : null;
            if (init != null && PyFunctionInsertHandler.hasParams(context, init)) {
                editor.getCaretModel().moveToOffset(offset + 1);
                AutoPopupController.getInstance(context.getProject()).autoPopupParameterInfo(context.getEditor(), init);
            }
            else {
                editor.getCaretModel().moveToOffset(offset + 2);
            }
        }
    }
}
