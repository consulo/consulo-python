// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.inspections.quickfix.DictCreationQuickFix;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public final class PyDictCreationInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameDictCreation();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonCodeStyleIssues();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyAssignmentStatement(PyAssignmentStatement node) {
            if (node.getAssignedValue() instanceof PyDictLiteralExpression) {
                if (node.getTargets().length != 1) {
                    return;
                }
                PyExpression target = node.getTargets()[0];
                String name = target.getName();
                if (name == null) {
                    return;
                }

                PyStatement statement = PsiTreeUtil.getNextSiblingOfType(node, PyStatement.class);

                while (statement instanceof PyAssignmentStatement assignmentStatement) {
                    List<Pair<PyExpression, PyExpression>> targets = getDictTargets(target, name, assignmentStatement);
                    if (targets == null) {
                        return;
                    }
                    if (!targets.isEmpty()) {
                        registerProblem(node,
                                        PyLocalize.inspDictCreationThisDictionaryCreationCouldBeRewrittenAsDictionaryLiteral().get(),
                                        new DictCreationQuickFix());
                        break;
                    }
                    statement = PsiTreeUtil.getNextSiblingOfType(assignmentStatement, PyStatement.class);
                }
            }
        }
    }

    public static @Nullable List<Pair<PyExpression, PyExpression>> getDictTargets(PyExpression target,
                                                                                  String name,
                                                                                  PyAssignmentStatement assignmentStatement) {
        List<Pair<PyExpression, PyExpression>> targets = new ArrayList<>();
        for (Pair<PyExpression, PyExpression> targetToValue : assignmentStatement.getTargetsToValuesMapping()) {
            if (targetToValue.first instanceof PySubscriptionExpression subscriptionExpression) {
                if (name.equals(subscriptionExpression.getOperand().getName()) &&
                    subscriptionExpression.getIndexExpression() != null &&
                    !referencesTarget(targetToValue.second, target)) {
                    targets.add(targetToValue);
                }
            }
            else {
                return null;
            }
        }
        return targets;
    }

    private static boolean referencesTarget(PyExpression expression, PsiElement target) {
        List<PsiElement> refs = new ArrayList<>();
        expression.accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyReferenceExpression(PyReferenceExpression node) {
                super.visitPyReferenceExpression(node);
                PsiPolyVariantReference ref = node.getReference();
                if (ref.isReferenceTo(target)) {
                    refs.add(node);
                }
            }
        });
        return !refs.isEmpty();
    }
}
