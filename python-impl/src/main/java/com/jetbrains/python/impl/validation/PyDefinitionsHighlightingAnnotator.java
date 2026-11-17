// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.validation.PyAnnotationHolder;
import com.jetbrains.python.validation.PyAnnotatorBase;
import consulo.application.dumb.IndexNotReadyException;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;

import java.util.Objects;

/**
 * Highlights class definitions, function definitions, and decorators.
 */
public final class PyDefinitionsHighlightingAnnotator extends PyAnnotatorBase {
    @Override
    public void annotate(PsiElement element, PyAnnotationHolder holder) {
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
        public void visitPyClass(PyClass node) {
            ASTNode name_node = node.getNameNode();
            if (name_node != null) {
                myHolder.addHighlightingAnnotation(name_node, PyHighlighter.PY_CLASS_DEFINITION);
            }
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            ASTNode nameNode = node.getNameNode();
            if (nameNode != null) {
                String name = node.getName();
                LanguageLevel languageLevel = LanguageLevel.forElement(node);
                if (PyNames.UNDERSCORED_ATTRIBUTES.contains(name) || PyNames.getBuiltinMethods(languageLevel).containsKey(name)) {
                    PyClass cls = node.getContainingClass();
                    if (PyUtil.isNewMethod(node)) {
                        boolean new_style_class = false;
                        try {
                            if (cls != null) {
                                new_style_class = cls.isNewStyleClass(null);
                            }
                        }
                        catch (IndexNotReadyException ignored) {
                        }
                        if (new_style_class) {
                            myHolder.addHighlightingAnnotation(nameNode, PyHighlighter.PY_PREDEFINED_DEFINITION);
                        }
                    }
                    else {
                        myHolder.addHighlightingAnnotation(nameNode, PyHighlighter.PY_PREDEFINED_DEFINITION);
                    }
                }
                else {
                    if (ScopeUtil.getScopeOwner(node) instanceof PyFunction) {
                        myHolder.addHighlightingAnnotation(nameNode, PyHighlighter.PY_NESTED_FUNC_DEFINITION);
                    }
                    else {
                        myHolder.addHighlightingAnnotation(nameNode, PyHighlighter.PY_FUNC_DEFINITION);
                    }
                }
            }
        }

        @Override
        public void visitPyDecorator(PyDecorator node) {
            PsiElement atSign = node.getFirstChild();
            if (atSign != null) {
                myHolder.addHighlightingAnnotation(atSign, PyHighlighter.PY_DECORATOR);
                if (node.getQualifiedName() != null) {
                    myHolder.addHighlightingAnnotation(Objects.requireNonNull(node.getCallee()), PyHighlighter.PY_DECORATOR);
                }
            }
        }
    }
}
