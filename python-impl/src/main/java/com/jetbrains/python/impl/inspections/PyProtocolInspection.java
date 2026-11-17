// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.controlflow.PyTypeAssertionEvaluator;
import com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyClassPattern;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeMember;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.util.QualifiedName;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import static consulo.language.editor.inspection.ProblemHighlightType.GENERIC_ERROR;
import static consulo.language.editor.inspection.ProblemHighlightType.GENERIC_ERROR_OR_WARNING;

@ExtensionImpl
public class PyProtocolInspection extends PyInspection {
    /**
     * The inspection does not check these protocol members against a subclass, because a subclass can change its constructor signature.
     */
    private static final Set<String> CONSTRUCTOR_NAMES = Set.of(PyNames.INIT, PyNames.NEW);

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameProtocolDefinitionAndUsages();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonTypeHinting();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        TypeEvalContext context = PyInspectionVisitor.getContext(session);
        if (context.getUsesExternalTypeEngine()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new Visitor(holder, context);
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        @RequiredReadAction
        public void visitPyClass(PyClass node) {
            super.visitPyClass(node);

            if (!(myTypeEvalContext.getType(node) instanceof PyClassType type)) {
                return;
            }
            List<@Nullable PyClassLikeType> superClassTypes = type.getSuperClassTypes(myTypeEvalContext);

            checkCompatibility(type, superClassTypes);
            checkProtocolBases(type, superClassTypes);
        }

        @Override
        @RequiredReadAction
        public void visitPyCallExpression(PyCallExpression node) {
            super.visitPyCallExpression(node);

            if (node.isCalleeText(PyNames.ISINSTANCE, PyNames.ISSUBCLASS)) {
                PyExpression[] arguments = node.getArguments();
                if (arguments.length < 2) {
                    return;
                }
                PyExpression classInfoArg = arguments[1];
                checkRuntimeProtocol(classInfoArg);
                checkNonDataProtocolInIssubclass(node, classInfoArg);
                checkUnsafeOverlap(node, classInfoArg);
            }
            checkNewTypeWithProtocols(node);
            checkProtocolInstantiation(node);
        }

        @Override
        @RequiredReadAction
        public void visitPyClassPattern(PyClassPattern node) {
            checkRuntimeProtocol(node.getClassNameReference());
        }

        @RequiredReadAction
        private void checkCompatibility(PyClassType type, List<@Nullable PyClassLikeType> superClassTypes) {
            for (PyClassLikeType superClassType : superClassTypes) {
                if (!(superClassType instanceof PyClassType protocol) || !PyProtocolsKt.isProtocol(protocol, myTypeEvalContext)) {
                    continue;
                }
                for (Pair<PyTypeMember, List<PyTypeMember>> it :
                    PyProtocolsKt.inspectProtocolSubclass(protocol.toInstance(), type.toInstance(), myTypeEvalContext)) {
                    List<PyTypeMember> subclassElements = it.getSecond();
                    String memberName = it.getFirst().getName();
                    if (!subclassElements.isEmpty() && (memberName == null || !CONSTRUCTOR_NAMES.contains(memberName))) {
                        checkMemberCompatibility(it.getFirst(), subclassElements, type, protocol);
                    }
                }
            }
        }

        @RequiredReadAction
        private void checkProtocolBases(PyClassType type, List<@Nullable PyClassLikeType> superClassTypes) {
            if (!PyProtocolsKt.isProtocol(type, myTypeEvalContext)) {
                return;
            }

            Predicate<@Nullable PyClassLikeType> correctBase = it -> {
                if (it == null) {
                    return true;
                }
                else {
                    String classQName = it.getClassQName();

                    return PyTypingTypeProvider.PROTOCOL.equals(classQName) ||
                        PyTypingTypeProvider.PROTOCOL_EXT.equals(classQName) ||
                        it instanceof PyClassType classType && PyProtocolsKt.isProtocol(classType, myTypeEvalContext);
                }
            };

            if (!superClassTypes.stream().allMatch(correctBase)) {
                registerProblem(type.getPyClass().getNameIdentifier(), PyLocalize.inspProtocolAllBasesProtocolMustBeProtocols().get());
            }
        }

        /**
         * Ensures runtime checks are applied only to @runtime_checkable protocols.
         */
        @RequiredReadAction
        private void checkRuntimeProtocol(PyExpression classInfoArg) {
            for (PyExpression expression : PyTypeAssertionEvaluator.expandClassInfoExpressions(classInfoArg)) {
                if (expression instanceof PyReferenceExpression referenceExpression) {
                    boolean isProtocolReference = false;
                    for (QualifiedName qName : PyResolveUtil.resolveImportedElementQNameLocally(referenceExpression)) {
                        String it = qName.toString();
                        if (it.equals(PyTypingTypeProvider.PROTOCOL) || it.equals(PyTypingTypeProvider.PROTOCOL_EXT)) {
                            isProtocolReference = true;
                            break;
                        }
                    }
                    if (isProtocolReference) {
                        registerProblem(expression,
                                        PyLocalize.inspProtocolOnlyRuntimeCheckableProtocolsCanBeUsedWithInstanceClassChecks().get(),
                                        GENERIC_ERROR);
                        continue;
                    }
                }

                PyType type = myTypeEvalContext.getType(expression);
                if (type instanceof PyClassType classType &&
                    PyProtocolsKt.isProtocol(classType, myTypeEvalContext) &&
                    !PyProtocolsKt.isRuntimeCheckable(classType, myTypeEvalContext)) {
                    registerProblem(expression,
                                    PyLocalize.inspProtocolOnlyRuntimeCheckableProtocolsCanBeUsedWithInstanceClassChecks().get(),
                                    GENERIC_ERROR);
                }
            }
        }

        /**
         * `issubclass()` is valid only for non-data protocols.
         */
        @RequiredReadAction
        private void checkNonDataProtocolInIssubclass(PyCallExpression call, PyExpression classInfoArg) {
            if (!call.isCalleeText(PyNames.ISSUBCLASS)) {
                return;
            }

            Map<PyClassType, PyExpression> protocolTypes = collectProtocolTypes(classInfoArg);
            for (Map.Entry<PyClassType, PyExpression> entry : protocolTypes.entrySet()) {
                if (isDataProtocol(entry.getKey())) {
                    registerProblem(entry.getValue(),
                                    PyLocalize.inspProtocolOnlyNonDataProtocolsCanBeUsedWithIssubclass().get(),
                                    GENERIC_ERROR);
                }
            }
        }

        /**
         * Rejects runtime checks when the argument type has an unsafe structural overlap with a protocol.
         */
        @RequiredReadAction
        private void checkUnsafeOverlap(PyCallExpression call, PyExpression classInfoArg) {
            Map<PyClassType, PyExpression> protocolTypes = collectProtocolTypes(classInfoArg);
            if (protocolTypes.isEmpty()) {
                return;
            }

            PyExpression[] arguments = call.getArguments();
            if (arguments.length < 1) {
                return;
            }
            PyExpression objectArg = arguments[0];
            PyType objectType = myTypeEvalContext.getType(objectArg);
            if (objectType == null || PyTypeChecker.isUnknown(objectType, myTypeEvalContext)) {
                return;
            }

            for (Map.Entry<PyClassType, PyExpression> entry : protocolTypes.entrySet()) {
                if (hasUnsafeOverlap(objectType, entry.getKey())) {
                    registerProblem(entry.getValue(),
                                    PyLocalize.inspProtocolRuntimeCheckableUnsafeOverlap().get(),
                                    GENERIC_ERROR);
                }
            }
        }

        /**
         * A type X unsafely overlaps with protocol P if:
         * 1. X is NOT assignable to P (structural type check fails)
         * 2. X IS assignable to the type-erased version of P (where all members have type Any)
         */
        private boolean hasUnsafeOverlap(@Nullable PyType actualType, PyClassType protocol) {
            if (PyTypeChecker.isUnknown(actualType, myTypeEvalContext)) {
                return false;
            }
            if (actualType instanceof PyUnionType unionType) {
                return unionType.getMembers().stream().anyMatch(it -> hasUnsafeOverlap(it, protocol));
            }

            if (!(actualType instanceof PyClassType classType)) {
                return false;
            }
            PyClassType instanceType = classType.toInstance();
            if (PyProtocolsKt.isProtocol(instanceType, myTypeEvalContext)) {
                return false;
            }

            List<Pair<PyTypeMember, List<PyTypeMember>>> members =
                PyProtocolsKt.inspectProtocolSubclass(protocol, instanceType, myTypeEvalContext);
            if (members.isEmpty()) {
                return false;
            }
            if (members.stream().anyMatch(it -> it.getSecond().isEmpty())) {
                return false;
            }

            return !PyTypeChecker.match(protocol.toInstance(), instanceType, myTypeEvalContext);
        }

        /**
         * Maps protocol class types to corresponding expression from the classinfo argument of `isinstance()` and `issubclass()` calls.
         */
        private Map<PyClassType, PyExpression> collectProtocolTypes(PyExpression classInfoArg) {
            Map<PyClassType, PyExpression> result = new LinkedHashMap<>();
            for (PyExpression expression : PyTypeAssertionEvaluator.expandClassInfoExpressions(classInfoArg)) {
                if (myTypeEvalContext.getType(expression) instanceof PyClassType type && PyProtocolsKt.isProtocol(type, myTypeEvalContext)) {
                    result.put(type, expression);
                }
            }
            return result;
        }

        /**
         * Data protocol is a protocol that includes any non-method members (attributes or properties).
         */
        private boolean isDataProtocol(PyClassType protocol) {
            List<Pair<PyTypeMember, List<PyTypeMember>>> members =
                PyProtocolsKt.inspectProtocolSubclass(protocol, protocol, myTypeEvalContext);
            for (Pair<PyTypeMember, List<PyTypeMember>> member : members) {
                PsiElement element = member.getFirst().getElement();
                if (element instanceof PyFunction function) {
                    if (function.getProperty() != null) {
                        return true;
                    }
                    continue;
                }
                if (element != null) {
                    return true;
                }
            }
            return false;
        }

        @RequiredReadAction
        private void checkNewTypeWithProtocols(PyCallExpression node) {
            if (!(node.getCallee() instanceof PyReferenceExpression callee)) {
                return;
            }
            PsiElement resolved = callee.followAssignmentsChain(getResolveContext()).getElement();
            if (resolved == null) {
                return;
            }
            boolean isNewTypeCall = resolved instanceof PyQualifiedNameOwner qualifiedNameOwner &&
                PyTypingTypeProvider.NEW_TYPE.equals(qualifiedNameOwner.getQualifiedName());
            if (isNewTypeCall) {
                PyExpression[] arguments = node.getArguments();
                PyExpression base = arguments.length > 1 ? arguments[1] : null;
                if (base != null) {
                    PyType type = myTypeEvalContext.getType(base);
                    if (type instanceof PyClassLikeType classLikeType && PyProtocolsKt.isProtocol(classLikeType, myTypeEvalContext)) {
                        registerProblem(base, PyLocalize.inspProtocolNewtypeCannotBeUsedWithProtocolClasses().get());
                    }
                }
            }
        }

        @RequiredReadAction
        private void checkMemberCompatibility(PyTypeMember expectedMember,
                                              List<PyTypeMember> subclassMembers,
                                              PyClassType type,
                                              PyClassType protocol) {
            for (PyTypeMember it : subclassMembers) {
                PsiElement element = it.getElement();
                if (element == null || !Objects.equals(element.getContainingFile(), type.getPyClass().getContainingFile())) {
                    continue;
                }
                @Nullable PsiElement place =
                    element instanceof PsiNameIdentifierOwner nameIdentifierOwner ? nameIdentifierOwner.getNameIdentifier() : element;
                if (!(element instanceof PsiNameIdentifierOwner nameOwner)) {
                    continue;
                }
                String elementName = nameOwner.getName();

                if (!PyTypeChecker.match(expectedMember.getType(), it.getType(), myTypeEvalContext)) {
                    PyType actualType = it.getType();
                    ProblemMessage message = PyInspectionMessages.problemMessage(
                        p -> PyLocalize.inspProtocolElementTypeIncompatibleWithProtocol(p[0], p[1]),
                        String.valueOf(elementName), CodifiedParam.ofReference(protocol.getPyClass()));
                    // The aligned diff (for structural members) with the assignability breakdown appended below it on-the-fly.
                    String diff = PyTypeDiff.diffTooltip(expectedMember.getType(), actualType, myTypeEvalContext);
                    ProblemMessage enriched = diff != null ? new ProblemMessage(message.description(), diff) : message;
                    registerProblemWithTooltip(place, enriched, GENERIC_ERROR_OR_WARNING, LocalQuickFix.EMPTY_ARRAY,
                                               () -> PyTypeCheckerInspectionProblemRegistrar.breakdownTooltip(enriched,
                                                                                                             expectedMember.getType(),
                                                                                                             actualType,
                                                                                                             myTypeEvalContext,
                                                                                                             place));
                }
                else if (PyTypeUtil.isSettable(expectedMember, myTypeEvalContext) && !PyTypeUtil.isSettable(it, myTypeEvalContext) ||
                    expectedMember.isDeletable() && !it.isDeletable()) {
                    registerProblem(place, PyInspectionMessages.problemMessage(
                        p -> PyLocalize.inspProtocolElementTypeNotWritable(p[0], p[1]),
                        String.valueOf(elementName), CodifiedParam.ofReference(protocol.getPyClass())));
                }
            }
        }

        @RequiredReadAction
        private void checkProtocolInstantiation(PyCallExpression node) {
            PyExpression calleeReferenceExpression = node.getCallee();
            if (calleeReferenceExpression instanceof PyReferenceExpression referenceExpression) {
                PsiElement cls = referenceExpression.followAssignmentsChain(getResolveContext()).getElement();
                if (cls instanceof PyClass pyClass) {
                    if (PyProtocolsKt.isProtocol(pyClass, myTypeEvalContext)) {
                        registerProblem(node, PyInspectionMessages.problemMessage(
                            p -> PyLocalize.inspProtocolCannotInstantiateProtocolClass(p[0]),
                            CodifiedParam.ofReference(pyClass)));
                    }
                }
            }
        }
    }
}
