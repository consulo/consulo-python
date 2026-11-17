// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.functionTypeComments.psi.PyFunctionTypeAnnotation;
import com.jetbrains.python.impl.codeInsight.functionTypeComments.psi.PyParameterTypeList;
import com.jetbrains.python.impl.codeInsight.typeHints.PyTypeHintFile;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyClassImpl;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.impl.refactoring.PyDefUseUtil;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyAnnotationOwner;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecoratable;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyLoopStatement;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyNonlocalStatement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeCommentOwner;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.psi.PyWhileStatement;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyInstantiableType;
import com.jetbrains.python.impl.psi.types.PySelfType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.MultiMap;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.jetbrains.python.impl.codeInsight.PyDataclassesKt.parseDataclassParameters;

@ExtensionImpl
public class PyFinalInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameFinalClassesMethodsAndVariables();
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
        public void visitPyClass(PyClass node) {
            super.visitPyClass(node);

            List<PyClass> finalSuperClasses = new ArrayList<>();
            for (PyClass superClass : node.getSuperClasses(myTypeEvalContext)) {
                if (isFinal((PyDecoratable) superClass)) {
                    finalSuperClasses.add(superClass);
                }
            }
            if (!finalSuperClasses.isEmpty()) {
                String superClassList = finalSuperClasses.stream().map(it -> "'" + it.getName() + "'").collect(Collectors.joining(", "));
                registerProblem(node.getNameIdentifier(),
                                PyInspectionMessages.problemMessage(
                                    msgArgs -> PyLocalize.inspFinalSuperClassesAreMarkedAsFinalAndShouldNotBeSubclassed(msgArgs[0], msgArgs[1]),
                                    superClassList, finalSuperClasses.size()));
            }

            if (!PyiUtil.isInsideStub(node)) {
                Pair<Map<String, PyTargetExpression>, Map<String, PyTargetExpression>> finalsAndInit =
                    getClassLevelFinalsAndInitAttributes(node);
                Map<String, PyTargetExpression> classLevelFinals = finalsAndInit.getFirst();
                Map<String, PyTargetExpression> initAttributes = finalsAndInit.getSecond();
                if (!isDataclass(node)) {
                    checkClassLevelFinalsAreInitialized(classLevelFinals, initAttributes);
                }
                checkSameNameClassAndInstanceFinals(classLevelFinals, initAttributes);
            }

            checkOverridingInheritedFinalWithNewOne(node);
            checkOverridingInheritedNonFinalWithFinal(node);
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            super.visitPyFunction(node);

            PyClass cls = node.getContainingClass();
            if (cls != null) {
                boolean isOverload = PyiUtil.isOverload(node, myTypeEvalContext);
                if (!isOverload ||
                    PyiUtil.getImplementation(node, myTypeEvalContext) == null &&
                        firstOrNull(PyiUtil.getOverloads(node, myTypeEvalContext)) == node) {
                    PyFunction finalSuper = null;
                    for (PsiElement superMethod : PySuperMethodsSearch.search(node, myTypeEvalContext)) {
                        if (superMethod instanceof PyFunction function && isFinal((PyDecoratable) function)) {
                            finalSuper = function;
                            break;
                        }
                    }
                    if (finalSuper != null) {
                        String qualifiedName = finalSuper.getQualifiedName();
                        if (qualifiedName == null) {
                            PyClass containingClass = finalSuper.getContainingClass();
                            qualifiedName = (containingClass != null ? containingClass.getName() : null) + "." + finalSuper.getName();
                        }
                        registerProblem(node.getNameIdentifier(),
                                        PyInspectionMessages.problemMessage(
                                            msgArgs -> PyLocalize.inspFinalMethodMarkedAsFinalShouldNotBeOverridden(msgArgs[0]),
                                            CodifiedParam.ofReference(finalSuper, qualifiedName)));
                    }
                }
                if (!PyiUtil.isInsideStub(node)) {
                    checkInstanceFinalsOutsideInit(node);
                }

                if (PyKnownDecoratorUtil.hasAbstractDecorator(node, myTypeEvalContext)) {
                    if (isFinal((PyDecoratable) node)) {
                        registerProblem(node.getNameIdentifier(), PyLocalize.inspFinalFinalCouldNotBeMixedWithAbstractDecorators().get());
                    }
                    else if (isFinal((PyDecoratable) cls)) {
                        String message = PyLocalize.inspFinalFinalClassCouldNotContainAbstractMethods().get();
                        registerProblem(node.getNameIdentifier(), message);
                        registerProblem(cls.getNameIdentifier(), message);
                    }
                }
                else if (isFinal((PyDecoratable) node) && isFinal((PyDecoratable) cls)) {
                    registerProblem(node.getNameIdentifier(), PyLocalize.inspFinalNoNeedToMarkMethodInFinalClassAsFinal().get(),
                                    ProblemHighlightType.WEAK_WARNING);
                }
            }
            else if (isFinal((PyDecoratable) node)) {
                registerProblem(node.getNameIdentifier(), PyLocalize.inspFinalNonMethodFunctionCouldNotBeMarkedAsFinal().get());
            }

            PyFunctionTypeAnnotation comment = PyTypingTypeProvider.getFunctionTypeAnnotation(node);
            if (comment != null) {
                boolean anyFinal = false;
                for (PyExpression it : comment.getParameterTypeList().getParameterTypes()) {
                    if (resolvesToFinal(it instanceof PySubscriptionExpression subscription ? subscription.getOperand() : it)) {
                        anyFinal = true;
                        break;
                    }
                }
                if (anyFinal) {
                    registerProblem(node.getTypeComment(),
                                    PyLocalize.inspFinalFinalCouldNotBeUsedInAnnotationsForFunctionParameters().get());
                }
            }

            PyExpression returnTypeAnnotation = PyTypingTypeProvider.getReturnTypeAnnotation(node, myTypeEvalContext);
            if (returnTypeAnnotation != null) {
                if (resolvesToFinal(returnTypeAnnotation instanceof PySubscriptionExpression subscription
                    ? subscription.getOperand()
                    : returnTypeAnnotation)) {
                    PsiElement typeComment = node.getTypeComment();
                    registerProblem(typeComment != null ? typeComment : node.getAnnotation(),
                                    PyLocalize.inspFinalFinalCouldNotBeUsedInAnnotationForFunctionReturnValue().get());
                }
            }
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            super.visitPyTargetExpression(node);

            PyStatement parent = PsiTreeUtil.getParentOfType(node, PyStatement.class);
            if (parent instanceof PyImportStatementBase) {
                return;
            }

            if (parent instanceof PyTypeDeclarationStatement || parent instanceof PyGlobalStatement || parent instanceof PyNonlocalStatement) {
                PyAnnotation annotation = node.getAnnotation();
                PyExpression it = annotation != null ? annotation.getValue() : null;
                if (it != null) {
                    if (PyiUtil.isInsideStub(node) || ScopeUtil.getScopeOwner(node) instanceof PyClass) {
                        if (resolvesToFinal(it)) {
                            registerProblem(it,
                                            PyLocalize.inspFinalIfAssignedValueOmittedThereShouldBeExplicitTypeArgumentToFinal().get());
                        }
                    }
                    else {
                        if (resolvesToFinal(it instanceof PySubscriptionExpression subscription ? subscription.getOperand() : it)) {
                            registerProblem(node, PyLocalize.inspFinalFinalNameShouldBeInitializedWithValue().get());
                        }
                    }
                }
            }
            else if (!isFinal(node)) {
                checkFinalReassignment(node);
            }

            if (isFinal(node)) {
                boolean redefined = false;
                for (PsiElement it : PyUtil.multiResolveTopPriority(node, getResolveContext())) {
                    if (it != node && !PyDefUseUtil.isDefinedBefore(node, Objects.requireNonNull(it))) {
                        redefined = true;
                        break;
                    }
                }
                if (redefined) {
                    registerProblem(node, PyLocalize.inspFinalAlreadyDeclaredNameCouldNotBeRedefinedAsFinal().get());
                }
            }
        }

        @Override
        public void visitPyNamedParameter(PyNamedParameter node) {
            super.visitPyNamedParameter(node);

            if (isFinal(node)) {
                PyAnnotation annotation = node.getAnnotation();
                PsiElement anchor = annotation != null ? annotation.getValue() : null;
                registerProblem(anchor != null ? anchor : node.getTypeComment(),
                                PyLocalize.inspFinalFinalCouldNotBeUsedInAnnotationsForFunctionParameters().get());
            }
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            super.visitPyReferenceExpression(node);

            checkFinalIsOuterMost(node);
        }

        @Override
        public void visitPySubscriptionExpression(PySubscriptionExpression node) {
            if (!resolvesToFinal(node.getOperand())) {
                return;
            }
            PyExpression indexExpression = node.getIndexExpression();
            if (indexExpression == null) {
                return;
            }
            if (indexExpression instanceof PyTupleExpression tuple && tuple.getElements().length > 1) {
                registerProblem(indexExpression, PyLocalize.inspFinalCanOnlyBeParameterizedWithOneType().get());
            }
        }

        @Override
        public void visitPyForStatement(PyForStatement node) {
            super.visitPyForStatement(node);
            checkFinalInsideLoop(node);
        }

        @Override
        public void visitPyWhileStatement(PyWhileStatement node) {
            super.visitPyWhileStatement(node);
            checkFinalInsideLoop(node);
        }

        @Override
        public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
            super.visitPyAugAssignmentStatement(node);

            PyExpression target = node.getTarget();
            if (target instanceof PyQualifiedExpression qualifiedExpression) {
                checkFinalReassignment(qualifiedExpression);
            }
        }

        private Pair<Map<String, PyTargetExpression>, Map<String, PyTargetExpression>> getClassLevelFinalsAndInitAttributes(PyClass cls) {
            Map<String, PyTargetExpression> classLevelFinals = new LinkedHashMap<>();
            for (PyTargetExpression it : cls.getClassAttributes()) {
                if (isFinal(it)) {
                    classLevelFinals.put(it.getName(), it);
                }
            }

            MultiMap<String, PyTargetExpression> initAttributes = new MultiMap<>();
            PyFunction init = cls.findMethodByName(PyNames.INIT, false, myTypeEvalContext);
            if (init != null) {
                PyClassImpl.collectInstanceAttributes(init, initAttributes);
            }

            return Pair.create(classLevelFinals, firstValues(initAttributes));
        }

        private Pair<Map<String, PyTargetExpression>, Map<String, PyTargetExpression>> getDeclaredClassAndInstanceFinals(PyClass cls) {
            Map<String, PyTargetExpression> classFinals = new LinkedHashMap<>();
            Map<String, PyTargetExpression> instanceFinals = new LinkedHashMap<>();

            for (PyTargetExpression classAttribute : cls.getClassAttributes()) {
                String name = classAttribute.getName();
                if (name == null) {
                    continue;
                }

                if (isFinal(classAttribute)) {
                    Map<String, PyTargetExpression> mapToPut = classAttribute.hasAssignedValue() ? classFinals : instanceFinals;
                    mapToPut.put(name, classAttribute);
                }
            }

            PyFunction init = cls.findMethodByName(PyNames.INIT, false, myTypeEvalContext);
            if (init != null) {
                MultiMap<String, PyTargetExpression> attributesInInit = new MultiMap<>();
                PyClassImpl.collectInstanceAttributes(init, attributesInInit);
                attributesInInit.keySet().removeAll(instanceFinals.keySet());
                for (Map.Entry<String, PyTargetExpression> entry : firstValues(attributesInInit).entrySet()) {
                    if (isFinal(entry.getValue())) {
                        instanceFinals.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            return Pair.create(classFinals, instanceFinals);
        }

        private void checkClassLevelFinalsAreInitialized(Map<String, PyTargetExpression> classLevelFinals,
                                                         Map<String, PyTargetExpression> initAttributes) {
            classLevelFinals.forEach((name, psi) -> {
                if (!psi.hasAssignedValue() && !initAttributes.containsKey(name)) {
                    registerProblem(psi, PyLocalize.inspFinalFinalNameShouldBeInitializedWithValue().get());
                }
            });
        }

        private void checkSameNameClassAndInstanceFinals(Map<String, PyTargetExpression> classLevelFinals,
                                                         Map<String, PyTargetExpression> initAttributes) {
            initAttributes.forEach((name, initAttribute) -> {
                PyTargetExpression sameNameClassLevelFinal = classLevelFinals.get(name);

                if (sameNameClassLevelFinal != null && isFinal(initAttribute)) {
                    if (sameNameClassLevelFinal.hasAssignedValue()) {
                        registerProblem(initAttribute, PyLocalize.inspFinalAlreadyDeclaredNameCouldNotBeRedefinedAsFinal().get());
                    }
                    else {
                        String message = PyLocalize.inspFinalEitherInstanceAttributeOrClassAttributeCouldBeTypeHintedAsFinal().get();
                        registerProblem(sameNameClassLevelFinal, message);
                        registerProblem(initAttribute, message);
                    }
                }
            });
        }

        private void checkOverridingInheritedFinalWithNewOne(PyClass cls) {
            Pair<Map<String, PyTargetExpression>, Map<String, PyTargetExpression>> newFinals = getDeclaredClassAndInstanceFinals(cls);
            Map<String, PyTargetExpression> newClassFinals = newFinals.getFirst();
            Map<String, PyTargetExpression> newInstanceFinals = newFinals.getSecond();

            Set<String> notRegisteredClassFinals = new LinkedHashSet<>(newClassFinals.keySet());
            Set<String> notRegisteredInstanceFinals = new LinkedHashSet<>(newInstanceFinals.keySet());
            if (notRegisteredClassFinals.isEmpty() && notRegisteredInstanceFinals.isEmpty()) {
                return;
            }

            for (PyClass ancestor : cls.getAncestorClasses(myTypeEvalContext)) {
                Pair<Map<String, PyTargetExpression>, Map<String, PyTargetExpression>> inheritedFinals =
                    getDeclaredClassAndInstanceFinals(ancestor);

                checkOverridingInheritedFinalWithNewOne(newClassFinals, inheritedFinals.getFirst(), ancestor.getName(),
                                                        notRegisteredClassFinals);
                checkOverridingInheritedFinalWithNewOne(newInstanceFinals, inheritedFinals.getSecond(), ancestor.getName(),
                                                        notRegisteredInstanceFinals);

                if (notRegisteredClassFinals.isEmpty() && notRegisteredInstanceFinals.isEmpty()) {
                    break;
                }
            }
        }

        private void checkOverridingInheritedFinalWithNewOne(Map<String, PyTargetExpression> newFinals,
                                                             Map<String, PyTargetExpression> inheritedFinals,
                                                             @Nullable String ancestorName,
                                                             Set<String> notRegistered) {
            if (notRegistered.isEmpty()) {
                return;
            }

            Set<String> commonFinals = new LinkedHashSet<>(newFinals.keySet());
            commonFinals.retainAll(inheritedFinals.keySet());
            for (String commonFinal : commonFinals) {
                String qualifiedName = ancestorName + "." + commonFinal;
                registerProblem(newFinals.get(commonFinal),
                                PyInspectionMessages.problemMessage(
                                    msgArgs -> PyLocalize.inspFinalFinalAttributeCouldNotBeOverridden(msgArgs[0]), qualifiedName));
                notRegistered.remove(commonFinal);
            }
        }

        private void checkOverridingInheritedNonFinalWithFinal(PyClass cls) {
            // A final attribute initialized in a class body is a class variable (PEP 591). Overriding a writable
            // inherited attribute with a final one narrows it to read-only, which violates the Liskov substitution
            // principle. Overriding an inherited *final* attribute is reported by checkOverridingInheritedFinalWithNewOne.
            Map<String, PyTargetExpression> newClassLevelFinals = new LinkedHashMap<>();
            for (PyTargetExpression attribute : cls.getClassAttributes()) {
                if (isFinal(attribute)) {
                    String name = attribute.getName();
                    if (name != null) {
                        newClassLevelFinals.put(name, attribute);
                    }
                }
            }
            if (newClassLevelFinals.isEmpty()) {
                return;
            }

            Set<String> notRegistered = new LinkedHashSet<>(newClassLevelFinals.keySet());
            for (PyClass ancestor : cls.getAncestorClasses(myTypeEvalContext)) {
                if (notRegistered.isEmpty()) {
                    break;
                }
                for (String name : new ArrayList<>(notRegistered)) {
                    PyTargetExpression inherited = ancestor.findClassAttribute(name, false, myTypeEvalContext);
                    if (inherited == null) {
                        continue;
                    }
                    // The nearest ancestor declaring the attribute decides which message (if any) applies.
                    notRegistered.remove(name);
                    if (!isFinal(inherited) && hasExplicitType(inherited)) {
                        registerProblem(newClassLevelFinals.get(name),
                                        PyLocalize.inspFinalCanNotOverrideNonFinalAttributeWithFinal(name, String.valueOf(ancestor.getName()))
                                            .get());
                    }
                }
            }
        }

        private void checkInstanceFinalsOutsideInit(PyFunction method) {
            if (PyUtil.isInitMethod(method)) {
                return;
            }

            MultiMap<String, PyTargetExpression> instanceAttributes = new MultiMap<>();
            PyClassImpl.collectInstanceAttributes(method, instanceAttributes);
            for (PyTargetExpression it : instanceAttributes.values()) {
                if (isFinal(it)) {
                    registerProblem(it, PyLocalize.inspFinalFinalAttributeShouldBeDeclaredInClassBodyOrInit().get());
                }
            }
        }

        private void checkFinalReassignment(PyQualifiedExpression target) {
            PyExpression qualifier = target.getQualifier();
            PyType qualifierType = qualifier != null ? myTypeEvalContext.getType(qualifier) : null;
            if (qualifierType instanceof PyInstantiableType<?> instantiableType) {
                boolean isDefinition = instantiableType.isDefinition();
                PyClass cls;
                if (qualifierType instanceof PySelfType selfType) {
                    cls = selfType.getScopeClassType().getPyClass();
                }
                else if (qualifierType instanceof PyClassType classType) {
                    cls = classType.getPyClass();
                }
                else {
                    cls = null;
                }
                if (cls != null) {
                    if (isDefinition) {
                        checkClassFinalReassignment(target, cls);
                    }
                    else {
                        checkInstanceFinalReassignment(target, cls);
                    }
                    return;
                }
            }

            // TODO: revert back to PyUtil#multiResolveTopPriority when resolve into global statement is implemented
            List<@Nullable PsiElement> resolved = new ArrayList<>();
            if (target instanceof PyReferenceOwner referenceOwner) {
                for (ResolveResult result : referenceOwner.getReference(getResolveContext()).multiResolve(false)) {
                    PsiElement element = result.getElement();
                    if (element != null) {
                        resolved.add(element);
                    }
                }
            }
            else {
                resolved.addAll(PyUtil.multiResolveTopPriority(target, getResolveContext()));
            }

            ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(target);
            if (!target.isQualified() && scopeOwner != null) {
                // multiResolve finds last assignments, but we need all earlier assignments
                Scope scope = ControlFlowCache.getScope(scopeOwner);
                resolved.addAll(scope.getNamedElements(target.getReferencedName(), false));
                String name = target.getName();
                if (name != null) {
                    for (PyImportedNameDefiner definer : scope.getImportedNameDefiners()) {
                        for (RatedResolveResult result : definer.multiResolveName(name)) {
                            PsiElement element = result.getElement();
                            if (element != null) {
                                resolved.add(element);
                            }
                        }
                    }
                }
            }


            for (PsiElement element : resolved) {
                if (!(element instanceof PyTargetExpression e)) {
                    continue;
                }
                if (isFinal(e) && !PyDefUseUtil.isDefinedBefore(target, e)) {
                    registerProblem(target, PyInspectionMessages.problemMessage(
                        msgArgs -> PyLocalize.inspFinalFinalTargetCouldNotBeReassigned(msgArgs[0]), target.getName()));
                    return;
                }
                PsiElement parent = e.getParent();
                if (parent instanceof PyNonlocalStatement || parent instanceof PyGlobalStatement) {
                    boolean resolvesToFinalTarget = false;
                    for (PsiElement it : PyUtil.multiResolveTopPriority(e, getResolveContext())) {
                        if (it instanceof PyTargetExpression targetExpression && isFinal(targetExpression)) {
                            resolvesToFinalTarget = true;
                            break;
                        }
                    }
                    if (resolvesToFinalTarget) {
                        registerProblem(target, PyInspectionMessages.problemMessage(
                            msgArgs -> PyLocalize.inspFinalFinalTargetCouldNotBeReassigned(msgArgs[0]), target.getName()));
                        return;
                    }
                }
            }

            if (!target.isQualified() && scopeOwner instanceof PyClass pyClass) {
                checkInheritedClassFinalReassignmentOnClassLevel(target, pyClass);
            }
        }

        private void checkClassFinalReassignment(PyQualifiedExpression target, PyClass cls) {
            String name = target.getName();
            if (name == null) {
                return;
            }
            PyTargetExpression classAttribute = cls.findClassAttribute(name, true, myTypeEvalContext);
            if (classAttribute != null) {
                boolean isFinal = isFinal(classAttribute);
                if (!isFinal && isDataclass(cls)) {
                    PyAnnotation annotation = classAttribute.getAnnotation();
                    isFinal = resolvesToClassVarFinal(annotation != null ? annotation.getValue() : null);
                }
                if (isFinal) {
                    registerProblem(target, PyInspectionMessages.problemMessage(
                        msgArgs -> PyLocalize.inspFinalFinalTargetCouldNotBeReassigned(msgArgs[0]), name));
                }
            }
        }

        private void checkInstanceFinalReassignment(PyQualifiedExpression target, PyClass cls) {
            String name = target.getName();
            if (name == null) {
                return;
            }

            PyTargetExpression classAttribute = cls.findClassAttribute(name, false, myTypeEvalContext);
            if (classAttribute != null && isFinal(classAttribute)) {
                if (!classAttribute.hasAssignedValue() && target instanceof PyTargetExpression) {
                    ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(target);
                    if (scopeOwner instanceof PyFunction function && cls.equals(PyUtil.turnConstructorIntoClass(function))) {
                        return;
                    }
                }
                registerProblem(target, PyInspectionMessages.problemMessage(
                    msgArgs -> PyLocalize.inspFinalFinalTargetCouldNotBeReassigned(msgArgs[0]), name));
            }

            List<PyClass> ancestors = cls.getAncestorClasses(myTypeEvalContext);
            for (PyClass ancestor : ancestors) {
                PyTargetExpression inheritedClassAttribute = ancestor.findClassAttribute(name, false, myTypeEvalContext);
                if (inheritedClassAttribute != null && !inheritedClassAttribute.hasAssignedValue() && isFinal(inheritedClassAttribute)) {
                    String qualifiedName = ancestor.getName() + "." + name;
                    registerProblem(target, PyInspectionMessages.problemMessage(
                        msgArgs -> PyLocalize.inspFinalFinalTargetCouldNotBeReassigned(msgArgs[0]), qualifiedName));
                    return;
                }
            }

            List<PyClass> classes = new ArrayList<>();
            classes.add(cls);
            classes.addAll(ancestors);
            for (PyClass current : classes) {
                PyFunction init = current.findMethodByName(PyNames.INIT, false, myTypeEvalContext);
                if (init != null) {
                    MultiMap<String, PyTargetExpression> attributesInInit = new MultiMap<>();
                    PyClassImpl.collectInstanceAttributes(init, attributesInInit);
                    boolean anyFinal = false;
                    for (PyTargetExpression it : attributesInInit.get(name)) {
                        if (it != target && isFinal(it)) {
                            anyFinal = true;
                            break;
                        }
                    }
                    if (anyFinal) {
                        String qualifiedName = (cls.equals(current) ? "" : current.getName() + ".") + name;
                        registerProblem(target, PyInspectionMessages.problemMessage(
                            msgArgs -> PyLocalize.inspFinalFinalTargetCouldNotBeReassigned(msgArgs[0]), qualifiedName));
                        break;
                    }
                }
            }
        }

        private void checkInheritedClassFinalReassignmentOnClassLevel(PyQualifiedExpression target, PyClass cls) {
            String name = target.getName();
            if (name == null) {
                return;
            }
            if (PyUtil.isClassPrivateName(name)) {
                return;
            }

            for (PyClass ancestor : cls.getAncestorClasses(myTypeEvalContext)) {
                PyTargetExpression ancestorClassAttribute = ancestor.findClassAttribute(name, false, myTypeEvalContext);

                if (ancestorClassAttribute != null && ancestorClassAttribute.hasAssignedValue() && isFinal(ancestorClassAttribute)) {
                    String qualifiedName = ancestor.getName() + "." + name;
                    registerProblem(target, PyInspectionMessages.problemMessage(
                        msgArgs -> PyLocalize.inspFinalFinalTargetCouldNotBeReassigned(msgArgs[0]), qualifiedName));
                    break;
                }
            }
        }

        private void checkFinalIsOuterMost(PyReferenceExpression node) {
            if (isTopLevelInAnnotationOrTypeComment(node)) {
                return;
            }
            if (node.getParent() instanceof PySubscriptionExpression it) {
                if (it.getOperand() == node && isTopLevelInAnnotationOrTypeComment(it)) {
                    return;
                }
            }

            ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(node);
            if (scopeOwner instanceof PyClass pyClass && isDataclass(pyClass)
                && resolvesToClassVarFinal(node.getParent().getParent() instanceof PyExpression expression ? expression : null)) {
                return;
            }

            if (PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext) && resolvesToFinal(node)) {
                registerProblem(node, PyLocalize.inspFinalFinalCouldOnlyBeUsedAsOutermostType().get());
            }
        }

        private void checkFinalInsideLoop(PyLoopStatement loop) {
            loop.acceptChildren(
                new PyRecursiveElementVisitor() {
                    @Override
                    public void visitElement(PsiElement element) {
                        if (!(element instanceof ScopeOwner)) {
                            super.visitElement(element);
                        }
                    }

                    @Override
                    public void visitPyForStatement(PyForStatement node) {
                    }

                    @Override
                    public void visitPyWhileStatement(PyWhileStatement node) {
                    }

                    @Override
                    public void visitPyTargetExpression(PyTargetExpression node) {
                        if (isFinal(node)) {
                            registerProblem(node, PyLocalize.inspFinalFinalCouldNotBeUsedInsideLoop().get());
                        }
                    }
                }
            );
        }

        private boolean isFinal(PyDecoratable decoratable) {
            return PyTypingTypeProvider.isFinal(decoratable, myTypeEvalContext);
        }

        private <T extends PyAnnotationOwner & PyTypeCommentOwner> boolean isFinal(T node) {
            return PyTypingTypeProvider.isFinal(node, myTypeEvalContext);
        }

        private static boolean hasExplicitType(PyTargetExpression target) {
            return target.getAnnotationValue() != null || target.getTypeCommentAnnotation() != null;
        }

        private boolean resolvesToFinal(@Nullable PyExpression expression) {
            if (!(expression instanceof PyReferenceExpression)) {
                return false;
            }
            for (String it : PyTypingTypeProvider.resolveToQualifiedNames(expression, myTypeEvalContext)) {
                if (PyTypingTypeProvider.FINAL.equals(it) || PyTypingTypeProvider.FINAL_EXT.equals(it)) {
                    return true;
                }
            }
            return false;
        }

        private boolean resolvesToClassVar(PyExpression expression) {
            return (expression instanceof PyReferenceExpression) &&
                PyTypingTypeProvider.resolveToQualifiedNames(expression, myTypeEvalContext).contains(PyTypingTypeProvider.CLASS_VAR);
        }

        private boolean resolvesToClassVarFinal(@Nullable PyExpression expression) {
            if (expression instanceof PySubscriptionExpression it) {
                if (resolvesToClassVar(it.getOperand())) {
                    if (it.getIndexExpression() instanceof PySubscriptionExpression indexSubscription) {
                        return resolvesToFinal(indexSubscription.getOperand());
                    }
                    return resolvesToFinal(it.getIndexExpression());
                }
                return false;
            }
            return false;
        }

        private boolean isDataclass(PyClass cls) {
            return parseDataclassParameters(cls, myTypeEvalContext) != null;
        }

        private static boolean isTopLevelInAnnotationOrTypeComment(PyExpression node) {
            PsiElement parent = node.getParent();
            if (parent instanceof PyAnnotation) {
                return true;
            }
            if (parent instanceof PyExpressionStatement && parent.getParent() instanceof PyTypeHintFile) {
                return true;
            }
            if (parent instanceof PyParameterTypeList) {
                return true;
            }
            if (parent instanceof PyFunctionTypeAnnotation) {
                return true;
            }
            return false;
        }

        private static <T> @Nullable T firstOrNull(List<T> list) {
            return list.isEmpty() ? null : list.get(0);
        }

        /**
         * Kotlin {@code multiMap.toHashMap().mapValues { it.value.first() }}.
         */
        private static Map<String, PyTargetExpression> firstValues(MultiMap<String, PyTargetExpression> multiMap) {
            Map<String, PyTargetExpression> result = new LinkedHashMap<>();
            for (Map.Entry<String, Collection<PyTargetExpression>> entry : new HashMap<>(multiMap.toHashMap()).entrySet()) {
                result.put(entry.getKey(), entry.getValue().iterator().next());
            }
            return result;
        }
    }
}
