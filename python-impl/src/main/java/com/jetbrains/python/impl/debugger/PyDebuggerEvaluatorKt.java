// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyLiteralExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.application.ReadAction;
import consulo.application.util.registry.Registry;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.execution.debug.evaluation.XDebuggerEvaluator;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Top-level functions of JetBrains' PyDebuggerEvaluator.kt.
 */
public final class PyDebuggerEvaluatorKt {
    private PyDebuggerEvaluatorKt() {
    }

    /**
     * Common implementation for evaluating expressions at a given document offset (for value tooltips).
     *
     * @param project            the project
     * @param document           the document
     * @param offset             the offset in the document
     * @param callback           the evaluation callback
     * @param evaluateExpression the function to evaluate the expression text
     */
    // TODO consulo: ValueHintType missing in execution-debug-api, JB passes the (unused) hint type too
    public static void evaluateWithOffset(Project project,
                                          Document document,
                                          int offset,
                                          XDebuggerEvaluator.XEvaluationCallback callback,
                                          Consumer<String> evaluateExpression) {
        if (!Registry.is("python.debugger.show.value.tooltip", true)) {
            callback.errorOccurred("");
            return;
        }

        PsiReference ref = ReadAction.compute(() -> {
            PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(document);
            return file != null ? file.findReferenceAt(offset) : null;
        });
        if (ref == null) {
            callback.errorOccurred("");
            return;
        }

        PsiElement element = ref.getElement();
        if (element instanceof PyExpression) {
            if (element.getParent() instanceof PyCallExpression) {
                callback.errorOccurred("");
            }
            else {
                evaluateExpression.accept(element.getText());
            }
        }
        else {
            callback.errorOccurred("");
        }
    }

    /**
     * Filters the expression range for hover evaluation, excluding expressions that may have side effects (PY-83446).
     * <p>
     * On hover, we avoid evaluating call expressions, binary expressions, and prefix expressions
     * to prevent unintended function execution. For function names inside calls, only the reference
     * range is returned (not the full call).
     *
     * @param project  the project
     * @param document the document
     * @param offset   the offset in the document
     * @param range    the original expression range from {@link PyDebugSupportUtils#getExpressionRangeAtOffset}
     * @return the filtered range, or null if the expression should not be evaluated on hover
     */
    public static @Nullable TextRange filterExpressionRangeForHover(Project project, Document document, int offset, TextRange range) {
        return ReadAction.compute(() -> {
            PsiFile psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document);
            if (psiFile == null) {
                return range;
            }
            PsiElement element = psiFile.findElementAt(offset);
            if (!(element instanceof PyExpression) || element instanceof PyLiteralExpression) {
                element = PsiTreeUtil.getParentOfType(element, PyExpression.class);
            }
            if (element instanceof PyReferenceExpression && element.getParent() instanceof PyCallExpression) {
                return element.getTextRange();
            }
            if (element instanceof PyCallExpression || element instanceof PyBinaryExpression || element instanceof PyPrefixExpression) {
                return null;
            }
            return range;
        });
    }
}
