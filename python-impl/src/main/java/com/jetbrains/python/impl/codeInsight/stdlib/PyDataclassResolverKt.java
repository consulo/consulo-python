// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.*;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.stdlib.PyDataclassNames.Dataclasses;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.impl.stubs.PyDataclassFieldStubImpl;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.stubs.PyDataclassFieldStub;
import com.jetbrains.python.impl.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.*;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Top-level functions of PyDataclassResolver.kt
 */
public final class PyDataclassResolverKt {
    private PyDataclassResolverKt() {
    }

    /**
     * The default {@link PyDataclassResolver#isFrozenInstance} rule, also used for dataclass types without a resolver.
     */
    public static boolean isFrozenInAnyDataclassOfHierarchy(PyClass cls, TypeEvalContext context) {
        List<PyClass> classes = new ArrayList<>();
        classes.add(cls);
        classes.addAll(cls.getAncestorClasses(context));
        for (PyClass it : classes) {
            PyDataclassParameters parameters = PyDataclassesKt.parseDataclassParameters(it, context);
            if (parameters != null && Boolean.TRUE.equals(parameters.getFrozen())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Contributes the dataclass fields declared *directly on* {@code pyClass} (no MRO traversal) to {@code acc}, using this
     * framework's rules for parameter name, type, default value and positional/keyword placement.
     * <p>
     * Never recurses into superclasses — the MRO walk is orchestrated by {@link #collectDataclassInitFields}, which calls this
     * once per ancestor. Frameworks customise the per-field primitives ({@link PyDataclassResolver#getInitParameterName},
     * {@link PyDataclassResolver#getInitParameterType}, {@link PyDataclassResolver#getInitParameterDefault},
     * {@link PyDataclassResolver#placeFieldInInitSignature}) rather than overriding the loop itself.
     */
    private static void collectDirectInitFields(
        PyDataclassResolver resolver,
        InitFieldsAccumulator acc,
        PyClass pyClass,
        PyDataclassParameters parameters,
        TypeEvalContext context
    ) {
        List<PyTargetExpression> classAttributes = new ArrayList<>(pyClass.getClassAttributes());
        Collections.reverse(classAttributes);
        List<PyDataclassField> fields = new ArrayList<>();
        for (PyTargetExpression it : classAttributes) {
            if (PyTypingTypeProvider.isClassVar(it, context)) {
                continue;
            }
            PyDataclassField field = createDataclassFieldFromClassAttribute(resolver, it, pyClass, parameters, context);
            if (field == null) {
                continue;
            }
            if (acc.getSeenNames().contains(field.getParameterName())) {
                continue;
            }
            fields.add(field);
        }

        int kwOnlyMarkerIndex = -1;
        for (int i = fields.size() - 1; i >= 0; i--) {
            PyCallableParameter parameter = fields.get(i).getParameter();
            if (parameter != null && isKwOnlyMarkerField(parameter, context)) {
                kwOnlyMarkerIndex = i;
                break;
            }
        }

        for (int index = 0; index < fields.size(); index++) {
            // note: attributes are visited from inheritors to ancestors, in reversed order for every of them
            resolver.placeFieldInInitSignature(acc, fields.get(index), index, kwOnlyMarkerIndex, context);
        }
    }

    /**
     * Builds a single field declared on {@code cls}, or {@code null} when it is not a generated `__init__` parameter.
     */
    private static @Nullable PyDataclassField createDataclassFieldFromClassAttribute(
        PyDataclassResolver resolver,
        PyTargetExpression field,
        PyClass cls,
        PyDataclassParameters parameters,
        TypeEvalContext context
    ) {
        String fieldName = field.getName();
        if (fieldName == null) {
            return null;
        }

        PyDataclassFieldParameters fieldParams = resolver.resolveFieldParameters(cls, parameters, field, context);
        if (fieldParams != null && !fieldParams.initValue()) {
            return new PyDataclassField(field, fieldName, fieldName, false, null);
        }
        if (fieldParams == null && field.getAnnotationValue() == null) {
            return null; // skip fields that are not annotated
        }

        // The alias precedence (Pydantic validation_alias > Field(alias=...) > field name) is resolved per framework in
        // buildFieldParameters and exposed as PyDataclassFieldParameters.parameterAlias; parameterName applies the
        // per-framework parameter-name rules (e.g. attrs leading-underscore stripping) on top of it.
        String parameterName = resolver.getInitParameterName(fieldName, fieldParams);

        PyCallableParameter parameter = PyCallableParameterImpl.nonPsi(
            parameterName,
            resolver.getInitParameterType(cls, field, context),
            resolver.getInitParameterDefault(cls, field, fieldParams, context),
            field
        );

        return new PyDataclassField(field, fieldName, parameterName, fieldParams != null ? fieldParams.kwOnly() : null, parameter);
    }

    static boolean isAssignedOmittedDefault(
        PyDataclassResolver resolver,
        PyClass dataclass,
        PyTargetExpression field,
        TypeEvalContext context
    ) {
        if (resolver.getOmittedDefaultQualifiedNames().isEmpty()) {
            return false;
        }
        QualifiedName assignedQName = field.getAssignedQName();
        if (assignedQName == null) {
            return false;
        }

        for (PsiElement it : PyResolveUtil.resolveQualifiedNameInScope(assignedQName,
                                                                       Objects.requireNonNull(ScopeUtil.getScopeOwner(dataclass)),
                                                                       context)) {
            if (it instanceof PyQualifiedNameOwner qualifiedNameOwner) {
                String qualifiedName = qualifiedNameOwner.getQualifiedName();
                if (qualifiedName != null && resolver.getOmittedDefaultQualifiedNames().contains(qualifiedName)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The field's custom stub, framework-agnostic: field stubs are built context-free by the
     * {@link PyDataclassParametersProvider} extensions.
     */
    static @Nullable PyDataclassFieldStub retrieveFieldStub(PyTargetExpression field) {
        if (field.getStub() != null) {
            // Reuse the already-built PSI stub when the field is stubbed; otherwise build the field stub from AST.
            return field.getStub().getCustomStub(PyDataclassFieldStub.class);
        }
        else {
            return PyDataclassFieldStubImpl.create(field);
        }
    }

    // @ApiStatus.Internal
    public static boolean isKwOnlyMarkerField(PyCallableParameter parameter, TypeEvalContext context) {
        PsiElement psi = parameter.getDeclarationElement();
        if (!(psi instanceof PyTargetExpression targetExpression)) {
            return false;
        }
        if (!(PyTypingTypeProvider.getAnnotationValue(targetExpression, context) instanceof PyReferenceExpression typeHint)) {
            return false;
        }
        // TODO consulo: Ref.deref missing
        Ref<PyType> typeRef = PyTypingTypeProvider.getType(typeHint, context);
        PyType type = typeRef != null ? typeRef.get() : null;
        return type instanceof PyClassType classType && Dataclasses.DATACLASSES_KW_ONLY.equals(classType.getClassQName());
    }

    // @ApiStatus.Internal
    public static List<PyCallableParameter> buildParameters(
        Map<String, PyCallableParameter> fields,
        Set<String> keywordOnly
    ) {
        List<Map.Entry<String, PyCallableParameter>> entries = new ArrayList<>(fields.entrySet());
        Collections.reverse(entries);
        if (keywordOnly.isEmpty()) {
            List<PyCallableParameter> result = new ArrayList<>();
            for (Map.Entry<String, PyCallableParameter> entry : entries) {
                result.add(entry.getValue());
            }
            return result;
        }

        List<PyCallableParameter> positionalOrKeyword = new ArrayList<>();
        List<PyCallableParameter> keyword = new ArrayList<>();

        for (Map.Entry<String, PyCallableParameter> entry : entries) {
            if (!keywordOnly.contains(entry.getKey())) {
                positionalOrKeyword.add(entry.getValue());
            }
            else {
                keyword.add(entry.getValue());
            }
        }

        List<PyCallableParameter> result = new ArrayList<>(positionalOrKeyword);
        result.add(PyCallableParameterImpl.keywordOnlySeparatorNonPsi());
        result.addAll(keyword);
        return result;
    }

    /**
     * Shared, framework-agnostic MRO traversal collecting the generated `__init__` field parameters. It walks the class and
     * its ancestors, gates on `__init__`/`__new__` and the `dataclass_transform` decorator, and delegates the per-class
     * field name/type/default and positional/keyword placement to the ancestor's {@link #collectDirectInitFields}.
     * The final signature shape is produced by {@link PyDataclassResolver#buildInitSignatureParameterSets} of the
     * controlling framework.
     */
    public static @Nullable InitFieldsAccumulator collectDataclassInitFields(
        PyClassType clsType,
        TypeEvalContext context,
        boolean initOnly
    ) {
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        InitFieldsAccumulator acc = new InitFieldsAccumulator();

        List<@Nullable PyClassLikeType> types = new ArrayList<>();
        types.add(clsType);
        types.addAll(clsType.getAncestorTypes(context));
        for (PyClassLikeType currentType : types) {
            if (currentType == null ||
                !isNullOrEmpty(currentType.resolveMember(PyNames.INIT, null, AccessDirection.READ, resolveContext, false)) ||
                !isNullOrEmpty(currentType.resolveMember(PyNames.NEW, null, AccessDirection.READ, resolveContext, false)) ||
                !(currentType instanceof PyClassType currentClassType)) {
                if (acc.getSeenInit()) {
                    continue;
                }
                else {
                    break;
                }
            }

            PyClass current = currentClassType.getPyClass();
            PyDataclassParameters parameters = PyDataclassesKt.parseDataclassParameters(current, context);

            if (parameters == null) {
                // The base class decorated with @dataclass_transform gets filtered out already here, because for it we don't detect DataclassParameters
                if (PyKnownDecoratorUtil.hasUnknownDecorator(current, context)) {
                    break;
                }
                else {
                    continue;
                }
            }
            else if (parameters.getType().getResolver() == null && !parameters.getType().isDataclassTransformBased()) {
                break;
            }

            if (acc.getControllingParameters() == null) {
                acc.setControllingParameters(parameters);
            }
            acc.setSeenInit(acc.getSeenInit() || parameters.getInit());
            acc.setSeenKeywordOnlyClass(acc.getSeenKeywordOnlyClass() || parameters.getKwOnly());

            if (!initOnly || acc.getSeenInit()) {
                PyDataclassResolver resolver = parameters.getType().getResolver();
                if (resolver != null) {
                    collectDirectInitFields(resolver, acc, current, parameters, context);
                }
            }
        }

        if (initOnly && !acc.getSeenInit()) {
            return null;
        }
        return acc;
    }

    private static boolean isNullOrEmpty(@Nullable List<? extends RatedResolveResult> list) {
        return list == null || list.isEmpty();
    }

    /**
     * All per-framework resolvers, built-in plus extension-point-contributed. Used by lookups that don't have parsed
     * parameters yet (e.g. resolving a `dataclasses.replace` call whose target class is not known in advance). The generic
     * third-party fallback is omitted because its copy functions duplicate the stdlib ones.
     */
    public static List<PyDataclassResolver> getAllRegisteredDataclassResolvers() {
        List<PyDataclassResolver> result = new ArrayList<>();
        for (PyDataclassParametersProvider it : PyDataclassParametersProvider.EP_NAME.getExtensionList()) {
            PyDataclassResolver resolver = it.getType().getResolver();
            if (resolver != null) {
                result.add(resolver);
            }
        }
        return result;
    }
}
