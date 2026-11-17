// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyNamedParameter;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

/**
 * User: catherine
 * <p>
 * QuickFix to replace mutable default argument. For instance,
 * def foo(args=[]):
 * pass
 * replace with:
 * def foo(args=None):
 * if not args: args = []
 * pass
 */
public class PyDefaultArgumentQuickFix implements LocalQuickFix {

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixDefaultArgument();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyNamedParameter param = PsiTreeUtil.getParentOfType(element, PyNamedParameter.class);
        PyFunction function = PsiTreeUtil.getParentOfType(element, PyFunction.class);
        assert param != null;
        String defName = param.getName();
        if (function != null && defName != null) {
            PyElementGenerator generator = PyElementGenerator.getInstance(project);
            LanguageLevel languageLevel = LanguageLevel.forElement(function);

            PyNamedParameter newParam = generator.createParameter(defName, PyNames.NONE, null, languageLevel);
            param.replace(newParam);

            String conditionalText = "if " + defName + " is None:" +
                    "\n\t" + defName + " = " + element.getText();
            PyIfStatement conditionalAssignment = generator.createFromText(languageLevel, PyIfStatement.class, conditionalText);
            PyPsiRefactoringUtil.addElementToStatementList(conditionalAssignment, function.getStatementList(), true);
        }
    }
}
