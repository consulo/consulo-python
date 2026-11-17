// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import consulo.codeEditor.Editor;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.ParenthesesInsertHandler;

// Declared in PyMultipleArgumentsCompletionContributor.kt in JetBrains sources
public class PyMultipleArgumentsInsertHandler extends ParenthesesInsertHandler<LookupElement> {
    private final PyCallExpression myCall;

    public PyMultipleArgumentsInsertHandler(PyCallExpression call) {
        myCall = call;
    }

    @Override
    protected boolean placeCaretInsideParentheses(InsertionContext context, LookupElement item) {
        return false;
    }

    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
        Editor editor = context.getEditor();
        context.commitDocument();
        PyArgumentList argumentList = myCall.getArgumentList();
        if (argumentList == null || argumentList.getClosingParen() == null) {
            editor.getDocument().insertString(context.getTailOffset(), ")");
            editor.getCaretModel().moveToOffset(context.getTailOffset());
        }
        else {
            editor.getCaretModel().moveToOffset(context.getTailOffset() + 1);
        }
    }
}
