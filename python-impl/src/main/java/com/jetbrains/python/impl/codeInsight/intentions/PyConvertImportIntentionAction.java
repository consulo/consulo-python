// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStatement;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.stream.Collectors;

// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
public abstract class PyConvertImportIntentionAction extends PsiElementBaseIntentionAction {
    private final LocalizeValue myIntentionText;

    protected PyConvertImportIntentionAction(LocalizeValue intentionText) {
        myIntentionText = intentionText;
    }

    @Override
    public LocalizeValue getText() {
        return myIntentionText;
    }

    public void replaceImportStatement(PyFromImportStatement statement, PsiFile file, String path) {
        String imported = Arrays.stream(statement.getImportElements()).map(PyImportElement::getText).collect(Collectors.joining(", "));

        PyElementGenerator generator = PyElementGenerator.getInstance(file.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(file);
        PyFromImportStatement generatedStatement = generator.createFromImportStatement(languageLevel, path, imported, null);
        PsiElement formattedStatement = CodeStyleManager.getInstance(file.getProject()).reformat(generatedStatement);
        statement.replace(formattedStatement);
    }

    public @Nullable PyFromImportStatement findStatement(PsiElement element) {
        return PsiTreeUtil.getParentOfType(element, PyFromImportStatement.class, true, PyStatement.class);
    }
}
