// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.python.impl.localize.PyLocalize;

/**
 * Checks for non-top-level star imports.
 */
public class PyImportAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    public PyImportAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    @Override
    public void visitPyFromImportStatement(PyFromImportStatement node) {
        if (node.isStarImport() && PsiTreeUtil.getParentOfType(node, PyFunction.class, PyClass.class) != null) {
            myHolder.newAnnotation(HighlightSeverity.WARNING, PyLocalize.annStarImportAtTopOnly().get()).create();
        }
    }
}
