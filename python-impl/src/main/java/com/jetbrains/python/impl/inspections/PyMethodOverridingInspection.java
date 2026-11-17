// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.inspections.PyInspectionExtension;
import consulo.localize.LocalizeValue;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.util.collection.ContainerUtil;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.impl.psi.types.PyCallableParameterListTypeImpl;
import com.jetbrains.python.impl.psi.types.PySelfType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ExtensionImpl
public final class PyMethodOverridingInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameMethodOver();
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

    public static class Visitor extends PyInspectionVisitor {
        public Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyFunction(PyFunction function) {
            PyClass containingClass = function.getContainingClass();

            if (containingClass == null || skipFunctionValidation(function)) {
                return;
            }

            for (PsiElement psiElement : PySuperMethodsSearch.search(function, myTypeEvalContext).findAll()) {
                if (psiElement instanceof PyFunction baseMethod) {
                    validateOverriddenFunction(function, baseMethod, containingClass);
                }
            }
        }

        private boolean skipFunctionValidation(PyFunction function) {
            // Constructors are not subject to the Liskov substitution principle, so they are normally exempt from override
            // compatibility checks. An explicit `@override` decorator opts them in, see https://github.com/python/typing/issues/2222.
            return (PyUtil.isConstructorLikeMethod(function) && !PyKnownDecoratorUtil.hasOverrideDecorator(function, myTypeEvalContext)) ||
                   // Only the implementation of an overloaded method is compared with the base method: an individual @overload
                   // declaration is not required to be assignable to the whole signature of the overridden method.
                   PyKnownDecoratorUtil.hasRedeclarationDecorator(function, myTypeEvalContext) ||
                   PyKnownDecoratorUtil.hasUnknownOrChangingSignatureDecorator(function, myTypeEvalContext) ||
                   ContainerUtil.exists(PyInspectionExtension.EP_NAME.getExtensionList(),
                                        e -> e.ignoreMethodParameters(function, myTypeEvalContext));
        }

        private void validateOverriddenFunction(PyFunction function,
                                                PyFunction baseMethod,
                                                PyClass containingClass) {
            PyClass baseClass = baseMethod.getContainingClass();
            String methodSignature = containingClass.getName() + "." + function.getName() + "()";
            String baseClassName = baseClass != null ? baseClass.getName() : "";

            // The method signature names `function`; the base class name names `baseClass` (when present).
            CodifiedParam methodSignatureParam = CodifiedParam.ofReference(function, methodSignature);
            Object baseClassParam = baseClass != null ? CodifiedParam.ofReference(baseClass, baseClassName) : baseClassName;

            List<PyCallableParameter> baseMethodParameters = baseMethod.getParameters(myTypeEvalContext);
            List<PyCallableParameter> functionParameters = function.getParameters(myTypeEvalContext);

            // We only inspect `self`/`cls` if Base declares an explicit receiver contract.
            // see com.jetbrains.python.PyMethodOverridingInspectionTest#testExplicitReceiverDomainNotHonored
            PyCallableParameter baseReceiver = ContainerUtil.getFirstItem(baseMethodParameters);
            boolean baseReceiverIsFixed = baseReceiver != null && baseReceiver.isSelf() &&
                                          !(baseReceiver.getType(myTypeEvalContext) instanceof PySelfType);
            if (!baseReceiverIsFixed) {
                baseMethodParameters = ParamHelper.dropSelf(baseMethodParameters);
                functionParameters = ParamHelper.dropSelf(functionParameters);
            }

            PyCallableParameterListTypeImpl baseMethodInputSignature = new PyCallableParameterListTypeImpl(baseMethodParameters);
            PyCallableParameterListTypeImpl functionInputSignature = new PyCallableParameterListTypeImpl(functionParameters);

            if (!PyTypeChecker.match(baseMethodInputSignature, functionInputSignature, myTypeEvalContext)) {
                ProblemMessage msg = PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspSignatureMismatch(msgArgs[0], msgArgs[1]), methodSignatureParam, baseClassParam);
                LocalQuickFix fix = PythonUiService.getInstance().createPyChangeSignatureQuickFixForMismatchingMethods(function, baseMethod);

                // Keep the headline's clickable `B.f()`/`A` links: feed the rich tooltip HTML, not the escaped description.
                String diff = PyTypeDiff.paramsDiffTooltip(baseMethodParameters, functionParameters,
                                                           PyInspectionMessages.tooltipHeadline(msg), myTypeEvalContext,
                                                           baseMethod.getTypeParameterList(), function.getTypeParameterList());
                ProblemMessage problemMessage = new ProblemMessage(msg.description(), diff);
                // The diff is the message tooltip; registerOverrideMismatch appends the breakdown below it on-the-fly.
                if (fix != null) {
                    registerOverrideMismatch(function.getParameterList(), baseMethodInputSignature, functionInputSignature, problemMessage, fix);
                }
                else {
                    registerOverrideMismatch(function.getParameterList(), baseMethodInputSignature, functionInputSignature, problemMessage);
                }
            }

            PyAnnotation annotation = function.getAnnotation();
            PyExpression returnExpression = annotation != null ? annotation.getValue() : null;
            String baseMethodAnnotation = baseMethod.getAnnotationValue();

            if (returnExpression == null || baseMethodAnnotation == null) {
                return;
            }

            PyType baseMethodReturnType = myTypeEvalContext.getReturnType(baseMethod);
            PyType overriddenReturnType = myTypeEvalContext.getReturnType(function);

            if (!PyTypeChecker.match(baseMethodReturnType, overriddenReturnType, myTypeEvalContext)) {
                ProblemMessage msg = PyInspectionMessages.problemMessage(msgArgs -> PyLocalize.inspOverriddenMethodReturnTypeMismatch(msgArgs[0], msgArgs[1]), methodSignatureParam, baseClassParam);
                registerOverrideMismatch(returnExpression, baseMethodReturnType, overriddenReturnType, msg);
            }
        }

        /**
         * Registers an override-incompatibility problem, attaching the breakdown ({@link PyTypeChecker#explainMismatch})
         * as a separate on-the-fly HTML tooltip while keeping the one-line {@code message} as the flat description.
         * Falls back to a plain problem when not on-the-fly or when the failure category is not instrumented.
         */
        private void registerOverrideMismatch(@Nullable PsiElement element,
                                              @Nullable PyType expected,
                                              @Nullable PyType actual,
                                              ProblemMessage message,
                                              LocalQuickFix ... fixes) {
            registerProblemWithTooltip(element, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                                       fixes,
                                       () -> PyTypeCheckerInspectionProblemRegistrar.breakdownTooltip(message, expected, actual, myTypeEvalContext, element));
        }
    }
}
