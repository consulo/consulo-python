/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.inspections.quickfix;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jetbrains.python.impl.inspections.PyDictCreationInspection;
import com.jetbrains.python.psi.*;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;

import java.util.List;
import java.util.Map;

/**
 * @author Alexey.Ivanov
 * @since 2010-02-26
 */
public class DictCreationQuickFix implements LocalQuickFix {
    private final PyAssignmentStatement myStatement;

    public DictCreationQuickFix(@Nonnull PyAssignmentStatement statement) {
        myStatement = statement;
    }

    @Nonnull
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixDictCreation();
    }

    @Override
    public void applyFix(@Nonnull Project project, @Nonnull ProblemDescriptor descriptor) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        Map<String, String> statementsMap = Maps.newLinkedHashMap();
        PyExpression assignedValue = myStatement.getAssignedValue();
        if (assignedValue instanceof PyDictLiteralExpression) {
            for (PyKeyValueExpression expression : ((PyDictLiteralExpression) assignedValue).getElements()) {
                PyExpression value = expression.getValue();
                if (value != null) {
                    statementsMap.put(expression.getKey().getText(), value.getText());
                }
            }

            PyStatement statement = PsiTreeUtil.getNextSiblingOfType(myStatement, PyStatement.class);
            while (statement instanceof PyAssignmentStatement) {
                PyAssignmentStatement assignmentStatement = (PyAssignmentStatement) statement;
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
                        PySubscriptionExpression subscription = (PySubscriptionExpression) targetToValue.first;
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
            List<String> statements = Lists.newArrayList();
            for (Map.Entry<String, String> entry : statementsMap.entrySet()) {
                statements.add(entry.getKey() + ": " + entry.getValue());
            }
            PyExpression expression = elementGenerator.createExpressionFromText(
                LanguageLevel.forElement(myStatement),
                "{" + StringUtil.join(statements, ", ") + "}"
            );
            if (expression != null) {
                assignedValue.replace(expression);
            }
        }
    }
}
