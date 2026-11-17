// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import consulo.document.Document;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.Lookup;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.util.lang.CharArrayUtil;


public class OverwriteEqualsInsertHandler implements InsertHandler<LookupElement> {
    public static final OverwriteEqualsInsertHandler INSTANCE = new OverwriteEqualsInsertHandler();

    private OverwriteEqualsInsertHandler() {
    }

    @Override
    public void handleInsert(InsertionContext context, LookupElement item) {
        if (context.getCompletionChar() != Lookup.REPLACE_SELECT_CHAR) {
            return;
        }
        String lookupString = item.getLookupString();
        Document doc = context.getDocument();
        int tailOffset = context.getTailOffset();
        if (lookupString.endsWith("=") && CharArrayUtil.regionMatches(doc.getCharsSequence(), tailOffset, "=")) {
            doc.deleteString(tailOffset, tailOffset + 1);
        }
        else if (lookupString.endsWith(" = ") && CharArrayUtil.regionMatches(doc.getCharsSequence(), tailOffset, " = ")) {
            doc.deleteString(tailOffset, tailOffset + 3);
        }
    }
}
