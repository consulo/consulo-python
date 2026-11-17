// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.classes;

import consulo.util.lang.function.Condition;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;

/**
 * Searches for element in another element's usages.
 * Parametrize it with needle and make stack to accept it.
 *
 * @author Ilya.Kazakevich
 */
final class DependencyVisitor extends PyRecursiveElementVisitor {

    private final PyElement myElementToFind;
    private boolean myDependencyFound;

    /**
     * @param elementToFind what to find
     */
    DependencyVisitor(PyElement elementToFind) {
        myElementToFind = elementToFind;
    }

    @Override
    public void visitPyTargetExpression(PyTargetExpression node) {
        PyExpression value = node.findAssignedValue();
        if (value != null) {
            PsiReference reference = value.getReference();
            if (reference != null && reference.isReferenceTo(myElementToFind)) {
                myDependencyFound = true;
                return;
            }
        }
        super.visitPyTargetExpression(node);
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
        PyExpression callee = node.getCallee();
        if (callee != null) {
            PsiReference calleeReference = callee.getReference();
            if ((calleeReference != null) && calleeReference.isReferenceTo(myElementToFind)) {
                myDependencyFound = true;
                return;
            }
            String calleeName = callee.getName();

            String name = myElementToFind.getName();
            if ((calleeName != null) && calleeName.equals(name)) {  // Check by name also
                myDependencyFound = true;
            }

            // Member could be used as method param
            PyArgumentList list = node.getArgumentList();
            if (list != null) {
                for (PyExpression expression : node.getArgumentList().getArgumentExpressions()) {
                    PsiReference reference = expression.getReference();
                    if ((reference != null) && reference.isReferenceTo(myElementToFind)) {
                        myDependencyFound = true;
                    }
                    if ((name != null) && name.equals(expression.getName())) {
                        myDependencyFound = true;
                    }
                }
            }
        }
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {

        PsiPolyVariantReference reference = node.getReference();
        if (reference.isReferenceTo(myElementToFind)) {
            myDependencyFound = true;
            return;
        }
        // TODO: This step is member-type specific. Move to MemberManagers?
        if (myElementToFind instanceof PyAssignmentStatement) {
            PyExpression[] targets = ((PyAssignmentStatement)myElementToFind).getTargets();

            if (targets.length != 1) {
                return;
            }
            PyExpression expression = targets[0];

            if (reference.isReferenceTo(expression)) {
                myDependencyFound = true;
                return;
            }
            if (node.getText().equals(expression.getText())) { // Check by name also
                myDependencyFound = true;
            }
            return;
        }
        PsiElement declaration = reference.resolve();
        myDependencyFound = PsiTreeUtil.findFirstParent(declaration, new PsiElementCondition()) != null;
    }

    public boolean isDependencyFound() {
        return myDependencyFound;
    }

    private class PsiElementCondition implements Condition<PsiElement> {
        @Override
        public boolean value(PsiElement psiElement) {
            return psiElement.equals(myElementToFind);
        }
    }
}
