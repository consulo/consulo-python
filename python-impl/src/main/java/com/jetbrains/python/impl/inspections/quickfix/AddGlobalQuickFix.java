// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ref.Ref;

public class AddGlobalQuickFix implements LocalQuickFix {
    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixAddGlobal();
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        if (element instanceof PyReferenceExpression expression) {
            String name = expression.getReferencedName();
            ScopeOwner owner = PsiTreeUtil.getParentOfType(element, ScopeOwner.class);
            assert owner instanceof PyClass || owner instanceof PyFunction :
                "Add global quickfix is available only inside class or function, but applied for " +
                owner;
            Ref<Boolean> added = new Ref<>(false);
            owner.accept(new PyRecursiveElementVisitor() {
                @Override
                public void visitElement(PsiElement element) {
                    if (!added.get()) {
                        super.visitElement(element);
                    }
                }

                @Override
                public void visitPyGlobalStatement(PyGlobalStatement node) {
                    if (!added.get()) {
                        node.addGlobal(name);
                        added.set(true);
                    }
                }
            });
            if (added.get()) {
                return;
            }
            PyGlobalStatement globalStatement =
                PyElementGenerator.getInstance(project).createFromText(LanguageLevel.getDefault(), PyGlobalStatement.class, "global " + name);
            PyStatementList statementList;
            boolean hasDocString = false;
            if (owner instanceof PyClass) {
                statementList = ((PyClass)owner).getStatementList();
                if (((PyClass)owner).getDocStringExpression() != null) {
                    hasDocString = true;
                }
            }
            else {
                statementList = ((PyFunction)owner).getStatementList();
                if (((PyFunction)owner).getDocStringExpression() != null) {
                    hasDocString = true;
                }
            }
            PyStatement first = statementList.getStatements()[0];
            if (hasDocString) {
                first = statementList.getStatements()[1];
            }
            statementList.addBefore(globalStatement, first);
        }
    }
}
