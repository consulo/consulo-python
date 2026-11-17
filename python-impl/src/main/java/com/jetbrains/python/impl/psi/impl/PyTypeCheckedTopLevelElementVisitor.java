// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyLambdaExpression;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SyntaxTraverser;
import org.jspecify.annotations.Nullable;

public abstract class PyTypeCheckedTopLevelElementVisitor extends PyTypeCheckedElementVisitor {
    public PyTypeCheckedTopLevelElementVisitor(LanguageLevel languageLevel) {
        super(languageLevel);
    }

    @Override
    public void visitPyElement(PyElement node) {
        super.visitPyElement(node);
        checkAddElement(node);
    }

    @Override
    public void visitPyClass(PyClass node) {
        checkAddElement(node); // do not recurse into classes
    }

    @Override
    public void visitPyFunction(PyFunction node) {
        checkAddElement(node); // do not recurse into functions
    }

    @Override
    public void visitPyComprehensionElement(PyComprehensionElement node) {
        if (PyUtil.isOwnScopeComprehension(node)) {
            checkAddElement(node); // do not recurse: the loop variable stays inside the comprehension scope
            // but walrus (:=) targets leak to the enclosing scope (PEP 572), so collect them explicitly
            SyntaxTraverser.psiTraverser(node)
                .forceIgnore(it -> it instanceof PyLambdaExpression) // a walrus inside a nested lambda binds in the lambda, not here
                .filter(PyAssignmentExpression.class)
                .forEach(it -> {
                    PsiElement target = it.getTarget();
                    if (target != null) {
                        checkAddElement(target);
                    }
                });
        }
        else {
            super.visitPyComprehensionElement(node);
        }
    }

    protected abstract void checkAddElement(@Nullable PsiElement node);
}
