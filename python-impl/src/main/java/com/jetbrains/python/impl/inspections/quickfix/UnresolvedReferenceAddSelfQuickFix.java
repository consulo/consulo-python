// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * User: catherine
 *
 * QuickFix to add self to unresolved reference
 */
public class UnresolvedReferenceAddSelfQuickFix implements LocalQuickFix, HighPriorityAction {
    private final String myQualifier;
    private final String myAttributeName;

    public UnresolvedReferenceAddSelfQuickFix(PyReferenceExpression element, String qualifier) {
        myAttributeName = element.getName();
        myQualifier = qualifier;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixUnresolvedReference(myAttributeName, myQualifier);
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyReferenceExpression reference = as(element, PyReferenceExpression.class);
        if (reference == null) {
            return;
        }
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        PyExpression expression = elementGenerator.createExpressionFromText(LanguageLevel.forElement(reference),
                myQualifier + "." + reference.getText());
        reference.replace(expression);
    }
}
