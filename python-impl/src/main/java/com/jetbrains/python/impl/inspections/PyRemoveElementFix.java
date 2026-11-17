// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyPattern;
import consulo.codeEditor.Editor;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

// JB declares this class in PyPatternInspection.kt; ModCommand PsiUpdateModCommandAction is not available in Consulo
public class PyRemoveElementFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    private final boolean myIsPattern;

    public PyRemoveElementFix(PyElement element) {
        super(element);
        myIsPattern = element instanceof PyPattern;
    }

    @Override
    public LocalizeValue getText() {
        return myIsPattern ? PyLocalize.qfixRemovePattern() : PyLocalize.qfixNameRemoveElement();
    }

    @Override
    public void invoke(Project project, PsiFile file, @Nullable Editor editor, PsiElement startElement, PsiElement endElement) {
        startElement.delete();
    }
}
