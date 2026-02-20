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

import com.jetbrains.python.psi.*;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.BaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author vlan
 */
public class PyYieldFromIntention extends BaseIntentionAction {
    @Nonnull
    @Override
    public LocalizeValue getText() {
        return PyLocalize.intnYieldFrom();
    }

    @Override
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (LanguageLevel.forElement(file).isAtLeast(LanguageLevel.PYTHON33)) {
            PyForStatement forLoop = findForStatementAtCaret(editor, file);
            if (forLoop != null) {
                PyTargetExpression forTarget = findSingleForLoopTarget(forLoop);
                PyReferenceExpression yieldValue = findSingleYieldValue(forLoop);
                if (forTarget != null && yieldValue != null) {
                    String targetName = forTarget.getName();
                    if (targetName != null && targetName.equals(yieldValue.getName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void invoke(@Nonnull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PyForStatement forLoop = findForStatementAtCaret(editor, file);
        if (forLoop != null) {
            PyExpression source = forLoop.getForPart().getSource();
            if (source != null) {
                PyElementGenerator generator = PyElementGenerator.getInstance(project);
                String text = "yield from foo";
                PyExpressionStatement exprStmt =
                    generator.createFromText(LanguageLevel.forElement(file), PyExpressionStatement.class, text);
                PyExpression expr = exprStmt.getExpression();
                if (expr instanceof PyYieldExpression) {
                    PyExpression yieldValue = ((PyYieldExpression) expr).getExpression();
                    if (yieldValue != null) {
                        yieldValue.replace(source);
                        forLoop.replace(exprStmt);
                    }
                }
            }
        }
    }

    @Nullable
    private static PyForStatement findForStatementAtCaret(@Nonnull Editor editor, @Nonnull PsiFile file) {
        PsiElement elementAtCaret = file.findElementAt(editor.getCaretModel().getOffset());
        return PsiTreeUtil.getParentOfType(elementAtCaret, PyForStatement.class);
    }

    @Nullable
    private static PyTargetExpression findSingleForLoopTarget(@Nonnull PyForStatement forLoop) {
        PyForPart forPart = forLoop.getForPart();
        PyExpression forTarget = forPart.getTarget();
        if (forTarget instanceof PyTargetExpression) {
            return (PyTargetExpression) forTarget;
        }
        return null;
    }

    @Nullable
    private static PyReferenceExpression findSingleYieldValue(@Nonnull PyForStatement forLoop) {
        PyForPart forPart = forLoop.getForPart();
        PyStatementList stmtList = forPart.getStatementList();
        if (stmtList != null && forLoop.getElsePart() == null) {
            PyStatement[] statements = stmtList.getStatements();
            if (statements.length == 1) {
                PyStatement firstStmt = statements[0];
                if (firstStmt instanceof PyExpressionStatement) {
                    PyExpression firstExpr = ((PyExpressionStatement) firstStmt).getExpression();
                    if (firstExpr instanceof PyYieldExpression) {
                        PyYieldExpression yieldExpr = (PyYieldExpression) firstExpr;
                        PyExpression yieldValue = yieldExpr.getExpression();
                        if (yieldValue instanceof PyReferenceExpression) {
                            return (PyReferenceExpression) yieldValue;
                        }
                    }
                }
            }
        }
        return null;
    }
}
