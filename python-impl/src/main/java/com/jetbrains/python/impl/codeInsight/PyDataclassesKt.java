// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.stdlib.PyAttrsDataclassType;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassParametersBuilder;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassResolver;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassResolverKt;
import com.jetbrains.python.impl.codeInsight.stdlib.PyStdlibDataclassType;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.impl.stubs.PyDataclassStubImpl;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyDataclassStub;
import com.jetbrains.python.impl.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.impl.psi.types.PyCallableTypeImpl;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.StubAwareComputation;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.util.QualifiedName;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.Trinity;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Java counterpart of the top-level declarations in JB {@code PyDataclasses.kt}.
 */
public final class PyDataclassesKt {
    private PyDataclassesKt() {
    }

    /**
     * It should be used only to map arguments to parameters and
     * determine what settings dataclass has.
     */
    private static final List<Trinity<PyKnownDecorator, PyDataclassParameters.Type, List<String>>> DECORATOR_AND_TYPE_AND_PARAMETERS = List.of(
        Trinity.create(PyKnownDecorator.DATACLASSES_DATACLASS, PyStdlibDataclassType.INSTANCE, PyDataclassNames.Dataclasses.DECORATOR_PARAMETERS),
        Trinity.create(PyKnownDecorator.ATTR_S, PyAttrsDataclassType.INSTANCE, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
        Trinity.create(PyKnownDecorator.ATTR_ATTRS, PyAttrsDataclassType.INSTANCE, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
        Trinity.create(PyKnownDecorator.ATTR_ATTRIBUTES, PyAttrsDataclassType.INSTANCE, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
        Trinity.create(PyKnownDecorator.ATTR_DATACLASS, PyAttrsDataclassType.INSTANCE, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
        Trinity.create(PyKnownDecorator.ATTR_DEFINE, PyAttrsDataclassType.INSTANCE, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
        Trinity.create(PyKnownDecorator.ATTR_MUTABLE, PyAttrsDataclassType.INSTANCE, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
        Trinity.create(PyKnownDecorator.ATTR_FROZEN, PyAttrsDataclassType.INSTANCE, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
        Trinity.create(PyKnownDecorator.ATTRS_DEFINE, PyAttrsDataclassType.INSTANCE, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
        Trinity.create(PyKnownDecorator.ATTRS_MUTABLE, PyAttrsDataclassType.INSTANCE, PyDataclassNames.Attrs.DECORATOR_PARAMETERS),
        Trinity.create(PyKnownDecorator.ATTRS_FROZEN, PyAttrsDataclassType.INSTANCE, PyDataclassNames.Attrs.DECORATOR_PARAMETERS)
    );

    private static final PyDataclassStub NON_PARAMETERIZED_CANDIDATE_STUB = new PyDataclassStubImpl(
        "DATACLASS_TRANSFORM",
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        null
    );

    public static @Nullable PyDataclassParameters parseStdDataclassParameters(PyClass cls, TypeEvalContext context) {
        PyDataclassParameters parameters = parseDataclassParameters(cls, context);
        return parameters != null && parameters.getType().getName().equals(PyStdlibDataclassType.INSTANCE.getName()) ? parameters : null;
    }

    public static @Nullable PyDataclassParameters parseStdOrDataclassTransformDataclassParameters(PyClass cls, TypeEvalContext context) {
        PyDataclassParameters parameters = parseDataclassParameters(cls, context);
        if (parameters != null &&
            (parameters.getType().getName().equals(PyStdlibDataclassType.INSTANCE.getName()) ||
                parameters.getType().isDataclassTransformBased())) {
            return parameters;
        }
        return null;
    }

    public static @Nullable PyDataclassParameters parseDataclassParameters(PyClass cls, TypeEvalContext context) {
        return PyUtil.getNullableParameterizedCachedValue(cls, context, ctx ->
            StubAwareComputation.on(cls)
                .withCustomStub(stub -> stub.getCustomStub(PyDataclassStub.class))
                .overStub(dataclassStub -> resolveDataclassParameters(
                    cls,
                    dataclassStub != null ? dataclassStub : NON_PARAMETERIZED_CANDIDATE_STUB,
                    null,
                    ctx
                ))
                .overAst(it -> {
                    Pair<PyDataclassStub, DataclassParameterArgumentMapping> parsed = parseDataclassParametersFromAST(it, ctx);
                    PyDataclassStub dataclassStub = parsed != null ? parsed.getFirst() : NON_PARAMETERIZED_CANDIDATE_STUB;
                    DataclassParameterArgumentMapping dataclassParamArgMapping = parsed != null ? parsed.getSecond() : null;
                    return resolveDataclassParameters(cls, dataclassStub, dataclassParamArgMapping, ctx);
                })
                .withStubBuilder(it -> PyDataclassStubImpl.create(it))
                .compute(ctx)
        );
    }

    /**
     * Returns true if instances of {@code cls} reject an assignment to an attribute.
     * The dataclass can be a standard, attrs, or {@code dataclass_transform} dataclass, and {@code frozen_default} applies.
     * The resolver of the nearest dataclass in the MRO of {@code cls} decides.
     * By default, {@code cls} is frozen when it or one of its dataclass ancestors is frozen.
     *
     * @see PyDataclassResolver#isFrozenInstance
     */
    public static boolean isFrozenDataclass(PyClass cls, TypeEvalContext context) {
        List<PyClass> classes = new ArrayList<>();
        classes.add(cls);
        classes.addAll(cls.getAncestorClasses(context));
        PyDataclassResolver resolver = null;
        for (PyClass it : classes) {
            PyDataclassParameters parameters = parseDataclassParameters(it, context);
            if (parameters != null) {
                resolver = parameters.getType().getResolver();
                break;
            }
        }
        return resolver != null ? resolver.isFrozenInstance(cls, context) : PyDataclassResolverKt.isFrozenInAnyDataclassOfHierarchy(cls, context);
    }

    /**
     * Returns true if {@code attribute} is a class or instance attribute of a frozen dataclass, so its value cannot change.
     * A {@code ClassVar} is not frozen, because an assignment through the class object can change it.
     *
     * @see #isFrozenDataclass
     */
    public static boolean isFrozenAttribute(PyTargetExpression attribute, TypeEvalContext context) {
        if (!PyUtil.isAttribute(attribute) || PyTypingTypeProvider.isClassVar(attribute, context)) {
            return false;
        }
        PyClass cls = attribute.getContainingClass();
        if (cls == null) {
            return false;
        }
        return isFrozenDataclass(cls, context);
    }

    /**
     * This method MUST be used only while building stub for dataclass.
     *
     * @see #parseStdDataclassParameters
     * @see #parseDataclassParameters
     */
    public static @Nullable PyDataclassStub parseDataclassParametersForStub(PyClass cls) {
        Pair<PyDataclassStub, DataclassParameterArgumentMapping> parsed = parseDataclassParametersFromAST(cls, null);
        return parsed != null ? parsed.getFirst() : null;
    }

    /**
     * The predefined dataclass kind {@code cls} belongs to (stdlib / attrs / dataclass_transform, including Pydantic), or {@code null}
     * if {@code cls} is not a dataclass in {@code context} or is a dataclass of a third-party framework without a predefined kind.
     * <p>
     * Shared entry point for type providers and inspections so they don't re-derive the framework from decorators/config.
     */
    public static PyDataclassParameters.@Nullable Type getDataclassKind(PyClass cls, TypeEvalContext context) {
        PyDataclassParameters parameters = parseDataclassParameters(cls, context);
        return parameters != null ? parameters.getType() : null;
    }

    /**
     * {@code expression} is the "no default" sentinel.
     * see {@link PyDataclassResolver#getOmittedDefaultQualifiedNames()}).
     */
    public static boolean resolvesToOmittedDefault(PyDataclassResolver resolver, PyExpression expression) {
        if (resolver.getOmittedDefaultQualifiedNames().isEmpty() || !(expression instanceof PyReferenceExpression referenceExpression)) {
            return false;
        }
        for (QualifiedName it : PyResolveUtil.resolveImportedElementQNameLocally(referenceExpression)) {
            if (resolver.getOmittedDefaultQualifiedNames().contains(it.toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * It should be used only to map arguments to parameters and
     * determine what settings dataclass has.
     */
    public static List<Trinity<QualifiedName, PyDataclassParameters.Type, List<PyCallableParameter>>> decoratorAndTypeAndMarkedCallee(Project project) {
        List<Trinity<QualifiedName, PyDataclassParameters.Type, List<PyCallableParameter>>> result = new ArrayList<>();
        for (PyDataclassParametersProvider provider : PyDataclassParametersProvider.EP_NAME.getExtensionList()) {
            Trinity<QualifiedName, PyDataclassParameters.Type, List<PyCallableParameter>> triple = provider.getDecoratorAndTypeAndParameters(project);
            if (triple != null) {
                result.add(triple);
            }
        }
        for (Trinity<PyKnownDecorator, PyDataclassParameters.Type, List<String>> it : DECORATOR_AND_TYPE_AND_PARAMETERS) {
            if (it.getSecond().getName().equals(PyStdlibDataclassType.INSTANCE.getName())) {
                List<PyCallableParameter> parameters = new ArrayList<>();
                parameters.add(PyCallableParameterImpl.keywordOnlySeparatorNonPsi());
                for (String name : it.getThird()) {
                    parameters.add(PyCallableParameterImpl.nonPsi(name, PyAnyType.getUnknown(), PyNames.ELLIPSIS));
                }

                result.add(Trinity.create(it.getFirst().getQualifiedName(), it.getSecond(), parameters));
            }
            else {
                List<PyCallableParameter> parameters = new ArrayList<>();
                for (String name : it.getThird()) {
                    parameters.add(PyCallableParameterImpl.nonPsi(name, PyAnyType.getUnknown(), PyNames.ELLIPSIS));
                }
                result.add(Trinity.create(it.getFirst().getQualifiedName(), it.getSecond(), parameters));
            }
        }
        return result;
    }

    public static @Nullable Pair<PyDataclassStub, DataclassParameterArgumentMapping> buildDecoratorDataclassStubAndMapping(
        PyClass cls,
        @Nullable TypeEvalContext context
    ) {
        PyDecoratorList decorators = cls.getDecoratorList();
        if (decorators == null) {
            return null;
        }

        for (PyDecorator decorator : decorators.getDecorators()) {
            if (!(decorator.getCallee() instanceof PyReferenceExpression callee)) {
                continue;
            }

            for (QualifiedName decoratorQualifiedName : PyResolveUtil.resolveImportedElementQNameLocally(callee)) {
                Trinity<QualifiedName, PyDataclassParameters.Type, List<PyCallableParameter>> decoratorAndTypeAndMarkedCallee = null;
                for (Trinity<QualifiedName, PyDataclassParameters.Type, List<PyCallableParameter>> it : decoratorAndTypeAndMarkedCallee(cls.getProject())) {
                    if (it.getFirst().equals(decoratorQualifiedName)) {
                        decoratorAndTypeAndMarkedCallee = it;
                        break;
                    }
                }
                if (decoratorAndTypeAndMarkedCallee == null) {
                    continue;
                }

                PyCallExpression.PyArgumentsMapping mapping = PyCallExpressionHelper.mapArguments(
                    decorator,
                    new PyCallableTypeImpl(decoratorAndTypeAndMarkedCallee.getThird(), PyAnyType.getUnknown()),
                    context != null ? context : TypeEvalContext.codeInsightFallback(cls.getProject())
                );

                PyDataclassParametersBuilder builder =
                    new PyDataclassParametersBuilder(decoratorAndTypeAndMarkedCallee.getSecond(), decoratorAndTypeAndMarkedCallee.getFirst());

                for (Map.Entry<PyExpression, PyCallableParameter> entry : mapping.getMappedParameters().entrySet()) {
                    builder.update(entry.getValue().getName(), entry.getKey());
                }

                return builder.build();
            }
        }

        return null;
    }

    private static @Nullable Pair<PyDataclassStub, DataclassParameterArgumentMapping> parseDataclassParametersFromAST(PyClass cls,
                                                                                                                     @Nullable TypeEvalContext context) {
        return PyDataclassParametersProvider.EP_NAME.computeSafeIfAny(it -> it.buildDataclassStub(cls, context));
    }

    /**
     * Combine immediate properties from a dataclass stub with those from its ancestors and other sources.
     */
    private static @Nullable PyDataclassParameters resolveDataclassParameters(PyClass pyClass,
                                                                              PyDataclassStub stub,
                                                                              @Nullable DataclassParameterArgumentMapping argumentMapping,
                                                                              TypeEvalContext context) {
        return PyDataclassParametersProvider.EP_NAME.computeSafeIfAny(it -> {
            PyDataclassParameters.Type type = it.getType();
            PyDataclassResolver resolver = type.getResolver();
            return resolver != null ? resolver.resolveClassParameters(pyClass, stub, type, argumentMapping, context) : null;
        });
    }

    public static @Nullable PyDataclassFieldParameters resolveDataclassFieldParameters(PyClass dataclass,
                                                                                       PyDataclassParameters dataclassParams,
                                                                                       PyTargetExpression field,
                                                                                       TypeEvalContext context) {
        PyDataclassResolver resolver = dataclassParams.getType().getResolver();
        return resolver != null ? resolver.resolveFieldParameters(dataclass, dataclassParams, field, context) : null;
    }

    /**
     * Walks {@code cls} and its dataclass ancestors and returns the {@code InitVar[...]} pseudo-fields in declaration order, or {@code null}
     * when the class opts out of a generated {@code __init__}. {@code InitVar} is a stdlib-level annotation, so the detection is
     * framework-agnostic (stdlib-based frameworks such as Pydantic honor it too); frameworks that never generate an
     * {@code __init__} simply never see one. This is shared model construction, not per-framework behaviour, so it lives here
     * rather than on {@link PyDataclassResolver}.
     */
    public static @Nullable List<InitVarInfo> getDataclassInitVars(PyClass cls,
                                                                   @Nullable PyDataclassParameters params,
                                                                   TypeEvalContext context) {
        if (params == null || !params.getInit()) {
            return null;
        }
        List<PyClass> ancestors = new ArrayList<>(cls.getAncestorClasses(context));
        List<PyClass> classes = new ArrayList<>();
        for (int i = ancestors.size() - 1; i >= 0; i--) {
            PyClass ancestor = ancestors.get(i);
            if (parseDataclassParameters(ancestor, context) != null) {
                classes.add(ancestor);
            }
        }
        classes.add(cls);
        List<InitVarInfo> result = new ArrayList<>();
        for (PyClass it : classes) {
            for (PyTargetExpression field : it.getClassAttributes()) {
                PyType type = context.getType(field);
                if (type instanceof PyClassType classType &&
                    classType.isParameterized() &&
                    PyDataclassNames.Dataclasses.DATACLASSES_INITVAR.equals(classType.getClassQName())) {
                    List<PyType> typeArguments = classType.getTypeArguments();
                    result.add(new InitVarInfo(field, typeArguments.size() == 1 ? typeArguments.get(0) : null));
                }
            }
        }
        return result;
    }
}
