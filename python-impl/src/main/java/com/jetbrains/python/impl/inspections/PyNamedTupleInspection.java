// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames.Dataclasses;
import com.jetbrains.python.impl.codeInsight.stdlib.PyNamedTupleTypeProvider;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@ExtensionImpl
public class PyNamedTupleInspection extends PyInspection {
    public static final class Helper {
        private Helper() {
        }

        /**
         * Kotlin {@code (PsiElement, String, ProblemHighlightType) -> Unit} callback.
         */
        @FunctionalInterface
        public interface ProblemCallback {
            void invoke(PsiElement element, String message, ProblemHighlightType highlightType);
        }

        @RequiredReadAction
        public static void inspectFieldsOrder(PyClass cls,
                                              Predicate<PyClass> classFieldsFilter,
                                              boolean checkInheritedOrder,
                                              TypeEvalContext context,
                                              ProblemCallback callback) {
            inspectFieldsOrder(cls, classFieldsFilter, checkInheritedOrder, context, callback, it -> true);
        }

        @RequiredReadAction
        public static void inspectFieldsOrder(PyClass cls,
                                              Predicate<PyClass> classFieldsFilter,
                                              boolean checkInheritedOrder,
                                              TypeEvalContext context,
                                              ProblemCallback callback,
                                              Predicate<PyTargetExpression> fieldsFilter) {
            inspectFieldsOrder(cls, classFieldsFilter, checkInheritedOrder, context, callback, fieldsFilter,
                               PyTargetExpression::hasAssignedValue);
        }

        @RequiredReadAction
        public static void inspectFieldsOrder(PyClass cls,
                                              Predicate<PyClass> classFieldsFilter,
                                              boolean checkInheritedOrder,
                                              TypeEvalContext context,
                                              ProblemCallback callback,
                                              Predicate<PyTargetExpression> fieldsFilter,
                                              Predicate<PyTargetExpression> hasAssignedValue) {
            LocalFieldsProcessor fieldsProcessor =
                classFieldsFilter.test(cls) ? processFields(cls, fieldsFilter, hasAssignedValue, context) : null;

            if ((fieldsProcessor == null || fieldsProcessor.fieldsWithoutDefaultValue.isEmpty()) && !checkInheritedOrder) {
                return;
            }

            List<PyClass> ancestors = cls.getAncestorClasses(context);
            Set<String> attributeNames = new HashSet<>();
            List<Ancestor> ancestorKinds = new ArrayList<>();
            List<PyClass> reversedAncestors = new ArrayList<>(ancestors);
            Collections.reverse(reversedAncestors);
            for (PyClass ancestor : reversedAncestors) {
                Ancestor kind;
                if (!classFieldsFilter.test(ancestor)) {
                    kind = Ancestor.FILTERED;
                }
                else {
                    LocalFieldsProcessor processor = processFields(ancestor, fieldsFilter, hasAssignedValue, context);
                    if (!processor.fieldsWithDefaultValue.isEmpty()) {
                        kind = Ancestor.HAS_FIELD_WITH_DEFAULT_VALUE;
                    }
                    else if (!processor.fieldsWithoutDefaultValue.isEmpty() &&
                        !attributeNames.containsAll(processor.fieldsWithoutDefaultValue.stream()
                                                        .map(PyTargetExpression::getName)
                                                        .collect(Collectors.toList()))) {
                        kind = Ancestor.HAS_FIELD_WITHOUT_DEFAULT_VALUE;
                    }
                    else {
                        kind = Ancestor.FILTERED;
                    }
                }

                ancestorKinds.add(kind);
                for (PyTargetExpression attribute : ancestor.getClassAttributes()) {
                    String attributeName = attribute.getName();
                    if (attributeName != null) {
                        attributeNames.add(attributeName);
                    }
                }
            }
            Collections.reverse(ancestorKinds);

            if (checkInheritedOrder) {
                PyClass seenAncestorHavingFieldWithDefaultValue = null;
                for (int i = Math.min(ancestors.size(), ancestorKinds.size()) - 1; i >= 0; i--) {
                    PyClass ancestor = ancestors.get(i);
                    Ancestor ancestorKind = ancestorKinds.get(i);
                    if (ancestorKind == Ancestor.HAS_FIELD_WITH_DEFAULT_VALUE) {
                        seenAncestorHavingFieldWithDefaultValue = ancestor;
                    }
                    else if (ancestorKind == Ancestor.HAS_FIELD_WITHOUT_DEFAULT_VALUE && seenAncestorHavingFieldWithDefaultValue != null) {
                        String msg = PyLocalize.inspNamedTupleDefaultValueOrderInherited(
                            String.valueOf(ancestor.getName()),
                            String.valueOf(seenAncestorHavingFieldWithDefaultValue.getName())
                        ).get();
                        callback.invoke(cls.getSuperClassExpressionList(), msg, ProblemHighlightType.GENERIC_ERROR);
                        break;
                    }
                }
            }

            if (fieldsProcessor == null) {
                return;
            }

            Set<String> ancestorFieldNames = new HashSet<>();
            for (PyClass ancestor : ancestors) {
                for (PyTargetExpression attribute : ancestor.getClassAttributes()) {
                    ancestorFieldNames.add(attribute.getName());
                }
            }
            List<PyTargetExpression> fieldsWithoutDefaultNotOverriden = new ArrayList<>();
            for (PyTargetExpression field : fieldsProcessor.fieldsWithoutDefaultValue) {
                if (!ancestorFieldNames.contains(field.getName())) {
                    fieldsWithoutDefaultNotOverriden.add(field);
                }
            }

            if (!fieldsWithoutDefaultNotOverriden.isEmpty()) {
                if (ancestorKinds.contains(Ancestor.HAS_FIELD_WITH_DEFAULT_VALUE)) {
                    PsiElement classNameElement = cls.getNameIdentifier();
                    if (classNameElement != null) {
                        List<String> names = new ArrayList<>();
                        for (int i = 0; i < Math.min(ancestors.size(), ancestorKinds.size()); i++) {
                            if (ancestorKinds.get(i) == Ancestor.HAS_FIELD_WITH_DEFAULT_VALUE) {
                                names.add("'" + ancestors.get(i).getName() + "'");
                            }
                        }
                        String ancestorNames = String.join(", ", names);

                        String msg = PyLocalize.inspNamedTupleDefaultValueOrderSuperclass(ancestorNames).get();
                        callback.invoke(fieldsWithoutDefaultNotOverriden.get(0), msg, ProblemHighlightType.GENERIC_ERROR);
                    }
                }
                PyTargetExpression lastField = fieldsWithoutDefaultNotOverriden.get(fieldsWithoutDefaultNotOverriden.size() - 1);
                for (PyTargetExpression field : fieldsProcessor.fieldsWithDefaultValue) {
                    if (!PyPsiUtils.isBefore(field, lastField)) {
                        break;
                    }
                    callback.invoke(field, PyLocalize.inspNamedTupleDefaultValueOrderLocal().get(), ProblemHighlightType.GENERIC_ERROR);
                }
            }
        }
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNamedTuple();
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

    private static LocalFieldsProcessor processFields(PyClass cls,
                                                      Predicate<PyTargetExpression> filter,
                                                      Predicate<PyTargetExpression> hasAssignedValue,
                                                      TypeEvalContext context) {
        boolean isDataclass = PyDataclassesKt.parseDataclassParameters(cls, context) != null;
        LocalFieldsProcessor fieldsProcessor = new LocalFieldsProcessor(filter, hasAssignedValue, isDataclass, context);
        cls.processClassLevelDeclarations(fieldsProcessor);
        return fieldsProcessor;
    }

    private enum Ancestor {
        FILTERED, HAS_FIELD_WITH_DEFAULT_VALUE, HAS_FIELD_WITHOUT_DEFAULT_VALUE
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        @RequiredReadAction
        public void visitPyClass(PyClass node) {
            super.visitPyClass(node);

            if (LanguageLevel.forElement(node).isAtLeast(LanguageLevel.PYTHON36) &&
                PyNamedTupleTypeProvider.Helper.isTypingNamedTupleDirectInheritor(node, myTypeEvalContext)) {
                Helper.inspectFieldsOrder(node, it -> it == node, false,
                                          myTypeEvalContext,
                                          this::registerProblem);
            }
        }
    }

    private static class LocalFieldsProcessor implements PsiScopeProcessor {
        private final Predicate<PyTargetExpression> filter;
        private final Predicate<PyTargetExpression> hasAssignedValue;
        private final boolean isDataclass;
        private final TypeEvalContext context;

        final List<PyTargetExpression> fieldsWithDefaultValue = new ArrayList<>();
        final List<PyTargetExpression> fieldsWithoutDefaultValue = new ArrayList<>();
        boolean kwOnlyMarkerVisited = false;

        LocalFieldsProcessor(Predicate<PyTargetExpression> filter,
                             Predicate<PyTargetExpression> hasAssignedValue,
                             boolean isDataclass,
                             TypeEvalContext context) {
            this.filter = filter;
            this.hasAssignedValue = hasAssignedValue;
            this.isDataclass = isDataclass;
            this.context = context;
        }

        @Override
        public boolean execute(PsiElement element, ResolveState state) {
            if (element instanceof PyTargetExpression targetExpression && filter.test(targetExpression)) {
                if (isDataclass && isKwOnlyMarker(targetExpression, context)) {
                    kwOnlyMarkerVisited = true;
                }
                else if (kwOnlyMarkerVisited) {
                    return true;
                }
                else if (hasAssignedValue.test(targetExpression)) {
                    fieldsWithDefaultValue.add(targetExpression);
                }
                else {
                    fieldsWithoutDefaultValue.add(targetExpression);
                }
            }
            return true;
        }

        private static boolean isKwOnlyMarker(PyTargetExpression field, TypeEvalContext context) {
            return context.getType(field) instanceof PyClassType classType &&
                Dataclasses.DATACLASSES_KW_ONLY.equals(classType.getClassQName());
        }
    }
}
