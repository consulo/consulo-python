// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.pyi;

import com.jetbrains.python.inspections.PyInspectionExtension;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
public class PyiInspectionExtension extends PyInspectionExtension {
    @Override
    public boolean ignoreUnused(PsiElement element, TypeEvalContext evalContext) {
        if (!(element.getContainingFile() instanceof PyiFile)) {
            return false;
        }
        List<PyImportElement> elements;
        if (element instanceof PyFromImportStatement fromImportStatement) {
            elements = fromImportStatement.isStarImport() ? Collections.emptyList() : Arrays.asList(fromImportStatement.getImportElements());
        }
        else if (element instanceof PyImportStatement importStatement) {
            elements = Arrays.asList(importStatement.getImportElements());
        }
        else if (element instanceof PyImportElement importElement) {
            elements = List.of(importElement);
        }
        else {
            elements = Collections.emptyList();
        }
        return elements.isEmpty() || elements.stream().anyMatch(it -> it.getAsName() != null);
    }

    @Override
    public boolean ignoreProtectedSymbol(PyReferenceExpression expression, TypeEvalContext context) {
        PsiElement referencedElement = expression.getReference().resolve();
        return referencedElement != null && referencedElement.getContainingFile() instanceof PyiFile;
    }
}
