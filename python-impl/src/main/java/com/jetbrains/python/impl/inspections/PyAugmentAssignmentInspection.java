// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.localize.LocalizeValue;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.ast.TokenSet;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.inspections.quickfix.AugmentedAssignmentQuickFix;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.impl.psi.types.PyNumericTowerUtil;
import com.jetbrains.python.impl.psi.types.PyStructuralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * User: catherine
 * <p>
 * Inspection to detect assignments that can be replaced with augmented assignments.
 */
@ExtensionImpl
public final class PyAugmentAssignmentInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameAugmentAssignment();
    }


    private static final TokenSet OPERATIONS = TokenSet.create(PyTokenTypes.PLUS, PyTokenTypes.MINUS, PyTokenTypes.MULT,
                                                                        PyTokenTypes.FLOORDIV, PyTokenTypes.DIV, PyTokenTypes.PERC,
                                                                        PyTokenTypes.AND,
                                                                        PyTokenTypes.OR, PyTokenTypes.XOR, PyTokenTypes.LTLT,
                                                                        PyTokenTypes.GTGT,
                                                                        PyTokenTypes.EXP);
    private static final TokenSet COMMUTATIVE_OPERATIONS =
        TokenSet.create(PyTokenTypes.PLUS, PyTokenTypes.MULT, PyTokenTypes.OR, PyTokenTypes.AND);

    private static final List<String> SEQUENCE_METHODS = Arrays.asList(PyNames.LEN, PyNames.ITER, PyNames.GETITEM, PyNames.CONTAINS);

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {

        Visitor(@Nullable ProblemsHolder holder,
                TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyAssignmentStatement(PyAssignmentStatement node) {
            PyExpression target = node.getLeftHandSideExpression();
            PyBinaryExpression value = PyUtil.as(node.getAssignedValue(), PyBinaryExpression.class);

            if (target != null && value != null && node.getTargets().length == 1) {
                PyExpression leftExpression = value.getLeftExpression();
                PyExpression rightExpression = PyPsiUtils.flattenParens(value.getRightExpression());

                if (leftExpression == null || rightExpression == null) {
                    return;
                }

                String targetText = target.getText();
                PyExpression mainOperandExpression;
                PyExpression otherOperandExpression;
                boolean changedParts;

                if (targetText.equals(leftExpression.getText())) {
                    mainOperandExpression = leftExpression;
                    otherOperandExpression = rightExpression;
                    changedParts = false;
                }
                else if (targetText.equals(rightExpression.getText())) {
                    mainOperandExpression = rightExpression;
                    otherOperandExpression = leftExpression;
                    changedParts = true;
                }
                else {
                    return;
                }

                PyElementType operator = value.getOperator();
                if (operator != null && assignmentCanBeReplaced(mainOperandExpression, otherOperandExpression, operator, changedParts)) {
                    registerProblem(node, PyLocalize.inspAssignmentCanBeReplacedWithAugmentedAssignment().get(),
                                    new AugmentedAssignmentQuickFix());
                }
            }
        }

        private boolean assignmentCanBeReplaced(PyExpression mainOperandExpression,
                                                PyExpression otherOperandExpression,
                                                PyElementType operator,
                                                boolean changedParts) {
            if (!(mainOperandExpression instanceof PyReferenceExpression || mainOperandExpression instanceof PySubscriptionExpression)) {
                return false;
            }

            if (!changedParts && OPERATIONS.contains(operator) || changedParts && COMMUTATIVE_OPERATIONS.contains(operator)) {
                PyType otherOperandType = myTypeEvalContext.getType(otherOperandExpression);

                if (!PyTypeChecker.isUnknown(otherOperandType, myTypeEvalContext)) {
                    if (changedParts) {
                        if (hasAnySequenceMethod(otherOperandType, otherOperandExpression)) {
                            return false;
                        }

                        PyType mainOperandType = myTypeEvalContext.getType(mainOperandExpression);
                        if (mainOperandType != null && hasAnySequenceMethod(mainOperandType, mainOperandExpression)) {
                            return false;
                        }
                    }

                    return isNumeric(otherOperandType, PyBuiltinCache.getInstance(otherOperandExpression)) ||
                           hasAnySequenceMethod(otherOperandType, otherOperandExpression);
                }
            }

            return false;
        }

        private boolean hasAnySequenceMethod(PyType type, PyExpression location) {
            if (type instanceof PyStructuralType) {
                Set<String> attributeNames = ((PyStructuralType)type).getAttributeNames();

                return SEQUENCE_METHODS.stream().anyMatch(attributeNames::contains);
            }

            PyResolveContext resolveContext = PyResolveContext.defaultContext(myTypeEvalContext);

            return !SEQUENCE_METHODS
                .stream()
                .map(method -> type.resolveMember(method, location, AccessDirection.READ, resolveContext))
                .allMatch(ContainerUtil::isEmpty);
        }

        private boolean isNumeric(PyType type, PyBuiltinCache cache) {
            return PyTypeChecker.match(PyNumericTowerUtil.enrich(cache.getComplexType()), type, myTypeEvalContext);
        }
    }
}
