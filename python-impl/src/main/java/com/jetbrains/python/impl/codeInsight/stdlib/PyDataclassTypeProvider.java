// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.InitFieldsAccumulator;
import com.jetbrains.python.impl.codeInsight.InitVarInfo;
import com.jetbrains.python.impl.codeInsight.PyDataclassCopyFunction;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.PyDataclassesKt;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionNavigator;
import com.jetbrains.python.impl.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.impl.psi.types.PyCallableTypeImpl;
import com.jetbrains.python.impl.psi.types.PyOverloadType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

@ExtensionImpl(id = "pyDataclassTypeProvider", order = "after pyTypingNewTypeTypeProvider, before pyTypingTypeProvider")
public class PyDataclassTypeProvider extends PyTypeProviderBase {

    @Override
    public @Nullable PyType getReferenceExpressionType(PyReferenceExpression referenceExpression, TypeEvalContext context) {
        return getDataclassesReplaceType(referenceExpression, context);
    }

    @Override
    public @Nullable Ref<PyType> getReferenceType(PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
        if (referenceTarget instanceof PyTargetExpression targetExpression) {
            PyType type = getUnannotatedConverterFieldType(targetExpression, context);
            if (type != null) {
                return Ref.create(type);
            }
        }

        return null;
    }

    /**
     * The converter return type of a `dataclass_transform` {@code field} without an annotation. An annotated field keeps its declared type.
     */
    private static @Nullable PyType getUnannotatedConverterFieldType(PyTargetExpression field, TypeEvalContext context) {
        if (field.getAnnotationValue() != null || field.getTypeCommentAnnotation() != null) {
            return null;
        }
        return PyDataclassTransformResolver.INSTANCE.getConverterReturnType(field, context);
    }

    @Override
    public @Nullable Ref<PyType> getParameterType(PyNamedParameter param, PyFunction func, TypeEvalContext context) {
        if (param.isPositionalContainer() || param.isKeywordContainer() || param.getAnnotationValue() != null) {
            return null;
        }

        PyClass cls = func.getContainingClass();
        if (cls == null) {
            return null;
        }
        PyDataclassParameters dataclassParameters = PyDataclassesKt.parseDataclassParameters(cls, context);
        if (dataclassParameters == null) {
            return null;
        }
        PyDataclassResolver resolver = dataclassParameters.getType().getResolver();
        if (!Objects.equals(func.getName(), resolver != null ? resolver.postInitFunctionName() : null)) {
            return null;
        }

        List<PyCallableParameter> allParameters = func.getParameters(context);
        List<PyCallableParameter> parameters = allParameters.isEmpty() ? allParameters : allParameters.subList(1, allParameters.size());
        int parameterIndex = -1;
        for (int i = 0; i < parameters.size(); i++) {
            if (parameters.get(i).getParameter() == param) {
                parameterIndex = i;
                break;
            }
        }
        if (parameterIndex == -1) {
            return null;
        }

        List<InitVarInfo> initVars = PyDataclassesKt.getDataclassInitVars(cls, dataclassParameters, context);
        if (initVars == null) {
            return null;
        }
        return parameterIndex < initVars.size() ? Ref.create(initVars.get(parameterIndex).getType()) : null;
    }

    @Override
    public @Nullable Ref<@Nullable PyType> prepareCalleeTypeForCall(@Nullable PyType type, PyExpression callee, TypeEvalContext context) {
        if (type instanceof PyClassType pyClassType && pyClassType.isDefinition()) {
            PyClassType classType = PyCallExpressionHelper.stripDefaultTypeArguments(pyClassType, context);
            PyType dataclassType = generateDataclassConstructorType(classType, context);
            if (dataclassType != null) {
                return Ref.create(dataclassType);
            }
        }
        return null;
    }

    @Override
    public @Nullable List<PyTypeMember> getMemberTypes(PyType type,
                                                       String name,
                                                       @Nullable PyExpression location,
                                                       AccessDirection direction,
                                                       PyResolveContext context) {
        if (!(type instanceof PyClassType classType)) {
            return null;
        }
        PyDataclassParameters dataclassParameters = PyDataclassesKt.parseDataclassParameters(classType.getPyClass(), context.getTypeEvalContext());
        if (dataclassParameters == null) {
            return null;
        }
        PyDataclassResolver resolver = dataclassParameters.getType().getResolver();
        if (PyNames.HASH.equals(name)) {
            if (resolver == null) {
                return null;
            }

            // See `unsafe_hash` section here https://docs.python.org/3/library/dataclasses.html
            if (dataclassParameters.getUnsafeHash()) {
                return null;
            }

            if (!dataclassParameters.getEq()) {
                return null;
            }

            if (Boolean.TRUE.equals(dataclassParameters.getFrozen())) {
                return null;
            }

            List<? extends RatedResolveResult> resolvedMembers = classType.resolveMember(name, location, direction, context, false);
            if (resolvedMembers != null && !resolvedMembers.isEmpty()) {
                return null;
            }
            return List.of(new PyTypeMember((PsiElement) null, PyBuiltinCache.getInstance(classType.getPyClass()).getNoneType()));
        }
        else {
            if (resolver != null && Boolean.TRUE.equals(dataclassParameters.getFrozen())) {
                List<? extends RatedResolveResult> resolvedMembers = classType.resolveMember(name, location, direction, context, false);
                if (resolvedMembers != null && !resolvedMembers.isEmpty()) {
                    List<PyTypeMember> result = new ArrayList<>(resolvedMembers.size());
                    for (RatedResolveResult it : resolvedMembers) {
                        PsiElement element = it.getElement();
                        PyType memberType;
                        if (element instanceof PyTypedElement typedElement) {
                            memberType = context.getTypeEvalContext().getType(typedElement);
                        }
                        else {
                            memberType = null;
                        }
                        result.add(new PyTypeMember(element, memberType, false, element, null, null));
                    }
                    return result;
                }
            }
        }

        return null;
    }

    private static @Nullable PyCallableType getDataclassesReplaceType(PyReferenceExpression referenceExpression, TypeEvalContext context) {
        PyCallExpression call = PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression);
        if (call == null) {
            return null;
        }
        if (!(call.getCallee() instanceof PyReferenceExpression callee)) {
            return null;
        }

        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        List<PsiElement> resolvedCallees = PyUtil.multiResolveTopPriority(callee.getReference(resolveContext));
        PsiElement resolvedCallee = resolvedCallees.size() == 1 ? resolvedCallees.get(0) : null;

        return resolvedCallee instanceof PyCallable callable ? getDataclassesReplaceType(callable, call, context) : null;
    }

    private static @Nullable PyCallableType getDataclassesReplaceType(PyCallable resolvedCallee,
                                                                      PyCallExpression call,
                                                                      TypeEvalContext context) {
        String qualifiedName = resolvedCallee.getQualifiedName();
        if (qualifiedName == null) {
            return null;
        }
        String instanceName = null;
        outer:
        for (PyDataclassResolver resolver : PyDataclassResolverKt.getAllRegisteredDataclassResolvers()) {
            for (PyDataclassCopyFunction copyFunction : resolver.copyFunctions()) {
                if (copyFunction.getQualifiedName().toString().equals(qualifiedName)) {
                    instanceName = copyFunction.getInstanceParameterName();
                    break outer;
                }
            }
        }
        if (instanceName == null) {
            return null;
        }

        PyTypedElement obj = call.getArgument(0, instanceName, PyTypedElement.class);
        if (obj == null) {
            return null;
        }
        if (!(context.getType(obj) instanceof PyClassType objType)) {
            return null;
        }
        if (objType.isDefinition()) {
            return null;
        }

        if (!(generateDataclassConstructorType(objType, context) instanceof PyCallableType dataclassType)) {
            return null;
        }
        List<PyCallableParameter> dataclassParameters = dataclassType.getParameters(context);
        if (dataclassParameters == null) {
            return null;
        }

        List<PyCallableParameter> parameters = new ArrayList<>();
        PyElementGenerator elementGenerator = PyElementGenerator.getInstance(resolvedCallee.getProject());

        parameters.add(PyCallableParameterImpl.nonPsi(instanceName, objType));
        parameters.add(PyCallableParameterImpl.psi(elementGenerator.createSingleStarParameter()));

        PyExpression ellipsis = elementGenerator.createEllipsis();
        for (PyCallableParameter it : dataclassParameters) {
            parameters.add(PyCallableParameterImpl.nonPsi(it.getName(), it.getType(context), ellipsis));
        }

        return new PyCallableTypeImpl(parameters, dataclassType.getReturnType(context));
    }

    /**
     * Returns the type of the synthesized constructor of the dataclass {@code clsType}, or {@code null} if {@code clsType} is not a dataclass.
     */
    public static @Nullable PyType generateDataclassConstructorType(@Nullable PyType clsType, TypeEvalContext context) {
        if (!(clsType instanceof PyClassType classType)) {
            return null;
        }
        PyClassType genericClassType = classType.isParameterized() ? classType : null;
        if (genericClassType == null) {
            genericClassType = PyTypeChecker.findGenericDefinitionType(classType.getPyClass(), context);
        }
        if (genericClassType == null) {
            genericClassType = classType;
        }

        InitFieldsAccumulator acc = PyDataclassResolverKt.collectDataclassInitFields(genericClassType, context, true);
        if (acc == null) {
            return null;
        }
        PyDataclassParameters controlling = acc.getControllingParameters();
        if (controlling == null) {
            return null;
        }
        PyDataclassResolver dataclassResolver = controlling.getType().getResolver();
        List<List<PyCallableParameter>> paramsSets = dataclassResolver != null ? dataclassResolver.buildInitSignatureParameterSets(acc) : null;
        if (paramsSets == null) {
            return null;
        }
        if (paramsSets.isEmpty()) {
            return null;
        }

        List<PyCallableType> signatures = new ArrayList<>();
        for (List<PyCallableParameter> it : new LinkedHashSet<>(paramsSets)) {
            signatures.add(new PyCallableTypeImpl(it, genericClassType.toInstance()));
        }
        return signatures.size() > 1 ? new PyOverloadType(signatures, null) : signatures.get(0);
    }
}
