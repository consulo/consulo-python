// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.ast.PyAstTypeParameter;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.PyTypeParameterListOwner;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@ExtensionImpl
public class PyNewStyleGenericSyntaxInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameNewStyleGenericsTypeParamSyntax();
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
        public void visitPyTypeParameter(PyTypeParameter typeParameter) {
            PyExpression boundExpression = typeParameter.getBoundExpression();
            PyExpression defaultExpression = typeParameter.getDefaultExpression();

            if (boundExpression != null) {
                boundExpression.accept(new PyRecursiveElementVisitor() {
                    @Override
                    @RequiredReadAction
                    public void visitPyElement(PyElement node) {
                        if (!(node instanceof PyParenthesizedExpression && node == boundExpression) &&
                            !(node instanceof PyTupleExpression && node.getParent() == boundExpression) &&
                            node instanceof PyReferenceExpression referenceExpression) {
                            if (getTypeParameterType(referenceExpression) != null) {
                                registerProblem(node,
                                                PyLocalize.inspNewStyleGenericsAreNotAllowedInsideTypeParamBounds().get(),
                                                ProblemHighlightType.GENERIC_ERROR);
                            }
                        }
                        super.visitPyElement(node);
                    }
                });
            }

            if (defaultExpression != null) {
                ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(typeParameter);
                if (scopeOwner == null) {
                    return;
                }
                List<PyTypeParameter> laterTypeParameters = Collections.emptyList();
                if (typeParameter.getParent() instanceof PyTypeParameterList typeParameterList) {
                    List<PyTypeParameter> typeParameters = typeParameterList.getTypeParameters();
                    int index = typeParameters.indexOf(typeParameter);
                    if (index >= 0) {
                        laterTypeParameters = typeParameters.subList(index + 1, typeParameters.size());
                    }
                }
                List<PyTypeParameter> finalLaterTypeParameters = laterTypeParameters;

                defaultExpression.accept(new PyRecursiveElementVisitor() {
                    @Override
                    @RequiredReadAction
                    public void visitPyReferenceExpression(PyReferenceExpression node) {
                        PyTypeParameterType it = getTypeParameterType(node);
                        if (it != null) {
                            PyQualifiedNameOwner declarationElement = it.getDeclarationElement();
                            if (scopeOwner != it.getScopeOwner() || declarationElement instanceof PyTargetExpression) {
                                registerProblem(node, PyInspectionMessages.problemMessage(
                                    p -> PyLocalize.inspNewStyleTypeParameterOutOfScope(p[0]), String.valueOf(node.getName())));
                            }
                            else if (declarationElement == typeParameter) {
                                registerProblem(node, PyInspectionMessages.problemMessage(
                                    p -> PyLocalize.inspNewStyleTypeParameterDefaultRefersToItself(p[0]), String.valueOf(node.getName())));
                            }
                            else if (finalLaterTypeParameters.contains(declarationElement)) {
                                registerProblem(node, PyLocalize.inspTypeHintsDefaultTypeRefersToTypeVarOutOfScope().get());
                            }
                        }
                        super.visitPyReferenceExpression(node);
                    }
                });
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyTypeParameterList(PyTypeParameterList node) {
            List<PyTypeParameter> typeParameters = node.getTypeParameters();
            boolean lastIsDefault = false;
            boolean lastIsTypeVarTuple = false;
            for (PyTypeParameter typeParameter : typeParameters) {
                if (typeParameter.getDefaultExpressionText() != null) {
                    lastIsDefault = true;
                    if (lastIsTypeVarTuple && typeParameter.getKind() == PyAstTypeParameter.Kind.TypeVar) {
                        registerProblem(typeParameter,
                                        PyLocalize.inspTypeHintsDefaultTypeVarCannotFollowTypeVarTuple().get(),
                                        ProblemHighlightType.GENERIC_ERROR);
                    }
                }
                else if (lastIsDefault) {
                    registerProblem(typeParameter,
                                    PyLocalize.inspTypeHintsNonDefaultTypeVarsCannotFollowDefaults().get(),
                                    ProblemHighlightType.GENERIC_ERROR);
                }
                lastIsTypeVarTuple = typeParameter.getKind() == PyAstTypeParameter.Kind.TypeVarTuple;
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyTypeAliasStatement(PyTypeAliasStatement node) {
            PyExpression typeExpression = node.getTypeExpression();
            if (typeExpression != null) {
                reportOldStyleTypeVarsUsage(typeExpression,
                                            PyLocalize.inspNewStyleGenericsOldStyleTypeVarsNotAllowedInNewStyleTypeAliases().get());
                reportAssignmentExpressions(typeExpression,
                                            PyLocalize.inspNewStyleGenericsAssignmentExpressionsNotAllowed().get());
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyFunction(PyFunction node) {
            PyAnnotation returnTypeAnnotation = node.getAnnotation();
            PyTypeParameterList typeParameterList = node.getTypeParameterList();

            if (typeParameterList != null) {
                reportOldStyleTypeVarsUsage(node.getParameterList(),
                                            PyLocalize.inspNewStyleGenericsMixingOldStyleAndNewStyleTypeVarsNotAllowed().get(),
                                            node);
                for (PyParameter parameter : node.getParameterList().getParameters()) {
                    if (parameter instanceof PyNamedParameter namedParameter) {
                        PyAnnotation annotation = namedParameter.getAnnotation();
                        if (annotation != null) {
                            reportAssignmentExpressions(annotation, PyLocalize.inspNewStyleGenericsAssignmentExpressionsNotAllowed().get());
                        }
                    }
                }
                if (returnTypeAnnotation != null) {
                    reportOldStyleTypeVarsUsage(returnTypeAnnotation,
                                                PyLocalize.inspNewStyleGenericsMixingOldStyleAndNewStyleTypeVarsNotAllowed().get(),
                                                node);
                    reportAssignmentExpressions(returnTypeAnnotation,
                                                PyLocalize.inspNewStyleGenericsAssignmentExpressionsNotAllowed().get());
                }
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyClass(PyClass node) {
            if (node.getTypeParameterList() != null) {
                PyArgumentList superClassExpressionList = node.getSuperClassExpressionList();
                if (superClassExpressionList != null) {
                    for (PyExpression ancestor : node.getSuperClassExpressions()) {
                        PyExpression reference = ancestor instanceof PySubscriptionExpression subscription ? subscription.getOperand() : ancestor;
                        PyType type = myTypeEvalContext.getType(reference);
                        if (type instanceof PyClassLikeType classLikeType) {
                            String qName = classLikeType.getClassQName();
                            if (PyTypingTypeProvider.GENERIC.equals(qName)) {
                                registerProblem(ancestor,
                                                PyLocalize.inspNewStyleGenericsClassesWithTypeParamListShouldNotExtendGeneric().get(),
                                                ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                            }
                            if (PyTypingTypeProvider.PROTOCOL.equals(qName) || PyTypingTypeProvider.PROTOCOL_EXT.equals(qName)) {
                                if (ancestor instanceof PySubscriptionExpression subscription) {
                                    registerProblem(subscription.getIndexExpression(),
                                                    PyLocalize.inspNewStyleGenericsExtendingProtocolDoesNotNeedParameterization().get(),
                                                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                                }
                            }
                        }
                    }
                    reportOldStyleTypeVarsUsage(superClassExpressionList,
                                                PyLocalize.inspNewStyleGenericsMixingOldStyleAndNewStyleTypeVarsNotAllowed().get(),
                                                node);
                    reportAssignmentExpressions(superClassExpressionList,
                                                PyLocalize.inspNewStyleGenericsAssignmentExpressionsNotAllowed().get());
                }
            }
        }

        private void reportOldStyleTypeVarsUsage(PsiElement element, String message) {
            reportOldStyleTypeVarsUsage(element, message, null);
        }

        /**
         * Reports references to traditional type variables in {@code element}.
         * A reference to a type alias that contains a type variable is not reported.
         * When {@code owner} is given, a type variable that a class or function around {@code owner} binds is not reported.
         */
        private void reportOldStyleTypeVarsUsage(PsiElement element, String message, @Nullable PyTypeParameterListOwner owner) {
            element.accept(new PyRecursiveElementVisitor() {
                @Override
                @RequiredReadAction
                public void visitPyReferenceExpression(PyReferenceExpression node) {
                    PyTypeParameterType it = getTypeParameterType(node);
                    if (it != null) {
                        PyQualifiedNameOwner declaration = it.getDeclarationElement();
                        if (declaration instanceof PyTargetExpression
                            && !(ScopeUtil.getScopeOwner(declaration) instanceof PyTypeAliasStatement)
                            && PyResolveUtil.resolveDeclaration(node.getReference(getResolveContext()), getResolveContext()) == declaration
                            && !isBoundByEnclosingScope(it, owner)) {
                            registerProblem(node, message, ProblemHighlightType.GENERIC_ERROR);
                        }
                    }
                }
            });
        }

        @RequiredReadAction
        private void reportAssignmentExpressions(PsiElement element, String message) {
            for (PyAssignmentExpression assignmentExpr : PsiTreeUtil.findChildrenOfAnyType(element, false, PyAssignmentExpression.class)) {
                registerProblem(assignmentExpr, message,
                                ProblemHighlightType.GENERIC_ERROR);
            }
        }

        private static boolean isBoundByEnclosingScope(PyTypeParameterType typeParameterType, @Nullable PyTypeParameterListOwner owner) {
            PyQualifiedNameOwner scopeOwner = typeParameterType.getScopeOwner();
            if (scopeOwner == null) {
                return false;
            }
            return owner != null && PsiTreeUtil.isAncestor(scopeOwner, owner, true);
        }

        private @Nullable PyTypeParameterType getTypeParameterType(PyReferenceExpression expression) {
            // TODO consulo: Ref.deref missing
            Ref<PyType> typeRef = PyTypingTypeProvider.getType(expression, myTypeEvalContext);
            return typeRef != null && typeRef.get() instanceof PyTypeParameterType typeParameterType ? typeParameterType : null;
        }
    }
}
