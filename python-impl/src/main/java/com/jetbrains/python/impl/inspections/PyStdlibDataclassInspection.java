// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames.Dataclasses;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
public class PyStdlibDataclassInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameStdlibDataclassDefinitionAndUsages();
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
        @RequiredReadAction
        public void visitPyClass(PyClass node) {
            PyDataclassParameters dataclassParameters = PyDataclassesKt.parseStdDataclassParameters(node, myTypeEvalContext);
            if (dataclassParameters == null) {
                return;
            }

            processDataclassParameters(node, dataclassParameters);

            PyFunction postInit = node.findMethodByName(Dataclasses.DUNDER_POST_INIT, false, myTypeEvalContext);
            List<@Nullable PyType> localInitVars = new ArrayList<>();

            node.processClassLevelDeclarations((element, state) -> {
                if (element instanceof PyTargetExpression targetExpression) {
                    if (!PyTypingTypeProvider.isClassVar(targetExpression, myTypeEvalContext)) {
                        processDefaultFieldValue(targetExpression);
                        InitVarField initVarField = processAsInitVar(targetExpression, postInit);
                        if (initVarField != null) {
                            localInitVars.add(initVarField.type);
                        }
                    }

                    processFieldFunctionCall(node, dataclassParameters, targetExpression);
                }

                return true;
            });

            if (postInit != null) {
                processPostInitDefinition(node, postInit, dataclassParameters, localInitVars);
            }
        }

        @RequiredReadAction
        private void processDefaultFieldValue(PyTargetExpression field) {
            if (field.getAnnotationValue() == null) {
                return;
            }

            PyExpression value = field.findAssignedValue();

            if (value instanceof PyCallExpression callExpression) {
                boolean fieldWithDefaultFactory = false;
                for (PyCallableType callableType : callExpression.multiResolveCallee(getResolveContext())) {
                    PyCallable callable = callableType.getCallable();
                    if (callable == null || !Dataclasses.DATACLASSES_FIELD.equals(callable.getQualifiedName())) {
                        continue;
                    }
                    boolean hasDefaultFactory = PyCallExpressionHelper.mapArguments(callExpression, callableType, myTypeEvalContext)
                        .getMappedParameters().values().stream()
                        .anyMatch(p -> "default_factory".equals(p.getName()));
                    if (hasDefaultFactory) {
                        fieldWithDefaultFactory = true;
                        break;
                    }
                }

                if (fieldWithDefaultFactory) {
                    return;
                }
            }

            // Here we rely on the fact that dataclasses.field is declared to return the type of its `default` argument,
            // so if `default` is a dict, or a list instance, we will highlight the call as if the same expression
            // was in the RHS directly. dataclasses.Field itself is not considered a forbidden mutable default.
            if (PyUtil.isForbiddenMutableDefault(value, myTypeEvalContext)) {
                registerProblem(value,
                                PyInspectionMessages.problemMessage(
                                    p -> PyLocalize.inspDataclassesMutableAttributeDefaultNotAllowedUseDefaultFactory(p[0]),
                                    String.valueOf(value != null ? value.getText() : null)),
                                ProblemHighlightType.GENERIC_ERROR);
            }
        }

        @RequiredReadAction
        private @Nullable InitVarField processAsInitVar(PyTargetExpression field, @Nullable PyFunction postInit) {
            PyType innerInitVarType = getInitVarType(field);
            if (innerInitVarType == null) {
                return null;
            }
            if (postInit == null) {
                registerProblem(field,
                                PyInspectionMessages.problemMessage(
                                    p -> PyLocalize.inspDataclassesAttributeUselessUntilPostInitDeclared(p[0]),
                                    String.valueOf(field.getName())),
                                ProblemHighlightType.LIKE_UNUSED_SYMBOL);
            }
            return new InitVarField(innerInitVarType);
        }

        private static class InitVarField {
            final @Nullable PyType type;

            InitVarField(@Nullable PyType type) {
                this.type = type;
            }
        }

        @RequiredReadAction
        private void processPostInitDefinition(PyClass cls,
                                               PyFunction postInit,
                                               PyDataclassParameters dataclassParameters,
                                               List<@Nullable PyType> localInitVars) {
            if (!dataclassParameters.getInit()) {
                registerProblem(postInit.getNameIdentifier(),
                                PyLocalize.inspDataclassesPostInitWouldNotBeCalledUntilInitParameterSetToTrue().get(),
                                ProblemHighlightType.LIKE_UNUSED_SYMBOL);

                return;
            }

            if (ParamHelper.isSelfArgsKwargsCallable(postInit, myTypeEvalContext)) {
                return;
            }

            List<@Nullable PyType> allInitVars = new ArrayList<>();
            List<PyClass> ancestors = new ArrayList<>(cls.getAncestorClasses(myTypeEvalContext));
            Collections.reverse(ancestors);
            for (PyClass ancestor : ancestors) {
                if (PyDataclassesKt.parseStdDataclassParameters(ancestor, myTypeEvalContext) == null) {
                    continue;
                }

                ancestor.processClassLevelDeclarations((element, state) -> {
                    if (element instanceof PyTargetExpression targetExpression) {
                        PyType initVarType = getInitVarType(targetExpression);
                        if (initVarType != null) {
                            allInitVars.add(initVarType);
                        }
                    }

                    return true;
                });
            }
            allInitVars.addAll(localInitVars);

            List<PyCallableParameter> allParameters = postInit.getParameters(myTypeEvalContext);
            List<PyCallableParameter> parameters = allParameters.isEmpty()
                ? Collections.emptyList()
                : allParameters.subList(1, allParameters.size());

            if (parameters.size() != allInitVars.size()) {
                String message;
                if (allInitVars.size() != localInitVars.size()) {
                    message =
                        PyLocalize.inspDataclassesPostInitShouldTakeAllInitOnlyVariablesIncludingInheritedInSameOrderTheyDefined().get();
                }
                else {
                    message = PyLocalize.inspDataclassesPostInitShouldTakeAllInitOnlyVariablesInSameOrderTheyDefined().get();
                }
                registerProblem(postInit.getParameterList(), message, ProblemHighlightType.GENERIC_ERROR);
            }
            else {
                for (int index = 0; index < parameters.size(); index++) {
                    PyCallableParameter callableParameter = parameters.get(index);
                    PyParameter parameter = callableParameter.getParameter();
                    if (!(parameter instanceof PyNamedParameter namedParameter)) {
                        continue;
                    }
                    PyExpression annotation = PyTypingTypeProvider.getAnnotationValue(namedParameter, myTypeEvalContext);
                    if (annotation == null) {
                        continue;
                    }
                    Ref<PyType> typeFromAnnotation = PyTypingTypeProvider.getType(annotation, myTypeEvalContext);
                    if (typeFromAnnotation == null) {
                        continue;
                    }
                    PyType initVarType = allInitVars.get(index);
                    if (!PyTypeChecker.match(typeFromAnnotation.get(), initVarType, myTypeEvalContext)) {
                        String initVarTypeName = PythonDocumentationProvider.getTypeName(initVarType, myTypeEvalContext);
                        String parameterTypeName = PythonDocumentationProvider.getVerboseTypeName(typeFromAnnotation.get(), myTypeEvalContext);
                        registerProblem(annotation,
                                        PyInspectionMessages.problemMessage(
                                            p -> PyLocalize.inspTypeCheckerExpectedTypeGotTypeInstead(p[0], p[1]),
                                            initVarTypeName, parameterTypeName));
                    }
                }
            }
        }
    }
}
