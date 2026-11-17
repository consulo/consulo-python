// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.DataclassParameterArgumentMapping;
import com.jetbrains.python.impl.codeInsight.PyDataclassFieldParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyDataclassFieldStub;
import com.jetbrains.python.impl.psi.stubs.PyDataclassStub;
import com.jetbrains.python.impl.psi.stubs.PyDataclassTransformDecoratorStub;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.IntentionalUnstubbing;
import com.jetbrains.python.psi.impl.StubAwareComputation;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Resolves PEP 681 `dataclass_transform` classes. The double-check that an actual `@dataclass_transform` marker resolves
 * in the ancestry/metaclass — rejecting transform candidates that turn out not to be dataclasses — lives here.
 */
public final class PyDataclassTransformResolver implements PyDataclassResolver {
    public static final PyDataclassTransformResolver INSTANCE = new PyDataclassTransformResolver();

    private PyDataclassTransformResolver() {
    }

    // dataclasses.MISSING is not mentioned in the spec, but because dataclasses.KW_ONLY is supported,
    // this one is special-cased as well
    @Override
    public Set<String> getOmittedDefaultQualifiedNames() {
        return PyDataclassNames.Dataclasses.OMITTED_DEFAULTS;
    }

    @Override
    public @Nullable PyDataclassParameters resolveClassParameters(
        PyClass pyClass,
        PyDataclassStub stub,
        PyDataclassParameters.Type type,
        @Nullable DataclassParameterArgumentMapping argumentMapping,
        TypeEvalContext context
    ) {
        PyDecoratorList decoratorList = pyClass.getDecoratorList();
        PyDecorator[] decorators = decoratorList != null ? decoratorList.getDecorators() : new PyDecorator[0];
        Stream<PsiElement> dataclassTransformTargets = Stream.<Supplier<Stream<? extends PsiElement>>>of(
            () -> Arrays.stream(decorators).flatMap(it -> PyDataclassTransformResolverKt.resolveDecoratorStubSafe(it, context).stream()),
            () -> pyClass.getAncestorClasses(context).stream(),
            () -> {
                PyClassLikeType metaClassType = pyClass.getMetaClassType(true, context);
                return metaClassType instanceof PyClassType classType ? Stream.of(classType.getPyClass()) : Stream.empty();
            }
        ).flatMap(Supplier::get);
        PyDecorator dataclassTransformDecorator =
            PyDataclassTransformResolverKt.findDataclassTransformDecorator(dataclassTransformTargets, context);
        if (dataclassTransformDecorator == null) {
            return null;
        }

        PyDataclassTransformDecoratorStub dataclassTransformStub = StubAwareComputation.on(dataclassTransformDecorator)
            .withCustomStub(dtStub -> dtStub.getCustomStub(PyDataclassTransformDecoratorStub.class))
            .overStub(dtStub -> dtStub)
            .withStubBuilder(PyDataclassTransformDecoratorStub::create)
            .compute(context);
        if (dataclassTransformStub == null) {
            return null;
        }

        Boolean frozenValue = stub.frozenValue();
        if (frozenValue == null) {
            boolean isOnMetaClass = PyDataclassTransformResolverKt.isDataclassTransformOnMetaclass(dataclassTransformDecorator, pyClass, context);

            if (isOnMetaClass) {
                frozenValue = dataclassTransformStub.getFrozenDefault();
            }
            else {
                Boolean frozenDefault = dataclassTransformStub.getFrozenDefault();
                frozenValue = frozenDefault != null ? frozenDefault : false;
            }
        }

        ScopeOwner decoratorScopeOwner = ScopeUtil.getScopeOwner(dataclassTransformDecorator);
        assert decoratorScopeOwner != null;
        List<QualifiedName> resolvedFieldSpecifiers = new ArrayList<>();
        for (QualifiedName it : dataclassTransformStub.getFieldSpecifiers()) {
            for (PsiElement resolved : PyResolveUtil.resolveQualifiedNameInScope(it, decoratorScopeOwner, context)) {
                if (resolved instanceof PyQualifiedNameOwner qualifiedNameOwner) {
                    String qualifiedName = qualifiedNameOwner.getQualifiedName();
                    if (qualifiedName != null) {
                        resolvedFieldSpecifiers.add(QualifiedName.fromDottedString(qualifiedName));
                    }
                }
            }
        }
        return new PyDataclassParameters(
            orDefault(stub.initValue(), true),
            orDefault(stub.reprValue(), true),
            orDefault(stub.eqValue(), dataclassTransformStub.getEqDefault()),
            orDefault(stub.orderValue(), dataclassTransformStub.getOrderDefault()),
            orDefault(stub.unsafeHashValue(), true),
            frozenValue,
            orDefault(stub.matchArgsValue(), true),
            orDefault(stub.kwOnly(), dataclassTransformStub.getKwOnlyDefault()),
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
            argumentMapping != null ? argumentMapping.getOthers() : Collections.emptyMap(),
            resolvedFieldSpecifiers
        );
    }

    @Override
    public String getInitParameterName(String fieldName, @Nullable PyDataclassFieldParameters fieldParams) {
        String alias = fieldParams != null ? fieldParams.getParameterAlias() : null;
        return alias != null ? alias : fieldName;
    }

    /**
     * A converter field accepts the converter input type in `__init__`.
     */
    @Override
    public @Nullable PyType getInitParameterType(PyClass cls, PyTargetExpression field, TypeEvalContext context) {
        PyType converterInputType = getConverterInputType(field, context);
        return converterInputType != null ? converterInputType : PyDataclassResolver.super.getInitParameterType(cls, field, context);
    }

    /**
     * The type that the `converter` of the `dataclass_transform` field {@code field} accepts, or {@code null} when {@code field} has no converter.
     * The generated `__init__` and an assignment to the field use this type.
     */
    public @Nullable PyType getConverterInputType(PyTargetExpression field, TypeEvalContext context) {
        PyExpression converter = findConverterArgument(field, context);
        return converter != null ? PyDataclassTransformResolverKt.getInputTypeOfConverter(converter, context) : null;
    }

    /**
     * The type that the `converter` of the `dataclass_transform` field {@code field} returns, or {@code null} when {@code field} has no converter.
     */
    public @Nullable PyType getConverterReturnType(PyTargetExpression field, TypeEvalContext context) {
        PyExpression converter = findConverterArgument(field, context);
        return converter != null ? PyDataclassTransformResolverKt.getReturnTypeOfConverter(converter, context) : null;
    }

    /**
     * The `converter` argument of the field-specifier call assigned to {@code field}, or {@code null} when there is none.
     * The AST of the file of {@code field} is loaded only when the field specifier declares a `converter` parameter.
     */
    private @Nullable PyExpression findConverterArgument(PyTargetExpression field, TypeEvalContext context) {
        QualifiedName fieldCalleeName = field.getCalleeName();
        if (fieldCalleeName == null) {
            return null;
        }
        if (!(ScopeUtil.getScopeOwner(field) instanceof PyClass dataclass)) {
            return null;
        }
        PyDataclassParameters parameters = PyDataclassesKt.parseDataclassParameters(dataclass, context);
        if (parameters == null || parameters.getType() != PyDataclassTransformType.INSTANCE) {
            return null;
        }
        PyFunction fieldSpecifier = resolveFieldSpecifierCallable(dataclass, parameters, field, fieldCalleeName, context);
        if (fieldSpecifier == null) {
            return null;
        }
        if (fieldSpecifier.getParameterList().findParameterByName(PyDataclassTransformResolverKt.CONVERTER) == null) {
            return null;
        }
        return IntentionalUnstubbing.onFileOf(field, () ->
            field.findAssignedValue() instanceof PyCallExpression call ? call.getKeywordArgument(PyDataclassTransformResolverKt.CONVERTER) : null
        );
    }

    /**
     * Generic `dataclass_transform` field resolution: the field's assigned value must resolve to one of the declared
     * {@link PyDataclassParameters#getFieldSpecifiers()}; `init` / `kw_only` defaults are then taken from that field-specifier
     * callable. Pydantic-specific behaviour (accepting a `Field(...)` nested in `Annotated[...]` and the Pydantic alias
     * precedence) is added by the Pydantic resolver, which reuses {@link #resolveFieldSpecifierCallable} and
     * {@link #buildFieldSpecifierParameters} rather than re-implementing the resolution.
     */
    @Override
    public @Nullable PyDataclassFieldParameters buildFieldParametersFromStub(
        PyClass dataclass,
        PyDataclassParameters parameters,
        PyTargetExpression field,
        @Nullable PyDataclassFieldStub fieldStub,
        TypeEvalContext context
    ) {
        QualifiedName fieldCalleeName = field.getCalleeName();
        if (fieldCalleeName == null) {
            return null;
        }
        PyFunction resolvedCallable = resolveFieldSpecifierCallable(dataclass, parameters, field, fieldCalleeName, context);
        if (resolvedCallable == null) {
            return null;
        }
        return buildFieldSpecifierParameters(parameters, fieldStub, resolvedCallable, fieldStub != null ? fieldStub.getAlias() : null);
    }

    /**
     * Resolves the field-specifier callable named by {@code fieldCalleeName}: the callee must resolve to one of the declared
     * {@link PyDataclassParameters#getFieldSpecifiers()}, and the matching overload is selected when the specifier is overloaded.
     * Returns {@code null} when {@code fieldCalleeName} is not a declared field specifier. Shared by the generic transform path and
     * the Pydantic override.
     */
    @SuppressWarnings("deprecation")
    public @Nullable PyFunction resolveFieldSpecifierCallable(
        PyClass dataclass,
        PyDataclassParameters parameters,
        PyTargetExpression field,
        QualifiedName fieldCalleeName,
        TypeEvalContext context
    ) {
        ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(dataclass);
        assert scopeOwner != null;
        PyQualifiedNameOwner fieldSpecifierDeclaration = null;
        for (PsiElement it : PyResolveUtil.resolveQualifiedNameInScope(fieldCalleeName, scopeOwner, context)) {
            if (it instanceof PyQualifiedNameOwner qualifiedNameOwner) {
                String qualifiedName = qualifiedNameOwner.getQualifiedName();
                if (qualifiedName != null && parameters.getFieldSpecifiers().contains(QualifiedName.fromDottedString(qualifiedName))) {
                    fieldSpecifierDeclaration = qualifiedNameOwner;
                    break;
                }
            }
        }
        if (fieldSpecifierDeclaration == null) {
            return null;
        }
        PyFunction fieldSpecifierCallable;
        if (fieldSpecifierDeclaration instanceof PyClass pyClass) {
            fieldSpecifierCallable = pyClass.findInitOrNew(true, context);
        }
        else if (fieldSpecifierDeclaration instanceof PyFunction function) {
            fieldSpecifierCallable = function;
        }
        else {
            fieldSpecifierCallable = null;
        }
        if (fieldSpecifierCallable == null) {
            return null;
        }

        boolean shouldMatchOverloads = !PyiUtil.getOverloads(fieldSpecifierCallable, context).isEmpty();
        if (shouldMatchOverloads) {
            PyCallExpression callExpression = IntentionalUnstubbing.onFileOf(field, () ->
                field.findAssignedValue() instanceof PyCallExpression call ? call : null
            );
            PyFunction overload =
                callExpression != null ? PyCallExpressionHelper.selectMatchingOverload(fieldSpecifierCallable, callExpression, context) : null;
            return overload != null ? overload : fieldSpecifierCallable;
        }
        else {
            return fieldSpecifierCallable;
        }
    }

    /**
     * Builds the {@link PyDataclassFieldParameters} from a resolved field-specifier {@code resolvedCallable} and the
     * precedence-resolved {@code parameterAlias}. Shared by the generic transform path and the Pydantic override.
     */
    public PyDataclassFieldParameters buildFieldSpecifierParameters(
        PyDataclassParameters parameters,
        @Nullable PyDataclassFieldStub fieldStub,
        PyFunction resolvedCallable,
        @Nullable String parameterAlias
    ) {
        Boolean initValue = fieldStub != null ? fieldStub.initValue() : null;
        if (initValue == null) {
            initValue = PyDataclassTransformResolverKt.getArgumentDefault("init", resolvedCallable);
        }
        Boolean kwOnly = fieldStub != null ? fieldStub.kwOnly() : null;
        if (kwOnly == null) {
            kwOnly = PyDataclassTransformResolverKt.getArgumentDefault("kw_only", resolvedCallable);
        }
        return new PyDataclassFieldParameters(
            fieldStub != null && fieldStub.hasDefault(),
            fieldStub != null && fieldStub.hasDefaultFactory(),
            // Per PEP 681 the field specifier's own `init` default wins; fall back to `true` (there is no class-level `init`
            // override for a single field — the class `init=False` case is handled earlier, in the type provider).
            initValue != null ? initValue : true,
            kwOnly != null ? kwOnly : parameters.getKwOnly(),
            parameterAlias
        );
    }

    private static boolean orDefault(@Nullable Boolean value, boolean defaultValue) {
        return value != null ? value : defaultValue;
    }
}
