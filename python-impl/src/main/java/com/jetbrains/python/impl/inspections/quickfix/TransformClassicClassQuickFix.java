// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class TransformClassicClassQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixClassicClassTransform();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        element = PsiTreeUtil.getParentOfType(element, PyClass.class);
        if (element != null) {
            PyClass pyClass = (PyClass)element;
            PyExpression[] superClassExpressions = pyClass.getSuperClassExpressions();
            PyElementGenerator generator = PyElementGenerator.getInstance(project);
            if (superClassExpressions.length == 0) {
                pyClass.replace(generator.createFromText(LanguageLevel.getDefault(), PyClass.class,
                        "class " + pyClass.getName() + "(" +
                        PyNames.OBJECT + "):\n    " + pyClass.getStatementList().getText()));
            }
            else {
                StringBuilder stringBuilder = new StringBuilder("class ");
                stringBuilder.append(pyClass.getName()).append("(");
                for (PyExpression expression : superClassExpressions) {
                    stringBuilder.append(expression.getText()).append(", ");
                }
                stringBuilder.append(PyNames.OBJECT).append(":\n    ");
                stringBuilder.append(pyClass.getStatementList().getText());
                pyClass.replace(generator.createFromText(LanguageLevel.getDefault(), PyClass.class, stringBuilder.toString()));
            }
        }
    }
}
