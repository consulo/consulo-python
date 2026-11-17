// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import consulo.codeEditor.Editor;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

// Consulo: JB PsiUpdateModCommandAction is expressed as LocalQuickFixAndIntentionActionOnPsiElement
public class WrapExceptTupleInParenthesesQuickFix extends LocalQuickFixAndIntentionActionOnPsiElement {

    public WrapExceptTupleInParenthesesQuickFix(PyTupleExpression exceptPartTuple) {
        super(exceptPartTuple);
    }

    @Override
    public LocalizeValue getText() {
        return PyLocalize.qfixExceptClauseMissingParens();
    }

    @Override
    public void invoke(Project project, PsiFile file, @Nullable Editor editor, PsiElement startElement, PsiElement endElement) {
        PyTupleExpression exceptPartTuple = (PyTupleExpression)startElement;
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        LanguageLevel level = LanguageLevel.forElement(exceptPartTuple);
        PyExpression wrapped = generator.createExpressionFromText(level, "(" + exceptPartTuple.getText() + ")");
        exceptPartTuple.replace(wrapped);
    }
}
