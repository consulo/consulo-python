// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiNamedElement;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.stubs.PyExceptPartStub;
import org.jspecify.annotations.Nullable;

public class PyExceptPartImpl extends PyBaseElementImpl<PyExceptPartStub> implements PyExceptPart {

    public PyExceptPartImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyExceptPartImpl(PyExceptPartStub stub) {
        super(stub, PyStubElementTypes.EXCEPT_PART);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyExceptBlock(this);
    }

    public @Nullable PsiNamedElement getNamedElement(String the_name) {
        // Requires switching from stubs to AST in getTarget()
        return PyUtil.IterHelper.findName(getNamedElements(), the_name);
    }
}
