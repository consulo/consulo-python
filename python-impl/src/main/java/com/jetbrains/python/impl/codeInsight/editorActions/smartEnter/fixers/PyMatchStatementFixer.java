// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.fixers;

import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.util.lang.Couple;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.impl.codeInsight.editorActions.smartEnter.PySmartEnterProcessor;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.psi.PyMatchStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.psi.impl.PyPsiUtils;

import static com.jetbrains.python.impl.psi.PyUtil.as;

public final class PyMatchStatementFixer extends PyFixer<PyStatement> {
    public PyMatchStatementFixer() {
        super(PyStatement.class);
    }

    @Override
    protected void doApply(Editor editor, PySmartEnterProcessor processor, PyStatement element) {
        Document document = editor.getDocument();
        PyMatchStatement matchStatement = as(element, PyMatchStatement.class);
        if (matchStatement != null) {
            PsiElement colon = PyPsiUtils.getFirstChildOfType(element, PyTokenTypes.COLON);
            assert colon != null;
            int colonEndOffset = colon.getTextRange().getEndOffset();
            // It's not enough to check matchStatement.getCaseClauses().isEmpty()
            boolean hasEmptyBody = colonEndOffset == matchStatement.getTextRange().getEndOffset();
            if (hasEmptyBody) {
                String matchIndent = PyIndentUtil.getElementIndent(element);
                String indent = PyIndentUtil.getIndentFromSettings(element.getContainingFile());
                String caseClausePlaceholder = "\n" + matchIndent + indent + PyNames.CASE + " :";
                document.insertString(colonEndOffset, caseClausePlaceholder);
                processor.registerUnresolvedError(colonEndOffset + caseClausePlaceholder.length() - 1);
            }
            return;
        }

        PyTypeDeclarationStatement typeDeclaration = as(element, PyTypeDeclarationStatement.class);
        // "match:" case
        if (typeDeclaration != null && isMatchIdentifier(typeDeclaration.getTarget())) {
            PyAnnotation annotation = typeDeclaration.getAnnotation();
            if (annotation != null && annotation.getValue() == null) {
                processor.registerUnresolvedError(annotation.getTextRange().getStartOffset());
                return;
            }
        }

        Couple<PsiElement> pair = findMatchKeywordAndSubjectInExpressionStatement(element);
        PsiElement matchKeyword = pair.getFirst();
        if (matchKeyword == null) {
            return;
        }
        PsiElement subject = pair.getSecond();
        if (subject != null) {
            int endOffset = subject.getTextRange().getEndOffset();
            String matchIndent = PyIndentUtil.getElementIndent(matchKeyword);
            String indent = PyIndentUtil.getIndentFromSettings(element.getContainingFile());
            String caseClausePlaceholder = ":\n" + matchIndent + indent + PyNames.CASE + " :";
            document.insertString(endOffset, caseClausePlaceholder);
            processor.registerUnresolvedError(endOffset + caseClausePlaceholder.length() - 1);
        }
        else {
            int endOffset = element.getTextRange().getEndOffset();
            document.insertString(endOffset, " :");
            processor.registerUnresolvedError(endOffset + 1);
        }
    }

    private static Couple<PsiElement> findMatchKeywordAndSubjectInExpressionStatement(PyStatement statement) {
        if (!(statement instanceof PyExpressionStatement)) {
            return Couple.of();
        }
        // "match <caret>expr" case
        PsiElement prevSibling = PyPsiUtils.getPrevNonWhitespaceSiblingOnSameLine(statement);
        if (prevSibling instanceof PyExpressionStatement && isMatchIdentifier(prevSibling)) {
            return Couple.of(prevSibling, statement);
        }
        if (isMatchIdentifier(statement)) {
            // "<caret>match expr"
            PsiElement nextSibling = PyPsiUtilsCore.getNextNonWhitespaceSiblingOnSameLine(statement);
            if (nextSibling instanceof PyExpressionStatement) {
                return Couple.of(statement, nextSibling);
            }
            // "match" case
            else if (nextSibling == null) {
                return Couple.of(statement, null);
            }
        }
        // "match + x" or "match [x]" case
        if (isMatchIdentifier(PsiTreeUtil.getDeepestFirst(statement))) {
            LanguageLevel languageLevel = LanguageLevel.forElement(statement);
            PyElementGenerator generator = PyElementGenerator.getInstance(statement.getProject());
            String subjectSuspect = StringUtil.trimStart(statement.getText(), PyNames.MATCH);
            try {
                generator.createExpressionFromText(languageLevel, subjectSuspect.trim());
                return Couple.of(statement, statement);
            }
            catch (IncorrectOperationException ignored) {
            }
        }
        return Couple.of();
    }

    private static boolean isMatchIdentifier(PsiElement element) {
        return element.getText().equals(PyNames.MATCH);
    }
}
