// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiNamedElement;
import com.jetbrains.python.ast.PyAstAssignmentStatement;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import org.jspecify.annotations.Nullable;

public class PyAssignmentStatementImpl extends PyElementImpl implements PyAssignmentStatement {
    private volatile PyExpression @Nullable [] myTargets;

    public PyAssignmentStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyAssignmentStatement(this);
    }

    @Override
    public PyExpression[] getTargets() {
        PyExpression[] result = myTargets;
        if (result == null) {
            myTargets = result = PyAstAssignmentStatement.calcTargets(this, false, PyExpression.EMPTY_ARRAY);
        }
        return result;
    }

    @Override
    public PyExpression[] getRawTargets() {
        return PyAstAssignmentStatement.calcTargets(this, true, PyExpression.EMPTY_ARRAY);
    }

    public @Nullable PsiNamedElement getNamedElement(String the_name) {
        // performance: check simple case first
        PyExpression[] targets = getTargets();
        if (targets.length == 1 && targets[0] instanceof PyTargetExpression target) {
            return !target.isQualified() && the_name.equals(target.getName()) ? target : null;
        }
        return PyUtil.IterHelper.findName(getNamedElements(), the_name);
    }

    @Override
    public void subtreeChanged() {
        super.subtreeChanged();
        myTargets = null;
    }
}
