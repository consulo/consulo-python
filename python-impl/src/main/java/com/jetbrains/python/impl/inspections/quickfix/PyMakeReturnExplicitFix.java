// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyPassStatement;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyStatement;
import consulo.codeEditor.Editor;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

/**
 * Appends missing `return None`, and transforms `return` into `return None`.
 */
// Consulo: JB PsiUpdateModCommandAction is expressed as LocalQuickFixAndIntentionActionOnPsiElement
public class PyMakeReturnExplicitFix extends LocalQuickFixAndIntentionActionOnPsiElement {
    public PyMakeReturnExplicitFix(PyStatement statement) {
        super(statement);
    }

    @Override
    public LocalizeValue getText() {
        // JB: getPresentation()
        PsiElement element = getStartElement();
        if (element instanceof PyReturnStatement || element instanceof PyPassStatement) {
            return PyLocalize.qfixReplaceWithReturnNone();
        }
        return PyLocalize.qfixAddExplicitReturnNone();
    }

    @Override
    public void invoke(Project project, PsiFile file, @Nullable Editor editor, PsiElement startElement, PsiElement endElement) {
        PyStatement element = (PyStatement)startElement;
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(element.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(element);

        PyReturnStatement returnStmt = elementGenerator.createFromText(languageLevel, PyReturnStatement.class, "return None");

        if (element instanceof PyReturnStatement || element instanceof PyPassStatement) {
            element.replace(returnStmt);
        }
        else {
            element.getParent().addAfter(returnStmt, element);
        }
    }
}
