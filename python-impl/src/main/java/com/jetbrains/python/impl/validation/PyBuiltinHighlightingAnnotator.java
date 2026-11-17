// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.validation.PyAnnotationHolder;
import com.jetbrains.python.validation.PyAnnotatorBase;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;

/**
 * Marks built-in names.
 */
public class PyBuiltinHighlightingAnnotator extends PyAnnotatorBase {
    @Override
    protected void annotate(PsiElement element, PyAnnotationHolder holder) {
        if (holder.isBatchMode()) {
            return;
        }
        element.accept(new MyVisitor(holder));
    }

    private static class MyVisitor extends PyElementVisitor {
        private final PyAnnotationHolder myHolder;

        private MyVisitor(PyAnnotationHolder holder) {
            myHolder = holder;
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            String name = node.getName();
            if (name == null) {
                return;
            }
            boolean highlightedAsAttribute = highlightAsAttribute(node, name);
            if (highlightedAsAttribute) {
                return;
            }
            if ((PyBuiltinCache.isInBuiltins(node) || PyUtil.isPy2ReservedWord(node)) && !(node.getParent() instanceof PyDecorator)) {
                myHolder.addHighlightingAnnotation(node, PyHighlighter.PY_BUILTIN_NAME);
            }
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            String name = node.getName();
            if (name != null) {
                highlightAsAttribute(node, name);
            }
        }

        /**
         * Try to highlight a node as a class attribute.
         *
         * @param node what to work with
         * @return true iff the node was highlighted.
         */
        private boolean highlightAsAttribute(PyQualifiedExpression node, String name) {
            LanguageLevel languageLevel = LanguageLevel.forElement(node);
            if (PyNames.UNDERSCORED_ATTRIBUTES.contains(name) || PyNames.getBuiltinMethods(languageLevel).containsKey(name)) {
                // things like __len__: foo.__len__ or class Foo: ... __len__ = my_len_impl
                if (node.isQualified() || ScopeUtil.getScopeOwner(node) instanceof PyClass) {
                    ASTNode astNode = node.getNode();
                    if (astNode != null) {
                        ASTNode tgt = astNode.findChildByType(PyTokenTypes.IDENTIFIER); // only the id, not all qualifiers subtree
                        if (tgt != null) {
                            myHolder.addHighlightingAnnotation(tgt, PyHighlighter.PY_PREDEFINED_USAGE);
                            return true;
                        }
                    }
                }
            }
            return false;
        }
    }
}
