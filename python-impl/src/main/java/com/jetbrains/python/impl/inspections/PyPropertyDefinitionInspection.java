/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.inspections;

import com.google.common.collect.ImmutableList;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.inspections.quickfix.PyUpdatePropertySignatureQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.RenameParameterQuickFix;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.types.PyNoneType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.ast.ASTNode;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.Comparing;
import consulo.util.lang.ref.Ref;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Checks that arguments to property() and @property and friends are ok.
 *
 * @author dcheryasov
 * @since 2010-06-30
 */
@ExtensionImpl
public class PyPropertyDefinitionInspection extends PyInspection {
    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNamePropertyDefinition();
    }

    private static final ImmutableList<String> SUFFIXES = ImmutableList.of(PyNames.SETTER, PyNames.DELETER);

    @Nonnull
    @Override
    public PsiElementVisitor buildVisitor(
        @Nonnull ProblemsHolder holder,
        boolean isOnTheFly,
        @Nonnull LocalInspectionToolSession session,
        Object state
    ) {
        return new Visitor(holder, session);
    }

    public static class Visitor extends PyInspectionVisitor {

        private LanguageLevel myLevel;
        private List<PyClass> myStringClasses;
        private PyFunction myOneParamFunction;
        private PyFunction myTwoParamFunction; // arglist with two args, 'self' and 'value'

        public Visitor(final ProblemsHolder holder, LocalInspectionToolSession session) {
            super(holder, session);
            PsiFile psiFile = session.getFile();
            // save us continuous checks for level, module, stc
            myLevel = LanguageLevel.forElement(psiFile);
            // string classes
            final List<PyClass> stringClasses = new ArrayList<>(2);
            final PyBuiltinCache builtins = PyBuiltinCache.getInstance(psiFile);
            PyClass cls = builtins.getClass("str");
            if (cls != null) {
                stringClasses.add(cls);
            }
            cls = builtins.getClass("unicode");
            if (cls != null) {
                stringClasses.add(cls);
            }
            myStringClasses = stringClasses;
            // reference signatures
            PyClass objectClass = builtins.getClass("object");
            if (objectClass != null) {
                final PyFunction methodRepr = objectClass.findMethodByName("__repr__", false, null);
                if (methodRepr != null) {
                    myOneParamFunction = methodRepr;
                }
                final PyFunction methodDelattr = objectClass.findMethodByName("__delattr__", false, null);
                if (methodDelattr != null) {
                    myTwoParamFunction = methodDelattr;
                }
            }
        }


        @Override
        public void visitPyFile(PyFile node) {
            super.visitPyFile(node);
        }

        @Override
        public void visitPyClass(final PyClass node) {
            super.visitPyClass(node);
            // check property() and @property
            node.scanProperties(property -> {
                PyTargetExpression target = property.getDefinitionSite();
                if (target != null) {
                    // target = property(); args may be all funny
                    PyCallExpression call = (PyCallExpression) target.findAssignedValue();
                    assert call != null : "Property has a null call assigned to it";
                    final PyArgumentList arglist = call.getArgumentList();
                    assert arglist != null : "Property call has null arglist";
                    // we assume fget, fset, fdel, doc names
                    final PyCallExpression.PyArgumentsMapping mapping = call.mapArguments(getResolveContext());
                    for (Map.Entry<PyExpression, PyNamedParameter> entry : mapping.getMappedParameters().entrySet()) {
                        final String paramName = entry.getValue().getName();
                        PyExpression argument = PyUtil.peelArgument(entry.getKey());
                        checkPropertyCallArgument(paramName, argument, node.getContainingFile());
                    }
                }
                else {
                    // @property; we only check getter, others are checked by visitPyFunction
                    // getter is always present with this form
                    final PyCallable callable = property.getGetter().valueOrNull();
                    if (callable instanceof PyFunction) {
                        checkGetter(callable, getFunctionMarkingElement((PyFunction) callable));
                    }
                }
                return false;  // always want more
            }, false);
        }

        private void checkPropertyCallArgument(String paramName, PyExpression argument, PsiFile containingFile) {
            assert argument != null : "Parameter mapped to null argument";
            PyCallable callable = null;
            if (argument instanceof PyReferenceExpression) {
                final PsiPolyVariantReference reference = ((PyReferenceExpression) argument).getReference(getResolveContext());
                PsiElement resolved = reference.resolve();
                if (resolved instanceof PyCallable) {
                    callable = (PyCallable) resolved;
                }
                else {
                    reportNonCallableArg(resolved, argument);
                    return;
                }
            }
            else if (argument instanceof PyLambdaExpression) {
                callable = (PyLambdaExpression) argument;
            }
            else if (!"doc".equals(paramName)) {
                reportNonCallableArg(argument, argument);
                return;
            }
            if (callable != null && callable.getContainingFile() != containingFile) {
                return;
            }
            if ("fget".equals(paramName)) {
                checkGetter(callable, argument);
            }
            else if ("fset".equals(paramName)) {
                checkSetter(callable, argument);
            }
            else if ("fdel".equals(paramName)) {
                checkDeleter(callable, argument);
            }
            else if ("doc".equals(paramName)) {
                PyType type = myTypeEvalContext.getType(argument);
                if (!(type instanceof PyClassType && myStringClasses.contains(((PyClassType) type).getPyClass()))) {
                    registerProblem(argument, PyLocalize.inspDocParamShouldBeStr().get());
                }
            }
        }

        private void reportNonCallableArg(PsiElement resolved, PsiElement element) {
            if (resolved instanceof PySubscriptionExpression || resolved instanceof PyNoneLiteralExpression) {
                return;
            }
            if (PyNames.NONE.equals(element.getText())) {
                return;
            }
            if (resolved instanceof PyTypedElement) {
                final PyType type = myTypeEvalContext.getType((PyTypedElement) resolved);
                final Boolean isCallable = PyTypeChecker.isCallable(type);
                if (isCallable != null && !isCallable) {
                    registerProblem(element, PyLocalize.inspStrangeArgWantCallable().get());
                }
            }
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            super.visitPyFunction(node);
            if (myLevel.isAtLeast(LanguageLevel.PYTHON26)) {
                // check @foo.setter and @foo.deleter
                PyClass cls = node.getContainingClass();
                if (cls != null) {
                    final PyDecoratorList decos = node.getDecoratorList();
                    if (decos != null) {
                        String name = node.getName();
                        for (PyDecorator deco : decos.getDecorators()) {
                            final QualifiedName qName = deco.getQualifiedName();
                            if (qName != null) {
                                List<String> nameParts = qName.getComponents();
                                if (nameParts.size() == 2) {
                                    final int suffixIndex = SUFFIXES.indexOf(nameParts.get(1));
                                    if (suffixIndex >= 0) {
                                        if (Comparing.equal(name, nameParts.get(0))) {
                                            // names are ok, what about signatures?
                                            PsiElement markable = getFunctionMarkingElement(node);
                                            if (suffixIndex == 0) {
                                                checkSetter(node, markable);
                                            }
                                            else {
                                                checkDeleter(node, markable);
                                            }
                                        }
                                        else {
                                            registerProblem(deco, PyLocalize.inspFuncPropertyNameMismatch().get());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        @Nullable
        private static PsiElement getFunctionMarkingElement(PyFunction node) {
            if (node == null) {
                return null;
            }
            final ASTNode nameNode = node.getNameNode();
            PsiElement markable = node;
            if (nameNode != null) {
                markable = nameNode.getPsi();
            }
            return markable;
        }


        private void checkGetter(PyCallable callable, PsiElement beingChecked) {
            if (callable != null) {
                checkOneParameter(callable, beingChecked, true);
                checkReturnValueAllowed(callable, beingChecked, true, PyLocalize.inspGetterReturnSmth().get());
            }
        }

        private void checkSetter(PyCallable callable, PsiElement beingChecked) {
            if (callable != null) {
                // signature: at least two params, more optionals ok; first arg 'self'
                final PyParameterList paramList = callable.getParameterList();
                if (myTwoParamFunction != null && !PyUtil.isSignatureCompatibleTo(callable, myTwoParamFunction, myTypeEvalContext)) {
                    registerProblem(
                        beingChecked,
                        PyLocalize.inspSetterSignatureAdvice().get(),
                        new PyUpdatePropertySignatureQuickFix(true)
                    );
                }
                checkForSelf(paramList);
                // no explicit return type
                checkReturnValueAllowed(callable, beingChecked, false, PyLocalize.inspSetterShouldNotReturn().get());
            }
        }

        private void checkDeleter(PyCallable callable, PsiElement beingChecked) {
            if (callable != null) {
                checkOneParameter(callable, beingChecked, false);
                checkReturnValueAllowed(callable, beingChecked, false, PyLocalize.inspDeleterShouldNotReturn().get());
            }
        }

        private void checkOneParameter(PyCallable callable, PsiElement beingChecked, boolean isGetter) {
            final PyParameterList parameterList = callable.getParameterList();
            if (myOneParamFunction != null && !PyUtil.isSignatureCompatibleTo(callable, myOneParamFunction, myTypeEvalContext)) {
                if (isGetter) {
                    registerProblem(
                        beingChecked,
                        PyLocalize.inspGetterSignatureAdvice().get(),
                        new PyUpdatePropertySignatureQuickFix(false)
                    );
                }
                else {
                    registerProblem(
                        beingChecked,
                        PyLocalize.inspDeleterSignatureAdvice().get(),
                        new PyUpdatePropertySignatureQuickFix(false)
                    );
                }
            }
            checkForSelf(parameterList);
        }

        private void checkForSelf(PyParameterList paramList) {
            PyParameter[] parameters = paramList.getParameters();
            final PyClass cls = PsiTreeUtil.getParentOfType(paramList, PyClass.class);
            if (cls != null && cls.isSubclass("type", myTypeEvalContext)) {
                return;
            }
            if (parameters.length > 0 && !PyNames.CANONICAL_SELF.equals(parameters[0].getName())) {
                registerProblem(
                    parameters[0],
                    PyLocalize.inspAccessorFirstParamIs$0(PyNames.CANONICAL_SELF).get(),
                    ProblemHighlightType.WEAK_WARNING,
                    null,
                    new RenameParameterQuickFix(PyNames.CANONICAL_SELF)
                );
            }
        }

        private void checkReturnValueAllowed(
            @Nonnull PyCallable callable,
            @Nonnull PsiElement beingChecked,
            boolean allowed,
            @Nonnull String message
        ) {
            if (callable instanceof PyFunction) {
                final PyFunction function = (PyFunction) callable;

                if (PyUtil.isDecoratedAsAbstract(function)) {
                    return;
                }

                if (allowed && !someFlowHasExitPoint(function, Visitor::isAllowedExitPoint) || !allowed && someFlowHasExitPoint(
                    function,
                    Visitor::isDisallowedExitPoint
                )) {
                    registerProblem(beingChecked, message);
                }
            }
            else {
                final PyType type = myTypeEvalContext.getReturnType(callable);
                final boolean hasReturns = !(type instanceof PyNoneType);

                if (allowed ^ hasReturns) {
                    registerProblem(beingChecked, message);
                }
            }
        }

        private static boolean someFlowHasExitPoint(@Nonnull PyFunction function, @Nonnull Predicate<PsiElement> exitPointPredicate) {
            final Ref<Boolean> result = new Ref<>(false);

            ControlFlowUtil.process(
                ControlFlowCache.getControlFlow(function).getInstructions(),
                0,
                instruction -> {
                    result.set(exitPointPredicate.test(instruction.getElement()));
                    return !result.get();
                }
            );

            return result.get();
        }

        private static boolean isAllowedExitPoint(@Nullable PsiElement element) {
            return element instanceof PyRaiseStatement || element instanceof PyReturnStatement || element instanceof PyYieldExpression;
        }

        private static boolean isDisallowedExitPoint(@Nullable PsiElement element) {
            return element instanceof PyReturnStatement && ((PyReturnStatement) element).getExpression() != null || element instanceof PyYieldExpression;
        }
    }
}
