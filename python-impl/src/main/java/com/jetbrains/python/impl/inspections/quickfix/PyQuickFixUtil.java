// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.language.editor.util.PsiEditorUtil;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

public final class PyQuickFixUtil {
    public static @Nullable Editor getEditor(PsiElement element) {
        Document document = PsiDocumentManager.getInstance(element.getProject()).getDocument(element.getContainingFile());
        if (document == null) {
            return null;
        }

        return PsiEditorUtil.findEditor(element);
    }

    public static @Nullable PsiElement dereference(PsiElement element) {
        if (element instanceof PyReferenceExpression) {
            return element.getReference().resolve();
        }
        return element;
    }
}
