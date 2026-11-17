// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.validation.PyAnnotationHolder;
import com.jetbrains.python.validation.PyAnnotatorBase;
import consulo.colorScheme.TextAttributesKey;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.impl.psi.PyUtil.as;


public class PyFunctionHighlightingAnnotator extends PyAnnotatorBase {
    @Override
    protected void annotate(PsiElement element, PyAnnotationHolder holder) {
        if (holder.isBatchMode()) {
            return;
        }
        element.accept(new MyVisitor(holder));
    }

    private static class MyVisitor extends PyElementVisitor {
        public static final HighlightSeverity LOW_PRIORITY_HIGHLIGHTING = new HighlightSeverity("LOW_PRIORITY_HIGHLIGHTING",
            HighlightSeverity.INFORMATION.myVal - 3);

        private final PyAnnotationHolder myHolder;

        private MyVisitor(PyAnnotationHolder holder) {
            myHolder = holder;
        }

        @Override
        public void visitPyParameter(PyParameter node) {
            PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
            if (function != null) {
                TextAttributesKey attrKey = node.isSelf() ? PyHighlighter.PY_SELF_PARAMETER : PyHighlighter.PY_PARAMETER;
                if (isArgOrKwargParameter(node)) {
                    myHolder.addHighlightingAnnotation(node, attrKey);
                }
                else {
                    myHolder.addHighlightingAnnotation(node.getFirstChild(), attrKey);
                }
            }
        }

        private static boolean isArgOrKwargParameter(PyParameter parameter) {
            if (parameter instanceof PyNamedParameter namedParameter) {
                return namedParameter.isPositionalContainer() ||
                    namedParameter.isKeywordContainer();
            }
            return false;
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            String referencedName = node.getReferencedName();
            if (!node.isQualified() && referencedName != null) {
                PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
                if (function != null) {
                    PyNamedParameter element = findParameterRecursively(function, referencedName);
                    if (element != null) {
                        TextAttributesKey attrKey = element.isSelf() ? PyHighlighter.PY_SELF_PARAMETER : PyHighlighter.PY_PARAMETER;
                        myHolder.addHighlightingAnnotation(node, attrKey);
                    }
                }
            }
        }

        @Override
        public void visitPyKeywordArgument(PyKeywordArgument node) {
            ASTNode keywordNode = node.getKeywordNode();
            if (keywordNode != null) {
                myHolder.addHighlightingAnnotation(keywordNode, PyHighlighter.PY_KEYWORD_ARGUMENT);
            }
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            if (node.getParent() instanceof PyDecorator) {
                return; //if it's in decorator, then we've already highlighted it as a decorator
            }
            PyReferenceExpression callee = as(node.getCallee(), PyReferenceExpression.class);
            if (callee != null) {
                if (!callee.isQualified() && PyBuiltinCache.isInBuiltins(callee)) {
                    return;
                }
                ASTNode functionName = callee.getNameElement();
                if (functionName != null) {
                    TextAttributesKey attrKey = callee.isQualified() ? PyHighlighter.PY_METHOD_CALL : PyHighlighter.PY_FUNCTION_CALL;
                    myHolder.addHighlightingAnnotation(functionName, attrKey);
                }
            }
        }

        @Override
        public void visitPyAnnotation(PyAnnotation node) {
            PyExpression value = node.getValue();
            if (value != null) {
                highlightAnnotationValue(value);
            }
        }

        private void highlightAnnotationValue(PyExpression value) {
            int currentRangeStart = -1;
            int currentRangeEnd = -1;
            for (PsiElement element : SyntaxTraverser.psiTraverser(value).filter(MyVisitor::isHighlightableAnnotationLeaf)) {
                TextRange range = element.getTextRange();
                int rangeStart = range.getStartOffset();
                int rangeEnd = range.getEndOffset();
                if (currentRangeEnd == rangeStart) {
                    currentRangeEnd = rangeEnd;
                    continue;
                }
                if (currentRangeStart >= 0) {
                    myHolder.addHighlightingAnnotation(new TextRange(currentRangeStart, currentRangeEnd),
                        PyHighlighter.PY_ANNOTATION,
                        LOW_PRIORITY_HIGHLIGHTING);
                }
                currentRangeStart = rangeStart;
                currentRangeEnd = rangeEnd;
            }

            if (currentRangeStart >= 0) {
                myHolder.addHighlightingAnnotation(new TextRange(currentRangeStart, currentRangeEnd),
                    PyHighlighter.PY_ANNOTATION,
                    LOW_PRIORITY_HIGHLIGHTING);
            }
        }

        private static boolean isHighlightableAnnotationLeaf(PsiElement element) {
            return element.getFirstChild() == null &&
                !(element instanceof PsiComment) &&
                !(element instanceof PsiWhiteSpace) &&
                !element.getTextRange().isEmpty();
        }

        private static @Nullable PyNamedParameter findParameterRecursively(PyFunction function, String referencedName) {
            for (PyFunction f = function; f != null; f = ObjectUtil.tryCast(ScopeUtil.getScopeOwner(f), PyFunction.class)) {
                PyNamedParameter element = f.getParameterList().findParameterByName(referencedName);
                if (element != null) {
                    return element;
                }
            }
            return null;
        }
    }
}
