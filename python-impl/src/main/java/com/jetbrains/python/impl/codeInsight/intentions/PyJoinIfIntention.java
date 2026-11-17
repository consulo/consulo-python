// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.refactoring.PyReplaceExpressionUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * User: catherine
 * Intention to merge the if clauses in the case of nested ifs where only the inner if contains code (the outer if only contains the inner one)
 * For instance,
 * if a:
 * if b:
 * # stuff here
 * into
 * if a and b:
 * #stuff here
 */
// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PyJoinIfIntention", categories = "Python", fileExtensions = "py")
public final class PyJoinIfIntention extends PsiElementBaseIntentionAction {
    public PyJoinIfIntention() {
        setText(PyLocalize.intnNameJoinIf());
    }

    private static @Nullable PyIfStatement findElement(PsiElement leaf) {
        PsiElement current = leaf;
        while (current != null) {
            if (current instanceof PyIfStatement candidate) {
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
        PyIfStatement element = findElement(leaf);
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
        PyIfStatement element = findElement(leaf);
        if (element == null) {
            return;
        }
        invoke(element, editor);
    }


    private @Nullable LocalizeValue getPresentation(PyIfStatement expression) {
        PyIfStatement outer = getIfStatement(expression);
        if (outer == null) {
            return null;
        }
        if (outer.getElsePart() != null || outer.getElifParts().length > 0) {
            return null;
        }
        PyStatement firstStatement = getFirstStatement(outer);
        PyStatementList outerStList = outer.getIfPart().getStatementList();
        if (outerStList.getStatements().length != 1) {
            return null;
        }
        if (!(firstStatement instanceof PyIfStatement inner)) {
            return null;
        }
        if (inner.getElsePart() != null || inner.getElifParts().length > 0) {
            return null;
        }
        PyStatementList stList = inner.getIfPart().getStatementList();
        if (stList.getStatements().length == 0) {
            return null;
        }
        return PyLocalize.intnJoinIf();
    }

    private void invoke(PyIfStatement expression, Editor editor) {
        PyIfStatement outerIfStatement = getIfStatement(expression);
        if (outerIfStatement == null) {
            return;
        }
        PyIfStatement innerIfStatement = as(getFirstStatement(outerIfStatement), PyIfStatement.class);
        if (innerIfStatement == null) {
            return;
        }
        PyExpression innerCondition = innerIfStatement.getIfPart().getCondition();
        PyExpression outerCondition = outerIfStatement.getIfPart().getCondition();
        if (outerCondition == null || innerCondition == null) {
            return;
        }
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(expression.getProject());
        LanguageLevel pyVersion = LanguageLevel.forElement(expression.getContainingFile());
        PyBinaryExpression fakeAndExpression = (PyBinaryExpression)elementGenerator.createExpressionFromText(pyVersion, "foo and bar");
        StringBuilder replacementText = new StringBuilder();
        if (PyReplaceExpressionUtil.isNeedParenthesis(fakeAndExpression.getLeftExpression(), outerCondition)) {
            replacementText.append("(").append(outerCondition.getText()).append(")");
        }
        else {
            replacementText.append(outerCondition.getText());
        }
        replacementText.append(" and ");
        if (PyReplaceExpressionUtil.isNeedParenthesis(fakeAndExpression.getLeftExpression(), innerCondition)) {
            replacementText.append("(").append(innerCondition.getText()).append(")");
        }
        else {
            replacementText.append(innerCondition.getText());
        }

        PyExpression newCondition = elementGenerator.createExpressionFromText(pyVersion, replacementText.toString());
        outerCondition.replace(newCondition);

        PyStatementList innerStatementList = innerIfStatement.getIfPart().getStatementList();
        PyStatementList outerStatementList = outerIfStatement.getIfPart().getStatementList();
        List<PsiComment> comments = PsiTreeUtil.getChildrenOfTypeAsList(outerIfStatement.getIfPart(), PsiComment.class);
        comments = ContainerUtil.concat(comments, PsiTreeUtil.getChildrenOfTypeAsList(innerIfStatement.getIfPart(), PsiComment.class));
        comments = ContainerUtil.concat(comments, PsiTreeUtil.getChildrenOfTypeAsList(outerStatementList, PsiComment.class));
        comments = ContainerUtil.concat(comments, PsiTreeUtil.getChildrenOfTypeAsList(innerStatementList, PsiComment.class));

        for (PsiElement comm : comments) {
            outerIfStatement.getIfPart().addBefore(comm, outerStatementList);
            comm.delete();
        }
        outerStatementList.replace(innerStatementList);
    }

    private static @Nullable PyStatement getFirstStatement(PyIfStatement ifStatement) {
        PyStatementList stList = ifStatement.getIfPart().getStatementList();
        return ArrayUtil.getFirstElement(stList.getStatements());
    }

    private static @Nullable PyIfStatement getIfStatement(@Nullable PyIfStatement ifStatement) {
        while (ifStatement != null && !(getFirstStatement(ifStatement) instanceof PyIfStatement)) {
            ifStatement = PsiTreeUtil.getParentOfType(ifStatement, PyIfStatement.class);
        }
        return ifStatement;
    }
}

