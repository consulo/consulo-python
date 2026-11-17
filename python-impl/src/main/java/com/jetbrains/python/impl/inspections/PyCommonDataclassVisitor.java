// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.PyDataclassFieldParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.codeInsight.stdlib.PyAttrsDataclassType;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames.Attrs;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames.Dataclasses;
import com.jetbrains.python.impl.codeInsight.stdlib.PyStdlibDataclassType;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.impl.psi.types.PyStructuralType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyDelStatement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.ResolveResult;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Pair;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The framework-agnostic dataclass checks that apply to every dataclass regardless of framework
 */
class PyCommonDataclassVisitor extends PyDataclassVisitor {

    PyCommonDataclassVisitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
        super(holder, context);
    }

    @Override
    public void visitPyTargetExpression(PyTargetExpression node) {
        super.visitPyTargetExpression(node);

        checkMutatingFrozenAttribute(node);
    }

    @Override
    public void visitPyDelStatement(PyDelStatement node) {
        super.visitPyDelStatement(node);

        for (PyExpression target : node.getTargets()) {
            if (target instanceof PyReferenceExpression referenceExpression) {
                checkMutatingFrozenAttribute(referenceExpression);
            }
        }
    }

    @Override
    public void visitPyClass(PyClass node) {
        super.visitPyClass(node);

        PyDataclassParameters dataclassParameters = PyDataclassesKt.parseDataclassParameters(node, myTypeEvalContext);
        if (dataclassParameters == null) {
            return;
        }

        processAnnotationsExistence(node, dataclassParameters);

        PyNamedTupleInspection.Helper.inspectFieldsOrder(
            node,
            it -> {
                PyDataclassParameters parameters = PyDataclassesKt.parseDataclassParameters(it, myTypeEvalContext);
                return parameters != null && !parameters.getKwOnly();
            },
            (dataclassParameters.getType().getName().equals(PyStdlibDataclassType.INSTANCE.getName()) ||
                dataclassParameters.getType().isDataclassTransformBased()),
            myTypeEvalContext,
            this::registerProblem,
            it -> {
                PyClass containingClass = Objects.requireNonNull(it.getContainingClass());
                PyDataclassParameters dataclassParams =
                    Objects.requireNonNull(PyDataclassesKt.parseDataclassParameters(containingClass, myTypeEvalContext));
                PyDataclassFieldParameters fieldParams =
                    PyDataclassesKt.resolveDataclassFieldParameters(containingClass, dataclassParams, it, myTypeEvalContext);

                return (fieldParams == null || fieldParams.getInitValue() && !fieldParams.getKwOnly()) &&
                    !(fieldParams == null && it.getAnnotationValue() == null) && // skip fields that are not annotated
                    !PyTypingTypeProvider.isClassVar(it, myTypeEvalContext); // skip classvars
            },
            it -> {
                PyClass containingClass = Objects.requireNonNull(it.getContainingClass());
                PyDataclassParameters dataclassParams =
                    Objects.requireNonNull(PyDataclassesKt.parseDataclassParameters(containingClass, myTypeEvalContext));
                PyDataclassFieldParameters fieldParams =
                    PyDataclassesKt.resolveDataclassFieldParameters(containingClass, dataclassParams, it, myTypeEvalContext);
                if (fieldParams != null) {
                    return fieldParams.getHasDefault() ||
                        fieldParams.getHasDefaultFactory() ||
                        dataclassParameters.getType().getName().equals(PyAttrsDataclassType.INSTANCE.getName()) &&
                            ContainerUtil.exists(node.getMethods(), m -> {
                                PyDecoratorList decoratorList = m.getDecoratorList();
                                return decoratorList != null && decoratorList.findDecorator(it.getName() + ".default") != null;
                            });
                }
                else {
                    return it.hasAssignedValue();
                }
            }
        );
    }

    @Override
    public void visitPyBinaryExpression(PyBinaryExpression node) {
        super.visitPyBinaryExpression(node);

        String leftOperator = node.getReferencedName();
        if (leftOperator != null && PyDataclassVisitorKt.ORDER_OPERATORS.contains(leftOperator)) {
            PyClass leftClass = getInstancePyClass(node.getLeftExpression());
            if (leftClass == null) {
                return;
            }
            PyClass rightClass = getInstancePyClass(node.getRightExpression());
            if (rightClass == null) {
                return;
            }

            Pair<ClassOrder, PyDataclassParameters.@Nullable Type> left = getDataclassHierarchyOrder(leftClass, leftOperator);
            ClassOrder leftOrder = left.first;
            PyDataclassParameters.Type leftType = left.second;
            if (leftOrder == ClassOrder.MANUALLY) {
                return;
            }

            ClassOrder rightOrder = getDataclassHierarchyOrder(rightClass, PyNames.leftToRightOperatorName(leftOperator)).first;

            if (leftClass.equals(rightClass)) {
                if (leftOrder == ClassOrder.DC_UNORDERED && rightOrder != ClassOrder.MANUALLY) {
                    registerProblem(node.getPsiOperator(),
                                    PyInspectionMessages.problemMessage(
                                        a -> PyLocalize.inspDataclassesOperatorNotSupportedBetweenInstancesOfClass(a[0], a[1]),
                                        leftOperator, CodifiedParam.ofReference(leftClass)),
                                    ProblemHighlightType.GENERIC_ERROR);
                }
            }
            else {
                if (leftOrder == ClassOrder.DC_ORDERED ||
                    leftOrder == ClassOrder.DC_UNORDERED ||
                    rightOrder == ClassOrder.DC_ORDERED ||
                    rightOrder == ClassOrder.DC_UNORDERED) {
                    if (leftOrder == ClassOrder.DC_ORDERED &&
                        leftType != null && PyAttrsDataclassType.INSTANCE.getName().equals(leftType.getName()) &&
                        rightClass.isSubclass(leftClass, myTypeEvalContext)) {
                        return; // attrs allows to compare ancestor and its subclass
                    }

                    registerProblem(node.getPsiOperator(),
                                    PyInspectionMessages.problemMessage(
                                        a -> PyLocalize.inspDataclassesOperatorNotSupportedBetweenInstancesOfClasses(a[0], a[1], a[2]),
                                        leftOperator, CodifiedParam.ofReference(leftClass), CodifiedParam.ofReference(rightClass)),
                                    ProblemHighlightType.GENERIC_ERROR);
                }
            }
        }
    }

    @Override
    public void visitPyCallExpression(PyCallExpression node) {
        List<PyCallableType> callees = node.multiResolveCallee(getResolveContext());
        Set<String> calleeQNames = new LinkedHashSet<>();
        for (PyCallableType callee : callees) {
            PyCallable callable = callee.getCallable();
            String qualifiedName = callable != null ? callable.getQualifiedName() : null;
            if (qualifiedName != null) {
                calleeQNames.add(qualifiedName);
            }
        }
        String calleeQName = calleeQNames.size() == 1 ? calleeQNames.iterator().next() : null;

        if (calleeQName != null) {
            PyDataclassParameters.Type dataclassType;
            if (Dataclasses.HELPER_FUNCTIONS.contains(calleeQName)) {
                dataclassType = PyStdlibDataclassType.INSTANCE;
            }
            else if (Attrs.CLASS_HELPERS_FUNCTIONS.contains(calleeQName) || Attrs.INSTANCE_HELPER_FUNCTIONS.contains(calleeQName)) {
                dataclassType = PyAttrsDataclassType.INSTANCE;
            }
            else {
                return;
            }

            PyCallableType callableType = callees.get(0);
            PyCallExpression.PyArgumentsMapping mapping = PyCallExpressionHelper.mapArguments(node, callableType, myTypeEvalContext);

            List<PyCallableParameter> parameters = callableType.getParameters(myTypeEvalContext);
            PyCallableParameter dataclassParameter = parameters != null && !parameters.isEmpty() ? parameters.get(0) : null;
            PyExpression dataclassArgument = null;
            for (Map.Entry<PyExpression, PyCallableParameter> entry : mapping.getMappedParameters().entrySet()) {
                if (Objects.equals(entry.getValue(), dataclassParameter)) {
                    dataclassArgument = entry.getKey();
                    break;
                }
            }

            if (dataclassType.getName().equals(PyStdlibDataclassType.INSTANCE.getName())) {
                processHelperDataclassArgument(dataclassArgument, calleeQName);
            }
            else if (dataclassType.getName().equals(PyAttrsDataclassType.INSTANCE.getName())) {
                processHelperAttrsArgument(dataclassArgument, calleeQName);
            }
        }
    }

    @Override
    public void visitPyReferenceExpression(PyReferenceExpression node) {
        super.visitPyReferenceExpression(node);

        if (node.isQualified()) {
            PyClass cls = getInstancePyClass(node.getQualifier());
            if (cls == null) {
                return;
            }
            ResolveResult[] resolved = node.getReference(getResolveContext()).multiResolve(false);

            if (resolved.length > 0 &&
                Arrays.stream(resolved)
                    .map(ResolveResult::getElement)
                    .allMatch(it -> it instanceof PyTargetExpression targetExpression && getInitVarType(targetExpression) != null)) {
                registerProblem(node.getLastChild(),
                                PyInspectionMessages.problemMessage(
                                    a -> PyLocalize.inspDataclassesObjectCouldHaveNoAttributeBecauseItDeclaredAsInitOnly(a[0], a[1]),
                                    CodifiedParam.ofReference(cls), node.getName()),
                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
            }
        }
    }

    private void checkMutatingFrozenAttribute(PyQualifiedExpression expression) {
        PyClass cls = getInstancePyClass(expression.getQualifier());
        if (cls == null) {
            return;
        }

        if (PyDataclassesKt.isFrozenDataclass(cls, myTypeEvalContext)) {
            registerProblem(expression,
                            PyInspectionMessages.problemMessage(a -> PyLocalize.inspDataclassesObjectAttributeReadOnly(a[0], a[1]),
                                                                CodifiedParam.ofReference(cls), expression.getName()),
                            ProblemHighlightType.GENERIC_ERROR);
        }
    }

    private Pair<ClassOrder, PyDataclassParameters.@Nullable Type> getDataclassHierarchyOrder(PyClass cls, @Nullable String operator) {
        Pair<ClassOrder, PyDataclassParameters.@Nullable Type> seenUnordered = null;

        for (PyClass current : StreamEx.of(cls).append(cls.getAncestorClasses(myTypeEvalContext))) {
            Pair<ClassOrder, PyDataclassParameters.@Nullable Type> order = getDataclassOrder(current, operator);

            // `order=False` just does not add comparison methods
            // but it makes sense when no one in the hierarchy defines any of such methods
            if (order.first == ClassOrder.DC_UNORDERED) {
                seenUnordered = order;
            }
            else if (order.first != ClassOrder.UNKNOWN) {
                return order;
            }
        }

        return seenUnordered != null ? seenUnordered : Pair.create(ClassOrder.UNKNOWN, null);
    }

    private Pair<ClassOrder, PyDataclassParameters.@Nullable Type> getDataclassOrder(PyClass cls, @Nullable String operator) {
        PyClassLikeType type = cls.getType(myTypeEvalContext);
        if (operator != null && type != null) {
            List<?> members = type.resolveMember(operator, null, AccessDirection.READ, getResolveContext(), false);
            if (members != null && !members.isEmpty()) {
                return Pair.create(ClassOrder.MANUALLY, null);
            }
        }

        PyDataclassParameters parameters = PyDataclassesKt.parseDataclassParameters(cls, myTypeEvalContext);
        if (parameters == null) {
            return Pair.create(ClassOrder.UNKNOWN, null);
        }
        return parameters.getOrder()
            ? Pair.create(ClassOrder.DC_ORDERED, parameters.getType())
            : Pair.create(ClassOrder.DC_UNORDERED, parameters.getType());
    }

    private void processAnnotationsExistence(PyClass cls, PyDataclassParameters dataclassParameters) {
        if (dataclassParameters.getType() == PyStdlibDataclassType.INSTANCE ||
            dataclassParameters.getType().isDataclassTransformBased() ||
            PyEvaluator.evaluateAsBoolean(PyUtil.peelArgument(dataclassParameters.getOthers().get("auto_attribs")), false)) {
            cls.processClassLevelDeclarations((element, state) -> {
                if (element instanceof PyTargetExpression targetExpression
                    && targetExpression.getAnnotation() == null
                    && PyDataclassesKt.resolveDataclassFieldParameters(cls, dataclassParameters, targetExpression, myTypeEvalContext) != null) {
                    registerProblem(targetExpression,
                                    PyInspectionMessages.problemMessage(a -> PyLocalize.inspDataclassesAttributeLacksTypeAnnotation(a[0]),
                                                                        targetExpression.getName()),
                                    ProblemHighlightType.GENERIC_ERROR);
                }

                return true;
            });
        }
    }

    private void processHelperDataclassArgument(@Nullable PyExpression argument, String calleeQName) {
        if (argument == null) {
            return;
        }

        boolean allowDefinition = Dataclasses.DATACLASSES_FIELDS.equals(calleeQName);

        PyType type = myTypeEvalContext.getType(argument);
        boolean allowSubclass = !Dataclasses.DATACLASSES_ASDICT.equals(calleeQName);
        if (!isExpectedDataclass(type, PyStdlibDataclassType.INSTANCE, allowDefinition, true, allowSubclass)) {
            ProblemMessage message;
            if (allowDefinition) {
                message = PyInspectionMessages.problemMessage(a -> PyLocalize.inspDataclassesMethodShouldBeCalledOnDataclassInstancesOrTypes(a[0]),
                                                              calleeQName);
            }
            else {
                message = PyInspectionMessages.problemMessage(a -> PyLocalize.inspDataclassesMethodShouldBeCalledOnDataclassInstances(a[0]),
                                                              calleeQName);
            }

            registerProblem(argument, message);
        }
    }

    private void processHelperAttrsArgument(@Nullable PyExpression argument, String calleeQName) {
        if (argument == null) {
            return;
        }

        boolean instance = Attrs.INSTANCE_HELPER_FUNCTIONS.contains(calleeQName);

        PyType type = myTypeEvalContext.getType(argument);
        if (!isExpectedDataclass(type, PyAttrsDataclassType.INSTANCE, !instance, instance, true)) {
            ProblemMessage message;
            if (instance) {
                message = PyInspectionMessages.problemMessage(a -> PyLocalize.inspDataclassesMethodShouldBeCalledOnAttrsInstances(a[0]),
                                                              calleeQName);
            }
            else {
                message = PyInspectionMessages.problemMessage(a -> PyLocalize.inspDataclassesMethodShouldBeCalledOnAttrsTypes(a[0]),
                                                              calleeQName);
            }

            registerProblem(argument, message);
        }
    }

    private boolean isExpectedDataclass(@Nullable PyType type,
                                        PyDataclassParameters.@Nullable Type dataclassType,
                                        boolean allowDefinition,
                                        boolean allowInstance,
                                        boolean allowSubclass) {
        if (type instanceof PyStructuralType || PyTypeChecker.isUnknown(type, myTypeEvalContext)) {
            return true;
        }
        if (type instanceof PyUnionType unionType) {
            return ContainerUtil.exists(unionType.getMembers(),
                                        it -> isExpectedDataclass(it, dataclassType, allowDefinition, allowInstance, allowSubclass));
        }

        return type instanceof PyClassType classType &&
            (allowDefinition || !classType.isDefinition()) &&
            (allowInstance || classType.isDefinition()) &&
            (
                Objects.equals(PyDataclassesKt.getDataclassKind(classType.getPyClass(), myTypeEvalContext), dataclassType) ||
                    allowSubclass && ContainerUtil.exists(classType.getAncestorTypes(myTypeEvalContext),
                                                          it -> isExpectedDataclass(it, dataclassType, true, false, false))
            );
    }

    enum ClassOrder {
        MANUALLY, DC_ORDERED, DC_UNORDERED, UNKNOWN
    }
}
