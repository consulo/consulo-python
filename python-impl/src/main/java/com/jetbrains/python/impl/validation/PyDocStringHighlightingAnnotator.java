// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.documentation.docstrings.DocStringParser;
import com.jetbrains.python.documentation.docstrings.SphinxDocString;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.impl.documentation.docstrings.DocStringReferenceProvider;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.documentation.docstrings.SphinxReferences;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.validation.PyAnnotationHolder;
import com.jetbrains.python.validation.PyAnnotatorBase;
import consulo.document.util.TextRange;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Highlights doc strings in classes, functions, and files.
 */
public class PyDocStringHighlightingAnnotator extends PyAnnotatorBase {
    @Override
    protected void annotate(PsiElement element, PyAnnotationHolder holder) {
        if (holder.isBatchMode()) {
            return;
        }
        if (element instanceof PsiComment comment) {
            // Highlight the Sphinx role markers (e.g. :py:class:`...`) in a line comment like a docstring tag.
            for (TextRange tagRange : SphinxReferences.findTagRanges(comment)) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(tagRange.shiftRight(element.getTextRange().getStartOffset()))
                    .textAttributes(PyHighlighter.PY_DOC_COMMENT_TAG).create();
            }
            return;
        }
        element.accept(new PyDocStringAnnotatorVisitor(holder));
    }

    private static class PyDocStringAnnotatorVisitor extends PyElementVisitor {
        private final PyAnnotationHolder myHolder;

        private PyDocStringAnnotatorVisitor(PyAnnotationHolder holder) {
            myHolder = holder;
        }

        @Override
        public void visitPyFile(PyFile node) {
            annotateDocStringStmt(DocStringUtil.findDocStringExpression(node));
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            annotateDocStringStmt(DocStringUtil.findDocStringExpression(node.getStatementList()));
        }

        @Override
        public void visitPyClass(PyClass node) {
            annotateDocStringStmt(DocStringUtil.findDocStringExpression(node.getStatementList()));
        }

        @Override
        public void visitPyAssignmentStatement(PyAssignmentStatement node) {
            if (node.isAssignmentTo(PyNames.DOC)) {
                PyExpression right = node.getAssignedValue();
                if (right instanceof PyStringLiteralExpression stringLiteral) {
                    myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(right).textAttributes(PyHighlighter.PY_DOC_COMMENT).create();
                    annotateDocStringStmt(stringLiteral);
                }
            }
        }

        @Override
        public void visitPyExpressionStatement(PyExpressionStatement node) {
            if (node.getExpression() instanceof PyStringLiteralExpression &&
                DocStringUtil.isVariableDocString((PyStringLiteralExpression)node.getExpression())
            ) {
                annotateDocStringStmt((PyStringLiteralExpression)node.getExpression());
            }
        }

        private void annotateDocStringStmt(@Nullable PyStringLiteralExpression stmt) {
            if (stmt == null) {
                return;
            }
            if (DocStringParser.getConfiguredDocStringFormat(stmt) == DocStringFormat.REST) {
                String[] tags = SphinxDocString.ALL_TAGS;
                int pos = 0;
                while (true) {
                    TextRange textRange = DocStringReferenceProvider.findNextTag(stmt.getText(), pos, tags);
                    if (textRange == null) {
                        break;
                    }
                    myHolder.newSilentAnnotation(
                            HighlightSeverity.INFORMATION).range(textRange.shiftRight(stmt.getTextRange().getStartOffset()))
                        .textAttributes(PyHighlighter.PY_DOC_COMMENT_TAG).create();
                    pos = textRange.getEndOffset();
                }
            }
            // Highlight Sphinx cross-reference role/directive markers (e.g. :py:class:`...`) like other docstring tags.
            for (TextRange tagRange : SphinxReferences.findTagRanges(stmt)) {
                myHolder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(tagRange.shiftRight(stmt.getTextRange().getStartOffset()))
                    .textAttributes(PyHighlighter.PY_DOC_COMMENT_TAG).create();
            }
        }
    }
}
