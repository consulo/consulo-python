// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.PythonTemplateRunner;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class UnresolvedReferenceAddParameterQuickFix implements LocalQuickFix {
    private final String myName;

    public UnresolvedReferenceAddParameterQuickFix(String name) {
        myName = name;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixUnresolvedReferenceAddParam$0(myName);
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        final PsiElement element = descriptor.getPsiElement();
        final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        final PyNamedParameter parameter = elementGenerator.createParameter(element.getText() + "=None");
        final PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
        if (function != null) {
            final PyParameterList parameterList = function.getParameterList();
            parameterList.addParameter(parameter);
            CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(parameterList);
            final TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(parameter);
            builder.replaceRange(TextRange.create(parameter.getTextLength() - 4, parameter.getTextLength()), "None");
            PythonTemplateRunner.runTemplate(function.getContainingFile(), builder);
        }
    }
}
