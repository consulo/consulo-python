// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import consulo.ui.ex.action.AnActionEvent;
import consulo.application.ReadAction;
import consulo.document.Document;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.execution.debug.XDebugSession;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyLiteralExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import org.jspecify.annotations.Nullable;


public final class PyDebugSupportUtils {

    private PyDebugSupportUtils() {
    }

    // can expression be evaluated, or should be executed
    public static boolean isExpression(Project project, String expression) {
        return ReadAction.compute(() -> {

            PsiFile file = PyElementGenerator.getInstance(project).createDummyFile(LanguageLevel.getDefault(), expression);
            return file.getFirstChild() instanceof PyExpressionStatement && file.getFirstChild() == file.getLastChild();
        });
    }

    public static @Nullable TextRange getExpressionRangeAtOffset(Project project, Document document, int offset) {
        return ReadAction.compute(() -> {

            PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
            if (psiFile != null) {
                PsiElement element = psiFile.findElementAt(offset);
                if (!(element instanceof PyExpression) || element instanceof PyLiteralExpression) {
                    element = PsiTreeUtil.getParentOfType(element, PyExpression.class);
                }
                if (element instanceof PyLiteralExpression) {
                    return null;
                }
                if (element instanceof PyReferenceExpression && element.getParent() instanceof PyCallExpression parent) {
                    // Don't evaluate function objects, expand range for the entire call (`foo` -> `foo(arg1, ..., argN)`)
                    element = parent;
                }
                if (element != null && isSimpleEnough(element) && isExpression(project, document.getText(element.getTextRange()))) {
                    return element.getTextRange();
                }
            }
            return null;
        });
    }

    // is expression suitable to quick evaluate/display tooltip
    private static boolean isSimpleEnough(PsiElement element) {
        return element instanceof PyLiteralExpression ||
               element instanceof PyQualifiedExpression ||
               element instanceof PyNamedParameter ||
               element instanceof PyCallExpression;
    }

    // is expression a variable reference and can be evaluated
    // todo: use patterns (?)
    public static boolean canSaveToTemp(Project project, String expression) {
        return ReadAction.compute(() -> {

            PsiFile file = PyElementGenerator.getInstance(project).createDummyFile(LanguageLevel.getDefault(), expression);
            PsiElement root = file.getFirstChild();
            return !isVariable(root) && (root instanceof PyExpressionStatement);
        });
    }

    private static Boolean isVariable(PsiElement root) {
        return root instanceof PyExpressionStatement &&
               root.getFirstChild() instanceof PyReferenceExpression &&
               root.getFirstChild() == root.getLastChild() &&
               root.getFirstChild().getFirstChild() != null &&
               root.getFirstChild().getFirstChild().getNode().getElementType() == PyTokenTypes.IDENTIFIER &&
               root.getFirstChild().getFirstChild() == root.getFirstChild().getLastChild() &&
               root.getFirstChild().getFirstChild().getFirstChild() == null;
    }

    private static @Nullable String getLineText(Document document, int line) {
        if (line > 0 && line < document.getLineCount()) {
            return document.getText(TextRange.create(document.getLineStartOffset(line), document.getLineEndOffset(line)));
        }
        return null;
    }

    public static boolean isContinuationLine(Document document, int line) {
        String text = getLineText(document, line);
        if (text != null && text.trim().endsWith("\\")) {
            return true;
        }

        return false;
    }

    public static boolean isCurrentPythonDebugProcess(AnActionEvent event) {
        // TODO consulo: DebuggerUIUtil.getSession(AnActionEvent) missing
        XDebugSession session = event.getData(XDebugSession.DATA_KEY);
        return session != null && session.getDebugProcess() instanceof PyDebugProcess;
    }
}
