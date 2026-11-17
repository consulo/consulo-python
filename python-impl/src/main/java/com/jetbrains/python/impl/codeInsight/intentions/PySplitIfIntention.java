// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIfPart;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.ast.IElementType;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

@ExtensionImpl
@IntentionMetaData(ignoreId = "python.PySplitIfIntention", categories = "Python", fileExtensions = "py")
public final class PySplitIfIntention extends PyBaseIntentionAction {
    public PySplitIfIntention() {
        setText(PyLocalize.intnNameSplitIf());
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
        if (!(psiFile instanceof PyFile)) {
            return false;
        }

        PsiElement elementAtOffset = psiFile.findElementAt(editor.getCaretModel().getOffset());
        if (elementAtOffset == null || elementAtOffset.getNode() == null) {
            return false;
        }

        // PY-745
        IElementType elementType = elementAtOffset.getNode().getElementType();
        if (elementType == PyTokenTypes.COLON) {
            elementAtOffset = elementAtOffset.getPrevSibling();
            elementAtOffset = PyPsiUtils.getPrevNonCommentSibling(elementAtOffset, false);
        }
        else if (elementType == PyTokenTypes.IF_KEYWORD) {
            elementAtOffset = elementAtOffset.getNextSibling();
            elementAtOffset = PyPsiUtils.getNextNonCommentSibling(elementAtOffset, false);
        }

        PsiElement element = PsiTreeUtil.getParentOfType(elementAtOffset, PyBinaryExpression.class, false);
        if (element == null) {
            return false;
        }

        while (element.getParent() instanceof PyBinaryExpression) {
            element = element.getParent();
        }
        if (((PyBinaryExpression)element).getOperator() != PyTokenTypes.AND_KEYWORD
                || ((PyBinaryExpression)element).getRightExpression() == null) {
            return false;
        }
        PsiElement parent = element.getParent();
        if (!(parent instanceof PyIfPart)) {
            return false;
        }
        setText(PyLocalize.intnSplitIf());
        return true;
    }

    @Override
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement elementAtOffset = file.findElementAt(editor.getCaretModel().getOffset());
        // PY-745
        IElementType elementType = elementAtOffset.getNode().getElementType();
        if (elementType == PyTokenTypes.COLON) {
            elementAtOffset = elementAtOffset.getPrevSibling();
            elementAtOffset = PyPsiUtils.getPrevNonCommentSibling(elementAtOffset, false);
        }
        else if (elementType == PyTokenTypes.IF_KEYWORD) {
            elementAtOffset = elementAtOffset.getNextSibling();
            elementAtOffset = PyPsiUtils.getNextNonCommentSibling(elementAtOffset, false);
        }

        PyBinaryExpression element = PsiTreeUtil.getParentOfType(elementAtOffset, PyBinaryExpression.class, false);
        while (element.getParent() instanceof PyBinaryExpression) {
            element = (PyBinaryExpression)element.getParent();
        }
        PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(element, PyIfStatement.class);
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

        PyIfStatement subIf = (PyIfStatement)ifStatement.copy();

        subIf.getIfPart().getCondition().replace(element.getRightExpression());
        ifStatement.getIfPart().getCondition().replace(element.getLeftExpression());
        PyStatementList statementList =
                elementGenerator.createFromText(LanguageLevel.getDefault(), PyIfStatement.class, "if a:\n    a = 1").getIfPart().getStatementList();
        statementList.getStatements()[0].replace(subIf);
        PyIfStatement newIf = elementGenerator.createFromText(LanguageLevel.getDefault(), PyIfStatement.class, "if a:\n    a = 1");
        newIf.getIfPart().getCondition().replace(ifStatement.getIfPart().getCondition());
        newIf.getIfPart().getStatementList().replace(statementList);
        for (PyIfPart elif : ifStatement.getElifParts()) {
            newIf.add(elif);
        }
        if (ifStatement.getElsePart() != null) {
            newIf.add(ifStatement.getElsePart());
        }
        ifStatement.replace(newIf);
    }
}

