// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.PythonTemplateRunner;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

/**
 * User: catherine
 * <p>
 * Quickfix to introduce variable if statement seems to have no effect
 */
public class StatementEffectIntroduceVariableQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixIntroduceVariable();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        PyAssignmentStatement assignment = elementGenerator.createFromText(LanguageLevel.forElement(element), PyAssignmentStatement.class,
                "var = " + element.getText());

        element = element.replace(assignment);
        if (element == null) {
            return;
        }
        element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element);
        if (element == null) {
            return;
        }
        PyExpression leftHandSideExpression = ((PyAssignmentStatement)element).getLeftHandSideExpression();
        assert leftHandSideExpression != null;
        // Consulo: no ModPsiUpdater.templateBuilder, the template is run in the editor directly
        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(element);
        builder.replaceElement(leftHandSideExpression, "var");
        PythonTemplateRunner.runTemplate(element.getContainingFile(), builder);
    }
}
