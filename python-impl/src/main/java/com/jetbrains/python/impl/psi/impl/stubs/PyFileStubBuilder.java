// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.stubs;

import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.stub.DefaultStubBuilder;
import consulo.language.psi.stub.StubElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyIfPart;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyStatementPart;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PyFileStubBuilder extends DefaultStubBuilder {
    @Override
    protected StubElement createStubForFile(PsiFile file) {
        if (file instanceof PyFile) {
            return new PyFileStubImpl((PyFile)file);
        }

        return super.createStubForFile(file);
    }

    @Override
    public boolean skipChildProcessingWhenBuildingStubs(ASTNode parent, ASTNode node) {
        PsiElement parentPsi = parent.getPsi();
        if (parentPsi instanceof PyIfStatement && PyUtil.isIfNameEqualsMain((PyIfStatement)parentPsi)) {
            return true;
        }
        if (parentPsi instanceof PyIfStatement ifStatement && node.getPsi() instanceof PyStatementPart part) {
            return isAlwaysUnreachable(ifStatement, part);
        }
        return false;
    }

    private static boolean isAlwaysUnreachable(PyIfStatement ifStatement, PyStatementPart part) {
        assert part.getParent() == ifStatement;

        List<PyIfPart> ifParts = new ArrayList<>();
        ifParts.add(ifStatement.getIfPart());
        ifParts.addAll(Arrays.asList(ifStatement.getElifParts()));
        for (PyIfPart ifPart : ifParts) {
            if (ifPart == part) {
                break;
            }
            Boolean result = PyEvaluator.evaluateAsBooleanNoResolve(ifPart.getCondition());
            if (result == Boolean.TRUE) {
                return true;
            }
        }

        if (part instanceof PyIfPart ifPart) {
            Boolean result = PyEvaluator.evaluateAsBooleanNoResolve(ifPart.getCondition());
            if (result == Boolean.FALSE) {
                return true;
            }
        }

        return false;
    }
}
