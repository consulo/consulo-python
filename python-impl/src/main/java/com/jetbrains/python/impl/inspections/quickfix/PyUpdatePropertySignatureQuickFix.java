// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.*;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

public class PyUpdatePropertySignatureQuickFix implements LocalQuickFix {
    private final boolean myHasValue;

    public PyUpdatePropertySignatureQuickFix(boolean hasValue) {
        myHasValue = hasValue;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixNameUpdateParameters();
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyCallable function = PsiTreeUtil.getParentOfType(element, PyCallable.class, false);
        assert function != null;
        PyParameterList parameterList = function.getParameterList();
        PyParameter[] parameters = parameterList.getParameters();
        PyElementGenerator generator = PyElementGenerator.getInstance(project);
        String selfText = parameters.length != 0 ? parameters[0].getText() : PyNames.CANONICAL_SELF;
        StringBuilder functionText = new StringBuilder("def foo(" + selfText);
        if (myHasValue) {
            String valueText = parameters.length > 1 ? parameters[1].getText() : "value";
            functionText.append(", ").append(valueText);
        }
        functionText.append("): pass");

        PyParameterList list =
            generator.createFromText(LanguageLevel.forElement(element), PyFunction.class, functionText.toString()).getParameterList();
        parameterList.replace(list);
    }
}
