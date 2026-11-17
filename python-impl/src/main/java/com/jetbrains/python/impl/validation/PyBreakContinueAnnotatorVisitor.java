// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.psi.PyBreakStatement;
import com.jetbrains.python.psi.PyContinueStatement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.python.impl.localize.PyLocalize;

/**
 * Annotates misplaced 'break' and 'continue'.
 */
public class PyBreakContinueAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    public PyBreakContinueAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    @Override
    public void visitPyBreakStatement(PyBreakStatement node) {
        if (node.getLoopStatement() == null) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annBreakOutsideLoop().get()).create();
        }
    }

    @Override
    public void visitPyContinueStatement(PyContinueStatement node) {
        if (node.getLoopStatement() == null) {
            myHolder.newAnnotation(HighlightSeverity.ERROR, PyLocalize.annContinueOutsideLoop().get()).create();
        }
    }
}
