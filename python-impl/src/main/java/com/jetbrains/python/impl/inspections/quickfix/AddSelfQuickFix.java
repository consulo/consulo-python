// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

/**
 * Insert 'self' in a method that lacks any arguments
 */
public class AddSelfQuickFix implements LocalQuickFix {
    private final String myParamName;

    public AddSelfQuickFix(String paramName) {
        myParamName = paramName;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixAddParameterSelf(myParamName);
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyParameterList parameterList) {
            PyNamedParameter newParameter = PyElementGenerator.getInstance(project).createParameter(myParamName);
            parameterList.addParameter(newParameter);
        }
    }
}
