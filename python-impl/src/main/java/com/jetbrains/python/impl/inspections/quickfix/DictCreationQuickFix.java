// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.google.common.collect.Maps;
import com.jetbrains.python.impl.inspections.PyDictCreationInspection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyDoubleStarExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.jetbrains.python.impl.psi.PyUtil.as;

public class DictCreationQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixDictCreation();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        Map<String, String> statementsMap = Maps.newLinkedHashMap();
        PyAssignmentStatement myStatement = as(element, PyAssignmentStatement.class);
        if (myStatement == null) {
            return;
        }
        PyExpression assignedValue = myStatement.getAssignedValue();
        if (assignedValue instanceof PyDictLiteralExpression) {
            for (PsiElement expression : assignedValue.getChildren()) {
                if (expression instanceof PyKeyValueExpression kvExpr) {
                    PyExpression value = kvExpr.getValue();
                    if (value != null) {
                        statementsMap.put(kvExpr.getKey().getText(), value.getText());
                    }
                }
                else if (expression instanceof PyDoubleStarExpression) {
                    statementsMap.put(expression.getText(), null);
                }
            }

            PyStatement statement = PsiTreeUtil.getNextSiblingOfType(myStatement, PyStatement.class);
            while (statement instanceof PyAssignmentStatement assignmentStatement) {
                PyExpression target = myStatement.getTargets()[0];
                String targetName = target.getName();
                if (targetName != null) {
                    List<Pair<PyExpression, PyExpression>> targetsToValues =
                        PyDictCreationInspection.getDictTargets(target, targetName, assignmentStatement);
                    PyStatement nextStatement = PsiTreeUtil.getNextSiblingOfType(statement, PyStatement.class);
                    if (targetsToValues == null || targetsToValues.isEmpty()) {
                        break;
                    }
                    for (Pair<PyExpression, PyExpression> targetToValue : targetsToValues) {
                        PySubscriptionExpression subscription = (PySubscriptionExpression)targetToValue.first;
                        PyExpression indexExpression = subscription.getIndexExpression();
                        assert indexExpression != null;
                        String indexText;
                        if (indexExpression instanceof PyTupleExpression) {
                            indexText = "(" + indexExpression.getText() + ")";
                        }
                        else {
                            indexText = indexExpression.getText();
                        }

                        String valueText;
                        if (targetToValue.second instanceof PyTupleExpression) {
                            valueText = "(" + targetToValue.second.getText() + ")";
                        }
                        else {
                            valueText = targetToValue.second.getText();
                        }

                        statementsMap.put(indexText, valueText);
                        statement.delete();
                    }
                    statement = nextStatement;
                }
            }
            List<String> statements = new ArrayList<>();
            for (Map.Entry<String, String> entry : statementsMap.entrySet()) {
                if (entry.getValue() != null) {
                    statements.add(entry.getKey() + ": " + entry.getValue());
                }
                else {
                    statements.add(entry.getKey());
                }
            }
            PyExpression expression =
                elementGenerator.createExpressionFromText(LanguageLevel.forElement(myStatement), "{" + StringUtil.join(statements, ", ") + "}");
            assignedValue.replace(expression);
        }
    }
}
