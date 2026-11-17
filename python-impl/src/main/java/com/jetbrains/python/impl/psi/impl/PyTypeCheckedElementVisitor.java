// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElsePart;
import com.jetbrains.python.psi.PyIfPart;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PyTypeCheckedElementVisitor extends PyRecursiveElementVisitor {
    private final @Nullable LanguageLevel myLanguageLevel;

    public PyTypeCheckedElementVisitor(@Nullable LanguageLevel languageLevel) {
        myLanguageLevel = languageLevel;
    }

    @Override
    public void visitPyIfStatement(PyIfStatement node) {
        List<PyIfPart> ifParts = new ArrayList<>();
        ifParts.add(node.getIfPart());
        ifParts.addAll(Arrays.asList(node.getElifParts()));
        for (PyIfPart ifPart : ifParts) {
            Boolean result = PyEvaluator.evaluateAsBooleanNoResolve(ifPart.getCondition(), myLanguageLevel);
            if (result == null) {
                super.visitPyIfStatement(node);
                return;
            }
            if (result) {
                ifPart.getStatementList().accept(this);
                return;
            }
        }
        PyElsePart elsePart = node.getElsePart();
        if (elsePart != null) {
            elsePart.getStatementList().accept(this);
        }
    }
}
