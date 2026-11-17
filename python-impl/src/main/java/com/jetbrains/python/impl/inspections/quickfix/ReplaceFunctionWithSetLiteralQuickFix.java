// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.inspections.PySetFunctionToLiteralInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpressionStatement;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

/**
 * User : catherine
 * Quick Fix to replace function call of built-in function "set" with
 * set literal if applicable
 */
public class ReplaceFunctionWithSetLiteralQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixReplaceFunctionSetWithLiteral();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyElement[] elements = PySetFunctionToLiteralInspection.getSetCallArguments((PyCallExpression)element);
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        StringBuilder str = new StringBuilder("{");
        for (int i = 0; i != elements.length; ++i) {
            PyElement e = elements[i];
            str.append(e.getText());
            if (i != elements.length - 1) {
                str.append(", ");
            }
        }
        str.append("}");
        element.replace(elementGenerator.createFromText(LanguageLevel.forElement(element), PyExpressionStatement.class,
                str.toString()).getExpression());
    }
}
