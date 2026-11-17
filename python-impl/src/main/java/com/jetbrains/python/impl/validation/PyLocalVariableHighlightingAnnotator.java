// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.validation.PyAnnotationHolder;
import com.jetbrains.python.validation.PyAnnotatorBase;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;

public class PyLocalVariableHighlightingAnnotator extends PyAnnotatorBase {
    @Override
    protected void annotate(PsiElement element, PyAnnotationHolder holder) {
        element.accept(new PyLocalVariableAnnotatorVisitor(holder));
    }

    private static class PyLocalVariableAnnotatorVisitor extends PyElementVisitor {
        private final PyAnnotationHolder myHolder;

        private PyLocalVariableAnnotatorVisitor(PyAnnotationHolder holder) {
            myHolder = holder;
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            if (!isLocalVariable(node)) {
                return;
            }
            ASTNode nameElement = node.getNameElement();
            if (nameElement != null) {
                myHolder.addHighlightingAnnotation(nameElement, PyHighlighter.PY_LOCAL_VARIABLE);
            }
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            if (PyResolveUtil.resolveLocally(node).stream().anyMatch(it -> it instanceof PyTargetExpression target && isLocalVariable(target))) {
                myHolder.addHighlightingAnnotation(node.getNode(), PyHighlighter.PY_LOCAL_VARIABLE);
            }
        }

        private static boolean isLocalVariable(PyTargetExpression target) {
            if (target.isQualified()) {
                return false;
            }
            String name = target.getReferencedName();
            if (name == null) {
                return false;
            }
            if (name.equals(PyNames.UNDERSCORE)) {
                return false;
            }
            ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(target);
            if (!(scopeOwner instanceof PyFunction)) {
                return false;
            }
            Scope scope = ControlFlowCache.getScope(scopeOwner);
            return !(scope.isNonlocal(name) || scope.isGlobal(name));
        }
    }
}
