// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassTransformResolver;
import com.jetbrains.python.impl.codeInsight.stdlib.PyNamedTupleTypeProvider;
import com.jetbrains.python.impl.codeInsight.stdlib.PyStdlibTypeProvider;
import com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider.GeneratorTypeDescriptor;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.inspections.quickfix.PyMakeFunctionReturnTypeQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ArgumentMappingResults;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.impl.PyReferenceExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PySubscriptionExpressionImpl;
import com.jetbrains.python.impl.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.impl.psi.impl.ResolvedOperator;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.impl.psi.types.PyTypeChecker.GenericSubstitutions;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.PyCallExpression.PyArgumentsMapping;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.*;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.logging.Logger;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import consulo.util.lang.ref.SoftReference;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

@ExtensionImpl
public class PyTypeCheckerInspection extends PyInspection {
    private static final Logger LOG = Logger.getInstance(PyTypeCheckerInspection.class);
    private static final Key<Long> TIME_KEY = Key.create("PyTypeCheckerInspection.StartTime");

    private static final int STRICT_UNION_COMBINATION_LIMIT = 16;

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameTypeChecker();
    }

    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyTypeCheckerInspectionState();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        if (LOG.isDebugEnabled()) {
            session.putUserData(TIME_KEY, System.nanoTime());
        }
        TypeEvalContext context = PyInspectionVisitor.getContext(session);
        if (context.getUsesExternalTypeEngine()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        PyTypeCheckerInspectionState inspectionState = (PyTypeCheckerInspectionState) state;
        Visitor visitor = new Visitor(holder, context, inspectionState.allowCovariantAttributeOverrides,
                                      inspectionState.checkInferredAttributeOverrides);
        return new PyReachableElementVisitor(visitor, context);
    }

    public static class Visitor extends PyInspectionVisitor {
        private final ProblemsHolder holder;
        private final boolean allowCovariantAttributeOverrides;
        private final boolean checkInferredAttributeOverrides;

        private final TypedDictProblemReporter typedDictProblemReporter = new TypedDictProblemReporter();

        public Visitor(ProblemsHolder holder, TypeEvalContext context) {
            this(holder, context, false, false);
        }

        public Visitor(ProblemsHolder holder,
                       TypeEvalContext context,
                       boolean allowCovariantAttributeOverrides,
                       boolean checkInferredAttributeOverrides) {
            super(holder, context);
            this.holder = holder;
            this.allowCovariantAttributeOverrides = allowCovariantAttributeOverrides;
            this.checkInferredAttributeOverrides = checkInferredAttributeOverrides;
        }

        // TODO: Visit decorators with arguments
        @Override
        @RequiredReadAction
        public void visitPyCallExpression(PyCallExpression node) {
            checkCallSite(node);
        }

        // A class definition implicitly calls `__init_subclass__` of its base classes with the
        // class-definition keyword arguments; type-check those arguments against its parameters.
        @Override
        @RequiredReadAction
        public void visitPyClass(PyClass node) {
            if (node.getArguments().isEmpty()) {
                return;
            }
            checkCallSite(node);
        }

        @Override
        @RequiredReadAction
        public void visitPyBinaryExpression(PyBinaryExpression node) {
            if (node.getOperator() == PyTokenTypes.OR && PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext) &&
                PyTypingTypeProvider.getType(node, myTypeEvalContext) != null) {
                return;
            }
            checkCallSite(node);
        }

        @Override
        @RequiredReadAction
        public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
            checkCallSite(node);
            // Most of the following follows the logic of `visitPyTargetExpression` taking into account that
            // an augmented assignment target is normally a reference.
            PyExpression nodeTarget = node.getTarget();
            if (!(nodeTarget instanceof PyReferenceExpression target)) {
                return;
            }

            PsiElement resolved = target.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).resolve();
            if (!(resolved instanceof PyTargetExpression resolvedTarget)) {
                return;
            }
            PyType converterInputType = getConverterInputType(target);
            if (converterInputType == null && !hasExplicitType(resolvedTarget)) {
                return;
            }
            PyType expected = converterInputType != null ? converterInputType : myTypeEvalContext.getType(target);

            PyExpression qualifier = target.getQualifier();
            if (qualifier != null) {
                PyType unsubstitutedExpected = expected;
                expected = PyTypeUtil.compositeMap(myTypeEvalContext.getType(qualifier), it -> {
                    GenericSubstitutions substitutions = PyTypeChecker.unifyReceiver(it, myTypeEvalContext);
                    return PyTypeChecker.substitute(unsubstitutedExpected, substitutions, myTypeEvalContext);
                });
            }

            boolean isDescriptor = false;
            Ref<PyType> classAttrType = getClassAttributeType(target);
            if (classAttrType != null) {
                Ref<PyType> dunderSetValueType =
                    PyDescriptorTypeUtil.getExpectedValueTypeForDunderSet(target, classAttrType.get(), myTypeEvalContext);
                if (dunderSetValueType != null) {
                    expected = dunderSetValueType.get();
                    isDescriptor = true;
                }
            }
            // This gives the result type for calling the corresponding `__iXXX__` "inplace" method or the normal operator method.
            PyType actual = node.getType(myTypeEvalContext);
            if (!matchesExpectedType(expected, actual, null, null)) {
                PyInspectionMessages.ProblemMessage message;
                if (isDescriptor) {
                    message = typeMismatchMessage(
                        expected,
                        actual,
                        node,
                        p -> PyLocalize.inspTypeCheckerExpectedTypeFromDunderSetGotTypeInstead(p[0], p[1])
                    );
                }
                else {
                    message = typeMismatchMessage(
                        expected,
                        actual,
                        node,
                        p -> PyLocalize.inspTypeCheckerExpectedTypeFromAugAssignmentGotTypeInstead(p[0], p[1])
                    );
                }
                registerTypeMismatch(PyTypeCheckerSuppressionCode.BAD_ASSIGNMENT, node, expected, actual, message,
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        }

        @Override
        @RequiredReadAction
        public void visitPySubscriptionExpression(PySubscriptionExpression node) {
            PyType operandType = myTypeEvalContext.getType(node.getOperand());
            if (operandType instanceof PyTupleType tupleType && !tupleType.isHomogeneous()) {
                PyExpression indexExpression = node.getIndexExpression();
                for (Integer index : PySubscriptionExpressionImpl.getIndexExpressionPossibleValues(
                    indexExpression,
                    myTypeEvalContext,
                    Integer.class
                )) {
                    int count = tupleType.getElementCount();
                    if (index < -count || index >= count) {
                        PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_INDEX, indexExpression,
                                                            PyLocalize.inspTypeCheckerTupleIndexOutOfRange().get());
                    }
                }
            }
            // Type check in TypedDict subscription expressions cannot be properly done because each key should have its own value type,
            // so this case is covered by PyTypedDictInspection
            if (operandType instanceof PyTypedDictType) {
                return;
            }
            // Don't type check __class_getitem__ calls inside type hints. Normally these are not type hinted as a construct
            // special-cased by type checkers
            if (PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext)) {
                return;
            }
            checkCallSite(node);
        }

        @Override
        @RequiredReadAction
        public void visitPyForStatement(PyForStatement node) {
            if (checkIteratedValue(node.getForPart().getSource(), node.isAsync())) {
                return;
            }
            checkForTargetUnpacking(node);
        }

        @RequiredReadAction
        private void checkForTargetUnpacking(PyForStatement node) {
            PyForPart forPart = node.getForPart();
            PyExpression flattenedTarget = PyPsiUtils.flattenParens(forPart.getTarget());
            if (!(flattenedTarget instanceof PyTupleExpression) && !(flattenedTarget instanceof PyListLiteralExpression)) {
                return;
            }
            PySequenceExpression target = (PySequenceExpression) flattenedTarget;
            PyExpression source = forPart.getSource();
            if (source == null) {
                return;
            }
            PyType sourceType = myTypeEvalContext.getType(source);
            if (sourceType == null) {
                return;
            }
            if (PyTypeChecker.containsAny(sourceType, myTypeEvalContext)) {
                return;
            }
            PyType itemType = PyTargetExpressionImpl.getIterationType(sourceType, source, node.isAsync(), myTypeEvalContext);
            if (!PyTypeUtilKt.isAnyOrUnknown(itemType) && !PyTypeChecker.containsAny(itemType, myTypeEvalContext) &&
                !PyABCUtil.isSubtype(itemType, PyNames.ITERABLE, myTypeEvalContext)) {
                registerProblem(target,
                                PyInspectionMessages.problemMessage(p -> PyLocalize.inspTypeCheckerUnpackExpectedIterable(p[0]),
                                                                    CodifiedParam.ofType(itemType, target, myTypeEvalContext)),
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                return;
            }
            if (itemType instanceof PyTupleType tupleType && !tupleType.isHomogeneous()) {
                checkNestedUnpackingBalance(target.getElements(), tupleType);
            }
            if (itemType != null) {
                checkUnpackedTargetTypes(target, itemType, target);
            }
        }

        @RequiredReadAction
        private void checkUnpackedTargetTypes(PySequenceExpression targetSeq, PyType valueType, PsiElement highlight) {
            if (PyTypeChecker.containsAny(valueType, myTypeEvalContext)) {
                return;
            }
            for (PyExpression target : targetSeq.getElements()) {
                PyExpression leaf = PyPsiUtils.flattenParens(target);
                if (!(leaf instanceof PyTargetExpression leafTarget)) {
                    continue;
                }
                PyType annotatedType = resolvedDeclaredType(leafTarget);
                if (annotatedType == null) {
                    continue;
                }
                if (PyTypeChecker.containsAny(annotatedType, myTypeEvalContext)) {
                    continue;
                }
                PyType received;
                if (valueType instanceof PyTupleType tupleType && !tupleType.isHomogeneous()) {
                    received = PyTypeChecker.getTargetTypeFromTupleAssignment(leafTarget, targetSeq, tupleType, myTypeEvalContext);
                }
                else {
                    received = valueType instanceof PyClassType classType ? classType.getIteratedItemType() : null;
                }
                if (received == null) {
                    continue;
                }
                if (PyTypeChecker.containsAny(received, myTypeEvalContext)) {
                    continue;
                }
                PyType actual = PyLiteralType.upcastLiteralToClass(received);
                if (!PyTypeChecker.match(annotatedType, actual, myTypeEvalContext)) {
                    registerProblem(highlight,
                                    PyInspectionMessages.problemMessage(p -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(p[0], p[1]),
                                                                        CodifiedParam.ofType(annotatedType, highlight, myTypeEvalContext, true),
                                                                        CodifiedParam.ofType(actual, highlight, myTypeEvalContext)),
                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    // report only the first mismatch per value
                    return;
                }
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyWithStatement(PyWithStatement node) {
            for (PyWithItem withItem : node.getWithItems()) {
                checkContextManagerValue(withItem.getExpression(), node.isAsync());
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyReturnStatement(PyReturnStatement node) {
            ScopeOwner owner = ScopeUtil.getScopeOwner(node);
            if (owner instanceof PyFunction function) {
                if (hasExplicitType(function)) {
                    PyType expected = getExpectedReturnStatementType(function, myTypeEvalContext);
                    if (expected == null) {
                        return;
                    }

                    // We cannot just match annotated and inferred types, as we cannot promote inferred to Literal
                    PyExpression returnExpr = node.getExpression();
                    if (expected instanceof PyTypedDictType typedDictType) {
                        if (returnExpr != null && PyTypedDictType.isDictExpression(returnExpr, myTypeEvalContext)) {
                            reportTypedDictProblems(typedDictType, returnExpr);
                            return;
                        }
                    }

                    PyType actual;
                    if (returnExpr == null) {
                        PyType noneType = PyBuiltinCache.getInstance(node).getNoneType();
                        actual = noneType != null ? noneType : PyAnyType.getUnknown();
                    }
                    else {
                        actual = returnExpr.getType(myTypeEvalContext);
                    }
                    if (!matchesExpectedType(expected, actual, returnExpr, null)) {
                        if (returnExpr != null) {
                            PyType actualViaPromotion = tryPromotingType(returnExpr, expected);
                            if (matchesExpectedType(expected, actualViaPromotion, returnExpr, null)) {
                                return;
                            }
                        }

                        PsiElement anchor = returnExpr != null ? returnExpr : node;
                        PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_RETURN, anchor,
                                                            typeMismatchMessage(expected, actual, anchor),
                                                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                            new PyMakeFunctionReturnTypeQuickFix(function, myTypeEvalContext));
                    }
                }
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyYieldExpression(PyYieldExpression node) {
            ScopeOwner owner = ScopeUtil.getScopeOwner(node);
            if (!(owner instanceof PyFunction function)) {
                return;
            }

            if (node.isDelegating()) {
                visitDelegatingYieldExpression(node, function);
                return;
            }

            GeneratorTypeDescriptor annotatedGeneratorDesc = getGeneratorDescriptorFromAnnotation(function, node);
            if (annotatedGeneratorDesc == null) {
                return;
            }

            checkYieldType(annotatedGeneratorDesc.yieldType(), node, function);
        }

        @RequiredReadAction
        private void visitDelegatingYieldExpression(PyYieldExpression node, PyFunction function) {
            assert node.isDelegating();

            PyExpression yieldExpr = node.getExpression();
            if (yieldExpr == null) {
                return;
            }

            PyType delegateType = myTypeEvalContext.getType(yieldExpr);
            if (delegateType == null) {
                return;
            }

            GeneratorTypeDescriptor delegateDesc = GeneratorTypeDescriptor.fromGeneratorOrProtocol(delegateType, myTypeEvalContext);
            if (delegateDesc != null && delegateDesc.isAsync()) {
                PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_RETURN, yieldExpr,
                                                    PyInspectionMessages.problemMessage(
                                                        p -> PyLocalize.inspTypeCheckerYieldFromAsyncGenerator(p[0]),
                                                        CodifiedParam.ofType(delegateType, yieldExpr, myTypeEvalContext)));
                return;
            }

            if (checkIteratedValue(yieldExpr, false)) {
                return;
            }

            GeneratorTypeDescriptor annotatedGeneratorDesc = getGeneratorDescriptorFromAnnotation(function, node);
            if (annotatedGeneratorDesc == null) {
                return;
            }

            if (checkYieldType(annotatedGeneratorDesc.yieldType(), node, function)) {
                return;
            }

            // Reversed because SendType is contravariant
            PyType expectedSendType = annotatedGeneratorDesc.sendType();
            if (delegateDesc != null && !PyTypeChecker.match(delegateDesc.sendType(), expectedSendType, myTypeEvalContext)) {
                PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_RETURN, yieldExpr,
                                                    PyInspectionMessages.problemMessage(
                                                        p -> PyLocalize.inspTypeCheckerYieldFromSendTypeMismatch(p[0], p[1]),
                                                        CodifiedParam.ofType(expectedSendType, yieldExpr, myTypeEvalContext, true),
                                                        CodifiedParam.ofType(delegateDesc.sendType(), yieldExpr, myTypeEvalContext)));
            }
        }

        @RequiredReadAction
        private @Nullable GeneratorTypeDescriptor getGeneratorDescriptorFromAnnotation(PyFunction function, PyYieldExpression yieldExpr) {
            if (!hasExplicitType(function)) {
                return null;
            }

            PyType annotatedReturnType = myTypeEvalContext.getReturnType(function);
            if (annotatedReturnType == null) {
                return null;
            }

            GeneratorTypeDescriptor annotatedGeneratorDesc =
                GeneratorTypeDescriptor.fromGeneratorOrProtocol(annotatedReturnType, myTypeEvalContext);
            if (annotatedGeneratorDesc == null) {
                PyType inferredReturnType = function.getInferredReturnType(myTypeEvalContext);
                if (!PyTypeChecker.match(annotatedReturnType, inferredReturnType, myTypeEvalContext)) {
                    registerTypeMismatch(PyTypeCheckerSuppressionCode.BAD_RETURN, yieldExpr, annotatedReturnType, inferredReturnType,
                                         typeMismatchMessage(annotatedReturnType, inferredReturnType, yieldExpr),
                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                         new PyMakeFunctionReturnTypeQuickFix(function, myTypeEvalContext));
                }
                return null;
            }
            return annotatedGeneratorDesc;
        }

        @RequiredReadAction
        private boolean checkYieldType(@Nullable PyType expectedYieldType, PyYieldExpression node, PyFunction function) {
            PyType thisYieldType = node.getYieldType(myTypeEvalContext);
            if (!matchesExpectedType(expectedYieldType, thisYieldType, node.getExpression(), null)) {
                PyExpression yieldExpr = node.getExpression();
                PsiElement anchor = yieldExpr != null ? yieldExpr : node;
                registerTypeMismatch(PyTypeCheckerSuppressionCode.BAD_RETURN, anchor, expectedYieldType, thisYieldType,
                                     PyInspectionMessages.problemMessage(
                                         p -> PyLocalize.inspTypeCheckerYieldTypeMismatch(p[0], p[1]),
                                         CodifiedParam.ofType(expectedYieldType, anchor, myTypeEvalContext, true),
                                         CodifiedParam.ofType(thisYieldType, anchor, myTypeEvalContext)),
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                     new PyMakeFunctionReturnTypeQuickFix(function, myTypeEvalContext));
                return true;
            }
            return false;
        }

        @Override
        @RequiredReadAction
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            checkClassAttributeAccess(node);

            // Recompute the qualified reference type when it's `Unknown` to collect possible method binding errors.
            if (node.isQualified() &&
                PyTypeUtil.asUnionSequence(myTypeEvalContext.getType(node)).stream().anyMatch(PyTypeUtilKt::isUnknown)) {
                List<PyInspectionMessages.ProblemMessage> errors = new ArrayList<>();
                PyReferenceExpressionImpl.getQualifiedReferenceType(node, myTypeEvalContext, errors);
                for (PyInspectionMessages.ProblemMessage error : errors) {
                    registerProblem(node, error, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyAssignmentStatement(PyAssignmentStatement node) {
            PyExpression lhs = PyPsiUtils.flattenParens(node.getLeftHandSideExpression());
            PyExpression rhs = node.getAssignedValue();
            if ((!(lhs instanceof PyTupleExpression) && !(lhs instanceof PyListLiteralExpression)) || rhs == null) {
                return;
            }
            PySequenceExpression lhsSeq = (PySequenceExpression) lhs;

            // Check that the RHS is iterable
            if (checkUnpackIterableValue(rhs)) {
                return;
            }

            PyType rhsType = myTypeEvalContext.getType(rhs);
            if (!(rhsType instanceof PyTupleType rhsTupleType) || rhsTupleType.isHomogeneous()) {
                // Non-tuple/homogeneous iterable: the balance is unknown, so only validate the annotated targets' types.
                if (rhsType != null) {
                    checkUnpackedTargetTypes(lhsSeq, rhsType, rhs);
                }
                return;
            }

            PyExpression[] targets = lhsSeq.getElements();
            int lhsStarCount = (int) Arrays.stream(targets).filter(it -> it instanceof PyStarExpression).count();

            // The RHS value count. A starred RHS element contributes its operand's length only when that operand is a
            // statically known (heterogeneous) tuple; an unbounded operand (e.g. `*list_value`) makes the count
            // indeterminate, in which case the balance check is skipped.
            int rhsCount;
            if (rhs instanceof PyTupleExpression rhsTuple && Arrays.stream(rhsTuple.getElements()).anyMatch(it -> it instanceof PyStarExpression)) {
                rhsCount = getUnpackedTupleLength(rhsTuple);
            }
            else {
                rhsCount = rhsTupleType.getElementCount();
            }
            if (rhsCount >= 0 && checkUnpackBalance(targets.length, lhsStarCount, rhsCount, rhs, lhs,
                                                    CodifiedParam.ofType(rhsTupleType, rhs, myTypeEvalContext))) {
                return;
            }

            checkNestedUnpackingBalance(targets, rhsTupleType);

            // Per-element type mismatch check for annotated targets with a non-tuple RHS (`x, y = expr` / `[x] = expr`).
            // findAssignedValue() yields a synthetic subscription for these, so the mismatch is reported on the RHS itself.
            // A tuple RHS (`x, y = 1, 2` / `[x] = 1, 2`) maps to real value elements and is handled by
            // visitPyTargetExpression via findAssignedValue().
            if (lhsStarCount == 0 && !(rhs instanceof PyTupleExpression)) {
                for (PyExpression target : targets) {
                    if (!(target instanceof PyTargetExpression targetExpression)) {
                        continue;
                    }
                    if (!targetOrResolvedHasExplicitType(targetExpression)) {
                        continue;
                    }
                    PyType annotatedType = myTypeEvalContext.getType(targetExpression);
                    PyType unpackedType = PyTypeChecker.getTargetTypeFromTupleAssignment(targetExpression, lhsSeq, rhsTupleType, myTypeEvalContext);
                    if (unpackedType == null) {
                        continue;
                    }
                    if (PyTypeChecker.match(annotatedType, unpackedType, myTypeEvalContext)) {
                        continue;
                    }
                    PyType displayType = PyLiteralType.upcastLiteralToClass(unpackedType);
                    PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_ASSIGNMENT, rhs,
                                                        typeMismatchMessage(annotatedType, displayType, rhs),
                                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    // stop after the first error, because otherwise we might start reporting different type errors on the same element
                    return;
                }
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyStarExpression(PyStarExpression node) {
            PsiElement parent = node.getParent();
            if (parent instanceof PySequenceExpression) {
                // Skip star expressions that are assignment targets: `a, *b = ...`
                PsiElement possibleLhs = PsiTreeUtil.skipParentsOfType(parent, PyParenthesizedExpression.class);
                if (possibleLhs instanceof PyAssignmentStatement assignment &&
                    PyPsiUtils.flattenParens(assignment.getLeftHandSideExpression()) == parent) {
                    // Check type annotation compatibility for annotated star targets like `x: int; (*x,) = [1, 2, 3]`
                    PyExpression innerExpr = node.getExpression();
                    if (innerExpr instanceof PyTargetExpression innerTarget && targetOrResolvedHasExplicitType(innerTarget)) {
                        PyExpression rhs = assignment.getAssignedValue();
                        if (rhs != null) {
                            PyType rhsType = myTypeEvalContext.getType(rhs);
                            if (rhsType instanceof PyClassType rhsClassType && (rhsType instanceof PyTupleType || rhsClassType.isParameterized())) {
                                PyType elementType = PyLiteralType.upcastLiteralToClass(rhsClassType.getIteratedItemType());
                                PyClass listClass = PyBuiltinCache.getInstance(node).getClass("list");
                                if (listClass != null) {
                                    PyType actualType = new PyCollectionTypeImpl(listClass, false, Arrays.asList(elementType));
                                    PyType annotatedType = myTypeEvalContext.getType(innerTarget);
                                    if (annotatedType != null && !PyTypeChecker.isUnknown(annotatedType, myTypeEvalContext) &&
                                        !PyTypeChecker.match(annotatedType, actualType, myTypeEvalContext)) {
                                        PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_ASSIGNMENT, rhs,
                                                                            typeMismatchMessage(annotatedType, actualType, rhs),
                                                                            ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                                    }
                                }
                            }
                        }
                    }
                    return;
                }
                // Check iterability of starred operand in sequence literals `[*a], {*a}, (*a,)`
                // Skip type unpack expressions in type hints: [*tuple[T]], (*Ts,), etc.
                // TODO: consider Annotated[T, [*1]]
                if (PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext)) {
                    return;
                }
                checkUnpackIterableValue(node.getExpression());
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyDoubleStarExpression(PyDoubleStarExpression node) {
            // Check that **expr in dict literals ({**a}) has a mapping type
            if (node.getParent() instanceof PyDictLiteralExpression) {
                checkUnpackMappingValue(node.getExpression());
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyStarArgument(PyStarArgument node) {
            if (node.isKeyword()) {
                checkUnpackMappingValue(node.getExpression());
            }
            else {
                checkUnpackIterableValue(node.getExpression());
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyTargetExpression(PyTargetExpression node) {
            checkClassAttributeAccess(node);
            checkOverriddenAttributeType(node);
            PyExpression assignedValue = node.findAssignedValue();
            if (assignedValue == null) {
                return;
            }

            ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(node);
            if (scopeOwner instanceof PyClass scopeClass && PyStdlibTypeProvider.isCustomEnum(scopeClass, myTypeEvalContext)) {
                PyStdlibTypeProvider.EnumAttributeInfo info = PyStdlibTypeProvider.getEnumAttributeInfo(scopeClass, node, myTypeEvalContext);
                if (info == null || info.attributeKind() != PyStdlibTypeProvider.EnumAttributeKind.MEMBER) {
                    return;
                }
                if (PyStdlibTypeProvider.hasCustomMemberConstruction(scopeClass, myTypeEvalContext)) {
                    return;
                }

                PyType expected = PyStdlibTypeProvider.getEnumValueType(scopeClass, myTypeEvalContext);
                // `assignedValueType` is the member value type produced by the enum's metaclass/constructor. For enums that
                // transform the declaration (e.g. Django's `ChoicesType.__new__` drops a trailing label), the type provider
                // already stripped the extra elements, so a plain match here is correct for both stdlib and framework enums.
                PyType actual = info.assignedValueType();
                if (!PyTypeChecker.match(expected, actual, myTypeEvalContext)) {
                    registerTypeMismatch(PyTypeCheckerSuppressionCode.BAD_ASSIGNMENT, assignedValue, expected, actual,
                                         typeMismatchMessage(expected, actual, assignedValue),
                                         ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                }
                return;
            }

            // We don't report type errors on non-annotated assignments inside class bodies because there
            // the expected attribute type is either:
            // - just the type of the assigned value, so there is nothing to type check against
            // - special-cased for some metaprogramming API, e.g., Django models,
            //    so the type provided by a dedicated PyTypeProvider intentionally differs from the type of the assigned value
            //    (e.g. str instead of TextField), then type checking it normally will cause a false positive.
            if (scopeOwner instanceof PyClass && !targetOrResolvedHasExplicitType(node)) {
                return;
            }

            // `T = typing.NewType('T', int)`
            if (!node.isQualified() && assignedValue instanceof PyCallExpression assignedCall) {
                PyExpression callee = assignedCall.getCallee();
                PyType calleeType = callee != null ? myTypeEvalContext.getType(callee) : null;
                if (calleeType instanceof PyClassLikeType classLikeType &&
                    PyTypingTypeProvider.NEW_TYPE.equals(classLikeType.getClassQName())) {
                    return;
                }
            }

            PyType converterInputType = getConverterInputType(node);
            PyType expected = converterInputType != null ? converterInputType : myTypeEvalContext.getType(node);
            PyExpression qualifier = node.getQualifier();
            if (qualifier != null) {
                PyType unsubstitutedExpected = expected;
                expected = PyTypeUtil.compositeMap(myTypeEvalContext.getType(qualifier), it -> {
                    GenericSubstitutions substitutions = PyTypeChecker.unifyReceiver(it, myTypeEvalContext);
                    return PyTypeChecker.substitute(unsubstitutedExpected, substitutions, myTypeEvalContext);
                });
            }

            boolean isDescriptor = false;

            Ref<PyType> classAttrType = getClassAttributeType(node);
            if (classAttrType != null) {
                Ref<PyType> dunderSetValueType =
                    PyDescriptorTypeUtil.getExpectedValueTypeForDunderSet(node, classAttrType.get(), myTypeEvalContext);
                if (dunderSetValueType != null) {
                    expected = dunderSetValueType.get();
                    isDescriptor = true;
                }
            }

            if (expected instanceof PyTypedDictType typedDictType && PyTypedDictType.isDictExpression(assignedValue, myTypeEvalContext)) {
                reportTypedDictProblems(typedDictType, assignedValue);
                return;
            }

            PyType actual = myTypeEvalContext.getType(assignedValue);

            if (expected instanceof PySentinelType) {
                if (PyTypeUtilKt.isObject(actual)) {
                    return;
                }
            }

            if (!matchesExpectedType(expected, actual, assignedValue, null)) {
                // `tryPromotingType` may preserve element literals in a collection value (e.g. `list[Literal[1]]`),
                // which spuriously fails the invariant match against the value's own widened type (`list[int]`),
                // for instance for an un-annotated `x = [1, [1]]`. If the value's natural (un-promoted) type already
                // matches the expected type, the fresh literal is assignable and there is no real mismatch.
                // temporary special casing to avoid Literal problems PY-90366
                PyType promotedType = tryPromotingType(assignedValue, expected);
                if (!Objects.equals(promotedType, actual) && matchesExpectedType(expected, promotedType, assignedValue, null)) {
                    // fallback/promotion approach succeeded
                    return;
                }
                PyInspectionMessages.ProblemMessage message;
                if (isDescriptor) {
                    message = typeMismatchMessage(expected, actual, assignedValue,
                                                  p -> PyLocalize.inspTypeCheckerExpectedTypeFromDunderSetGotTypeInstead(p[0], p[1]));
                }
                else {
                    message = typeMismatchMessage(expected, actual, assignedValue);
                }
                registerTypeMismatch(PyTypeCheckerSuppressionCode.BAD_ASSIGNMENT, assignedValue, expected, actual, message,
                                     ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        }

        private PyInspectionMessages.ProblemMessage typeMismatchMessage(@Nullable PyType expected,
                                                                        @Nullable PyType actual,
                                                                        PsiElement anchor) {
            return typeMismatchMessage(expected, actual, anchor, p -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(p[0], p[1]));
        }

        private PyInspectionMessages.ProblemMessage typeMismatchMessage(@Nullable PyType expected,
                                                                        @Nullable PyType actual,
                                                                        PsiElement anchor,
                                                                        Function<Object[], LocalizeValue> messageKey) {
            PyInspectionMessages.ProblemMessage base = PyInspectionMessages.problemMessage(
                messageKey,
                CodifiedParam.ofType(expected, anchor, myTypeEvalContext, true),
                CodifiedParam.ofType(actual, anchor, myTypeEvalContext)
            );
            String diff = PyTypeDiff.diffTooltip(expected, actual, myTypeEvalContext);
            return diff != null ? new PyInspectionMessages.ProblemMessage(base.description(), diff) : base;
        }

        /**
         * Registers a type-mismatch problem. On-the-fly the editor-hover tooltip is the {@code message}'s own tooltip — the
         * aligned {@link PyTypeDiff} for structural mismatches — with the breakdown ({@link PyTypeChecker#explainMismatch}) appended
         * below it. The one-line {@code message} description stays flat, so batch results and existing golden tests are
         * unaffected; only the editor hover shows the diff and breakdown.
         */
        @RequiredReadAction
        private void registerTypeMismatch(PyTypeCheckerSuppressionCode code,
                                          @Nullable PsiElement element,
                                          @Nullable PyType expected,
                                          @Nullable PyType actual,
                                          PyInspectionMessages.ProblemMessage message,
                                          ProblemHighlightType type,
                                          LocalQuickFix... fixes) {
            // Description stays the plain one-liner; on-the-fly the tooltip is the rich breakdown (built from the
            // enriched [message]). reportWithTooltip runs the supplier only on-the-fly, since it re-runs the match,
            // and tags the problem with [code] so it can be suppressed per-category.
            PyTypeCheckerProblemReporter.reportWithTooltip(holder, code, element, message, type, fixes,
                                                           () -> PyTypeCheckerInspectionProblemRegistrar.breakdownTooltip(message, expected, actual, myTypeEvalContext, element));
        }

        private boolean matchesExpectedType(@Nullable PyType expected,
                                            @Nullable PyType actual,
                                            @Nullable PyExpression expression,
                                            @Nullable GenericSubstitutions substitutions) {
            boolean matches = substitutions == null
                ? PyTypeChecker.match(expected, actual, myTypeEvalContext)
                : PyTypeChecker.match(expected, actual, myTypeEvalContext, substitutions);
            if (matches) {
                return true;
            }
            return isCovariantMatchTempFix(expected, actual, expression, substitutions);
        }

        /**
         * The failing subtype check could be due to respecting variance.
         * However, the underlying reason is that the {@code actual} type was not correctly inferred.
         * As a temporary solution, we mimic a covariant subtype check. (TODO PY-89564)
         */
        private boolean isCovariantMatchTempFix(@Nullable PyType expected,
                                                @Nullable PyType actual,
                                                @Nullable PyExpression expExpr,
                                                @Nullable GenericSubstitutions substitutions) {
            // Answer this before substituting the types: a non-creational expression can only end in `false`.
            boolean isCreational = expExpr instanceof PySequenceExpression
                || expExpr instanceof PyCallExpression call && !(call.getCallee() instanceof PySubscriptionExpression)
                || expExpr instanceof PyParenthesizedExpression parenthesized && parenthesized.getContainedExpression() instanceof PyTupleExpression;
            if (!isCreational) {
                return false;
            }

            PyType expectedSubst = substitutions == null ? expected : PyTypeChecker.substitute(expected, substitutions, myTypeEvalContext);
            PyType actualSubst = substitutions == null ? actual : PyTypeChecker.substitute(actual, substitutions, myTypeEvalContext);
            if (expectedSubst instanceof PyClassType expectedClassType && expectedClassType.isParameterized() &&
                actualSubst instanceof PyClassType actualClassType && actualClassType.isParameterized()) {
                PyType expClassType = expectedClassType.getPyClass().getType(myTypeEvalContext);
                PyType actClassType = actualClassType.getPyClass().getType(myTypeEvalContext);
                PyTypeParameterMapping paramMapping = PyTypeParameterMapping.mapByShape(
                    expectedClassType.getTypeArguments(),
                    actualClassType.getTypeArguments(),
                    PyTypeParameterMapping.Option.USE_DEFAULTS
                );
                if (paramMapping != null && PyTypeChecker.match(expClassType, actClassType, myTypeEvalContext)) {
                    boolean allElementsMatch = true;
                    for (int i = 0; i < paramMapping.getMappedTypes().size(); i++) {
                        Pair<PyType, PyType> couple = paramMapping.getMappedTypes().get(i);
                        PyType expET = couple.getFirst();
                        PyType actET = couple.getSecond();
                        if (actET instanceof PyUnpackedTupleType unpackedTupleType && unpackedTupleType.isUnbound()) {
                            actET = unpackedTupleType.getElementTypes().get(0);
                        }
                        if (!PyTypeChecker.match(expET, actET, myTypeEvalContext) && !isCovariantMatchTempFix(expET, actET, expExpr, substitutions)) {
                            allElementsMatch = false;
                            break;
                        }
                    }
                    if (allElementsMatch) {
                        return true;
                    }
                }
            }
            return false;
        }

        // Using generic classes (parameterized or not) to access attributes will result in type check failure.
        @RequiredReadAction
        private <T extends PyQualifiedExpression & PyReferenceOwner> void checkClassAttributeAccess(T expression) {
            PyExpression qualifier = expression.getQualifier();
            if (qualifier != null) {
                PyType qualifierType = myTypeEvalContext.getType(qualifier);
                if (qualifierType instanceof PyClassType classType && classType.isDefinition()) {
                    PsiElement resolved = expression.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).resolve();
                    if (resolved instanceof PyTargetExpression resolvedTarget && PyUtil.isClassAttribute(resolvedTarget)) {
                        PyType targetType = myTypeEvalContext.getType(resolvedTarget);
                        if (requiresTypeSpecialization(targetType)) {
                            ASTNode nameElement = expression.getNameElement();
                            registerProblem(
                                nameElement != null ? nameElement.getPsi() : null,
                                PyLocalize.inspTypeCheckerAccessToGenericInstanceVariablesViaClassIsAmbiguous().get()
                            );
                        }
                    }
                }
            }
        }

        /**
         * The converter input type when {@code attribute} is a {@code dataclass_transform} field with a {@code converter}, accessed on an instance.
         * An assignment on the class does not run the converter.
         */
        @RequiredReadAction
        private <T extends PyQualifiedExpression & PyReferenceOwner> @Nullable PyType getConverterInputType(T attribute) {
            PyExpression qualifier = attribute.getQualifier();
            if (qualifier == null) {
                return null;
            }
            if (myTypeEvalContext.getType(qualifier) instanceof PyClassLikeType classLikeType && classLikeType.isDefinition()) {
                return null;
            }
            if (!(attribute.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).resolve() instanceof PyTargetExpression resolved)) {
                return null;
            }
            // A reference can resolve to an assignment `self.field = ...` in a method. The converter is on the class-level declaration.
            PyTargetExpression field;
            if (resolved.isQualified()) {
                String fieldName = resolved.getName();
                if (fieldName == null) {
                    return null;
                }
                PyClass containingClass = resolved.getContainingClass();
                field = containingClass != null ? containingClass.findClassAttribute(fieldName, true, myTypeEvalContext) : null;
                if (field == null) {
                    return null;
                }
            }
            else {
                field = resolved;
            }
            return PyDataclassTransformResolver.INSTANCE.getConverterInputType(field, myTypeEvalContext);
        }

        /**
         * Checks the type of a class attribute that overrides an attribute of the nearest ancestor class.
         * An annotated override must have the inherited type, because a mutable attribute is invariant.
         * An annotated override of a read-only attribute, such as a frozen dataclass field, can have a subtype of the inherited type.
         * A NamedTuple field is read-only, but the subclass keeps the constructor of the NamedTuple, so the field stays invariant.
         * The value of an unannotated override must be assignable to the inherited type.
         * Without {@code checkInferredAttributeOverrides}, skips an override of an inherited attribute without a type annotation.
         */
        @RequiredReadAction
        private void checkOverriddenAttributeType(PyTargetExpression node) {
            if (node.isQualified() || !PyUtil.isClassAttribute(node)) {
                return;
            }
            if (!(ScopeUtil.getScopeOwner(node) instanceof PyClass ownerClass)) {
                return;
            }
            String name = node.getName();
            if (name == null) {
                return;
            }
            PyType ownDeclaredType = resolvedDeclaredType(node);

            for (PyClass ancestor : ownerClass.getAncestorClasses(myTypeEvalContext)) {
                PyTargetExpression inherited = ancestor.findClassAttribute(name, false, myTypeEvalContext);
                if (inherited == null) {
                    continue;
                }
                if (inherited == node) {
                    continue;
                }
                // PyFinalInspection reports an override of a `Final` attribute.
                if (PyTypingTypeProvider.isFinal(inherited, myTypeEvalContext)) {
                    return;
                }
                PyType inheritedDeclaredType = declaredTypeFromStub(inherited);
                if (inheritedDeclaredType == null && !checkInferredAttributeOverrides) {
                    return;
                }
                PyType inheritedType = specializeForClass(ownerClass, inheritedDeclaredType != null
                    ? inheritedDeclaredType
                    : PyLiteralType.upcastLiteralToClass(myTypeEvalContext.getType(inherited)));

                if (ownDeclaredType != null) {
                    Function<Object[], LocalizeValue> invarianceMessageKey;
                    if (PyNamedTupleTypeProvider.Helper.isNamedTupleField(inherited, myTypeEvalContext)) {
                        invarianceMessageKey = p -> PyLocalize.inspTypeCheckerOverriddenNamedTupleFieldTypeNotInvariant(p[0], p[1]);
                    }
                    else if (PyTypeUtil.isReadOnlyAttribute(inherited, myTypeEvalContext)) {
                        invarianceMessageKey = null;
                    }
                    else {
                        invarianceMessageKey = p -> PyLocalize.inspTypeCheckerOverriddenAttributeTypeNotInvariant(p[0], p[1]);
                    }
                    checkReannotatedOverride(node, inheritedType, ownDeclaredType, invarianceMessageKey);
                }
                else {
                    checkUnannotatedOverrideValue(node, inheritedType);
                }
                return;
            }
        }

        /**
         * Returns the declared type of {@code attribute}.
         * The attribute can be in another file, so this function reads the annotation only from the stub.
         */
        private @Nullable PyType declaredTypeFromStub(PyTargetExpression attribute) {
            if (attribute.getAnnotationValue() == null && attribute.getTypeCommentAnnotation() == null) {
                return null;
            }
            return myTypeEvalContext.getType(attribute);
        }

        /**
         * Replaces the type parameters of the ancestor classes in {@code memberType} with the type arguments that {@code ownerClass} gives them.
         */
        private @Nullable PyType specializeForClass(PyClass ownerClass, @Nullable PyType memberType) {
            if (!(myTypeEvalContext.getType(ownerClass) instanceof PyClassType ownerClassType)) {
                return memberType;
            }
            PyClassType ownerType = ownerClassType.toInstance();
            return PyTypeUtil.specializeMemberType(ownerType, ownerType, memberType, myTypeEvalContext);
        }

        /**
         * Reports an annotated override with a type other than {@code inheritedType}.
         * {@code invarianceMessageKey} reports a subtype override of an invariant attribute.
         * If it is null or with {@code allowCovariantAttributeOverrides}, reports only an override type that is not a subtype of {@code inheritedType}.
         */
        @RequiredReadAction
        private void checkReannotatedOverride(PyTargetExpression node,
                                              @Nullable PyType inheritedType,
                                              @Nullable PyType ownType,
                                              @Nullable Function<Object[], LocalizeValue> invarianceMessageKey) {
            boolean subtypeOfBase = PyTypeChecker.match(inheritedType, ownType, myTypeEvalContext);
            boolean compatible = allowCovariantAttributeOverrides || invarianceMessageKey == null
                ? subtypeOfBase
                : subtypeOfBase && PyTypeChecker.match(ownType, inheritedType, myTypeEvalContext);
            if (compatible) {
                return;
            }
            Function<Object[], LocalizeValue> messageKey;
            if (subtypeOfBase) {
                if (invarianceMessageKey == null) {
                    return;
                }
                messageKey = invarianceMessageKey;
            }
            else {
                messageKey = p -> PyLocalize.inspTypeCheckerOverriddenAttributeTypeIncompatible(p[0], p[1]);
            }
            PyAnnotation annotation = node.getAnnotation();
            PyExpression annotationValue = annotation != null ? annotation.getValue() : null;
            PsiElement anchor = annotationValue != null ? annotationValue : node;
            registerTypeMismatch(
                PyTypeCheckerSuppressionCode.BAD_ASSIGNMENT, anchor, inheritedType, ownType,
                typeMismatchMessage(inheritedType, ownType, anchor, messageKey),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            );
        }

        /**
         * Reports an unannotated override with a value that is not assignable to {@code inheritedType}.
         */
        @RequiredReadAction
        private void checkUnannotatedOverrideValue(PyTargetExpression node, @Nullable PyType inheritedType) {
            PyExpression assignedValue = node.findAssignedValue();
            if (assignedValue == null) {
                return;
            }
            PyType valueType = PyLiteralType.upcastLiteralToClass(myTypeEvalContext.getType(assignedValue));
            if (PyTypeChecker.match(inheritedType, valueType, myTypeEvalContext)) {
                return;
            }
            registerTypeMismatch(
                PyTypeCheckerSuppressionCode.BAD_ASSIGNMENT, assignedValue, inheritedType, valueType,
                typeMismatchMessage(inheritedType, valueType, assignedValue),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            );
        }

        private <T extends PyQualifiedExpression & PyReferenceOwner> @Nullable Ref<PyType> getClassAttributeType(T attribute) {
            if (!attribute.isQualified()) {
                return null;
            }
            PsiElement definition = attribute.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).resolve();
            if (!(definition instanceof PyTargetExpression definitionTarget && PyUtil.isAttribute(definitionTarget))) {
                return null;
            }
            return Ref.create(myTypeEvalContext.getType(definitionTarget));
        }

        @RequiredReadAction
        private void reportTypedDictProblems(PyTypedDictType expectedType, PyExpression expression) {
            PyTypedDictType.TypeCheckingResult result = new PyTypedDictType.TypeCheckingResult();
            PyTypedDictType.checkExpression(expectedType, expression, myTypeEvalContext, result);
            for (PyTypedDictType.ValueTypeError error : result.getValueTypeErrors()) {
                PyExpression actualExpression = error.actualExpression();
                if (actualExpression == null) {
                    continue;
                }
                typedDictProblemReporter.report(
                    holder,
                    PyTypeCheckerSuppressionCode.BAD_TYPED_DICT,
                    actualExpression,
                    typeMismatchMessage(error.expectedType(), error.actualType(), actualExpression),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                );
            }
            for (PyTypedDictType.ExtraKeyError error : result.getExtraKeys()) {
                typedDictProblemReporter.report(
                    holder,
                    PyTypeCheckerSuppressionCode.BAD_TYPED_DICT_KEY,
                    error.actualExpression() != null ? error.actualExpression() : expression,
                    PyInspectionMessages.problemMessage(p -> PyLocalize.inspTypeCheckerTypedDictExtraKey(p[0], p[1]),
                                                        error.key(), error.expectedTypedDictName())
                );
            }
            for (PyTypedDictType.MissingKeysError error : result.getMissingKeys()) {
                typedDictProblemReporter.report(
                    holder,
                    PyTypeCheckerSuppressionCode.BAD_TYPED_DICT,
                    error.actualExpression() != null ? error.actualExpression() : expression,
                    PyInspectionMessages.problemMessage(
                        p -> PyLocalize.inspTypeCheckerTypedDictMissingKeys(p[0], p[1], p[2]), error.expectedTypedDictName(),
                        error.missingKeys().size(),
                        CodifiedParam.joinNames(error.missingKeys())
                    )
                );
            }
        }

        @RequiredReadAction
        private void reportUnpackedTypedDictProblems(PyUnpackedTypedDictType expectedType, PyExpression expression) {
            PyType argumentType = myTypeEvalContext.getType(expression);
            PyTypedDictType typedDictType = expectedType.getTypedDictType();
            if (PyTypedDictType.isDictExpression(expression, myTypeEvalContext)) {
                reportTypedDictProblems(typedDictType, expression);
                return;
            }
            if (!PyTypeChecker.match(typedDictType, argumentType, myTypeEvalContext)) {
                typedDictProblemReporter.report(
                    holder,
                    PyTypeCheckerSuppressionCode.BAD_ARGUMENT_TYPE,
                    expression,
                    PyInspectionMessages.problemMessage(
                        p -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(p[0], p[1]),
                        CodifiedParam.ofType(typedDictType, expression, myTypeEvalContext),
                        CodifiedParam.ofType(argumentType, expression, myTypeEvalContext)
                    )
                );
            }
        }

        private @Nullable PyType tryPromotingType(PyExpression expr, @Nullable PyType expected) {
            PyType promotedToLiteral = PyLiteralType.promoteToLiteral(expr, expected, myTypeEvalContext, null);
            if (!PyTypeChecker.isUnknown(promotedToLiteral, myTypeEvalContext)) {
                return promotedToLiteral;
            }
            return myTypeEvalContext.getType(expr);
        }

        @Override
        @RequiredReadAction
        public void visitPyFunction(PyFunction node) {
            if (hasExplicitType(node)) {
                PyAnnotation annotation = node.getAnnotation();
                PyType expected = getExpectedReturnStatementType(node, myTypeEvalContext);
                PyType builtinNoneType = PyBuiltinCache.getInstance(node).getNoneType();
                PyType noneType = builtinNoneType != null ? builtinNoneType : PyAnyType.getUnknown();
                boolean returnsNone = PyNoneTypeKt.isNoneType(expected);
                boolean returnsOptional = PyTypeChecker.match(expected, noneType, myTypeEvalContext);

                if (expected != null && !returnsOptional && !PyUtil.isEmptyFunction(node)) {
                    List<PyStatement> returnPoints = node.getReturnPoints(myTypeEvalContext);
                    boolean hasImplicitReturns = returnPoints.stream().anyMatch(it -> !(it instanceof PyReturnStatement));

                    if (hasImplicitReturns) {
                        PyType actual = node.getReturnStatementType(myTypeEvalContext);
                        PsiElement annotationValue = annotation != null ? annotation.getValue() : node.getTypeComment();
                        if (annotationValue != null) {
                            PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_RETURN, annotationValue,
                                                                typeMismatchMessage(expected, actual, annotationValue),
                                                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                new PyMakeFunctionReturnTypeQuickFix(node, myTypeEvalContext));
                        }
                    }
                }

                PyType annotatedType = myTypeEvalContext.getReturnType(node);

                if (PyUtil.isInitMethod(node) && !(returnsNone || annotatedType instanceof PyNeverType)) {
                    PyTypeCheckerProblemReporter.report(
                        holder,
                        PyTypeCheckerSuppressionCode.BAD_RETURN,
                        annotation != null ? annotation.getValue() : node.getTypeComment(),
                        PyLocalize.inspTypeCheckerInitShouldReturnNone().get()
                    );
                }

                if (node.isGenerator()) {
                    GeneratorTypeDescriptor generatorDesc = GeneratorTypeDescriptor.fromGeneratorOrProtocol(annotatedType, myTypeEvalContext);
                    boolean shouldBeAsync = node.isAsync() && node.isAsyncAllowed();
                    boolean wrongSyncAsync = generatorDesc != null && generatorDesc.isAsync() != shouldBeAsync;

                    PyType inferredType = node.getInferredReturnType(myTypeEvalContext);
                    if (wrongSyncAsync || (generatorDesc == null && !PyTypeChecker.match(annotatedType, inferredType, myTypeEvalContext))) {
                        PsiElement annotationValue = annotation != null ? annotation.getValue() : node.getTypeComment();
                        if (annotationValue != null) {
                            PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_RETURN, annotationValue,
                                                                typeMismatchMessage(inferredType, annotatedType, annotationValue),
                                                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                                                new PyMakeFunctionReturnTypeQuickFix(node, myTypeEvalContext));
                        }
                    }
                }
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyNamedParameter(PyNamedParameter node) {
            PyExpression defaultValue = PyPsiUtils.flattenParens(node.getDefaultValue());
            if (defaultValue == null) {
                return;
            }

            if (defaultValue instanceof PyEllipsisLiteralExpression && (isProtocolMethodParameter(node) || isOverloadSignature(node))) {
                return;
            }

            // we use `PyTypingTypeProvider.getType` of the annotation directly, instead of `node.getType`,
            //  because otherwise `PyTypingTypeProvider` will inject the type of `None`
            Ref<PyType> expectedRef;
            if (hasExplicitType(node)) {
                PyAnnotation annotation = node.getAnnotation();
                PyExpression annotationValue = annotation != null ? annotation.getValue() : null;
                if (annotationValue == null) {
                    return;
                }
                expectedRef = PyTypingTypeProvider.getType(annotationValue, myTypeEvalContext);
                if (expectedRef == null) {
                    return;
                }
            }
            else {
                expectedRef = findInheritedParameterAnnotationType(node);
                if (expectedRef == null) {
                    return;
                }
            }
            PyType expected = expectedRef.get();
            PyType actual = myTypeEvalContext.getType(defaultValue);

            if (actual instanceof PySentinelType) {
                return;
            }

            if (!matchesExpectedType(expected, actual, defaultValue, null)) {
                PyType promotedType = tryPromotingType(defaultValue, expected);
                if (!Objects.equals(promotedType, actual) && matchesExpectedType(expected, promotedType, defaultValue, null)) {
                    return; // fallback/promotion approach succeeded
                }
                PyTypeCheckerProblemReporter.report(
                    holder,
                    PyTypeCheckerSuppressionCode.BAD_ASSIGNMENT,
                    defaultValue, typeMismatchMessage(expected, actual, defaultValue),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                );
            }
        }

        private @Nullable Ref<PyType> findInheritedParameterAnnotationType(PyNamedParameter node) {
            String paramName = node.getName();
            if (paramName == null) {
                return null;
            }
            if (!(node.getParent() instanceof PyParameterList parameterList)) {
                return null;
            }
            if (!(parameterList.getContainingCallable() instanceof PyFunction function)) {
                return null;
            }
            if (function.getContainingClass() == null) {
                return null;
            }

            List<PyFunction> superFunctions = new ArrayList<>();
            for (PsiElement superMethod : PySuperMethodsSearch.search(function, true, myTypeEvalContext).findAll()) {
                if (superMethod instanceof PyFunction superFunction) {
                    superFunctions.add(superFunction);
                }
            }

            for (PyFunction superFunction : superFunctions) {
                PyNamedParameter superParameter = null;
                for (PyParameter parameter : superFunction.getParameterList().getParameters()) {
                    if (parameter instanceof PyNamedParameter namedParameter && paramName.equals(namedParameter.getName())) {
                        superParameter = namedParameter;
                        break;
                    }
                }
                if (superParameter == null) {
                    continue;
                }
                if (!hasExplicitType(superParameter)) {
                    continue;
                }
                PyAnnotation annotation = superParameter.getAnnotation();
                PyExpression annotationValue = annotation != null ? annotation.getValue() : null;
                if (annotationValue == null) {
                    continue;
                }
                Ref<PyType> ref = PyTypingTypeProvider.getType(annotationValue, myTypeEvalContext);
                if (ref == null) {
                    continue;
                }
                return ref;
            }
            return null;
        }

        private boolean isProtocolMethodParameter(PyNamedParameter node) {
            PsiElement parent = node.getContext();
            if (parent instanceof PyParameterList parameterList) {
                PyCallable containingCallable = parameterList.getContainingCallable();
                if (containingCallable instanceof PyFunction function) {
                    PyClass containingClass = function.getContainingClass();
                    if (containingClass == null) {
                        return false;
                    }
                    PyType classType = myTypeEvalContext.getType(containingClass);
                    if (classType instanceof PyClassLikeType classLikeType && PyProtocolsKt.isProtocol(classLikeType, myTypeEvalContext)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private boolean isOverloadSignature(PyNamedParameter node) {
            PsiElement parent = node.getParent();
            if (parent instanceof PyParameterList parameterList) {
                PyCallable containingCallable = parameterList.getContainingCallable();
                if (containingCallable instanceof PyFunction function) {
                    return PyiUtil.isOverload(function, myTypeEvalContext);
                }
            }
            return false;
        }

        @Override
        @RequiredReadAction
        public void visitPyComprehensionElement(PyComprehensionElement node) {
            super.visitPyComprehensionElement(node);

            for (PyComprehensionForComponent forComponent : node.getForComponents()) {
                checkIteratedValue(forComponent.getIteratedList(), forComponent.isAsync());
            }
        }

        @RequiredReadAction
        private void checkCallSite(PyElement callSite) {
            if (callSite instanceof PyCallExpression callExpression) {
                PyExpression callee = callExpression.getCallee();
                if (callee == null) {
                    return;
                }
                // Check constructor call self argument type
                PyType calleeType = myTypeEvalContext.getType(callee);
                if (calleeType instanceof PyClassType classType && classType.isDefinition()) {
                    List<PyInspectionMessages.ProblemMessage> errors = new ArrayList<>();
                    PyType constructorType = PyCallExpressionHelper.createCallableFromClass(classType, getResolveContext(), errors);
                    if (PyTypeUtilKt.isUnknown(constructorType)) {
                        for (PyInspectionMessages.ProblemMessage error : errors) {
                            registerProblem(callSite, error, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                        }
                        return;
                    }
                }

                List<PyType> calleeTypeComponents = PyTypeUtil.getCompositeComponents(
                    PyCallExpressionHelper.getCalleeType(callee, getResolveContext())
                );

                List<List<PyArgumentsMapping>> argumentMappingsPerCallee = new ArrayList<>();
                for (PyType componentType : calleeTypeComponents) {
                    List<PyCallableType> callableItems = PyTypeUtil.getCallableItems(componentType);
                    if (callableItems.isEmpty()) {
                        continue;
                    }

                    List<PyArgumentsMapping> mappings = new ArrayList<>();
                    for (PyCallableType callable : callableItems) {
                        mappings.add(PyCallExpressionHelper.mapArguments(callExpression, callable, myTypeEvalContext));
                    }
                    argumentMappingsPerCallee.add(mappings);
                }

                // Calling a value of a union type is valid only if *every* member is callable and accepts the arguments
                // (a member that is not callable at all is reported by PyCallingNonCallableInspection). This differs from an
                // overloaded callable (a `PyOverloadType`, not a `PyUnionType`), for which matching *any* overload is enough.
                // An intersection callee needs the overload-set rule, not this one, which PY-90282 tracks.
                for (List<PyArgumentsMapping> mappings : argumentMappingsPerCallee) {
                    if (reportArgumentTypeMismatch(callSite, mappings)) {
                        return;
                    }
                }
            }
            else if (callSite instanceof PyQualifiedElement qualifiedElement) {
                List<ResolvedOperator> resolvedOperators = PyCallExpressionHelper.multiResolveOperator(qualifiedElement, getResolveContext());
                List<PyCalleeResults> analyzedCallees = analyzeOperatorCallees(callSite, resolvedOperators);

                if (reportStrictUnionOperatorArgumentMismatch(callSite, analyzedCallees)) {
                    return;
                }
                // Operator candidates are all arity-complete (see [analyzeOperatorCallees]), so there is no near miss to add.
                List<PyCalleeResults> callees = analyzedCallees != null ? analyzedCallees : List.of();
                reportIfNoCalleeMatches(callSite,
                                        callees.stream().map(it -> Pair.create(it.getMapping(), it.getCalleeResults())).toList(),
                                        callees.stream().map(PyCalleeResults::getMapping).toList());
            }
            else {
                reportArgumentTypeMismatch(callSite, PyCallExpressionHelper.mapArguments(callSite, getResolveContext()));
            }
        }

        private @Nullable List<PyCalleeResults> analyzeOperatorCallees(PyElement callSite, List<ResolvedOperator> operators) {
            if (operators.isEmpty()) {
                return null;
            }

            List<PyCalleeResults> analyzedCallees = new ArrayList<>();
            for (ResolvedOperator operator : operators) {
                PyType selfType = operator.selfType();
                PyType method = operator.method();
                List<PyExpression> arguments = operator.arguments();
                for (PyCallableType callable : PyTypeUtil.getCallableItems(method)) {
                    PyArgumentsMapping mapping = PyCallExpressionHelper.mapArguments(callSite, arguments, callable, myTypeEvalContext);
                    if (mapping.isComplete()) {
                        AnalyzeCalleeResults analysis = analyzeCallee(mapping);
                        if (analysis == null) {
                            continue;
                        }
                        analyzedCallees.add(new PyCalleeResults(selfType, analysis, mapping));
                    }
                }
            }

            return analyzedCallees;
        }

        @RequiredReadAction
        private boolean reportStrictUnionOperatorArgumentMismatch(PyElement callSite, @Nullable List<PyCalleeResults> analyzedCallees) {
            if (!PyUnionType.isStrictSemanticsEnabled()) {
                return false;
            }
            if (!(callSite instanceof PyQualifiedElement qualifiedElement)) {
                return false;
            }
            if (PyTypingTypeProvider.isInsideTypeHint(callSite, myTypeEvalContext)) {
                return false;
            }

            String operatorName = qualifiedElement.getReferencedName();
            if (operatorName == null) {
                return false;
            }
            PyExpression lhs;
            PyExpression rhs;
            if (callSite instanceof PyBinaryExpression binaryExpression) {
                lhs = binaryExpression.getLeftExpression();
                rhs = binaryExpression.getRightExpression();
            }
            else if (callSite instanceof PyAugAssignmentStatement augAssignment) {
                lhs = augAssignment.getTarget();
                rhs = augAssignment.getValue();
            }
            else {
                return false;
            }

            PyType lhsType = lhs != null ? myTypeEvalContext.getType(lhs) : null;
            PyType rhsType = rhs != null ? myTypeEvalContext.getType(rhs) : null;

            if (!(lhsType instanceof PyUnionType) && !(rhsType instanceof PyUnionType)) {
                return false;
            }

            List<PyType> leftTypes = lhsType == null ? List.of() : strictUnionSequence(lhsType).stream()
                .filter(Objects::nonNull)
                .filter(it -> !PyTypeChecker.containsAny(it, myTypeEvalContext))
                .toList();
            List<PyType> rightTypes = rhsType == null ? List.of() : strictUnionSequence(rhsType).stream()
                .filter(Objects::nonNull)
                .filter(it -> !PyTypeChecker.containsAny(it, myTypeEvalContext))
                .toList();
            if (leftTypes.isEmpty() || rightTypes.isEmpty()) {
                return false;
            }

            ASTNode operatorNameElement = qualifiedElement.getNameElement();
            PsiElement operatorElement = operatorNameElement != null ? operatorNameElement.getPsi() : null;
            if (operatorElement == null) {
                return false;
            }
            String compoundOperatorText = callSite instanceof PyBinaryExpression binaryExpression
                ? PyNames.COMPOUND_OPERATOR_DISPLAY_TEXT.get(binaryExpression.getOperatorTokensText())
                : null;
            String operatorText = compoundOperatorText != null ? compoundOperatorText : operatorElement.getText();

            // No operator resolved on any union member at all: there is nothing to break down, report the operands as a whole.
            if (analyzedCallees == null) {
                return PyTypeCheckerProblemReporter.report(
                    holder,
                    PyTypeCheckerSuppressionCode.UNSUPPORTED_OPERATOR,
                    operatorElement,
                    unsupportedOperatorMessage(operatorText, operatorElement, lhsType, rhsType),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                );
            }

            Pair<Set<String>, Set<String>> dispatchedOperators = computeDispatchedOperators(callSite, operatorName);
            Set<String> directOperators = dispatchedOperators.getFirst();
            Set<String> reflectedOperators = dispatchedOperators.getSecond();

            Map<PyType, List<PyCalleeResults>> directCandidates = groupByReceiver(analyzedCallees, directOperators);
            Map<PyType, List<PyCalleeResults>> reflectedCandidates = groupByReceiver(analyzedCallees, reflectedOperators);

            long combinationCount = (long) leftTypes.size() * (long) rightTypes.size();
            boolean skipBreakdown = combinationCount > STRICT_UNION_COMBINATION_LIMIT;

            List<Pair<PyType, PyType>> unsupportedPairs = collectUnsupportedOperandPairs(leftTypes, rightTypes, directCandidates,
                                                                                         reflectedCandidates, skipBreakdown);
            if (unsupportedPairs.isEmpty()) {
                return false;
            }

            // For large unions, stop after the first failing pair to avoid expensive, noisy breakdowns.
            if (skipBreakdown) {
                return PyTypeCheckerProblemReporter.report(
                    holder,
                    PyTypeCheckerSuppressionCode.UNSUPPORTED_OPERATOR,
                    operatorElement,
                    unsupportedOperatorMessage(operatorText, operatorElement, lhsType, rhsType),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                );
            }

            return reportUnsupportedOperandCombinations(operatorText, operatorElement, directCandidates, reflectedCandidates,
                                                        unsupportedPairs, directOperators, reflectedOperators);
        }

        /**
         * The (left member, right member) combinations for which neither a direct operator on the left member
         * nor a reflected one on the right member applies.
         */
        private List<Pair<PyType, PyType>> collectUnsupportedOperandPairs(List<PyType> leftTypes,
                                                                         List<PyType> rightTypes,
                                                                         Map<PyType, List<PyCalleeResults>> directCandidates,
                                                                         Map<PyType, List<PyCalleeResults>> reflectedCandidates,
                                                                         boolean stopAtFirstMismatch) {
            List<Pair<PyType, PyType>> unsupportedPairs = new ArrayList<>();

            for (PyType leftTypeMember : leftTypes) {
                List<PyCalleeResults> direct = forReceiver(directCandidates, leftTypeMember);

                for (PyType rightTypeMember : rightTypes) {
                    if (acceptsOperand(direct, rightTypeMember)) {
                        continue;
                    }

                    List<PyCalleeResults> reflected = forReceiver(reflectedCandidates, rightTypeMember);
                    if (acceptsOperand(reflected, leftTypeMember)) {
                        continue;
                    }

                    unsupportedPairs.add(Pair.create(leftTypeMember, rightTypeMember));
                    if (stopAtFirstMismatch) {
                        return unsupportedPairs;
                    }
                }
            }

            return unsupportedPairs;
        }

        private PyInspectionMessages.ProblemMessage unsupportedOperatorMessage(String operatorText,
                                                                               PsiElement operatorElement,
                                                                               @Nullable PyType leftType,
                                                                               @Nullable PyType rightType) {
            return PyInspectionMessages.problemMessage(
                p -> PyLocalize.inspTypeCheckerUnsupportedOperatorBetweenTypes(p[0], p[1], p[2]),
                operatorText,
                CodifiedParam.ofType(leftType, operatorElement, myTypeEvalContext),
                CodifiedParam.ofType(rightType, operatorElement, myTypeEvalContext)
            );
        }

        /**
         * The operators dispatched on the left operand, and the reflected ones dispatched on the right operand.
         * The two sets never intersect, which is what allows telling the sides of a resolved group apart by callable name.
         */
        private static Pair<Set<String>, Set<String>> computeDispatchedOperators(PyElement callSite, String operatorName) {
            if (callSite instanceof PyBinaryExpression) {
                if (PyNames.STANDALONE_RIGHT_OPERATORS.contains(operatorName)) {
                    return Pair.create(Set.of(), Set.of(operatorName));
                }
                else {
                    return Pair.create(Set.of(operatorName), setOfNotNull(PyNames.leftToRightOperatorName(operatorName)));
                }
            }
            if (callSite instanceof PyAugAssignmentStatement) {
                return Pair.create(setOfNotNull(operatorName, PyNames.inplaceToLeftOperatorName(operatorName)),
                                   setOfNotNull(PyNames.inplaceToRightOperatorName(operatorName)));
            }
            return Pair.create(Set.of(), Set.of());
        }

        private static Set<String> setOfNotNull(@Nullable String... elements) {
            Set<String> result = new LinkedHashSet<>();
            for (String element : elements) {
                if (element != null) {
                    result.add(element);
                }
            }
            return result;
        }

        /**
         * The already-analyzed {@code operatorNames} overloads grouped by receiver, merging analyses that share one:
         * a single receiver can contribute several operators, e.g. {@code x += y} resolves both {@code __iadd__}
         * and {@code __add__} on {@code x}.
         */
        private static Map<PyType, List<PyCalleeResults>> groupByReceiver(List<PyCalleeResults> callees, Set<String> operatorNames) {
            if (operatorNames.isEmpty()) {
                return Map.of();
            }

            Map<PyType, List<PyCalleeResults>> result = new LinkedHashMap<>();
            for (PyCalleeResults callee : callees) {
                PyCallable callable = callee.getCalleeResults().getCallable();
                if (operatorNames.contains(callable != null ? callable.getName() : null)) {
                    result.computeIfAbsent(callee.getMemberType(), k -> new ArrayList<>()).add(callee);
                }
            }
            return result;
        }

        /**
         * Like {@link PyTypeUtil#asUnionSequence}, but keeps a {@link PyUnsafeUnionType} as a single operand so strict-union checks
         * still see it whole instead of per-member.
         */
        private static List<@Nullable PyType> strictUnionSequence(@Nullable PyType type) {
            if (type instanceof PyUnionType unionType) {
                return new ArrayList<>(unionType.getMembers());
            }
            List<@Nullable PyType> result = new ArrayList<>();
            result.add(type);
            return result;
        }

        private static List<PyCalleeResults> forReceiver(Map<PyType, List<PyCalleeResults>> candidates, PyType type) {
            List<PyCalleeResults> direct = candidates.get(type);
            if (direct != null) {
                return direct;
            }
            List<PyCalleeResults> result = new ArrayList<>();
            for (PyType component : PyTypeUtil.getCompositeComponents(type)) {
                if (component == null) {
                    continue;
                }
                List<PyCalleeResults> componentCandidates = candidates.get(component);
                if (componentCandidates != null) {
                    result.addAll(componentCandidates);
                }
            }
            return result;
        }

        private boolean acceptsOperand(List<PyCalleeResults> candidates, @Nullable PyType operandType) {
            for (PyCalleeResults candidate : candidates) {
                AnalyzeCalleeResults calleeResults = candidate.getCalleeResults();
                if (!calleeResults.getUnmatchedArguments().isEmpty() ||
                    !calleeResults.getUnmatchedParameters().isEmpty() ||
                    !calleeResults.getUnfilledPositionalVarargs().isEmpty() ||
                    !candidate.getMapping().isComplete()) {
                    continue;
                }
                List<AnalyzeArgumentResult> results = calleeResults.getResults();
                if (results.size() != 1) {
                    if (isMatched(calleeResults, candidate.getMapping())) {
                        return true;
                    }
                    continue;
                }
                AnalyzeArgumentResult operand = results.get(0);
                if (matchesExpectedType(chooseExpectedTypeForMismatch(operand), operandType, operand.getArgument(), null)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Reports the {@code unsupportedPairs}, which the caller guarantees to be non-empty, with a per-combination breakdown tooltip.
         */
        @RequiredReadAction
        private boolean reportUnsupportedOperandCombinations(String operatorText,
                                                             PsiElement operatorElement,
                                                             Map<PyType, List<PyCalleeResults>> directCandidates,
                                                             Map<PyType, List<PyCalleeResults>> reflectedCandidates,
                                                             List<Pair<PyType, PyType>> unsupportedPairs,
                                                             Set<String> directOperators,
                                                             Set<String> reflectedOperators) {
            LinkedHashSet<@Nullable PyType> leftMembers = new LinkedHashSet<>();
            LinkedHashSet<@Nullable PyType> rightMembers = new LinkedHashSet<>();
            for (Pair<PyType, PyType> pair : unsupportedPairs) {
                leftMembers.add(pair.getFirst());
                rightMembers.add(pair.getSecond());
            }
            PyType leftUnion = PyUnionType.unionOrUnknown(leftMembers);
            PyType rightUnion = PyUnionType.unionOrUnknown(rightMembers);

            PyInspectionMessages.ProblemMessage unsupportedOperatorMessage =
                unsupportedOperatorMessage(operatorText, operatorElement, leftUnion, rightUnion);

            return PyTypeCheckerProblemReporter.reportWithTooltip(
                holder,
                PyTypeCheckerSuppressionCode.UNSUPPORTED_OPERATOR,
                operatorElement,
                unsupportedOperatorMessage,
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                () -> PyTypeCheckerInspectionProblemRegistrar.breakdownTooltip(
                    unsupportedOperatorMessage,
                    explainUnsupportedOperandCombinations(
                        directCandidates,
                        reflectedCandidates,
                        unsupportedPairs,
                        leftUnion,
                        rightUnion,
                        directOperators,
                        reflectedOperators,
                        operatorElement
                    )
                )
            );
        }

        @RequiredReadAction
        private List<PyTypeMismatchExplanation> explainUnsupportedOperandCombinations(Map<PyType, List<PyCalleeResults>> directCandidates,
                                                                                       Map<PyType, List<PyCalleeResults>> reflectedCandidates,
                                                                                       List<Pair<PyType, PyType>> unsupportedPairs,
                                                                                       @Nullable PyType leftUnionType,
                                                                                       @Nullable PyType rightUnionType,
                                                                                       Set<String> directOperators,
                                                                                       Set<String> reflectedOperators,
                                                                                       PsiElement operatorElement) {
            boolean groupByRightOperand = directOperators.isEmpty() && !reflectedOperators.isEmpty();

            Map<PyType, List<Pair<PyType, PyType>>> pairsByReceiver = new LinkedHashMap<>();
            for (Pair<PyType, PyType> pair : unsupportedPairs) {
                PyType receiver = groupByRightOperand ? pair.getSecond() : pair.getFirst();
                pairsByReceiver.computeIfAbsent(receiver, k -> new ArrayList<>()).add(pair);
            }
            boolean singleReceiver = pairsByReceiver.size() == 1;

            List<PyTypeMismatchExplanation> explanations = new ArrayList<>();
            for (Map.Entry<PyType, List<Pair<PyType, PyType>>> entry : pairsByReceiver.entrySet()) {
                PyType receiverType = entry.getKey();
                List<Pair<PyType, PyType>> pairs = entry.getValue();
                List<PyTypeMismatchExplanation> reasons = new ArrayList<>();
                List<PyType> undefined = new ArrayList<>();

                for (Pair<PyType, PyType> pair : pairs) {
                    PyType leftType = pair.getFirst();
                    PyType rightType = pair.getSecond();
                    List<PyTypeMismatchExplanation> pairReasons = new ArrayList<>();
                    pairReasons.addAll(explainRejectedOperand(
                        forReceiver(directCandidates, leftType),
                        leftType,
                        rightType,
                        rightUnionType,
                        operatorElement
                    ));
                    pairReasons.addAll(explainRejectedOperand(
                        forReceiver(reflectedCandidates, rightType),
                        rightType,
                        leftType,
                        leftUnionType,
                        operatorElement
                    ));
                    if (pairReasons.isEmpty()) {
                        undefined.add(groupByRightOperand ? leftType : rightType);
                    }
                    else {
                        reasons.addAll(pairReasons);
                    }
                }

                List<PyTypeMismatchExplanation> effectiveReasons = new ArrayList<>();
                Set<String> seenDescriptions = new HashSet<>();
                for (PyTypeMismatchExplanation reason : reasons) {
                    if (seenDescriptions.add(reason.getMessage().description())) {
                        effectiveReasons.add(reason);
                    }
                }
                if (!undefined.isEmpty()) {
                    PyType operandUnion = PyUnionType.unionOrUnknown(new LinkedHashSet<@Nullable PyType>(undefined));
                    PyTypeMismatchExplanation undefinedExplanation = explainUndefinedOperator(
                        receiverType,
                        groupByRightOperand ? receiverType : operandUnion,
                        directOperators, reflectedOperators, operatorElement
                    );
                    if (undefinedExplanation != null) {
                        effectiveReasons.add(undefinedExplanation);
                    }
                }

                if (singleReceiver) {
                    explanations.addAll(effectiveReasons);
                }
                else {
                    PyType memberUnionType = groupByRightOperand ? rightUnionType : leftUnionType;
                    LinkedHashSet<@Nullable PyType> otherOperands = new LinkedHashSet<>();
                    for (Pair<PyType, PyType> pair : pairs) {
                        otherOperands.add(groupByRightOperand ? pair.getFirst() : pair.getSecond());
                    }
                    PyType otherOperandUnion = PyUnionType.unionOrUnknown(otherOperands);
                    explanations.add(
                        new PyTypeMismatchExplanation(
                            PyInspectionMessages.problemMessage(
                                p -> PyLocalize.inspTypeCheckerStrictUnionUnsupportedOperatorMember(p[0], p[1], p[2], p[3]),
                                CodifiedParam.ofType(receiverType, operatorElement, myTypeEvalContext),
                                CodifiedParam.ofType(memberUnionType, operatorElement, myTypeEvalContext),
                                operatorElement.getText(),
                                CodifiedParam.ofType(otherOperandUnion, operatorElement, myTypeEvalContext)
                            ),
                            effectiveReasons
                        )
                    );
                }
            }
            return explanations;
        }

        private @Nullable PyTypeMismatchExplanation explainUndefinedOperator(PyType receiverType,
                                                                             @Nullable PyType operandType,
                                                                             Set<String> directOperators,
                                                                             Set<String> reflectedOperators,
                                                                             PsiElement anchor) {
            PyInspectionMessages.ProblemMessage message;
            if (!directOperators.isEmpty() && !reflectedOperators.isEmpty()) {
                message = PyInspectionMessages.problemMessage(
                    p -> PyLocalize.inspTypeCheckerStrictUnionUnsupportedOperatorNoOverloads(p[0], p[1], p[2], p[3]),
                    CodifiedParam.ofType(receiverType, anchor, myTypeEvalContext),
                    CodifiedParam.joinNames(directOperators),
                    CodifiedParam.ofType(operandType, anchor, myTypeEvalContext),
                    CodifiedParam.joinNames(reflectedOperators)
                );
            }
            else if (!directOperators.isEmpty()) {
                message = undefinedOperatorMessage(receiverType, directOperators, anchor);
            }
            else if (!reflectedOperators.isEmpty()) {
                message = undefinedOperatorMessage(operandType, reflectedOperators, anchor);
            }
            else {
                return null;
            }
            return new PyTypeMismatchExplanation(message);
        }

        private PyInspectionMessages.ProblemMessage undefinedOperatorMessage(@Nullable PyType type,
                                                                             Set<String> operatorNames,
                                                                             PsiElement anchor) {
            return PyInspectionMessages.problemMessage(
                p -> PyLocalize.inspTypeCheckerStrictUnionUnsupportedOperatorNotDefined(p[0], p[1]),
                CodifiedParam.ofType(type, anchor, myTypeEvalContext),
                CodifiedParam.joinNames(operatorNames)
            );
        }

        private List<PyTypeMismatchExplanation> explainRejectedOperand(List<PyCalleeResults> candidates,
                                                                       PyType receiverType,
                                                                       @Nullable PyType operandType,
                                                                       @Nullable PyType operandUnionType,
                                                                       PsiElement anchor) {
            List<PyTypeMismatchExplanation> explanations = new ArrayList<>();
            for (PyCalleeResults candidate : candidates) {
                if (!candidate.getMapping().getUnmappedParameters().isEmpty()) {
                    PyCallable method = candidate.getCalleeResults().getCallable();
                    if (method == null) {
                        continue;
                    }
                    String methodName = method.getName();
                    if (methodName == null) {
                        continue;
                    }
                    String receiverName = PythonDocumentationProvider.getTypeName(receiverType, myTypeEvalContext);
                    CodifiedParam methodParam = CodifiedParam.ofReference(method, receiverName + "." + methodName);
                    List<String> unmappedParameterNames = new ArrayList<>();
                    for (PyCallableParameter parameter : candidate.getMapping().getUnmappedParameters()) {
                        String parameterName = parameter.getName();
                        if (parameterName != null) {
                            unmappedParameterNames.add(parameterName);
                        }
                    }
                    explanations.add(new PyTypeMismatchExplanation(
                        PyInspectionMessages.problemMessage(
                            p -> PyLocalize.inspTypeCheckerStrictUnionUnsupportedOperatorMissingArgument(p[0], p[1]),
                            methodParam,
                            CodifiedParam.joinNames(unmappedParameterNames)
                        )
                    ));
                    continue;
                }

                List<AnalyzeArgumentResult> results = candidate.getCalleeResults().getResults();
                if (results.size() != 1) {
                    continue;
                }
                AnalyzeArgumentResult operand = results.get(0);
                PyType expectedType = chooseExpectedTypeForMismatch(operand);

                if (matchesExpectedType(expectedType, operandType, operand.getArgument(), null)) {
                    continue;
                }

                PyCallable method = candidate.getCalleeResults().getCallable();
                if (method == null) {
                    continue;
                }
                String methodName = method.getName();
                if (methodName == null) {
                    continue;
                }
                String receiverName = PythonDocumentationProvider.getTypeName(receiverType, myTypeEvalContext);
                PyCallableParameter operandParameter = operand.getParameter();
                String paramName = operandParameter != null ? operandParameter.getName() : null;

                CodifiedParam operandParam = CodifiedParam.ofType(operandType, anchor, myTypeEvalContext);
                CodifiedParam methodParam = CodifiedParam.ofReference(method, receiverName + "." + methodName);
                CodifiedParam expectedParam = CodifiedParam.ofType(expectedType, anchor, myTypeEvalContext, true);

                PyInspectionMessages.ProblemMessage header;
                if (Objects.equals(operandUnionType, operandType)) {
                    header = PyInspectionMessages.problemMessage(
                        p -> PyLocalize.inspTypeCheckerStrictUnionUnsupportedOperatorOperandNotAssignable(p[0], p[1], p[2], p[3]),
                        operandParam, paramName, methodParam, expectedParam
                    );
                }
                else {
                    header = PyInspectionMessages.problemMessage(
                        p -> PyLocalize.inspTypeCheckerStrictUnionUnsupportedOperatorMemberNotAssignable(p[0], p[1], p[2], p[3], p[4]),
                        operandParam,
                        CodifiedParam.ofType(operandUnionType, anchor, myTypeEvalContext),
                        paramName, methodParam, expectedParam
                    );
                }

                PyTypeMismatchExplanation lowLevel = PyTypeChecker.explainMismatch(expectedType, operandType, myTypeEvalContext, anchor);

                explanations.add(new PyTypeMismatchExplanation(header, lowLevel != null ? List.of(lowLevel) : List.of()));
            }
            return explanations;
        }

        private @Nullable PyType chooseExpectedTypeForMismatch(AnalyzeArgumentResult mismatch) {
            PyType substituted = mismatch.getExpectedTypeAfterSubstitution();
            PyType declared = mismatch.getExpectedType();

            if (substituted != null &&
                !substituted.equals(declared) &&
                !PyTypeChecker.containsAny(substituted, myTypeEvalContext)) {
                return substituted;
            }
            else {
                return declared;
            }
        }

        @RequiredReadAction
        private boolean reportIfNoCalleeMatches(PyElement callSite,
                                                List<Pair<PyArgumentsMapping, AnalyzeCalleeResults>> calleesResults,
                                                List<PyArgumentsMapping> allMappings) {
            if (calleesResults.isEmpty() || calleesResults.stream().anyMatch(it -> isMatched(it.getSecond(), it.getFirst()))) {
                return false;
            }

            // When there is only ONE arity-complete candidate — where the report would otherwise collapse to a single
            // "expected …, got …" message that hides that the callee is overloaded — also list any NEAR-MISS overload:
            // an incomplete one that merely lacks a required argument (so the reader sees the fuller overload the call
            // was closest to). Near misses are rendered only, so they are analyzed with their type-check side effects
            // suppressed (see [analyzeCallee]); candidates keep declaration order.
            List<AnalyzeCalleeResults> reportResults = new ArrayList<>();
            if (calleesResults.size() != 1) {
                for (Pair<PyArgumentsMapping, AnalyzeCalleeResults> it : calleesResults) {
                    reportResults.add(it.getSecond());
                }
            }
            else {
                Pair<PyArgumentsMapping, AnalyzeCalleeResults> single = calleesResults.get(0);
                for (PyArgumentsMapping mapping : allMappings) {
                    AnalyzeCalleeResults results;
                    if (mapping.isComplete()) {
                        results = single.getFirst() == mapping ? single.getSecond() : null;
                    }
                    else if (!mapping.getUnmappedParameters().isEmpty() && mapping.getUnmappedArguments().isEmpty()) {
                        results = analyzeCallee(mapping, false);
                    }
                    else {
                        results = null;
                    }
                    if (results != null) {
                        reportResults.add(results);
                    }
                }
            }

            PyTypeCheckerInspectionProblemRegistrar
                .registerProblem(
                    holder, callSite, reportResults, myTypeEvalContext,
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                );
            return true;
        }

        @RequiredReadAction
        private boolean reportArgumentTypeMismatch(PyElement callSite, List<PyArgumentsMapping> argumentsMappings) {
            List<PyArgumentsMapping> shapeMatches = new ArrayList<>();
            List<PyArgumentsMapping> shapeMismatches = new ArrayList<>();
            for (PyArgumentsMapping mapping : argumentsMappings) {
                (mapping.isComplete() ? shapeMatches : shapeMismatches).add(mapping);
            }

            List<Pair<PyArgumentsMapping, AnalyzeCalleeResults>> shapeMatchesCalleesResults = new ArrayList<>();
            for (PyArgumentsMapping mapping : shapeMatches) {
                AnalyzeCalleeResults results = analyzeCallee(mapping);
                if (results != null) {
                    shapeMatchesCalleesResults.add(Pair.create(mapping, results));
                }
            }
            if (!shapeMatchesCalleesResults.isEmpty()) {
                return reportIfNoCalleeMatches(callSite, shapeMatchesCalleesResults, argumentsMappings);
            }

            // We can only reliably report an argument type mismatch if there is a single callable candidate and we have extra arguments
            if (shapeMismatches.size() != 1) {
                return false;
            }
            PyArgumentsMapping onlyShapeMismatch = shapeMismatches.get(0);
            if (onlyShapeMismatch.getUnmappedArguments().isEmpty() || !onlyShapeMismatch.getUnmappedParameters().isEmpty()) {
                return false;
            }
            AnalyzeCalleeResults typeMatchResult = analyzeCallee(onlyShapeMismatch);
            if (typeMatchResult == null) {
                return false;
            }
            if (areTypesMatched(typeMatchResult)) {
                return false;
            }

            PyTypeCheckerInspectionProblemRegistrar
                .registerProblem(
                    holder, callSite, List.of(typeMatchResult), myTypeEvalContext,
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                );
            return true;
        }

        @RequiredReadAction
        private boolean checkIteratedValue(@Nullable PyExpression iteratedValue, boolean isAsync) {
            return checkIteratedValue(iteratedValue, iteratedValue, isAsync);
        }

        @RequiredReadAction
        private boolean checkIteratedValue(@Nullable PyExpression iteratedValue, @Nullable PsiElement highlightElement, boolean isAsync) {
            if (iteratedValue == null || highlightElement == null) {
                return false;
            }
            PyType type = myTypeEvalContext.getType(iteratedValue);
            String iterableClassName = isAsync ? PyNames.ASYNC_ITERABLE : PyNames.ITERABLE;

            if (type != null && !PyTypeChecker.isUnknown(type, myTypeEvalContext) && !PyABCUtil.isSubtype(type, iterableClassName, myTypeEvalContext)) {
                String iterableQualifiedName = isAsync ? PyTypingTypeProvider.ASYNC_ITERABLE : PyTypingTypeProvider.ITERABLE;
                Object expected = expectedClassParam(iterableQualifiedName, highlightElement);
                PyTypeCheckerProblemReporter.report(
                    holder,
                    PyTypeCheckerSuppressionCode.NOT_ITERABLE,
                    highlightElement,
                    PyInspectionMessages.problemMessage(p -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(p[0], p[1]), expected,
                                                        CodifiedParam.ofType(type, highlightElement, myTypeEvalContext)),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                );
                return true;
            }
            return false;
        }

        /**
         * Renders the class {@code classQualifiedName} like any other expected type.
         * Falls back to the qualified name when the class does not resolve from {@code anchor}.
         */
        private Object expectedClassParam(String classQualifiedName, PsiElement anchor) {
            PyClassTypeImpl type = PyClassTypeImpl.createTypeByQName(anchor, classQualifiedName, false);
            if (type == null) {
                return classQualifiedName;
            }
            return CodifiedParam.ofType(type, anchor, myTypeEvalContext);
        }

        @RequiredReadAction
        private boolean checkUnpackIterableValue(@Nullable PyExpression iteratedValue) {
            if (iteratedValue == null) {
                return false;
            }
            PyExpression value = iteratedValue;
            if (value instanceof PyStarExpression starExpression) {
                value = starExpression.getExpression();
            }
            if (value == null) {
                return false;
            }
            // A generic-class subscription like `*A[int]` is always iterable at runtime:
            // `types.GenericAlias.__iter__` yields the subscript args.
            if (value instanceof PySubscriptionExpression subscriptionExpression) {
                PyType operandType = myTypeEvalContext.getType(subscriptionExpression.getOperand());
                if (operandType instanceof PyClassLikeType classLikeType && classLikeType.isDefinition()) {
                    return false;
                }
            }
            PyType type = myTypeEvalContext.getType(value);
            if (type != null && !PyTypeChecker.isUnknown(type, myTypeEvalContext) && !PyABCUtil.isSubtype(type, PyNames.ITERABLE, myTypeEvalContext)) {
                PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.NOT_ITERABLE, value,
                                                    PyInspectionMessages.problemMessage(p -> PyLocalize.inspTypeCheckerUnpackExpectedIterable(p[0]),
                                                                                        CodifiedParam.ofType(type, value, myTypeEvalContext)),
                                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                return true;
            }
            return false;
        }

        @RequiredReadAction
        private void checkUnpackMappingValue(@Nullable PyExpression mappingValue) {
            if (mappingValue == null) {
                return;
            }
            PyExpression value = mappingValue;
            if (value instanceof PyDoubleStarExpression doubleStarExpression) {
                value = doubleStarExpression.getExpression();
            }
            if (value == null) {
                return;
            }
            PyType type = myTypeEvalContext.getType(value);
            if (type != null && !PyTypeChecker.isUnknown(type, myTypeEvalContext) &&
                // TODO: it's not Mapping, but a more wider type
                !PyABCUtil.isSubtype(type, PyNames.MAPPING, myTypeEvalContext)) {
                PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.NOT_MAPPING, value,
                                                    PyInspectionMessages.problemMessage(p -> PyLocalize.inspTypeCheckerUnpackExpectedMapping(p[0]),
                                                                                        CodifiedParam.ofType(type, value, myTypeEvalContext)),
                                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        }

        private boolean targetOrResolvedHasExplicitType(PyTargetExpression target) {
            PsiElement current = target;
            while (current instanceof PyTargetExpression currentTarget) {
                if (hasExplicitType(currentTarget)) {
                    return true;
                }
                PsiElement resolved = currentTarget.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).resolve();
                if (resolved == current || resolved == null) {
                    break;
                }
                current = resolved;
            }
            return false;
        }

        /**
         * The declared type of {@code target} from its annotation. Unlike {@code getType(target)}, this is not narrowed by the value
         * of the current unpacking, so it is safe to compare an unpacked value against the declared annotation.
         */
        private @Nullable PyType resolvedDeclaredType(PyTargetExpression target) {
            PsiElement current = target;
            while (current instanceof PyTargetExpression currentTarget) {
                PyAnnotation annotation = currentTarget.getAnnotation();
                PyExpression annotationValue = annotation != null ? annotation.getValue() : null;
                if (annotationValue != null) {
                    return SoftReference.deref(PyTypingTypeProvider.getType(annotationValue, myTypeEvalContext));
                }
                if (currentTarget.getTypeCommentAnnotation() != null) {
                    return myTypeEvalContext.getType(currentTarget);
                }
                PsiElement resolved = currentTarget.getReference(PyResolveContext.defaultContext(myTypeEvalContext)).resolve();
                if (resolved == current || resolved == null) {
                    break;
                }
                current = resolved;
            }
            return null;
        }

        /**
         * Number of values produced by a tuple expression used as the right-hand side of an unpacking assignment.
         * A starred element contributes the length of its operand only when the operand is a statically known
         * (heterogeneous, bounded) tuple; if any starred operand has an indeterminate length, returns {@code -1}.
         */
        private int getUnpackedTupleLength(PyTupleExpression rhsTuple) {
            int count = 0;
            for (PyExpression element : rhsTuple.getElements()) {
                if (element instanceof PyStarExpression starExpression) {
                    PyExpression operand = starExpression.getExpression();
                    if (operand == null) {
                        return -1;
                    }
                    PyType operandType = myTypeEvalContext.getType(operand);
                    if (!(operandType instanceof PyTupleType tupleType) || tupleType.isHomogeneous()) {
                        return -1;
                    }
                    count += tupleType.getElementCount();
                }
                else {
                    count++;
                }
            }
            return count;
        }

        @RequiredReadAction
        private boolean checkUnpackBalance(int targetCount, int starCount, int valueCount,
                                           PsiElement balanceHighlight, PsiElement starHighlight,
                                           CodifiedParam valueType) {
            if (starCount > 1) {
                PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_UNPACKING, starHighlight,
                                                    PyLocalize.inspTupleAssignmentBalanceOnlyOneStarredExpressionAllowedInAssignment().get());
                return true;
            }
            // A starred target absorbs any surplus, so it only constrains the minimum number of values.
            int expectedCount = starCount == 1 ? targetCount - 1 : targetCount;
            boolean tooMany = starCount == 0 && expectedCount < valueCount;
            boolean notEnough = expectedCount > valueCount;
            if (tooMany || notEnough) {
                Function<Object[], LocalizeValue> key = tooMany
                    ? p -> PyLocalize.inspTupleAssignmentBalanceTooManyValuesToUnpack(p[0], p[1], p[2])
                    : p -> PyLocalize.inspTupleAssignmentBalanceNeedMoreValuesToUnpack(p[0], p[1], p[2]);
                PyTypeCheckerProblemReporter.report(holder, PyTypeCheckerSuppressionCode.BAD_UNPACKING, balanceHighlight,
                                                    PyInspectionMessages.problemMessage(key, expectedCount, valueCount, valueType),
                                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                return true;
            }
            return false;
        }

        @RequiredReadAction
        private void checkNestedUnpackingBalance(PyExpression[] targets, PyTupleType assignedTupleType) {
            if (assignedTupleType.isHomogeneous()) {
                return;
            }
            int count = assignedTupleType.getElementCount();
            int starIndex = -1;
            for (int i = 0; i < targets.length; i++) {
                if (targets[i] instanceof PyStarExpression) {
                    starIndex = i;
                    break;
                }
            }
            for (int i = 0; i < targets.length; i++) {
                if (i == starIndex) {
                    continue;
                }
                PyExpression nested = PyPsiUtils.flattenParens(targets[i]);
                if (!(nested instanceof PyTupleExpression) && !(nested instanceof PyListLiteralExpression)) {
                    continue;
                }
                int effectiveIndex = starIndex >= 0 && starIndex < i ? count - (targets.length - i) : i;
                if (effectiveIndex < 0 || effectiveIndex >= count) {
                    continue;
                }
                PyType elementType = assignedTupleType.getElementType(effectiveIndex);
                if (elementType instanceof PyTupleType elementTupleType) {
                    checkUnpackingTargetBalance((PySequenceExpression) nested, elementTupleType);
                }
            }
        }

        @RequiredReadAction
        private void checkUnpackingTargetBalance(PySequenceExpression targetSeq, PyTupleType assignedTupleType) {
            if (assignedTupleType.isHomogeneous()) {
                return;
            }
            int valueCount = assignedTupleType.getElementCount();
            if (valueCount < 0) {
                return;
            }
            PyExpression[] targets = targetSeq.getElements();
            int starCount = (int) Arrays.stream(targets).filter(it -> it instanceof PyStarExpression).count();
            CodifiedParam valueType = CodifiedParam.ofType(assignedTupleType, targetSeq, myTypeEvalContext);
            if (checkUnpackBalance(targets.length, starCount, valueCount, targetSeq, targetSeq, valueType)) {
                return;
            }
            checkNestedUnpackingBalance(targets, assignedTupleType);
        }

        @RequiredReadAction
        private void checkContextManagerValue(@Nullable PyExpression iteratedValue, boolean isAsync) {
            if (iteratedValue != null) {
                PyType type = myTypeEvalContext.getType(iteratedValue);
                String contextManagerClassName = isAsync ? PyNames.ABSTRACT_ASYNC_CONTEXT_MANAGER : PyNames.ABSTRACT_CONTEXT_MANAGER;

                if (type != null && !PyTypeChecker.isUnknown(type, myTypeEvalContext) &&
                    !PyABCUtil.isSubtype(type, contextManagerClassName, myTypeEvalContext)) {
                    Object expected = expectedClassParam("contextlib." + contextManagerClassName, iteratedValue);
                    PyTypeCheckerProblemReporter.report(
                        holder,
                        PyTypeCheckerSuppressionCode.BAD_CONTEXT_MANAGER,
                        iteratedValue,
                        PyInspectionMessages.problemMessage(p -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(p[0], p[1]), expected,
                                                            CodifiedParam.ofType(type, iteratedValue, myTypeEvalContext)),
                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING
                    );
                }
            }
        }

        private @Nullable AnalyzeCalleeResults analyzeCallee(PyArgumentsMapping mapping) {
            return analyzeCallee(mapping, true);
        }

        private @Nullable AnalyzeCalleeResults analyzeCallee(PyArgumentsMapping mapping, boolean reportProblems) {
            PyCallableType callableType = mapping.getCallableType();
            if (callableType == null) {
                return null;
            }

            List<AnalyzeArgumentResult> result = new ArrayList<>();
            List<UnexpectedArgumentForParamSpec> unexpectedArgumentForParamSpecs = new ArrayList<>();
            List<UnfilledParameterFromParamSpec> unfilledParameterFromParamSpecs = new ArrayList<>();

            GenericSubstitutions substitutions = PyTypeInferenceCspFactory.unifyReceiver(mapping, myTypeEvalContext);

            Map<PyExpression, PyCallableParameter> mappedParameters = mapping.getMappedParameters();
            Map<PyExpression, PyCallableParameter> regularMappedParameters = PyCallExpressionHelper.getRegularMappedParameters(mappedParameters);

            for (Map.Entry<PyExpression, PyCallableParameter> entry : regularMappedParameters.entrySet()) {
                PyExpression argument = Objects.requireNonNull(entry.getKey());
                PyCallableParameter parameter = entry.getValue();
                PyType expected = parameter.getArgumentType(myTypeEvalContext);
                PyType actual = myTypeEvalContext.getType(argument);

                if (expected instanceof PyParamSpecType paramSpecType) {
                    analyzeParamSpec(
                        paramSpecType, mapping.getArguments(), substitutions, result, unexpectedArgumentForParamSpecs,
                        unfilledParameterFromParamSpecs, reportProblems
                    );
                    break;
                }
                else if (expected instanceof PyConcatenateType concatenateType) {
                    if (mapping.getArguments().isEmpty()) {
                        break;
                    }

                    List<PyType> firstExpectedTypes = concatenateType.getFirstTypes();
                    int nonStarCount = 0;
                    for (PyExpression arg : mapping.getArguments()) {
                        if (arg instanceof PyStarArgument) {
                            break;
                        }
                        nonStarCount++;
                    }
                    int argumentRightBound = Math.min(firstExpectedTypes.size(), nonStarCount);
                    List<PyExpression> firstArguments = mapping.getArguments().subList(0, argumentRightBound);
                    matchArgumentsAndTypes(firstArguments, firstExpectedTypes, substitutions, result, reportProblems);

                    PyParamSpecType paramSpec = concatenateType.getParamSpec();
                    List<PyExpression> restArguments = mapping.getArguments().subList(argumentRightBound, mapping.getArguments().size());
                    if (paramSpec != null) {
                        if (argumentRightBound < firstExpectedTypes.size()) {
                            // Not enough positional arguments to satisfy the Concatenate prefix, e.g., int, str in Concatenate[int, str, P]
                            PyCallableParameterListType paramSpecSubst = getParamSpecSubstitution(paramSpec, substitutions);
                            if (paramSpecSubst == null) {
                                for (PyExpression arg : restArguments) {
                                    if (arg instanceof PyStarArgument) {
                                        unexpectedArgumentForParamSpecs.add(new UnexpectedArgumentForParamSpec(arg, paramSpec));
                                        break;
                                    }
                                }
                            }
                        }
                        analyzeParamSpec(
                            paramSpec, restArguments, substitutions, result, unexpectedArgumentForParamSpecs,
                            unfilledParameterFromParamSpecs, reportProblems
                        );
                    }

                    break;
                }
                else {
                    GenericSubstitutions substitutionsCopy = substitutions.addToCopy(null, null, null); // avoid pollution during match call
                    boolean matched = matchParameterAndArgument(expected, actual, argument, substitutionsCopy);
                    PyType actualPromoted;
                    if (matched) {
                        actualPromoted = actual;
                    }
                    else {
                        // fallback/promotion approach: only worth trying if the plain match failed
                        PyType promoted = PyLiteralType.promoteToLiteral(argument, expected, myTypeEvalContext, substitutions);
                        actualPromoted = PyTypeChecker.isUnknown(promoted, myTypeEvalContext) ? actual : promoted;
                    }
                    boolean isMatched;
                    if (Objects.equals(actualPromoted, actual)) {
                        substitutions = substitutionsCopy;
                        isMatched = matched;
                    }
                    else {
                        // promoted type differs: re-match against the original substitutions
                        isMatched = matchParameterAndArgument(expected, actualPromoted, argument, substitutions, reportProblems);
                    }

                    PyType expectedSubstituted = substituteGenerics(expected, substitutions);
                    result.add(new AnalyzeArgumentResult(argument, parameter, expected, expectedSubstituted, actualPromoted, isMatched));
                }
            }

            PyCallableParameter positionalContainer = PyCallExpressionHelper.getMappedPositionalContainer(mappedParameters);
            List<PyExpression> positionalArguments = PyCallExpressionHelper.getArgumentsMappedToPositionalContainer(mappedParameters);
            PyCallableParameter keywordContainer = PyCallExpressionHelper.getMappedKeywordContainer(mappedParameters);
            List<PyExpression> keywordArguments = PyCallExpressionHelper.getArgumentsMappedToKeywordContainer(mappedParameters);
            List<PyExpression> allArguments = new ArrayList<>(positionalArguments);
            allArguments.addAll(keywordArguments);

            PyParamSpecType paramSpecType = getParamSpecTypeFromContainerParameters(keywordContainer, positionalContainer);
            if (paramSpecType != null) {
                // Keyword arguments for positional parameters preceding *args: P.args
                // might shadow the values in ParamSpec, causing runtime errors. Report them when P is unsubstituted.
                PyCallableParameterListType paramSpecSubst = getParamSpecSubstitution(paramSpecType, substitutions);
                if (paramSpecSubst == null) {
                    for (Map.Entry<PyExpression, PyCallableParameter> entry : regularMappedParameters.entrySet()) {
                        if (entry.getKey() instanceof PyKeywordArgument) {
                            unexpectedArgumentForParamSpecs.add(new UnexpectedArgumentForParamSpec(entry.getKey(), paramSpecType));
                        }
                    }
                }
                analyzeParamSpec(
                    paramSpecType, allArguments, substitutions, result, unexpectedArgumentForParamSpecs,
                    unfilledParameterFromParamSpecs, reportProblems
                );
            }
            else {
                if (positionalContainer != null) {
                    result.addAll(analyzeContainerMapping(positionalContainer, positionalArguments, substitutions, reportProblems));
                }
                if (keywordContainer != null) {
                    result.addAll(analyzeContainerMapping(keywordContainer, keywordArguments, substitutions, reportProblems));
                }
            }

            List<UnfilledPositionalVararg> unfilledPositionalVarargs = new ArrayList<>();
            for (PyCallableParameter unmappedContainer : mapping.getUnmappedContainerParameters()) {
                PyType containerType = unmappedContainer.getArgumentType(myTypeEvalContext);
                String unmappedContainerName = unmappedContainer.getName();
                if (unmappedContainerName == null || !(containerType instanceof PyPositionalVariadicType)) {
                    continue;
                }
                PyType expandedVararg = PyTypeChecker.substitute(containerType, substitutions, myTypeEvalContext);
                if (!(expandedVararg instanceof PyUnpackedTupleType expandedTuple) || expandedTuple.isUnbound()) {
                    continue;
                }
                if (expandedTuple.getElementTypes().isEmpty()) {
                    continue;
                }
                if (expandedTuple.getElementTypes().stream().allMatch(it -> it instanceof PyPositionalVariadicType)) {
                    continue;
                }
                unfilledPositionalVarargs.add(
                    new UnfilledPositionalVararg(unmappedContainerName, expandedTuple)
                );
            }

            return new AnalyzeCalleeResults(
                callableType, callableType.getCallable(), result,
                unexpectedArgumentForParamSpecs,
                unfilledParameterFromParamSpecs,
                unfilledPositionalVarargs,
                // The required parameters this call leaves unfilled (arity mismatch); optional ones are excluded by the mapping.
                mapping.getUnmappedParameters()
            );
        }

        private void analyzeParamSpec(PyParamSpecType paramSpec,
                                      List<PyExpression> arguments,
                                      GenericSubstitutions substitutions,
                                      List<AnalyzeArgumentResult> result,
                                      List<UnexpectedArgumentForParamSpec> unexpectedArgumentForParamSpecs,
                                      List<UnfilledParameterFromParamSpec> unfilledParameterFromParamSpecs,
                                      boolean reportProblems) {
            PyCallableParameterListType paramSpecSubst = getParamSpecSubstitution(paramSpec, substitutions);
            if (paramSpecSubst == null) {
                analyzeUnsubstitutedParamSpec(paramSpec, arguments, unexpectedArgumentForParamSpecs);
                return;
            }

            ArgumentMappingResults mapping = PyCallExpressionHelper.analyzeArguments(
                arguments.stream().map(PyCallableArgument::new).toList(), paramSpecSubst, myTypeEvalContext);
            for (Map.Entry<PyCallableArgument, PyCallableParameter> entry : mapping.getMappedParameters().entrySet()) {
                PyCallableArgument argument = entry.getKey();
                PyCallableParameter parameter = entry.getValue();
                PyType argType = argument.getType(myTypeEvalContext);
                PyType paramType = parameter.getType(myTypeEvalContext);
                boolean matched = matchParameterAndArgument(paramType, argType, argument.getExpression(), substitutions, reportProblems);
                PyExpression argExpr = argument.getExpression();
                if (argExpr != null) {
                    result.add(new AnalyzeArgumentResult(argExpr, parameter, paramType, substituteGenerics(paramType, substitutions), argType, matched));
                }
            }
            if (!mapping.getUnmappedArguments().isEmpty()) {
                for (PyCallableArgument argument : mapping.getUnmappedArguments()) {
                    PyExpression argExpr = argument.getExpression();
                    if (argExpr != null) {
                        unexpectedArgumentForParamSpecs.add(new UnexpectedArgumentForParamSpec(argExpr, paramSpec));
                    }
                }
            }
            List<PyCallableParameter> unmappedParameters = mapping.getUnmappedParameters();
            if (!unmappedParameters.isEmpty()) {
                unfilledParameterFromParamSpecs.add(new UnfilledParameterFromParamSpec(unmappedParameters.get(0), paramSpec));
            }
        }

        private void analyzeUnsubstitutedParamSpec(PyParamSpecType paramSpec,
                                                   List<PyExpression> arguments,
                                                   List<UnexpectedArgumentForParamSpec> unexpectedArgs) {
            for (PyExpression argument : arguments) {
                if (argument instanceof PyStarArgument starArgument) {
                    PyExpression innerExpr = starArgument.getExpression();
                    if (innerExpr != null && isParamSpecContainerForwarding(innerExpr, paramSpec, !starArgument.isKeyword())) {
                        continue;
                    }
                }
                unexpectedArgs.add(new UnexpectedArgumentForParamSpec(argument, paramSpec));
            }
        }

        private boolean isParamSpecContainerForwarding(PyExpression expr, PyParamSpecType paramSpec, boolean expectPositional) {
            PyType type = myTypeEvalContext.getType(expr);
            if (!(type instanceof PyParamSpecType) || !type.equals(paramSpec)) {
                return false;
            }
            if (expr instanceof PyReferenceExpression referenceExpression) {
                PsiElement resolved = referenceExpression.getReference().resolve();
                if (resolved instanceof PyNamedParameter namedParameter) {
                    return expectPositional ? namedParameter.isPositionalContainer() : namedParameter.isKeywordContainer();
                }
            }
            return true;
        }

        private void matchArgumentsAndTypes(List<PyExpression> arguments,
                                            List<PyType> types,
                                            GenericSubstitutions substitutions,
                                            List<AnalyzeArgumentResult> result,
                                            boolean reportProblems) {
            int size = Math.min(arguments.size(), types.size());
            for (int i = 0; i < size; i++) {
                PyType expected = types.get(i);
                PyExpression argument = arguments.get(i);
                PyType actual = myTypeEvalContext.getType(argument);
                boolean matched = matchParameterAndArgument(expected, actual, argument, substitutions, reportProblems);
                result.add(new AnalyzeArgumentResult(argument, null, expected, substituteGenerics(expected, substitutions), actual, matched));
            }
        }

        private List<AnalyzeArgumentResult> analyzeContainerMapping(PyCallableParameter container,
                                                                    List<PyExpression> arguments,
                                                                    GenericSubstitutions substitutions,
                                                                    boolean reportProblems) {
            PyType expected = container.getArgumentType(myTypeEvalContext);

            if (container.isPositionalContainer() && expected instanceof PyPositionalVariadicType) {
                List<PyType> argumentTypeList = new ArrayList<>();
                for (PyExpression argument : arguments) {
                    argumentTypeList.add(myTypeEvalContext.getType(argument));
                }
                PyType argumentTypes = PyUnpackedTupleTypeImpl.create(argumentTypeList);
                boolean matched = matchParameterAndArgument(expected, argumentTypes, null, substitutions, reportProblems);
                List<AnalyzeArgumentResult> results = new ArrayList<>();
                for (PyExpression argument : arguments) {
                    PyType expectedWithSubstitutions = substituteGenerics(expected, substitutions);
                    results.add(new AnalyzeArgumentResult(argument, container, expected, expectedWithSubstitutions, argumentTypes, matched));
                }
                return results;
            }

            // For an expected type with generics we have to match all the actual types against it in order to do proper generic unification
            if (PyTypeChecker.hasGenerics(expected, myTypeEvalContext)) {
                // First collect type parameter substitutions by matching the expected type with the union, if it's a keyword container
                // otherwise, match as usual arguments, passed to a function
                if (container.isKeywordContainer()) {
                    List<@Nullable PyType> argumentTypes = new ArrayList<>();
                    for (PyExpression argument : arguments) {
                        argumentTypes.add(myTypeEvalContext.getType(argument));
                    }
                    PyType actualJoin = PyUnionType.unionOrUnknown(argumentTypes);
                    matchParameterAndArgument(expected, actualJoin, null, substitutions, reportProblems);
                }
                List<AnalyzeArgumentResult> results = new ArrayList<>();
                for (PyExpression it : arguments) {
                    // Then match each argument type against the expected type after these substitutions.
                    PyType actual = myTypeEvalContext.getType(it);
                    boolean matched = matchParameterAndArgument(expected, actual, it, substitutions, reportProblems);
                    results.add(new AnalyzeArgumentResult(it, container, expected, substituteGenerics(expected, substitutions), actual, matched));
                }
                return results;
            }
            else {
                List<AnalyzeArgumentResult> results = new ArrayList<>();
                for (PyExpression argument : arguments) {
                    PyType promotedToLiteral =
                        PyLiteralType.promoteToLiteral(argument, expected, myTypeEvalContext, substitutions);
                    PyType actual = !PyTypeChecker.isUnknown(promotedToLiteral, myTypeEvalContext)
                        ? promotedToLiteral
                        : myTypeEvalContext.getType(argument);
                    boolean matched = matchParameterAndArgument(expected, actual, argument, substitutions, reportProblems);
                    PyType expectedWithSubstitutions = substituteGenerics(expected, substitutions);
                    results.add(new AnalyzeArgumentResult(argument, container, expected, expectedWithSubstitutions, actual, matched));
                }
                return results;
            }
        }

        private @Nullable PyParamSpecType getParamSpecTypeFromContainerParameters(@Nullable PyCallableParameter positionalContainer,
                                                                                  @Nullable PyCallableParameter keywordContainer) {
            if (positionalContainer == null && keywordContainer == null) {
                return null;
            }
            PyCallableParameter container = positionalContainer != null ? positionalContainer : keywordContainer;
            return Objects.requireNonNull(container).getType(myTypeEvalContext) instanceof PyParamSpecType paramSpecType ? paramSpecType : null;
        }

        private boolean matchParameterAndArgument(@Nullable PyType parameterType,
                                                  @Nullable PyType argumentType,
                                                  @Nullable PyExpression argument,
                                                  GenericSubstitutions substitutions) {
            return matchParameterAndArgument(parameterType, argumentType, argument, substitutions, true);
        }

        /**
         * @param reportProblems When false (an incomplete overload analyzed only to render the report), the TypedDict sub-checks below skip
         *                       their problem registration, so an overload that doesn't even apply can't add a spurious TypedDict warning.
         */
        private boolean matchParameterAndArgument(@Nullable PyType parameterType,
                                                  @Nullable PyType argumentType,
                                                  @Nullable PyExpression argument,
                                                  GenericSubstitutions substitutions,
                                                  boolean reportProblems) {
            PyExpression peeledArgument = PyUtil.peelArgument(argument);
            PyExpression expression;
            if (argument instanceof PyStarArgument) {
                expression = PyUtil.peelArgument(PsiTreeUtil.getChildOfType(argument, PyExpression.class));
            }
            else {
                expression = peeledArgument;
            }

            if (expression != null) {
                if (PyTypedDictType.isDictExpression(expression, myTypeEvalContext) && parameterType instanceof PyTypedDictType typedDictType) {
                    if (reportProblems) {
                        reportTypedDictProblems(typedDictType, expression);
                    }
                    return true;
                }
                if (parameterType instanceof PyUnpackedTypedDictType unpackedTypedDictType) {
                    if (reportProblems) {
                        reportUnpackedTypedDictProblems(unpackedTypedDictType, expression);
                    }
                    return true;
                }
            }

            return matchesExpectedType(parameterType, argumentType, peeledArgument, substitutions)
                && !PyProtocolsKt.matchingProtocolDefinitions(parameterType, argumentType, myTypeEvalContext);
        }

        private @Nullable PyType substituteGenerics(@Nullable PyType expectedArgumentType, GenericSubstitutions substitutions) {
            return PyTypeChecker.hasGenerics(expectedArgumentType, myTypeEvalContext)
                ? PyTypeChecker.substitute(expectedArgumentType, substitutions, myTypeEvalContext)
                : PyAnyType.getUnknown();
        }

        public static @Nullable PyType getExpectedReturnStatementType(PyFunction function, TypeEvalContext typeEvalContext) {
            PyType returnType = typeEvalContext.getReturnType(function);
            if (function.isGenerator()) {
                GeneratorTypeDescriptor generatorDesc = GeneratorTypeDescriptor.fromGeneratorOrProtocol(returnType, typeEvalContext);
                if (generatorDesc != null) {
                    return generatorDesc.returnType();
                }
                return PyAnyType.getUnknown();
            }
            if (function.isAsync()) {
                return PyTypeUtil.derefOrUnknown(PyTypingTypeProvider.coroutineOrGeneratorElementType(returnType));
            }
            return returnType;
        }

        private static boolean requiresTypeSpecialization(@Nullable PyType type) {
            if (type instanceof PyTypeParameterType typeParameterType && typeParameterType.getDefaultType() == null &&
                !(type instanceof PySelfType)) {
                return true;
            }
            return type instanceof PyClassType classType &&
                classType.getTypeArguments().stream().anyMatch(Visitor::requiresTypeSpecialization);
        }

        private static @Nullable PyCallableParameterListType getParamSpecSubstitution(PyParamSpecType paramSpecType,
                                                                                      GenericSubstitutions substitutions) {
            return substitutions.getParamSpecs().get(paramSpecType) instanceof PyCallableParameterListType parameterListType
                ? parameterListType
                : null;
        }

        private static boolean isMatched(AnalyzeCalleeResults calleeResults, PyArgumentsMapping mapping) {
            return areTypesMatched(calleeResults) && mapping.isComplete();
        }

        private static boolean areTypesMatched(AnalyzeCalleeResults calleeResults) {
            return calleeResults.getResults().stream().allMatch(AnalyzeArgumentResult::isMatched) &&
                calleeResults.getUnmatchedArguments().isEmpty() &&
                calleeResults.getUnmatchedParameters().isEmpty() &&
                calleeResults.getUnfilledPositionalVarargs().isEmpty() &&
                calleeResults.getUnfilledRegularParameters().isEmpty();
        }

        private static boolean hasExplicitType(PsiElement node) {
            if (node instanceof PyAnnotationOwner annotationOwner && annotationOwner.getAnnotation() != null) {
                return true;
            }
            if (node instanceof PyTypeCommentOwner typeCommentOwner && typeCommentOwner.getTypeCommentAnnotation() != null) {
                return true;
            }
            return false;
        }
    }

    @Override
    public void inspectionFinished(LocalInspectionToolSession session, ProblemsHolder problemsHolder, Object state) {
        if (LOG.isDebugEnabled()) {
            Long startTime = session.getUserData(TIME_KEY);
            if (startTime != null) {
                LOG.debug(
                    String.format(
                        "[%d] elapsed time: %d ms\n",
                        Thread.currentThread().getId(),
                        (System.nanoTime() - startTime) / 1000000
                    )
                );
            }
        }
    }

    static class TypedDictProblemReporter {
        private final Set<Pair<PsiElement, String>> reportedKeys = new HashSet<>();

        @RequiredReadAction
        void report(ProblemsHolder holder,
                    PyTypeCheckerSuppressionCode code,
                    PsiElement element,
                    PyInspectionMessages.ProblemMessage message) {
            report(holder, code, element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        }

        @RequiredReadAction
        void report(ProblemsHolder holder,
                    PyTypeCheckerSuppressionCode code,
                    PsiElement element,
                    PyInspectionMessages.ProblemMessage message,
                    ProblemHighlightType type) {
            if (reportedKeys.add(Pair.create(element, message.description()))) {
                PyTypeCheckerProblemReporter.report(holder, code, element, message, type);
            }
        }
    }

    static class AnalyzeCalleeResults {
        private final PyCallableType callableType;
        private final @Nullable PyCallable callable;
        private final List<AnalyzeArgumentResult> results;
        private final List<UnexpectedArgumentForParamSpec> unmatchedArguments;
        private final List<UnfilledParameterFromParamSpec> unmatchedParameters;
        private final List<UnfilledPositionalVararg> unfilledPositionalVarargs;
        /**
         * The required parameters left unfilled by this call (arity mismatch), so the overload report can show them
         * as wholly missing. Empty for a complete mapping; optional (defaulted) parameters are never listed here.
         */
        private final List<PyCallableParameter> unfilledRegularParameters;

        AnalyzeCalleeResults(PyCallableType callableType,
                             @Nullable PyCallable callable,
                             List<AnalyzeArgumentResult> results,
                             List<UnexpectedArgumentForParamSpec> unmatchedArguments,
                             List<UnfilledParameterFromParamSpec> unmatchedParameters,
                             List<UnfilledPositionalVararg> unfilledPositionalVarargs) {
            this(callableType, callable, results, unmatchedArguments, unmatchedParameters, unfilledPositionalVarargs, List.of());
        }

        AnalyzeCalleeResults(PyCallableType callableType,
                             @Nullable PyCallable callable,
                             List<AnalyzeArgumentResult> results,
                             List<UnexpectedArgumentForParamSpec> unmatchedArguments,
                             List<UnfilledParameterFromParamSpec> unmatchedParameters,
                             List<UnfilledPositionalVararg> unfilledPositionalVarargs,
                             List<PyCallableParameter> unfilledRegularParameters) {
            this.callableType = callableType;
            this.callable = callable;
            this.results = results;
            this.unmatchedArguments = unmatchedArguments;
            this.unmatchedParameters = unmatchedParameters;
            this.unfilledPositionalVarargs = unfilledPositionalVarargs;
            this.unfilledRegularParameters = unfilledRegularParameters;
        }

        PyCallableType getCallableType() {
            return callableType;
        }

        @Nullable
        PyCallable getCallable() {
            return callable;
        }

        List<AnalyzeArgumentResult> getResults() {
            return results;
        }

        List<UnexpectedArgumentForParamSpec> getUnmatchedArguments() {
            return unmatchedArguments;
        }

        List<UnfilledParameterFromParamSpec> getUnmatchedParameters() {
            return unmatchedParameters;
        }

        List<UnfilledPositionalVararg> getUnfilledPositionalVarargs() {
            return unfilledPositionalVarargs;
        }

        List<PyCallableParameter> getUnfilledRegularParameters() {
            return unfilledRegularParameters;
        }
    }

    static class PyCalleeResults {
        private final PyType memberType;
        private final AnalyzeCalleeResults calleeResults;
        private final PyArgumentsMapping mapping;

        PyCalleeResults(PyType memberType, AnalyzeCalleeResults calleeResults, PyArgumentsMapping mapping) {
            this.memberType = memberType;
            this.calleeResults = calleeResults;
            this.mapping = mapping;
        }

        PyType getMemberType() {
            return memberType;
        }

        AnalyzeCalleeResults getCalleeResults() {
            return calleeResults;
        }

        PyArgumentsMapping getMapping() {
            return mapping;
        }
    }

    static class AnalyzeArgumentResult {
        private final PyExpression argument;
        private final @Nullable PyCallableParameter parameter;
        private final @Nullable PyType expectedType;
        private final @Nullable PyType expectedTypeAfterSubstitution;
        private final @Nullable PyType actualType;
        private final boolean isMatched;

        AnalyzeArgumentResult(PyExpression argument,
                              @Nullable PyCallableParameter parameter,
                              @Nullable PyType expectedType,
                              @Nullable PyType expectedTypeAfterSubstitution,
                              @Nullable PyType actualType,
                              boolean isMatched) {
            this.argument = argument;
            this.parameter = parameter;
            this.expectedType = expectedType;
            this.expectedTypeAfterSubstitution = expectedTypeAfterSubstitution;
            this.actualType = actualType;
            this.isMatched = isMatched;
            PyAnyType.validate(expectedTypeAfterSubstitution);
        }

        PyExpression getArgument() {
            return argument;
        }

        @Nullable
        PyCallableParameter getParameter() {
            return parameter;
        }

        @Nullable
        PyType getExpectedType() {
            return expectedType;
        }

        @Nullable
        PyType getExpectedTypeAfterSubstitution() {
            return expectedTypeAfterSubstitution;
        }

        @Nullable
        PyType getActualType() {
            return actualType;
        }

        boolean isMatched() {
            return isMatched;
        }
    }

    static class UnfilledParameterFromParamSpec {
        private final PyCallableParameter parameter;
        private final PyParamSpecType paramSpecType;

        UnfilledParameterFromParamSpec(PyCallableParameter parameter, PyParamSpecType paramSpecType) {
            this.parameter = parameter;
            this.paramSpecType = paramSpecType;
        }

        PyCallableParameter getParameter() {
            return parameter;
        }

        PyParamSpecType getParamSpecType() {
            return paramSpecType;
        }
    }

    static class UnexpectedArgumentForParamSpec {
        private final PyExpression argument;
        private final PyParamSpecType paramSpecType;

        UnexpectedArgumentForParamSpec(PyExpression argument, PyParamSpecType paramSpecType) {
            this.argument = argument;
            this.paramSpecType = paramSpecType;
        }

        PyExpression getArgument() {
            return argument;
        }

        PyParamSpecType getParamSpecType() {
            return paramSpecType;
        }
    }

    record UnfilledPositionalVararg(String varargName, @Nullable PyType expectedType) {
    }
}
