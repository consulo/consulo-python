// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class PyUnpackTypeVarTupleQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameUnpackTypeVarTuple();
    }

    public static void replaceToTypingExtensionsUnpack(PsiElement elementTpReplace, PsiElement elementInUnpack,
                                                       PsiFile file, Project project) {
        AddImportHelper.addOrUpdateFromImportStatement(file, "typing_extensions", "Unpack", null,
                                                       AddImportHelper.ImportPriority.FUTURE, null);

        String unpacked = "Unpack[" + elementInUnpack.getText() + "]";
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        PyExpressionStatement expressionStatement =
            elementGenerator.createFromText(LanguageLevel.forElement(elementInUnpack), PyExpressionStatement.class, unpacked);
        PyExpression newElement = expressionStatement.getExpression();

        elementTpReplace.replace(newElement);
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        LanguageLevel languageLevel = LanguageLevel.forElement(element);
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

        if (languageLevel.isAtLeast(LanguageLevel.PYTHON311)) {
            String starred = "*" + element.getText();
            PyExpressionStatement expressionStatement = elementGenerator.createFromText(languageLevel, PyExpressionStatement.class, starred);
            PyExpression newElement = expressionStatement.getExpression();
            element.replace(newElement);
        }
        else {
            replaceToTypingExtensionsUnpack(element, element, element.getContainingFile(), project);
        }
    }
}
