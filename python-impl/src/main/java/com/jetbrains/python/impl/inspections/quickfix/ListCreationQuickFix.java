// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.impl.inspections.PyListCreationInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyListLiteralExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * User : catherine
 */
public class ListCreationQuickFix implements LocalQuickFix {

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixListCreation();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        PyAssignmentStatement assignmentStatement = as(element, PyAssignmentStatement.class);
        if (assignmentStatement == null) {
            return;
        }
        PyExpression assignedValue = assignmentStatement.getAssignedValue();
        if (assignedValue == null) {
            return;
        }

        List<PyExpressionStatement> appendCalls = PyListCreationInspection.collectSubsequentListAppendCalls(assignmentStatement);
        List<PyExpression> items = buildLiteralItems(assignedValue, appendCalls);
        appendCalls.forEach(PyExpressionStatement::delete);

        String text = "[" + StringUtil.join(items, PyExpression::getText, ", ") + "]";
        assignedValue.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(assignedValue), text));
    }

    private static List<PyExpression> buildLiteralItems(PyExpression assignedValue,
            List<PyExpressionStatement> statements) {
        List<PyExpression> values = new ArrayList<>();

        ContainerUtil.addAll(values, ((PyListLiteralExpression)assignedValue).getElements());

        for (PyExpressionStatement statement : statements) {
            ContainerUtil.addAll(values, ((PyCallExpression)statement.getExpression()).getArguments());
        }

        return values;
    }
}
