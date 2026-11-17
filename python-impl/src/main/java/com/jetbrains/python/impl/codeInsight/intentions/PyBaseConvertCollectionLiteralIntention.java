// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.codeEditor.Editor;
import consulo.document.util.TextRange;
import consulo.language.editor.intention.PsiElementBaseIntentionAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * @author Mikhail Golubev
 */
// TODO consulo: ModCommand API (PsiUpdateModCommandAction) missing, JB logic adapted to PsiElementBaseIntentionAction
public abstract class PyBaseConvertCollectionLiteralIntention extends PsiElementBaseIntentionAction {
    private final Class<? extends PySequenceExpression> myTargetCollectionClass;
    private final String myTargetCollectionName;
    private final String myRightBrace;
    private final String myLeftBrace;

    public PyBaseConvertCollectionLiteralIntention(Class<? extends PySequenceExpression> targetCollectionClass,
                                                   String targetCollectionName,
                                                   String leftBrace, String rightBrace) {
        myTargetCollectionClass = targetCollectionClass;
        myTargetCollectionName = targetCollectionName;
        myLeftBrace = leftBrace;
        myRightBrace = rightBrace;
        setText(PyLocalize.intnNameConvertCollectionLiteral(myTargetCollectionName));
    }

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiElement element) {
        LocalizeValue text = getPresentation(element);
        if (text == null) {
            return false;
        }
        setText(text);
        return true;
    }

    private @Nullable LocalizeValue getPresentation(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (!(file instanceof PyFile)) {
            return null;
        }
        PySequenceExpression literal = findCollectionLiteralUnderElement(element);
        if (myTargetCollectionClass.isInstance(literal)) {
            return null;
        }
        if (literal != null && isAvailableForCollection(literal)) {
            if (literal instanceof PyTupleExpression) {
                if (PyTypingTypeProvider.isInsideTypeHint(literal, TypeEvalContext.codeAnalysis(file.getProject(), file))) {
                    return null;
                }
                return PyLocalize.intnConvertCollectionLiteral("tuple", myTargetCollectionName);
            }
            else if (literal instanceof PyListLiteralExpression) {
                return PyLocalize.intnConvertCollectionLiteral("list", myTargetCollectionName);
            }
            else if (literal instanceof PySetLiteralExpression) {
                return PyLocalize.intnConvertCollectionLiteral("set", myTargetCollectionName);
            }
        }
        return null;
    }

    protected boolean isAvailableForCollection(PySequenceExpression literal) {
        return true;
    }

    @Override
    public void invoke(Project project, Editor editor, PsiElement element) throws IncorrectOperationException {
        PySequenceExpression literal = findCollectionLiteralUnderElement(element);
        assert literal != null;

        PsiElement replacedElement = wrapCollection(literal);
        PsiElement copy = prepareOriginalElementCopy(replacedElement.copy());

        TextRange contentRange = getRangeOfContentWithoutBraces(copy);
        String contentToWrap = contentRange.substring(copy.getText());
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
        PyExpression newLiteral = elementGenerator.createExpressionFromText(LanguageLevel.forElement(element.getContainingFile()),
            myLeftBrace + contentToWrap + myRightBrace);
        replacedElement.replace(newLiteral);
    }

    protected PsiElement prepareOriginalElementCopy(PsiElement copy) {
        PySequenceExpression sequence = unwrapCollection(copy);
        if (sequence instanceof PyTupleExpression) {
            PyExpression[] elements = sequence.getElements();
            if (elements.length == 1) {
                PsiElement next = PyPsiUtils.getNextNonCommentSibling(elements[0], true);
                // Strictly speaking single element tuple must contain trailing comma, but lets check explicitly nonetheless
                if (next != null && next.getNode().getElementType() == PyTokenTypes.COMMA) {
                    next.delete();
                }
            }
        }
        return copy;
    }

    protected static PySequenceExpression unwrapCollection(PsiElement literal) {
        PyParenthesizedExpression parenthesizedExpression = as(literal, PyParenthesizedExpression.class);
        if (parenthesizedExpression != null) {
            PyExpression containedExpression = parenthesizedExpression.getContainedExpression();
            assert containedExpression != null;
            return (PyTupleExpression) containedExpression;
        }
        return (PySequenceExpression) literal;
    }

    protected static PsiElement wrapCollection(PySequenceExpression literal) {
        if (literal instanceof PyTupleExpression && literal.getParent() instanceof PyParenthesizedExpression) {
            return literal.getParent();
        }
        return literal;
    }

    private static TextRange getRangeOfContentWithoutBraces(PsiElement literal) {
        if (literal instanceof PyTupleExpression) {
            return TextRange.create(0, literal.getTextLength());
        }

        String replacedText = literal.getText();

        PsiElement firstChild = literal.getFirstChild();
        int contentStartOffset;
        if (PyTokenTypes.OPEN_BRACES.contains(firstChild.getNode().getElementType())) {
            contentStartOffset = firstChild.getTextLength();
        }
        else {
            contentStartOffset = 0;
        }

        PsiElement lastChild = literal.getLastChild();
        int contentEndOffset;
        if (PyTokenTypes.CLOSE_BRACES.contains(lastChild.getNode().getElementType())) {
            contentEndOffset = replacedText.length() - lastChild.getTextLength();
        }
        else {
            contentEndOffset = replacedText.length();
        }

        return TextRange.create(contentStartOffset, contentEndOffset);
    }

    private static @Nullable PySequenceExpression findCollectionLiteralUnderElement(PsiElement curElem) {
        PySequenceExpression seqExpr = PsiTreeUtil.getParentOfType(curElem, PySequenceExpression.class);
        if (seqExpr != null) {
            return seqExpr;
        }
        PyParenthesizedExpression paren = (PyParenthesizedExpression) PsiTreeUtil.findFirstParent(curElem, element -> {
            PyParenthesizedExpression parenthesizedExpr = as(element, PyParenthesizedExpression.class);
            return parenthesizedExpr != null && parenthesizedExpr.getContainedExpression() instanceof PyTupleExpression;
        });
        return paren != null ? ((PyTupleExpression) paren.getContainedExpression()) : null;
    }
}
