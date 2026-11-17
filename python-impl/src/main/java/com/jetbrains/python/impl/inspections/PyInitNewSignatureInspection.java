// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import consulo.localize.LocalizeValue;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.application.util.HtmlChunk;
import consulo.language.psi.PsiElementVisitor;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.impl.psi.types.PyCallableParameterListTypeImpl;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Detect and report incompatibilities between __new__ and __init__ signatures.
 */
@ExtensionImpl
public final class PyInitNewSignatureInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameNewInitSignature();
    }

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
        public void visitPyFunction(PyFunction node) {
            PyClass cls = PyUtil.turnConstructorIntoClass(node);
            if (cls == null || !cls.isNewStyleClass(myTypeEvalContext)) {
                return;
            }

            List<PyFunction> complementaryMethods = PyConstructorSignatureUtil.findComplementaryConstructors(node, myTypeEvalContext);

            for (PyFunction complementaryMethod : complementaryMethods) {
                if (matchSignatures(node, complementaryMethod) || matchSignatures(complementaryMethod, node)) {
                    return;
                }
            }

            if (complementaryMethods.size() == 1) {
                registerIncompatibilityProblem(node, complementaryMethods.get(0),
                                               PythonUiService.getInstance().createPyChangeSignatureQuickFixForMismatchingMethods(node, complementaryMethods.get(0)));
            }
            else if (!complementaryMethods.isEmpty()) {
                // More than one complementary signature (overloads): there is no single signature to diff against, so report
                // the incompatibility without the structural diff rather than comparing to an arbitrary one.
                registerIncompatibilityProblem(node, null, null);
            }
        }

        private boolean matchSignatures(PyFunction current, PyFunction other) {
            List<PyCallableParameter> currentParameters = current.getParameters(myTypeEvalContext);
            List<PyCallableParameter> otherParameters = other.getParameters(myTypeEvalContext);

            var currentInputSignature = new PyCallableParameterListTypeImpl(ParamHelper.dropSelf(currentParameters));
            var otherInputSignature = new PyCallableParameterListTypeImpl(ParamHelper.dropSelf(otherParameters));

            return PyTypeChecker.match(currentInputSignature, otherInputSignature, myTypeEvalContext);
        }


        private void registerIncompatibilityProblem(PyFunction function, @Nullable PyFunction other, @Nullable LocalQuickFix quickFix) {
            PyParameterList parameterList = function.getParameterList();
            String description = PyUtil.isNewMethod(function) ? PyLocalize.inspNewIncompatibleToInit().get()
                                                                : PyLocalize.inspInitIncompatibleToNew().get();
            // No single complementary signature to compare against: keep the plain message (no aligned diff or breakdown).
            if (other == null) {
                if (quickFix != null) {
                    registerProblem(parameterList, description, quickFix);
                }
                else {
                    registerProblem(parameterList, description);
                }
                return;
            }
            List<PyCallableParameter> functionParams = ParamHelper.dropSelf(function.getParameters(myTypeEvalContext));
            List<PyCallableParameter> otherParams = ParamHelper.dropSelf(other.getParameters(myTypeEvalContext));
            // The aligned parameter diff with the assignability breakdown appended below it on-the-fly.
            String diff = PyTypeDiff.paramsDiffTooltip(otherParams, functionParams, HtmlChunk.text(description), myTypeEvalContext);
            ProblemMessage message = new ProblemMessage(description, diff);
            PyCallableParameterListTypeImpl otherSignature = new PyCallableParameterListTypeImpl(otherParams);
            PyCallableParameterListTypeImpl functionSignature = new PyCallableParameterListTypeImpl(functionParams);
            LocalQuickFix[] fixes = quickFix != null ? new LocalQuickFix[]{quickFix} : LocalQuickFix.EMPTY_ARRAY;
            registerProblemWithTooltip(parameterList, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, fixes,
                                       () -> PyTypeCheckerInspectionProblemRegistrar.breakdownTooltip(message, otherSignature, functionSignature, myTypeEvalContext, parameterList));
        }
    }
}
