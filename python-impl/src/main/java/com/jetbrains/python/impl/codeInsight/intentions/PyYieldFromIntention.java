// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyYieldExpression;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyYieldFromIntention", categories = "Python", fileExtensions = "py")
public final class PyYieldFromIntention extends PsiElementBaseIntentionAction {
    public PyYieldFromIntention() {
        setText(PyLocalize.intnYieldFrom());
    }

    private static @Nullable PyForStatement findElement(PsiElement leaf) {
        PsiElement current = leaf;
        while (current != null) {
            if (current instanceof PyForStatement candidate) {
                return candidate;
            }
            if (current instanceof PsiFile) {
                return null;
            }
            current = current.getParent();
        }
        return null;
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement leaf) {
        PyForStatement element = findElement(leaf);
        if (element == null) {
            return false;
        }
        LocalizeValue text = getPresentation(element);
        if (text == null) {
            return false;
        }
        setText(text);
        return true;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement leaf) throws IncorrectOperationException {
        PyForStatement element = findElement(leaf);
        if (element == null) {
            return;
        }
        invoke(element, editor);
    }


    private @Nullable LocalizeValue getPresentation(PyForStatement element) {
        if (!LanguageLevel.forElement(element).isPython2()) {
            PyTargetExpression forTarget = findSingleForLoopTarget(element);
            PyReferenceExpression yieldValue = findSingleYieldValue(element);
            if (forTarget != null && yieldValue != null) {
                String targetName = forTarget.getName();
                if (targetName != null && targetName.equals(yieldValue.getName())) {
                    return PyLocalize.intnYieldFrom();
                }
            }
        }
        return null;
    }

    private void invoke(PyForStatement element, Editor editor) {
        PyExpression source = element.getForPart().getSource();
        if (source != null) {
            PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
            String text = "yield from foo";
            PyExpressionStatement exprStmt = generator.createFromText(LanguageLevel.forElement(element), PyExpressionStatement.class, text);
            PyExpression expr = exprStmt.getExpression();
            if (expr instanceof PyYieldExpression) {
                PyExpression yieldValue = ((PyYieldExpression)expr).getExpression();
                if (yieldValue != null) {
                    yieldValue.replace(source);
                    element.replace(exprStmt);
                }
            }
        }
    }

    private static @Nullable PyTargetExpression findSingleForLoopTarget(PyForStatement forLoop) {
        PyForPart forPart = forLoop.getForPart();
        PyExpression forTarget = forPart.getTarget();
        if (forTarget instanceof PyTargetExpression) {
            return (PyTargetExpression)forTarget;
        }
        return null;
    }

    private static @Nullable PyReferenceExpression findSingleYieldValue(PyForStatement forLoop) {
        PyForPart forPart = forLoop.getForPart();
        if (forLoop.getElsePart() == null) {
            PyStatement[] statements = forPart.getStatementList().getStatements();
            if (statements.length == 1) {
                PyStatement firstStmt = statements[0];
                if (firstStmt instanceof PyExpressionStatement) {
                    PyExpression firstExpr = ((PyExpressionStatement)firstStmt).getExpression();
                    if (firstExpr instanceof PyYieldExpression yieldExpr) {
                        PyExpression yieldValue = yieldExpr.getExpression();
                        if (yieldValue instanceof PyReferenceExpression) {
                            return (PyReferenceExpression)yieldValue;
                        }
                    }
                }
            }
        }
        return null;
    }
}

