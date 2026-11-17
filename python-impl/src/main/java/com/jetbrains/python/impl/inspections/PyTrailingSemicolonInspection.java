// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.inspections.PyInspectionExtension;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.inspections.quickfix.RemoveTrailingSemicolonQuickFix;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.ast.TokenSet;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiWhiteSpace;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PyTrailingSemicolonInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameTrailingSemicolon();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonCodeStyleIssues();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    public static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyStatement(PyStatement statement) {
            ASTNode statementNode = statement.getNode();
            if (statementNode != null) {
                ASTNode[] nodes = statementNode.getChildren(TokenSet.create(PyTokenTypes.SEMICOLON));
                for (ASTNode node : nodes) {
                    ASTNode nextNode = statementNode.getTreeNext();
                    while ((nextNode instanceof PsiWhiteSpace) && (!nextNode.textContains('\n'))) {
                        nextNode = nextNode.getTreeNext();
                    }
                    if (nextNode == null || nextNode.textContains('\n')) {
                        if (ContainerUtil.exists(PyInspectionExtension.EP_NAME.getExtensionList(),
                                                 extension -> extension.ignoreTrailingSemicolon(statement))) {
                            return;
                        }
                        registerProblem(node.getPsi(), PyLocalize.inspTrailingSemicolon().get(), new RemoveTrailingSemicolonQuickFix());
                    }
                }
            }
        }
    }
}
