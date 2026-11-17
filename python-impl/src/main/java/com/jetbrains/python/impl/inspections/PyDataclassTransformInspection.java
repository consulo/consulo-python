// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassTransformResolver;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassTransformType;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

@ExtensionImpl
public class PyDataclassTransformInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameDataclassTransformDefinitionAndUsages();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonDataclasses();
    }

    /**
     * JB registration: suppressId="PyDataclass"
     */
    @Override
    public String getID() {
        return "PyDataclass";
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

    public static class Visitor extends PyDataclassVisitor {
        public Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyClass(PyClass node) {
            PyDataclassParameters dataclassParameters = PyDataclassesKt.parseDataclassParameters(node, myTypeEvalContext);
            if (dataclassParameters == null || dataclassParameters.getType() != PyDataclassTransformType.INSTANCE) {
                return;
            }

            processDataclassParameters(node, dataclassParameters);

            node.processClassLevelDeclarations((element, state) -> {
                if (element instanceof PyTargetExpression targetExpression) {
                    processFieldFunctionCall(node, dataclassParameters, targetExpression);
                    processConverterDefault(targetExpression);
                }

                return true;
            });
        }

        /**
         * Reports a `default`, `default_factory` or `factory` value that the field's `converter` cannot accept.
         */
        private void processConverterDefault(PyTargetExpression field) {
            PyType converterInputType = PyDataclassTransformResolver.INSTANCE.getConverterInputType(field, myTypeEvalContext);
            if (converterInputType == null) {
                return;
            }
            if (!(field.findAssignedValue() instanceof PyCallExpression call)) {
                return;
            }

            PyExpression defaultArgument = call.getKeywordArgument("default");
            if (defaultArgument != null) {
                checkConverterInput(converterInputType, myTypeEvalContext.getType(defaultArgument), defaultArgument);
            }
            for (String name : List.of("default_factory", "factory")) {
                PyExpression factory = call.getKeywordArgument(name);
                if (factory == null) {
                    continue;
                }
                PyType defaultType = PyCallExpressionHelper.getCallType(myTypeEvalContext.getType(factory), Collections.emptyList(), myTypeEvalContext);
                checkConverterInput(converterInputType, defaultType, factory);
            }
        }

        private void checkConverterInput(PyType expected, @Nullable PyType actual, PyExpression anchor) {
            if (PyTypeChecker.match(expected, actual, myTypeEvalContext)) {
                return;
            }
            registerProblem(anchor, PyInspectionMessages.problemMessage(a -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(a[0], a[1]),
                                                                        CodifiedParam.ofType(expected, anchor, myTypeEvalContext),
                                                                        CodifiedParam.ofType(actual, anchor, myTypeEvalContext)));
        }
    }
}
