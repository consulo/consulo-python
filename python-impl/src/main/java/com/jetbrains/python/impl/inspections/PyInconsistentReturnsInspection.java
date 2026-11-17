// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.extensions.PyMatchStatementExtKt;
import com.jetbrains.python.impl.inspections.quickfix.PyMakeReturnExplicitFix;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyIfPart;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyMatchStatement;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTryExceptStatement;
import com.jetbrains.python.psi.PyWhilePart;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * PEP8:
 * Be consistent in return statements. Either all return statements in a function should return an expression,
 * or none of them should. If any return statement returns an expression, any return statements where no value
 * is returned should explicitly state this as return None, and an explicit return statement should be present
 * at the end of the function (if reachable).
 */
@ExtensionImpl
public class PyInconsistentReturnsInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameInconsistentReturns();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonControlFlowIssues();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new PyInspectionVisitor(holder, PyInspectionVisitor.getContext(session)) {
            @Override
            public void visitPyFunction(PyFunction node) {
                List<PyStatement> returnPoints = node.getReturnPoints(myTypeEvalContext);
                boolean hasExplicitReturns = false;
                for (PyStatement it : returnPoints) {
                    if (it instanceof PyReturnStatement returnStatement && returnStatement.getExpression() != null) {
                        hasExplicitReturns = true;
                        break;
                    }
                }
                if (!hasExplicitReturns) {
                    return;
                }

                for (PyStatement statement : returnPoints) {
                    LocalizeValue message;
                    if (statement instanceof PyReturnStatement returnStatement && returnStatement.getExpression() == null) {
                        message = PyLocalize.inspInconsistentReturnsReturnWithoutValue();
                    }
                    else if (!(statement instanceof PyReturnStatement) && !isTooComplexCase(statement, myTypeEvalContext)) {
                        message = PyLocalize.inspInconsistentReturnsMissingReturnStmtOnSomePaths();
                    }
                    else {
                        continue;
                    }

                    TextRange firstLineRange = firstLineRange(statement);
                    Objects.requireNonNull(getHolder())
                        .newProblem(message)
                        .range(statement, firstLineRange != null ? firstLineRange : statement.getTextRange())
                        .withFix(new PyMakeReturnExplicitFix(statement))
                        .create();
                }
            }
        };
    }

    private static boolean isTooComplexCase(PyStatement statement, TypeEvalContext context) {
        PsiElement parent = findParentInFile(statement, it ->
            it instanceof ScopeOwner
                || it instanceof PyTryExceptStatement
                || it instanceof PyForPart
                || it instanceof PyWhilePart
                || it instanceof PyIfPart && isIfPartWithoutElse(it)
                || (it instanceof PyWithStatement withStatement && withStatement.isSuppressingExceptions(context))
                || (it instanceof PyMatchStatement matchStatement && !PyMatchStatementExtKt.isExhaustive(matchStatement, context))
        );
        return !(parent instanceof ScopeOwner);
    }

    private static boolean isIfPartWithoutElse(PsiElement ifPart) {
        PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(ifPart, PyIfStatement.class);
        return ifStatement == null || ifStatement.getElsePart() == null;
    }

    /**
     * {@code findParentInFile(withSelf = false, predicate)} from JB {@code psiTreeUtil.kt}.
     */
    private static @Nullable PsiElement findParentInFile(PsiElement element, java.util.function.Predicate<PsiElement> predicate) {
        if (element instanceof PsiFile) {
            return null;
        }
        PsiElement current = element.getParent();
        while (current != null) {
            if (predicate.test(current)) {
                return current;
            }
            if (current instanceof PsiFile) {
                break;
            }
            current = current.getParent();
        }
        return null;
    }

    private static @Nullable TextRange firstLineRange(PsiElement element) {
        Document document = element.getContainingFile().getFileDocument();
        int startOffset = element.getTextRange().getStartOffset();
        int lineEndOffset = document.getLineEndOffset(document.getLineNumber(startOffset));
        int endOffset = Math.min(lineEndOffset, element.getTextRange().getEndOffset());

        return new TextRange(0, endOffset - startOffset);
    }
}
