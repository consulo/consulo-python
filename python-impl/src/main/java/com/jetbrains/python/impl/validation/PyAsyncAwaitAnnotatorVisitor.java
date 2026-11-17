// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyDictCompExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PySetCompExpression;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.codeEditor.Editor;
import consulo.language.ast.ASTNode;
import consulo.language.editor.annotation.AnnotationBuilder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.inspection.LocalQuickFixAndIntentionActionOnPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public final class PyAsyncAwaitAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    public PyAsyncAwaitAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    private void createError(PsiElement node, @Nullable ScopeOwner scopeOwner, String message) {
        AnnotationBuilder annotation = myHolder
            .newAnnotation(HighlightSeverity.ERROR, message)
            .range(node);
        if (scopeOwner instanceof PyFunction pyFunction) {
            annotation = annotation.newFix(new ConvertIntoAsyncFunctionFix(pyFunction)).registerFix();
        }
        annotation.create();
    }

    private void checkComprehension(PyComprehensionElement node) {
        ASTNode asyncNode = node.getNode().findChildByType(PyTokenTypes.ASYNC_KEYWORD);
        if (asyncNode == null) {
            return;
        }

        ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(node);
        if (PyImplicitAsyncContextProvider.isAsyncAllowed(scopeOwner)) {
            return;
        }

        createError((PsiElement)asyncNode, scopeOwner, PyLocalize.annAsyncForOutsideFunction().get());
    }

    @Override
    public void visitPyPrefixExpression(PyPrefixExpression node) {
        super.visitPyPrefixExpression(node);

        if (node.getOperator() == PyTokenTypes.AWAIT_KEYWORD) {
            ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(node);
            if (PyImplicitAsyncContextProvider.isAsyncAllowed(scopeOwner)) {
                return;
            }

            createError(node.getFirstChild(), scopeOwner, PyLocalize.annAwaitOutsideAsyncFunction().get());
        }
    }

    @Override
    public void visitPyForStatement(PyForStatement node) {
        super.visitPyForStatement(node);
        if (!node.isAsync()) {
            return;
        }

        ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(node);
        if (PyImplicitAsyncContextProvider.isAsyncAllowed(scopeOwner)) {
            return;
        }

        createError(node.getFirstChild(), scopeOwner, PyLocalize.annAsyncForOutsideFunction().get());
    }

    @Override
    public void visitPyWithStatement(PyWithStatement node) {
        super.visitPyWithStatement(node);
        if (!node.isAsync()) {
            return;
        }

        ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(node);
        if (PyImplicitAsyncContextProvider.isAsyncAllowed(scopeOwner)) {
            return;
        }

        createError(node.getFirstChild(), scopeOwner, PyLocalize.annAsyncWithOutsideFunction().get());
    }

    @Override
    public void visitPyListCompExpression(PyListCompExpression node) {
        super.visitPyListCompExpression(node);
        checkComprehension(node);
    }

    @Override
    public void visitPyDictCompExpression(PyDictCompExpression node) {
        super.visitPyDictCompExpression(node);
        checkComprehension(node);
    }

    @Override
    public void visitPySetCompExpression(PySetCompExpression node) {
        super.visitPySetCompExpression(node);
        checkComprehension(node);
    }

    @Override
    public void visitPyFunction(PyFunction node) {
        if (!node.isAsyncAllowed()) {
            Optional
                .ofNullable(node.getNode())
                .map(astNode -> astNode.findChildByType(PyTokenTypes.ASYNC_KEYWORD))
                .ifPresent(asyncNode -> myHolder.newAnnotation(HighlightSeverity.ERROR,
                        PyLocalize.annFunctionCannotBeAsync(node.getName()).get())
                    .range(asyncNode).create());
        }
    }

    // JB: PsiUpdateModCommandAction<PyFunction> (ModCommand API is not available in Consulo)
    private static class ConvertIntoAsyncFunctionFix extends LocalQuickFixAndIntentionActionOnPsiElement {
        protected ConvertIntoAsyncFunctionFix(PyFunction element) {
            super(element);
        }

        @Override
        public void invoke(Project project, PsiFile file, @Nullable Editor editor, PsiElement startElement, PsiElement endElement) {
            PyFunction element = (PyFunction)startElement;
            ASTNode defKeyword = element.getNode().findChildByType(PyTokenTypes.DEF_KEYWORD);
            element.getNode().addLeaf(PyTokenTypes.ASYNC_KEYWORD, "async ", defKeyword);
        }

        @Override
        public LocalizeValue getText() {
            return PyLocalize.qfixConvertIntoAsyncFunction();
        }
    }
}
