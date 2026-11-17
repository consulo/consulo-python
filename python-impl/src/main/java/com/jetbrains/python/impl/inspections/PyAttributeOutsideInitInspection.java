// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.inspections.quickfix.AddFieldQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.PyMoveAttributeToInitQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyClassImpl;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.testing.PythonUnitTestDetectorsKt;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: ktisha
 * <p>
 * Inspection to detect situations, where instance attribute is defined outside __init__ function.
 */
@ExtensionImpl
public final class PyAttributeOutsideInitInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameAttributeOutsideInit();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonClassStructure();
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
        TypeEvalContext context = PyInspectionVisitor.getContext(session);
        if (context.getUsesExternalTypeEngine()) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new Visitor(holder, context);
    }


    private static class Visitor extends PyInspectionVisitor {
        Visitor(@Nullable ProblemsHolder holder,
                TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            PyClass containingClass = node.getContainingClass();
            if (containingClass == null) {
                return;
            }
            String name = node.getName();
            if (name != null && name.startsWith("_")) {
                return;
            }
            if (!isApplicable(containingClass, myTypeEvalContext)) {
                return;
            }
            if (node.getModifier() != null) {
                return;
            }

            Map<String, Property> localProperties = containingClass.getProperties();
            MultiMap<String, PyTargetExpression> declaredAttributes = new MultiMap<>();
            Set<String> inheritedProperties = new HashSet<>();

            StreamEx.of(containingClass.getClassAttributes())
                .filter(attribute -> !localProperties.containsKey(attribute.getName()))
                .forEach(attribute -> declaredAttributes.putValue(attribute.getName(), attribute));

            PyFunction initMethod = containingClass.findMethodByName(PyNames.INIT, false, myTypeEvalContext);
            if (initMethod != null) {
                PyClassImpl.collectInstanceAttributes(initMethod, declaredAttributes);
            }
            for (PyClass superClass : containingClass.getAncestorClasses(myTypeEvalContext)) {
                PyFunction superInit = superClass.findMethodByName(PyNames.INIT, false, myTypeEvalContext);
                if (superInit != null) {
                    PyClassImpl.collectInstanceAttributes(superInit, declaredAttributes);
                }

                for (PyTargetExpression classAttr : superClass.getClassAttributes()) {
                    declaredAttributes.putValue(classAttr.getName(), classAttr);
                }

                inheritedProperties.addAll(superClass.getProperties().keySet());
            }

            MultiMap<String, PyTargetExpression> attributes = new MultiMap<>();
            PyClassImpl.collectInstanceAttributes(node, attributes);

            for (PyTargetExpression attribute : attributes.values()) {
                String attributeName = attribute.getName();
                if (attributeName == null) {
                    continue;
                }
                if (!declaredAttributes.containsKey(attributeName) &&
                    !inheritedProperties.contains(attributeName) &&
                    !localProperties.containsKey(attributeName) &&
                    !isDefinedByProperty(attribute, localProperties.values(), declaredAttributes)) {
                    PyExpression assignedValue = attribute.findAssignedValue();
                    if (assignedValue == null) {
                        continue;
                    }

                    if (expressionReferencesLocalName(assignedValue, node)) {
                        registerProblem(attribute,
                                        PyInspectionMessages.problemMessage(args -> PyLocalize.inspAttributeOutsideInit(args[0]), attributeName),
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                        new AddFieldQuickFix(attributeName, "None", containingClass.getName(), false));
                    }
                    else {
                        registerProblem(attribute,
                                        PyInspectionMessages.problemMessage(args -> PyLocalize.inspAttributeOutsideInit(args[0]), attributeName),
                                        ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                        new PyMoveAttributeToInitQuickFix(),
                                        new AddFieldQuickFix(attributeName, "None", containingClass.getName(), false));
                    }
                }
            }
        }
    }

    private static boolean expressionReferencesLocalName(PyExpression assignedValue, PyFunction function) {
        Collection<PyReferenceExpression> references = PsiTreeUtil.collectElementsOfType(assignedValue, PyReferenceExpression.class);
        for (PyReferenceExpression reference : references) {
            if (reference.isQualified()) {
                continue;
            }
            Collection<PsiElement> resolved = PyResolveUtil.resolveLocally(reference);
            if (resolved.isEmpty()) {
                continue;
            }
            if (ContainerUtil.exists(resolved, it -> it instanceof PyParameter && ((PyParameter) it).isSelf())) {
                continue;
            }
            if (ContainerUtil.exists(resolved, it -> function == ScopeUtil.getScopeOwner(it))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDefinedByProperty(PyTargetExpression attribute,
                                               Collection<Property> properties,
                                               MultiMap<String, PyTargetExpression> attributesInInit) {
        return StreamEx.of(properties)
            .filter(it -> isSetBy(attribute, it))
            .anyMatch(it -> attributesInInit.containsKey(it.getName()));
    }

    private static boolean isApplicable(PyClass containingClass, TypeEvalContext context) {
        return !PythonUnitTestDetectorsKt.isTestClass(containingClass, context) &&
            !containingClass.isSubclass("django.db.models.base.Model", context);
    }

    private static @Nullable Collection<PyTargetExpression> getSetterTargetExpressions(Property property) {
        if (!property.getSetter().isDefined() || property.getSetter().value() == null) {
            return null;
        }
        PyFunction setter = property.getSetter().value().asMethod();
        if (setter == null) {
            return null;
        }
        return StreamEx.of(ControlFlowCache.getScope(setter).getTargetExpressions())
            .filter(PyUtil::isInstanceAttribute)
            .toList();
    }

    /**
     * Check whether the {@code property} sets the {@code attribute} provided.
     */
    private static boolean isSetBy(PyTargetExpression attribute, Property property) {
        Collection<PyTargetExpression> propertyTargetExpressions = getSetterTargetExpressions(property);
        return propertyTargetExpressions != null &&
            attribute.getName() != null &&
            StreamEx.of(propertyTargetExpressions)
                .map(targetExpression -> targetExpression.getName())
                .nonNull()
                .anyMatch(name -> name.equals(attribute.getName()));
    }
}
