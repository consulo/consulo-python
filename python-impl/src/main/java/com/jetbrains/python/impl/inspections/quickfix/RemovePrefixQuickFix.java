// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyStringLiteralUtil;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import static com.jetbrains.python.impl.psi.PyUtil.as;

public class RemovePrefixQuickFix implements LocalQuickFix {
    private final String myPrefix;

    public RemovePrefixQuickFix(String prefix) {
        myPrefix = prefix;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixRemoveStringPrefix(myPrefix);
    }

    @Override
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        final PyStringLiteralExpression pyString = as(descriptor.getPsiElement(), PyStringLiteralExpression.class);
        if (pyString != null) {
            final PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);
            for (ASTNode node : pyString.getStringNodes()) {
                final String nodeText = node.getText();
                final int prefixLength = PyStringLiteralUtil.getPrefixLength(nodeText);
                if (nodeText.substring(0, prefixLength).equalsIgnoreCase(myPrefix)) {
                    final PyStringLiteralExpression replacement =
                        elementGenerator.createStringLiteralAlreadyEscaped(nodeText.substring(prefixLength));
                    node.getPsi().replace(replacement.getFirstChild());
                }
            }
        }
    }
}
