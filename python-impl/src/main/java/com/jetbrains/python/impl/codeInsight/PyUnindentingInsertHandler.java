// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.codeInsight.PyUnindentUtil;
import com.jetbrains.python.impl.codeInsight.completion.PythonLookupElement;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;

/**
 * Adjusts indentation after a final part keyword is inserted, e.g. an "else:".
 */
public final class PyUnindentingInsertHandler implements InsertHandler<PythonLookupElement> {
    public static final PyUnindentingInsertHandler INSTANCE = new PyUnindentingInsertHandler();

    private PyUnindentingInsertHandler() {
    }

    @Override
    public void handleInsert(InsertionContext context, PythonLookupElement item) {
        PyUnindentUtil.unindentAsNeeded(context.getProject(), context.getEditor(), context.getFile());
    }
}
