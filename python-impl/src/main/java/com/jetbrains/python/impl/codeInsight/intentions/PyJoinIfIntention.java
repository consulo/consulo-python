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
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.*;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;

/**
 * Intention to merge the if clauses in the case of nested ifs where only the inner if contains code
 * (the outer if only contains the inner one).
 *
 * <p>For instance,
 * <code>if a:
 *   if b:
 *     # stuff here</code>
 * into
 * <code>if a and b:
 *   #stuff here</code></p>
 *
 * @author catherine
 */
public class PyJoinIfIntention extends BaseIntentionAction {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return PyLocalize.intnJoinIfText();
    }

    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PyFile)) {
            return false;
        }

        PyIfStatement expression =
            PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyIfStatement.class);

        PyIfStatement outer = getIfStatement(expression);
        if (outer != null) {
            if (outer.getElsePart() != null || outer.getElifParts().length > 0) {
                return false;
            }
            PyStatement firstStatement = getFirstStatement(outer);
            PyStatementList outerStList = outer.getIfPart().getStatementList();
            if (outerStList != null && outerStList.getStatements().length != 1) {
                return false;
            }
            if (firstStatement instanceof PyIfStatement) {
                PyIfStatement inner = (PyIfStatement) firstStatement;
                if (inner.getElsePart() != null || inner.getElifParts().length > 0) {
                    return false;
                }
                PyStatementList stList = inner.getIfPart().getStatementList();
                if (stList != null) {
                    if (stList.getStatements().length != 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PyIfStatement expression =
            PsiTreeUtil.getParentOfType(file.findElementAt(editor.getCaretModel().getOffset()), PyIfStatement.class);
        PyIfStatement ifStatement = getIfStatement(expression);

        PyStatement firstStatement = getFirstStatement(ifStatement);
        if (ifStatement == null) {
            return;
        }
        if (firstStatement != null && firstStatement instanceof PyIfStatement) {
            PyExpression condition = ((PyIfStatement) firstStatement).getIfPart().getCondition();
            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
            PyExpression ifCondition = ifStatement.getIfPart().getCondition();
            if (ifCondition == null || condition == null) {
                return;
            }
            StringBuilder replacementText = new StringBuilder(ifCondition.getText() + " and ");
            if (condition instanceof PyBinaryExpression && ((PyBinaryExpression) condition).getOperator() == PyTokenTypes.OR_KEYWORD) {
                replacementText.append("(").append(condition.getText()).append(")");
            }
            else {
                replacementText.append(condition.getText());
            }

            PyExpression newCondition = elementGenerator.createExpressionFromText(replacementText.toString());
            ifCondition.replace(newCondition);

            PyStatementList stList = ((PyIfStatement) firstStatement).getIfPart().getStatementList();
            PyStatementList ifStatementList = ifStatement.getIfPart().getStatementList();
            if (ifStatementList == null || stList == null) {
                return;
            }
            List<PsiComment> comments = PsiTreeUtil.getChildrenOfTypeAsList(ifStatement.getIfPart(), PsiComment.class);
            comments.addAll(PsiTreeUtil.getChildrenOfTypeAsList(((PyIfStatement) firstStatement).getIfPart(), PsiComment.class));
            comments.addAll(PsiTreeUtil.getChildrenOfTypeAsList(ifStatementList, PsiComment.class));
            comments.addAll(PsiTreeUtil.getChildrenOfTypeAsList(stList, PsiComment.class));

            for (PsiElement comm : comments) {
                ifStatement.getIfPart().addBefore(comm, ifStatementList);
                comm.delete();
            }
            ifStatementList.replace(stList);
        }
    }

    @Nullable
    private static PyStatement getFirstStatement(PyIfStatement ifStatement) {
        PyStatement firstStatement = null;
        if (ifStatement != null) {
            PyStatementList stList = ifStatement.getIfPart().getStatementList();
            if (stList != null) {
                if (stList.getStatements().length != 0) {
                    firstStatement = stList.getStatements()[0];
                }
            }
        }
        return firstStatement;
    }

    @Nullable
    private static PyIfStatement getIfStatement(PyIfStatement expression) {
        while (expression != null) {
            PyStatementList stList = expression.getIfPart().getStatementList();
            if (stList != null) {
                if (stList.getStatements().length != 0) {
                    PyStatement firstStatement = stList.getStatements()[0];
                    if (firstStatement instanceof PyIfStatement) {
                        break;
                    }
                }
            }
            expression = PsiTreeUtil.getParentOfType(expression, PyIfStatement.class);
        }
        return expression;
    }
}
