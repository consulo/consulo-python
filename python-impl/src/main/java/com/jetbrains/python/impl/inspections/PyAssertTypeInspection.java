// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.impl.psi.types.PyCloningTypeVisitor;
import com.jetbrains.python.impl.psi.types.PyNumericTowerUtil;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyTypeUtilKt;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ExtensionImpl
public class PyAssertTypeInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameAssertType();
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
        return new PyInspectionVisitor(holder, PyInspectionVisitor.getContext(session)) {
            @Override
            public void visitPyCallExpression(PyCallExpression callExpression) {
                List<PyCallable> callables = callExpression.multiResolveCalleeFunction(getResolveContext());
                PyCallable callable = callables.size() == 1 ? callables.get(0) : null;
                if (callable instanceof PyFunction && PyTypingTypeProvider.ASSERT_TYPE.equals(callable.getQualifiedName())) {
                    PyExpression[] arguments = callExpression.getArguments();
                    if (arguments.length == 2) {
                        PyType actualType = PyNumericTowerUtil.enrich(myTypeEvalContext.getType(arguments[0]));
                        PyType expectedType = PyTypeUtil.derefOrUnknown(PyTypingTypeProvider.getType(arguments[1], myTypeEvalContext));
                        if (!isSame(actualType, expectedType, myTypeEvalContext)) {
                            String expectedName = PythonDocumentationProvider.getVerboseTypeName(expectedType, myTypeEvalContext);
                            String actualName = PythonDocumentationProvider.getTypeName(actualType, myTypeEvalContext);
                            ProblemMessage message = PyInspectionMessages.problemMessage(
                                a -> PyLocalize.inspAssertTypeExpectedTypeGotTypeInstead(a[0], a[1]), expectedName, actualName);
                            // assert_type is an exact-match check, so the diff compares the types invariantly; it's shown alone (no
                            // assignability breakdown, which wouldn't apply to an equality check).
                            String diff = PyTypeDiff.diffTooltip(expectedType, actualType, myTypeEvalContext, true);
                            registerProblem(arguments[0], diff != null ? new ProblemMessage(message.description(), diff) : message);
                        }
                    }
                }
            }
        };
    }

    private static boolean isSame(@Nullable PyType type1, @Nullable PyType type2, TypeEvalContext context) {
        boolean type1AllAny = ContainerUtil.and(PyTypeUtil.asUnionSequence(type1), PyTypeUtilKt::isAnyOrUnknown);
        boolean type2AllAny = ContainerUtil.and(PyTypeUtil.asUnionSequence(type2), PyTypeUtilKt::isAnyOrUnknown);
        if (type1AllAny || type2AllAny) {
            return type1AllAny && type2AllAny;
        }
        if (type1 instanceof PyCallableType callableType1 && !(type1 instanceof PyClassLikeType) &&
            type2 instanceof PyCallableType callableType2 && !(type2 instanceof PyClassLikeType)) {
            PyType returnType1 = callableType1.getReturnType(context);
            PyType returnType2 = callableType2.getReturnType(context);
            if (!isSame(returnType1, returnType2, context)) {
                return false;
            }

            CallableParameters parameters1 = getCallableParameters(callableType1, context);
            CallableParameters parameters2 = getCallableParameters(callableType2, context);
            if (parameters1 == null || parameters2 == null) {
                return parameters1 == parameters2;
            }

            if (parameters1.posOnly().size() != parameters2.posOnly().size()) {
                return false;
            }
            for (int index = 0; index < parameters1.posOnly().size(); index++) {
                PyCallableParameter parameter1 = parameters1.posOnly().get(index);
                PyCallableParameter parameter2 = parameters2.posOnly().get(index);
                if (!isSame(parameter1.getType(context), parameter2.getType(context), context)) {
                    return false;
                }
            }

            if (parameters1.standard().size() != parameters2.standard().size()) {
                return false;
            }
            for (int index = 0; index < parameters1.standard().size(); index++) {
                PyCallableParameter parameter1 = parameters1.standard().get(index);
                PyCallableParameter parameter2 = parameters2.standard().get(index);
                if (!Objects.equals(parameter1.getName(), parameter2.getName())) {
                    return false;
                }
                if (parameter1.isPositionalContainer() != parameter2.isPositionalContainer()) {
                    return false;
                }
                if (!isSame(parameter1.getType(context), parameter2.getType(context), context)) {
                    return false;
                }
            }

            Map<@Nullable String, PyCallableParameter> keywordOnlyParameters2 = new HashMap<>();
            for (PyCallableParameter parameter : parameters2.keywordOnly()) {
                keywordOnlyParameters2.put(parameter.getName(), parameter);
            }

            for (PyCallableParameter parameter1 : parameters1.keywordOnly()) {
                PyCallableParameter parameter2 = keywordOnlyParameters2.remove(parameter1.getName());
                if (parameter2 == null) {
                    return false;
                }
                if (!isSame(parameter1.getType(context), parameter2.getType(context), context)) {
                    return false;
                }
            }
            return keywordOnlyParameters2.isEmpty();
        }
        if (Objects.equals(type1, type2)) {
            return true;
        }
        return Objects.equals(normalizeGradualType(type1, context), normalizeGradualType(type2, context));
    }

    /**
     * Replaces every nested `Unknown` with `Any`.
     * <p>
     * `Any` and `Unknown` are one gradual type. `Unknown` is how PyCharm shows the gradual type that no
     * annotation spells out, the way `ty` and `basedpyright` show it. `assert_type` compares types, so it
     * must see the two spellings as the same type. {@link #isSame} already does that for a top-level type.
     */
    private static @Nullable PyType normalizeGradualType(@Nullable PyType type, TypeEvalContext context) {
        return PyCloningTypeVisitor.clone(type, new PyCloningTypeVisitor(context) {
            @Override
            public @Nullable PyType visitUnknownType() {
                return PyAnyType.getAny();
            }
        });
    }

    private static @Nullable CallableParameters getCallableParameters(PyCallableType callableType, TypeEvalContext context) {
        List<PyCallableParameter> parameters = callableType.getParameters(context);
        if (parameters == null) {
            return null;
        }

        List<PyCallableParameter> posOnlyParameters;
        List<PyCallableParameter> standardAndKeywordOnlyParameters;

        int posOnlySeparatorIndex = ContainerUtil.indexOf(parameters, PyCallableParameter::isPositionOnlySeparator);
        if (posOnlySeparatorIndex == -1) {
            // TODO If CallableType is inferred from a 'Callable[]' type hint, there is no terminating '/' parameter.
            // Check whether all parameters have no name then.
            if (ContainerUtil.and(parameters, it -> it.getName() == null)) {
                posOnlyParameters = parameters;
                standardAndKeywordOnlyParameters = Collections.emptyList();
            }
            else {
                posOnlyParameters = Collections.emptyList();
                standardAndKeywordOnlyParameters = parameters;
            }
        }
        else {
            posOnlyParameters = parameters.subList(0, posOnlySeparatorIndex);
            standardAndKeywordOnlyParameters = parameters.subList(posOnlySeparatorIndex + 1, parameters.size());
        }

        int kwargSeparatorIndex = ContainerUtil.indexOf(standardAndKeywordOnlyParameters, PyCallableParameter::isKeywordOnlySeparator);
        if (kwargSeparatorIndex == -1) {
            return new CallableParameters(posOnlyParameters, standardAndKeywordOnlyParameters, Collections.emptyList());
        }
        else {
            return new CallableParameters(
                posOnlyParameters,
                standardAndKeywordOnlyParameters.subList(0, kwargSeparatorIndex),
                standardAndKeywordOnlyParameters.subList(kwargSeparatorIndex + 1, standardAndKeywordOnlyParameters.size())
            );
        }
    }

    private record CallableParameters(List<PyCallableParameter> posOnly,
                                      List<PyCallableParameter> standard,
                                      List<PyCallableParameter> keywordOnly) {
    }
}
