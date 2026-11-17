// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.localize.LocalizeValue;
import consulo.annotation.component.ExtensionImpl;
import com.google.common.collect.ImmutableList;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.ast.ASTNode;
import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.inspections.quickfix.PyUpdatePropertySignatureQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.RenameParameterQuickFix;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyNoneLiteralExpression;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyRaiseStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import static com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt.isProtocol;
import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;

/**
 * Checks that arguments to property() and @property and friends are ok.
 * <br/>
 */
@ExtensionImpl
public final class PyPropertyDefinitionInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNamePropertyDefinition();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonProperties();
    }


    private static final ImmutableList<String> SUFFIXES = ImmutableList.of(PyNames.SETTER, PyNames.DELETER);

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    public static class Visitor extends PyInspectionVisitor {

        public Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyClass(PyClass node) {
            super.visitPyClass(node);
            // check property() and @property
            node.scanProperties(property -> {
                PyTargetExpression target = property.getDefinitionSite();
                if (target != null) {
                    // target = property(); args may be all funny
                    PyCallExpression call = (PyCallExpression)target.findAssignedValue();
                    assert call != null : "Property has a null call assigned to it";
                    PyArgumentList arglist = call.getArgumentList();
                    assert arglist != null : "Property call has null arglist";
                    // we assume fget, fset, fdel, doc names
                    PyCallExpression.PyArgumentsMapping mapping = ContainerUtil.getFirstItem(call.multiMapArguments(getResolveContext()));
                    if (mapping != null) {
                        for (Map.Entry<PyExpression, PyCallableParameter> entry : mapping.getMappedParameters().entrySet()) {
                            String paramName = entry.getValue().getName();
                            PyExpression argument = PyUtil.peelArgument(entry.getKey());
                            checkPropertyCallArgument(paramName, argument, node.getContainingFile());
                        }
                    }
                }
                else {
                    // @property; we only check getter, others are checked by visitPyFunction
                    // getter is always present with this form
                    PyCallable callable = property.getGetter().valueOrNull();
                    if (callable instanceof PyFunction) {
                        checkGetter(callable, getFunctionMarkingElement((PyFunction)callable));
                    }
                }
                return false;  // always want more
            }, false);
        }

        private void checkPropertyCallArgument(String paramName, PyExpression argument, PsiFile containingFile) {
            assert argument != null : "Parameter mapped to null argument";
            PyCallable callable = null;
            if (argument instanceof PyReferenceExpression) {
                PsiPolyVariantReference reference = ((PyReferenceExpression)argument).getReference(getResolveContext());
                PsiElement resolved = reference.resolve();
                if (resolved instanceof PyCallable) {
                    callable = (PyCallable)resolved;
                }
                else {
                    reportNonCallableArg(resolved, argument);
                    return;
                }
            }
            else if (argument instanceof PyLambdaExpression) {
                callable = (PyLambdaExpression)argument;
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
                if (!PyTypeChecker.match(type, PyBuiltinCache.getInstance(argument).getStrType(), myTypeEvalContext)) {
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
                PyType type = myTypeEvalContext.getType((PyTypedElement)resolved);
                Boolean isCallable = PyTypeChecker.isCallable(type);
                if (isCallable != null && !isCallable) {
                    registerProblem(element, PyLocalize.inspStrangeArgWantCallable().get());
                }
            }
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            super.visitPyFunction(node);
            // check @foo.setter and @foo.deleter
            PyClass cls = node.getContainingClass();
            if (cls != null) {
                PyDecoratorList decos = node.getDecoratorList();
                if (decos != null) {
                    String name = node.getName();
                    for (PyDecorator deco : decos.getDecorators()) {
                        QualifiedName qName = deco.getQualifiedName();
                        if (qName != null) {
                            List<String> nameParts = qName.getComponents();
                            if (nameParts.size() == 2) {
                                int suffixIndex = SUFFIXES.indexOf(nameParts.get(1));
                                if (suffixIndex >= 0) {
                                    if (Objects.equals(name, nameParts.get(0))) {
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

        private static @Nullable PsiElement getFunctionMarkingElement(PyFunction node) {
            if (node == null) {
                return null;
            }
            ASTNode nameNode = node.getNameNode();
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
                PyParameterList paramList = callable.getParameterList();
                List<PyCallableParameter> parameters = callable.getParameters(myTypeEvalContext);
                if (parameters.size() != 2 || !parameters.getFirst().isSelf()) {
                    registerProblem(beingChecked, PyLocalize.inspSetterSignatureAdvice().get(), new PyUpdatePropertySignatureQuickFix(true));
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
            PyParameterList parameterList = callable.getParameterList();
            List<PyCallableParameter> parameters = callable.getParameters(myTypeEvalContext);
            if (parameters.size() != 1 || !parameters.getFirst().isSelf()) {
                if (isGetter) {
                    registerProblem(beingChecked, PyLocalize.inspGetterSignatureAdvice().get(),
                                    new PyUpdatePropertySignatureQuickFix(false));
                }
                else {
                    registerProblem(beingChecked, PyLocalize.inspDeleterSignatureAdvice().get(), new PyUpdatePropertySignatureQuickFix(false));
                }
            }
            checkForSelf(parameterList);
        }

        private void checkForSelf(PyParameterList paramList) {
            PyParameter[] parameters = paramList.getParameters();
            PyClass cls = PsiTreeUtil.getParentOfType(paramList, PyClass.class);
            if (cls != null && cls.isSubclass(PyNames.FQN.TYPE, myTypeEvalContext)) {
                return;
            }
            if (parameters.length > 0 && !PyNames.CANONICAL_SELF.equals(parameters[0].getName())) {
                registerProblem(
                    parameters[0], PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspPropertyCannotBeDeleted(msgArgs[0]), PyNames.CANONICAL_SELF), ProblemHighlightType.WEAK_WARNING,
                    new RenameParameterQuickFix(PyNames.CANONICAL_SELF));
            }
        }

        private void checkReturnValueAllowed(PyCallable callable,
                                             PsiElement beingChecked,
                                             boolean allowed,
                                             String message) {
            if (callable instanceof PyFunction function) {

                if (PyKnownDecoratorUtil.hasAbstractDecorator(function, myTypeEvalContext)) {
                    return;
                }

                PyClass containingClass = function.getContainingClass();
                if (containingClass != null && isProtocol(containingClass, myTypeEvalContext)) {
                    return;
                }

                if (allowed && !someFlowHasExitPoint(function, Visitor::isAllowedExitPoint) ||
                    !allowed && someFlowHasExitPoint(function, Visitor::isDisallowedExitPoint)) {
                    registerProblem(beingChecked, message);
                }
            }
            else {
                PyType type = myTypeEvalContext.getReturnType(callable);
                boolean hasReturns = !isNoneType(type);

                if (allowed ^ hasReturns) {
                    registerProblem(beingChecked, message);
                }
            }
        }

        private static boolean someFlowHasExitPoint(PyFunction function, Predicate<PsiElement> exitPointPredicate) {
            Ref<Boolean> result = new Ref<>(false);

            ControlFlowUtil.process(ControlFlowCache.getControlFlow(function).getInstructions(),
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
            return element instanceof PyReturnStatement && ((PyReturnStatement)element).getExpression() != null ||
                   element instanceof PyYieldExpression;
        }
    }
}
