// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import static com.jetbrains.python.impl.codeInsight.PyDataclassesKt.parseDataclassParameters;

@ExtensionImpl
public class PyDunderSlotsInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameDunderSlots();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonClassStructure();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyClass(PyClass node) {
            super.visitPyClass(node);

            PyDataclassParameters params = parseDataclassParameters(node, myTypeEvalContext);
            if (params != null && params.getSlots()) {
                PyTargetExpression explicitSlotsAttr = node.findClassAttribute(PyNames.SLOTS, false, myTypeEvalContext);
                if (explicitSlotsAttr != null) {
                    PsiElement anchor = params.getSlotsArgument();
                    if (anchor == null) {
                        anchor = node.getNameIdentifier();
                    }
                    if (anchor == null) {
                        anchor = node;
                    }
                    String message = PyLocalize.inspDunderSlotsEnabledTwice().get();
                    if (params.getSlotsArgument() != null) {
                        registerProblem(anchor, message, new RemoveSlotsKwargQuickFix());
                    }
                    else {
                        registerProblem(anchor, message);
                    }
                }
            }

            if (!LanguageLevel.forElement(node).isPython2()) {
                PyExpression slots = findSlotsValue(node);
                if (slots instanceof PySequenceExpression sequence) {
                    for (PyExpression element : sequence.getElements()) {
                        if (element instanceof PyStringLiteralExpression literal) {
                            processSlot(node, literal);
                        }
                    }
                }
                else if (slots instanceof PyStringLiteralExpression literal) {
                    processSlot(node, literal);
                }
            }
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            super.visitPyTargetExpression(node);

            checkAttributeExpression(node);
        }

        private @Nullable PyExpression findSlotsValue(PyClass pyClass) {
            PyTargetExpression target = pyClass.findClassAttribute(PyNames.SLOTS, false, myTypeEvalContext);
            PyExpression value = target != null ? target.findAssignedValue() : null;

            return PyPsiUtils.flattenParens(value);
        }

        private void processSlot(PyClass pyClass, PyStringLiteralExpression slot) {
            String name = slot.getStringValue();

            PyTargetExpression classAttribute = pyClass.findClassAttribute(name, false, myTypeEvalContext);
            if (classAttribute != null && classAttribute.hasAssignedValue()) {
                ProblemMessage message = PyInspectionMessages.problemMessage(
                    msgArgs -> PyLocalize.inspDunderSlotsNameInSlotsConflictsWithClassVariable(msgArgs[0]), name);
                registerProblem(slot, message);
            }
        }

        private void checkAttributeExpression(PyTargetExpression target) {
            String targetName = target.getName();
            PyExpression qualifier = target.getQualifier();

            if (targetName == null || qualifier == null) {
                return;
            }

            PyType qualifierType = myTypeEvalContext.getType(qualifier);
            if (qualifierType instanceof PyClassLikeType classLikeType && !classLikeType.isAttributeWritable(targetName, myTypeEvalContext)) {
                ProblemMessage message = PyInspectionMessages.problemMessage(
                    msgArgs -> PyLocalize.inspDunderSlotsClassObjectMissingAttribute(msgArgs[0], msgArgs[1]),
                    classLikeType.getName(), targetName);
                registerProblem(target, message);
            }
        }
    }

    // JB: ModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class RemoveSlotsKwargQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PyLocalize.qfixDunderSlotsEnabledTwice();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            PsiElement element = descriptor.getPsiElement();
            PyKeywordArgument kwArg = PsiTreeUtil.getParentOfType(element, PyKeywordArgument.class);
            if (kwArg == null) {
                kwArg = element instanceof PyKeywordArgument keywordArgument ? keywordArgument : null;
            }
            if (kwArg == null) {
                return;
            }

            // Only remove if it's the 'slots' kwarg inside an argument list
            if (!"slots".equals(kwArg.getKeyword())) {
                return;
            }
            if (!(kwArg.getParent() instanceof PyArgumentList)) {
                return;
            }

            // Remove neighboring comma to keep argument list syntax correct
            PsiElement commaAfter = PsiTreeUtil.skipWhitespacesForward(kwArg);
            if (commaAfter != null && commaAfter.getNode().getElementType() != PyTokenTypes.COMMA) {
                commaAfter = null;
            }
            PsiElement commaBefore = PsiTreeUtil.skipWhitespacesBackward(kwArg);
            if (commaBefore != null && commaBefore.getNode().getElementType() != PyTokenTypes.COMMA) {
                commaBefore = null;
            }

            // Prefer removing the trailing comma, otherwise remove the leading one
            if (commaAfter != null) {
                commaAfter.delete();
                kwArg.delete();
            }
            else if (commaBefore != null) {
                kwArg.delete();
                commaBefore.delete();
            }
            else {
                kwArg.delete();
            }
        }
    }
}
