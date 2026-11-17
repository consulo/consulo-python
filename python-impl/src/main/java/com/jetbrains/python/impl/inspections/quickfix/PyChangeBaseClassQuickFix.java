// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.PythonTemplateRunner;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class PyChangeBaseClassQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixChangeBaseClass();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);
        assert pyClass != null;

        PyArgumentList expressionList = pyClass.getSuperClassExpressionList();
        if (expressionList != null && expressionList.getArguments().length != 0) {
            // Consulo: no ModPsiUpdater.templateBuilder, the template is run in the editor directly
            PyExpression argument = expressionList.getArguments()[0];
            TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(argument);
            builder.replaceElement(argument, argument.getText());
            PythonTemplateRunner.runTemplate(argument.getContainingFile(), builder);
        }
    }
}
