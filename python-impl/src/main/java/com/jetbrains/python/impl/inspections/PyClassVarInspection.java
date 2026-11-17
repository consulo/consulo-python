// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.typeHints.PyTypeHintFile;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.psi.types.PySelfType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyAnnotationOwner;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeCommentOwner;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ExtensionImpl
public class PyClassVarInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameClassVarVariables();
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
        public void visitPyTargetExpression(PyTargetExpression node) {
            checkClassVarReassignment(node);
            checkClassVarDeclaration(node);
            checkAssignedValue(node);
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            if (!PyTypingTypeProvider.isInsideTypeHint(node, myTypeEvalContext)) {
                return;
            }
            if (!resolvesToClassVar(node)) {
                return;
            }

            PyExpression classVar = node;
            PsiElement classVarParent = classVar.getParent();
            // promote analyzing ClassVar to the subscription expression wrapping it
            if (classVarParent instanceof PySubscriptionExpression subscriptionExpression && subscriptionExpression.getOperand() == classVar) {
                classVar = subscriptionExpression;
                checkClassVarParameterization(subscriptionExpression);
            }

            PsiElement parent = classVar.getParent();

            if (parent instanceof PyAssignmentStatement) {
                registerProblem(classVar, PyLocalize.inspClassVarIsNotAllowedHere().get());
                return;
            }

            boolean reportNestedClassVar;
            if (parent instanceof PyAnnotation) {
                reportNestedClassVar = false;
            }
            else if (parent instanceof PyExpressionStatement) {
                reportNestedClassVar = !(parent.getParent() instanceof PyTypeHintFile); // type comment
            }
            else if (parent instanceof PyTupleExpression tupleExpression) {
                reportNestedClassVar = !isWrappedWithTypingAnnotated(tupleExpression);
            }
            else {
                reportNestedClassVar = true;
            }

            if (reportNestedClassVar) {
                registerProblem(classVar,
                                PyLocalize.inspClassVarCanNotBeNested().get());
            }
        }

        private boolean isWrappedWithTypingAnnotated(PyTupleExpression tupleExpression) {
            PsiElement parentOfTuple = tupleExpression.getParent();
            return (parentOfTuple instanceof PySubscriptionExpression subscriptionExpression
                && ContainerUtil.exists(PyTypingTypeProvider.resolveToQualifiedNames(subscriptionExpression.getOperand(), myTypeEvalContext),
                                        it -> it.equals(PyTypingTypeProvider.ANNOTATED) || it.equals(PyTypingTypeProvider.ANNOTATED_EXT)));
        }

        private void checkClassVarParameterization(PySubscriptionExpression node) {
            PyExpression operand = node.getOperand();
            if (resolvesToClassVar(operand)) {
                PyExpression indexExpression = node.getIndexExpression();
                if (indexExpression != null) {
                    if (indexExpression instanceof PyTupleExpression tupleExpression && tupleExpression.getElements().length > 1) {
                        registerProblem(indexExpression, PyLocalize.inspClassVarCanOnlyBeParameterizedWithOneType().get());
                    }
                    else {
                        Ref<PyType> typeRef = PyTypingTypeProvider.getType(indexExpression, myTypeEvalContext);
                        if (typeRef == null) {
                            registerProblem(indexExpression, PyLocalize.inspClassVarNotAValidType().get());
                        }
                        Collection<PyReferenceExpression> references =
                            PsiTreeUtil.findChildrenOfAnyType(indexExpression, false, PyReferenceExpression.class);
                        for (PyReferenceExpression reference : references) {
                            // TODO consulo: Ref.deref missing
                            Ref<PyType> referenceTypeRef = PyTypingTypeProvider.getType(reference, myTypeEvalContext);
                            PyType referenceType = referenceTypeRef != null ? referenceTypeRef.get() : null;
                            if (referenceType instanceof PyTypeParameterType && !(referenceType instanceof PySelfType)) {
                                registerProblem(reference,
                                                PyLocalize.inspClassVarCanNotIncludeTypeVariables().get());
                            }
                        }
                    }
                }
            }
        }

        private void checkAssignedValue(PyTargetExpression node) {
            if (!node.hasAssignedValue()) {
                return;
            }
            PyAnnotation annotation = node.getAnnotation();
            PyExpression annotationValue = annotation != null ? annotation.getValue() : null;
            if (annotationValue != null) {
                if (annotationValue instanceof PySubscriptionExpression subscriptionExpression && resolvesToClassVar(subscriptionExpression.getOperand())) {
                    PyType expectedType = myTypeEvalContext.getType(node);
                    PyExpression assignedValue = node.findAssignedValue();
                    PyType actualType = assignedValue != null ? myTypeEvalContext.getType(assignedValue) : null;
                    if (expectedType != null && actualType != null &&
                        !PyTypeChecker.match(expectedType, actualType, myTypeEvalContext)
                    ) {
                        String expectedName = PythonDocumentationProvider.getTypeName(expectedType, myTypeEvalContext);
                        String actualName = PythonDocumentationProvider.getTypeName(actualType, myTypeEvalContext);
                        registerProblem(node.findAssignedValue(),
                                        PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(a[0], a[1]),
                                                                            expectedName, actualName));
                    }
                }
            }
        }

        @Override
        public void visitPyNamedParameter(PyNamedParameter node) {
            super.visitPyNamedParameter(node);
            if (isClassVar(node)) {
                PyAnnotation annotation = node.getAnnotation();
                PyExpression annotationValue = annotation != null ? annotation.getValue() : null;
                registerProblem(annotationValue != null ? annotationValue : node.getTypeComment(),
                                PyLocalize.inspClassVarCanNotBeUsedInAnnotationsForFunctionParameters().get());
            }
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            super.visitPyFunction(node);
            PyExpression returnTypeAnnotation = PyTypingTypeProvider.getReturnTypeAnnotation(node, myTypeEvalContext);
            if (returnTypeAnnotation != null) {
                if (resolvesToClassVar(returnTypeAnnotation instanceof PySubscriptionExpression subscriptionExpression
                                       ? subscriptionExpression.getOperand()
                                       : returnTypeAnnotation)) {
                    PsiElement typeComment = node.getTypeComment();
                    PyAnnotation annotation = node.getAnnotation();
                    registerProblem(typeComment != null ? typeComment : annotation != null ? annotation.getValue() : null,
                                    PyLocalize.inspClassVarCanNotBeUsedInAnnotationForFunctionReturnValue().get());
                }
            }
        }

        private void checkClassVarDeclaration(PyTargetExpression target) {
            PsiElement scopeOwner = ScopeUtil.getScopeOwner(target);
            if (scopeOwner instanceof PyClass pyClass) {
                checkInheritedClassClassVarReassignmentOnClassLevel(target, pyClass);
            }
            else {
                if (isClassVar(target)) {
                    PsiElement typeComment = target.getTypeComment();
                    PyAnnotation annotation = target.getAnnotation();
                    registerProblem(typeComment != null ? typeComment : annotation != null ? annotation.getValue() : null,
                                    PyLocalize.inspClassVarCanBeUsedOnlyInClassBody().get());
                }
            }
        }

        private void checkClassVarReassignment(PyTargetExpression target) {
            PyExpression qualifier = target.getQualifier();
            PyType qualifierType = qualifier != null ? myTypeEvalContext.getType(qualifier) : null;
            if (qualifierType instanceof PyClassType classType && !classType.isDefinition()) {
                checkInstanceClassVarReassignment(target, classType.getPyClass());
            }
        }

        private void checkInheritedClassClassVarReassignmentOnClassLevel(PyTargetExpression target, PyClass cls) {
            String name = target.getName();
            if (name == null) {
                return;
            }
            for (PyClass ancestor : cls.getAncestorClasses(myTypeEvalContext)) {
                PyTargetExpression ancestorClassAttribute = ancestor.findClassAttribute(name, false, myTypeEvalContext);
                if (ancestorClassAttribute != null && hasExplicitType(ancestorClassAttribute) && hasExplicitType(target)) {
                    // PEP 591: a final attribute initialized in a class body is a class variable, not an instance variable,
                    // so it must not be reported as overriding a class variable with an instance one. The illegal narrowing
                    // of a writable attribute to a final one is reported separately by PyFinalInspection
                    if (isClassVar(ancestorClassAttribute) && !isClassVar(target) && !isFinalClassVariable(target)) {
                        registerProblem(target, PyInspectionMessages.problemMessage(a -> PyLocalize.inspClassVarCanNotOverrideClassVariable(a[0], a[1]),
                                                                                    name, CodifiedParam.ofReference(ancestor)));
                        break;
                    }
                    if (!isClassVar(ancestorClassAttribute) && isClassVar(target)) {
                        registerProblem(target, PyInspectionMessages.problemMessage(a -> PyLocalize.inspClassVarCanNotOverrideInstanceVariable(a[0], a[1]),
                                                                                    name, CodifiedParam.ofReference(ancestor)));
                        break;
                    }
                }
            }
        }

        private void checkInstanceClassVarReassignment(PyQualifiedExpression target, PyClass cls) {
            String name = target.getName();
            if (name == null) {
                return;
            }
            List<PyClass> classes = new ArrayList<>();
            classes.add(cls);
            classes.addAll(cls.getAncestorClasses(myTypeEvalContext));
            for (PyClass ancestor : classes) {
                PyTargetExpression inheritedClassAttribute = ancestor.findClassAttribute(name, false, myTypeEvalContext);
                if (inheritedClassAttribute != null && isClassVar(inheritedClassAttribute)) {
                    registerProblem(target, PyInspectionMessages.problemMessage(a -> PyLocalize.inspClassVarCanNotBeAssignedToInstance(a[0]), name));
                    return;
                }
            }
        }

        private static boolean hasExplicitType(PyTargetExpression target) {
            return target.getAnnotationValue() != null || target.getTypeCommentAnnotation() != null;
        }

        private <T extends PyAnnotationOwner & PyTypeCommentOwner> boolean isClassVar(T owner) {
            return PyTypingTypeProvider.isClassVar(owner, myTypeEvalContext);
        }

        private boolean isFinalClassVariable(PyTargetExpression target) {
            return target.hasAssignedValue() && PyTypingTypeProvider.isFinal(target, myTypeEvalContext);
        }

        private boolean resolvesToClassVar(@Nullable PyExpression expression) {
            return expression instanceof PyReferenceExpression &&
                ContainerUtil.exists(PyTypingTypeProvider.resolveToQualifiedNames(expression, myTypeEvalContext),
                                     it -> it.equals(PyTypingTypeProvider.CLASS_VAR));
        }
    }
}
