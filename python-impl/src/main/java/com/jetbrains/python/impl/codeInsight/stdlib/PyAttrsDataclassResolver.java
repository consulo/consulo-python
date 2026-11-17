// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.*;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.impl.psi.stubs.PyDataclassStub;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyTypeUtilKt;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.*;

final class PyAttrsDataclassResolver implements PyDataclassResolver {
    public static final PyAttrsDataclassResolver INSTANCE = new PyAttrsDataclassResolver();

    private PyAttrsDataclassResolver() {
    }

    @Override
    public Set<String> getOmittedDefaultQualifiedNames() {
        return PyDataclassNames.Attrs.ATTRS_NOTHING;
    }

    @Override
    public @Nullable PyDataclassParameters resolveClassParameters(
        PyClass pyClass,
        PyDataclassStub stub,
        PyDataclassParameters.Type type,
        @Nullable DataclassParameterArgumentMapping argumentMapping,
        TypeEvalContext context
    ) {
        if (!stub.getType().equals(PyAttrsDataclassType.INSTANCE.getName())) {
            return null;
        }

        // TODO remove this hack, make it a proper field
        Map<String, PyExpression> extraArguments = new LinkedHashMap<>();
        if (Objects.equals(stub.decoratorName(), PyKnownDecorator.ATTR_DATACLASS.getQualifiedName())) {
            extraArguments.put("auto_attribs",
                               PyElementGenerator.getInstance(pyClass.getProject())
                                   .createExpressionFromText(LanguageLevel.forElement(pyClass), PyNames.TRUE));
        }

        Map<String, PyExpression> others = new LinkedHashMap<>(argumentMapping != null ? argumentMapping.getOthers() : Collections.emptyMap());
        others.putAll(extraArguments);

        Boolean frozen = stub.frozenValue();
        QualifiedName decoratorName = stub.decoratorName();
        List<QualifiedName> fieldSpecifiers = new ArrayList<>();
        for (String it : PyDataclassNames.Attrs.FIELD_FUNCTIONS) {
            fieldSpecifiers.add(QualifiedName.fromDottedString(it));
        }

        return new PyDataclassParameters(
            orDefault(stub.initValue(), true),
            orDefault(stub.reprValue(), true),
            orDefault(stub.eqValue(), true),
            orDefault(stub.orderValue(), true),
            orDefault(stub.unsafeHashValue(), false),
            frozen != null ? frozen : PyDataclassNames.Attrs.ATTRS_FROZEN.contains(decoratorName != null ? decoratorName.toString() : null),
            orDefault(stub.matchArgsValue(), true),
            orDefault(stub.kwOnly(), false),
            orDefault(stub.slotsValue(), false),
            argumentMapping != null ? argumentMapping.getInitArgument() : null,
            argumentMapping != null ? argumentMapping.getReprArgument() : null,
            argumentMapping != null ? argumentMapping.getEqArgument() : null,
            argumentMapping != null ? argumentMapping.getOrderArgument() : null,
            argumentMapping != null ? argumentMapping.getUnsafeHashArgument() : null,
            argumentMapping != null ? argumentMapping.getFrozenArgument() : null,
            argumentMapping != null ? argumentMapping.getMatchArgsArgument() : null,
            argumentMapping != null ? argumentMapping.getKwOnlyArgument() : null,
            argumentMapping != null ? argumentMapping.getSlotsArgument() : null,
            type,
            others,
            fieldSpecifiers
        );
    }

    @Override
    public String getInitParameterName(String fieldName, @Nullable PyDataclassFieldParameters fieldParams) {
        // Fields starting with more than one underscore will be mangled into ClassName__field_name, but we don't support that
        String parameterAlias = fieldParams != null ? fieldParams.parameterAlias() : null;
        if (parameterAlias != null) {
            return parameterAlias;
        }
        return fieldName.startsWith("_") ? fieldName.substring(1) : fieldName;
    }

    @Override
    public @Nullable PyType getInitParameterType(PyClass cls, PyTargetExpression field, TypeEvalContext context) {
        if (context.maySwitchToAST(field)) {
            if (field.findAssignedValue() instanceof PyCallExpression call) {
                PyExpression typeArgument = call.getKeywordArgument("type");
                if (typeArgument != null) {
                    Ref<PyType> typeRef = PyTypingTypeProvider.getType(typeArgument, context);
                    if (typeRef != null) {
                        return typeRef.get();
                    }
                }
            }
        }

        PyType type = PyDataclassResolver.super.getInitParameterType(cls, field, context);
        if (PyTypeUtilKt.isAnyOrUnknown(type)) {
            PyFunction method = methodDecoratedAsAttributeDefault(cls, field.getName());
            PyType returnType = method != null ? method.getReturnType(context) : null;
            if (returnType != null) {
                return PyUnionType.createWeakType(PyTypeUtil.widenLiteralAndNumeric(returnType));
            }
        }
        return type;
    }

    @Override
    public @Nullable PyExpression getInitParameterDefault(
        PyClass cls,
        PyTargetExpression field,
        @Nullable PyDataclassFieldParameters fieldParams,
        TypeEvalContext context
    ) {
        if (fieldParams == null) {
            return PyDataclassResolver.super.getInitParameterDefault(cls, field, fieldParams, context);
        }
        if (fieldParams.hasDefault() ||
            fieldParams.hasDefaultFactory() ||
            methodDecoratedAsAttributeDefault(cls, field.getName()) != null) {
            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(cls.getProject());
            return elementGenerator.createEllipsis();
        }
        else {
            return null;
        }
    }

    @Override
    public void placeFieldInInitSignature(
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

        // attrs: a kw_only class makes every field keyword-only (not only those with kw_only != false).
        if ((acc.getSeenKeywordOnlyClass() || index < indexOfKeywordOnlyAttribute || Boolean.TRUE.equals(kwOnly))
            && !acc.getPositionalAliasOrFieldNameParams().containsKey(aliasOrFieldName)) {
            acc.getKeywordOnlyAliasOrFieldNameParams().add(aliasOrFieldName);
            acc.getKeywordOnlyFieldNameParams().add(fieldName);
        }

        if (parameter == null) {
            acc.getSeenNames().add(aliasOrFieldName);
        }
        else if (!PyDataclassResolverKt.isKwOnlyMarkerField(parameter, context) &&
                 !acc.getPositionalAliasOrFieldNameParams().containsKey(aliasOrFieldName)) {
            // attrs: an attribute that overrides an ancestor's attribute changes the order
            acc.getPositionalAliasOrFieldNameParams().put(aliasOrFieldName, parameter);
        }
    }

    private static @Nullable PyFunction methodDecoratedAsAttributeDefault(PyClass cls, @Nullable String attributeName) {
        if (attributeName == null) {
            return null;
        }
        for (PyFunction it : cls.getMethods()) {
            PyDecoratorList decoratorList = it.getDecoratorList();
            if (decoratorList != null && decoratorList.findDecorator(attributeName + ".default") != null) {
                return it;
            }
        }
        return null;
    }

    @Override
    public List<PyDataclassCopyFunction> copyFunctions() {
        Set<String> names = new LinkedHashSet<>(PyDataclassNames.Attrs.ATTRS_ASSOC);
        names.addAll(PyDataclassNames.Attrs.ATTRS_EVOLVE);
        List<PyDataclassCopyFunction> result = new ArrayList<>();
        for (String it : names) {
            result.add(new PyDataclassCopyFunction(QualifiedName.fromDottedString(it), "inst"));
        }
        return result;
    }

    @Override
    public boolean applyCustomDecoratorParameter(
        ParametersAccumulator accumulator,
        String name,
        @Nullable PyExpression value,
        @Nullable PyExpression argument
    ) {
        switch (name) {
            case "eq": {
                accumulator.setEq(PyEvaluator.evaluateAsBooleanNoResolve(value));
                accumulator.setEqArgument(argument);
                if (accumulator.getOrderArgument() == null && accumulator.getEqArgument() != null) {
                    accumulator.setOrder(accumulator.getEq());
                    accumulator.setOrderArgument(accumulator.getEqArgument());
                }
                break;
            }
            case "order": {
                if (!(argument instanceof PyNoneLiteralExpression)) {
                    accumulator.setOrder(PyEvaluator.evaluateAsBooleanNoResolve(value));
                    accumulator.setOrderArgument(argument);
                }
                break;
            }
            case "cmp": {
                accumulator.setEq(PyEvaluator.evaluateAsBooleanNoResolve(value));
                accumulator.setEqArgument(argument);
                accumulator.setOrder(accumulator.getEq());
                accumulator.setOrderArgument(accumulator.getEqArgument());
                break;
            }
            case "hash": {
                accumulator.setUnsafeHash(PyEvaluator.evaluateAsBooleanNoResolve(value));
                accumulator.setUnsafeHashArgument(argument);
                break;
            }
            default:
                return false;
        }
        return true;
    }

    private static boolean orDefault(@Nullable Boolean value, boolean defaultValue) {
        return value != null ? value : defaultValue;
    }
}
