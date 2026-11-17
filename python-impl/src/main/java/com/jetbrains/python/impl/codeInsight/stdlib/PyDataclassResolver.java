// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.impl.codeInsight.*;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames.Dataclasses;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.impl.psi.stubs.PyDataclassStub;
import com.jetbrains.python.impl.psi.stubs.PyDataclassFieldStub;
import com.jetbrains.python.impl.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.impl.psi.types.PyDescriptorTypeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.*;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface PyDataclassResolver {
    @Nullable PyDataclassParameters resolveClassParameters(
        PyClass pyClass,
        PyDataclassStub stub,
        PyDataclassParameters.Type type,
        @Nullable DataclassParameterArgumentMapping argumentMapping,
        TypeEvalContext context
    );

    /**
     * Qualified names whose reference as a field's assigned value or as a field-call default means "this field has no
     * default". Empty when the framework has no such sentinel.
     */
    default Set<String> getOmittedDefaultQualifiedNames() {
        return Collections.emptySet();
    }

    default @Nullable PyDataclassFieldParameters resolveFieldParameters(
        PyClass dataclass,
        PyDataclassParameters parameters,
        PyTargetExpression field,
        TypeEvalContext context
    ) {
        assert field.getContainingClass() == dataclass;

        if (PyDataclassResolverKt.isAssignedOmittedDefault(this, dataclass, field, context)) {
            return new PyDataclassFieldParameters(
                false,
                false,
                parameters.getInit(),
                parameters.getKwOnly(),
                null
            );
        }

        return buildFieldParametersFromStub(dataclass, parameters, field, PyDataclassResolverKt.retrieveFieldStub(field), context);
    }

    /**
     * Builds the {@link PyDataclassFieldParameters} once the field's {@code fieldStub} has been retrieved. Default = the stub-only
     * resolution shared by stdlib, attrs and third-party dataclasses (never Pydantic models, so only the explicit alias is
     * considered). {@link PyDataclassTransformResolver} overrides this to resolve the field-specifier callable.
     */
    default @Nullable PyDataclassFieldParameters buildFieldParametersFromStub(
        PyClass dataclass,
        PyDataclassParameters parameters,
        PyTargetExpression field,
        @Nullable PyDataclassFieldStub fieldStub,
        TypeEvalContext context
    ) {
        if (fieldStub == null) {
            return null;
        }
        Boolean kwOnly = fieldStub.kwOnly();
        return new PyDataclassFieldParameters(
            fieldStub.hasDefault(),
            fieldStub.hasDefaultFactory(),
            fieldStub.initValue(),
            kwOnly != null ? kwOnly : false,
            fieldStub.getAlias()
        );
    }

    /**
     * Name of the generated `__init__` parameter for {@code fieldName}. A per-framework hook because the mapping from field name
     * to parameter name differs by framework: stdlib uses the field name verbatim, attrs strips a single leading
     * underscore, and dataclass_transform/Pydantic use the resolved {@link PyDataclassFieldParameters#parameterAlias}.
     */
    default String getInitParameterName(String fieldName, @Nullable PyDataclassFieldParameters fieldParams) {
        return fieldName;
    }

    /**
     * Type of the generated `__init__` parameter for {@code field}.
     */
    default @Nullable PyType getInitParameterType(PyClass cls, PyTargetExpression field, TypeEvalContext context) {
        PyType type = context.getType(field);
        if (type instanceof PyClassType classType &&
            classType.isParameterized() &&
            Dataclasses.DATACLASSES_INITVAR.equals(classType.getClassQName())) {
            List<PyType> typeArguments = classType.getTypeArguments();
            return typeArguments.isEmpty() ? null : typeArguments.get(0);
        }
        if (type instanceof PyClassLikeType classLikeType) {
            Ref<PyType> expected = PyDescriptorTypeUtil.getExpectedValueTypeForDunderSet(field, classLikeType, context);
            if (expected != null) {
                // TODO consulo: Ref.deref missing
                return expected.get();
            }
        }
        return type;
    }

    /**
     * Default value (or `...` placeholder) of the generated `__init__` parameter, or {@code null} if required.
     */
    default @Nullable PyExpression getInitParameterDefault(
        PyClass cls,
        PyTargetExpression field,
        @Nullable PyDataclassFieldParameters fieldParams,
        TypeEvalContext context
    ) {
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(cls.getProject());
        PyExpression ellipsis = elementGenerator.createEllipsis();

        if (fieldParams == null) {
            if (context.maySwitchToAST(field)) {
                return field.findAssignedValue();
            }
            else if (field.hasAssignedValue()) {
                return ellipsis;
            }
            else {
                return null;
            }
        }
        else {
            return fieldParams.hasDefault() || fieldParams.hasDefaultFactory() ? ellipsis : null;
        }
    }

    /**
     * Places one field's parameter into {@code acc}; overridden by attrs to change ordering.
     */
    default void placeFieldInInitSignature(
        InitFieldsAccumulator acc,
        PyDataclassField fieldInfo,
        int index,
        int indexOfKeywordOnlyAttribute,
        TypeEvalContext context
    ) {
        String aliasOrFieldName = fieldInfo.getParameterName();
        Boolean kwOnly = fieldInfo.getKwOnly();
        PyCallableParameter parameter = fieldInfo.getParameter();
        String fieldName = fieldInfo.getName();

        if (acc.getSeenKeywordOnlyClass() && !Boolean.FALSE.equals(kwOnly) ||
            index < indexOfKeywordOnlyAttribute ||
            Boolean.TRUE.equals(kwOnly)) {
            if (!acc.getPositionalAliasOrFieldNameParams().containsKey(aliasOrFieldName)) {
                acc.getKeywordOnlyAliasOrFieldNameParams().add(aliasOrFieldName);
            }
            if (!acc.getPositionalFieldNameParams().containsKey(fieldName)) {
                acc.getKeywordOnlyFieldNameParams().add(fieldName);
            }
        }

        if (parameter == null) {
            acc.getSeenNames().add(aliasOrFieldName);
        }
        else if (!PyDataclassResolverKt.isKwOnlyMarkerField(parameter, context)) {
            PyCallableParameter removedAliasOrFieldNameParam = acc.getPositionalAliasOrFieldNameParams().remove(aliasOrFieldName);
            acc.getPositionalAliasOrFieldNameParams().put(aliasOrFieldName,
                                                          removedAliasOrFieldNameParam != null ? removedAliasOrFieldNameParam : parameter);

            PyCallableParameter fieldNameParam =
                PyCallableParameterImpl.nonPsi(fieldName, parameter.getType(context), parameter.getDefaultValue());
            PyCallableParameter removedFieldNameParam = acc.getPositionalFieldNameParams().remove(fieldName);
            acc.getPositionalFieldNameParams().put(fieldName, removedFieldNameParam != null ? removedFieldNameParam : fieldNameParam);
        }
    }

    /**
     * Default: single signature. Pydantic overrides to also expose the by-field-name signature.
     */
    default List<List<PyCallableParameter>> buildInitSignatureParameterSets(InitFieldsAccumulator acc) {
        return List.of(PyDataclassResolverKt.buildParameters(acc.getPositionalAliasOrFieldNameParams(),
                                                             acc.getKeywordOnlyAliasOrFieldNameParams()));
    }

    /**
     * Whether instances of {@code cls} reject attribute assignment. Default: frozen if {@code cls} or any dataclass ancestor is
     * frozen, matching stdlib and attrs, which forbid unfreezing a frozen base.
     */
    default boolean isFrozenInstance(PyClass cls, TypeEvalContext context) {
        return PyDataclassResolverKt.isFrozenInAnyDataclassOfHierarchy(cls, context);
    }

    /**
     * Free functions like `dataclasses.replace(obj, ...)` / `attrs.evolve(inst, ...)`. Empty if none.
     */
    default List<PyDataclassCopyFunction> copyFunctions() {
        return Collections.emptyList();
    }

    /**
     * Name of the post-init hook Python calls after the generated `__init__` (e.g. stdlib `__post_init__`), or {@code null}.
     */
    default @Nullable String postInitFunctionName() {
        return null;
    }

    default boolean applyCustomDecoratorParameter(
        ParametersAccumulator accumulator,
        String name,
        @Nullable PyExpression value,
        @Nullable PyExpression argument
    ) {
        switch (name) {
            case "eq": {
                accumulator.setEq(PyEvaluator.evaluateAsBooleanNoResolve(value));
                accumulator.setEqArgument(argument);
                break;
            }
            case "order": {
                accumulator.setOrder(PyEvaluator.evaluateAsBooleanNoResolve(value));
                accumulator.setOrderArgument(argument);
                break;
            }
            case "unsafe_hash": {
                accumulator.setUnsafeHash(PyEvaluator.evaluateAsBooleanNoResolve(value));
                accumulator.setUnsafeHashArgument(argument);
                break;
            }
            default:
                return false;
        }
        return true;
    }
}
