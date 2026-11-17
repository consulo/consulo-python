// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.parameterInfo;

import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.stubs.PyTypingAliasStubType;
import com.jetbrains.python.impl.psi.types.PyParamSpecType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedRatedResolveResult;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

/**
 * Helpers for the "Parameter Info" popup shown inside the square brackets of a parameterized type, such as
 * `Generator[<caret>]`. Mirrors the resolution performed by `PyTypeHintsInspection` when it validates type arguments.
 */
// @ApiStatus.Internal
public final class PyTypeParameterInfoUtil {
    private PyTypeParameterInfoUtil() {
    }

    /**
     * If the operand of {@code subscription} refers to something that can be parameterized with type arguments
     * (a generic class or a generic type alias), returns the ordered list of its type parameters. Otherwise,
     * returns an empty list.
     */
    public static List<PyTypeParameterType> getTypeParameters(PySubscriptionExpression subscription, TypeEvalContext context) {
        if (!(subscription.getOperand() instanceof PyReferenceExpression operand)) {
            return Collections.emptyList();
        }
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
        PsiElement declaration = null;
        for (QualifiedRatedResolveResult result : operand.multiFollowAssignmentsChain(resolveContext, target -> {
            if (PyTypingTypeProvider.isExplicitTypeAlias(target, context)) {
                return false;
            }
            else if (PyTypingAliasStubType.getAssignedValueStubLike(target) instanceof PyReferenceExpression) {
                String qualifiedName = target.getQualifiedName();
                return qualifiedName != null && !PyTypingTypeProvider.OPAQUE_NAMES.contains(qualifiedName);
            }
            else {
                return false;
            }
        })) {
            PsiElement element = result.getElement();
            if (element != null) {
                declaration = element;
                break;
            }
        }
        if (declaration == null) {
            return Collections.emptyList();
        }

        if (declaration instanceof PyClass pyClass) {
            return classTypeParameters(pyClass, context);
        }
        else if (declaration instanceof PyTypeAliasStatement typeAliasStatement) {
            PyTypeParameterList typeParameterList = typeAliasStatement.getTypeParameterList();
            if (typeParameterList == null) {
                return Collections.emptyList();
            }
            List<PyTypeParameterType> result = new ArrayList<>();
            for (PyTypeParameter it : typeParameterList.getTypeParameters()) {
                PyTypeParameterType type = PyTypingTypeProvider.getTypeParameterTypeFromTypeParameter(it, context);
                if (type != null) {
                    result.add(type);
                }
            }
            return result;
        }
        else if (declaration instanceof PyTargetExpression targetExpression) {
            return aliasTypeParameters(subscription, targetExpression, context);
        }
        else {
            return Collections.emptyList();
        }
    }

    /**
     * Returns the user-visible representations of the type parameters accepted by the operand of {@code subscription}, formatted
     * as `name[: bound][ = default]`. For instance `["_YieldT_co", "_SendT_contra", "_ReturnT_co"]` for `Generator`, or
     * `["T: int = int", "*Ts", "**P"]` when bounds, defaults (PEP 696) and variadics are involved.
     */
    public static List<String> getTypeParameterRepresentations(PySubscriptionExpression subscription, TypeEvalContext context) {
        List<String> result = new ArrayList<>();
        for (PyTypeParameterType it : getTypeParameters(subscription, context)) {
            result.add(representation(it, context));
        }
        return result;
    }

    private static String representation(PyTypeParameterType typeParameter, TypeEvalContext context) {
        // The name already carries the leading '*'/'**' for TypeVarTuple/ParamSpec; only plain TypeVars can have a bound.
        StringBuilder result = new StringBuilder(typeParameter.getName());
        // An absent bound/default is modeled as `null` or as the `Unknown`/`Any` sentinel depending on the
        // `python.type.any` registry, so skip both.
        PyType bound = typeParameter instanceof PyTypeVarType typeVarType ? typeVarType.getBound() : null;
        if (bound != null && !(bound instanceof PyAnyType)) {
            result.append(": ").append(PythonDocumentationProvider.getTypeName(bound, context));
        }
        Ref<? extends PyType> defaultTypeRef = typeParameter.getDefaultType();
        PyType defaultType = defaultTypeRef != null ? defaultTypeRef.get() : null;
        if (typeParameter instanceof PyParamSpecType && defaultTypeRef != null && isUnknown(defaultType)) {
            result.append(" = ...");
        }
        else if (defaultType != null && !(defaultType instanceof PyAnyType)) {
            result.append(" = ").append(PythonDocumentationProvider.getTypeName(defaultType, context));
        }
        return result.toString();
    }

    private static List<PyTypeParameterType> classTypeParameters(PyClass cls, TypeEvalContext context) {
        PyClassType genericDefinitionType = PyTypeChecker.findGenericDefinitionType(cls, context);
        if (genericDefinitionType == null) {
            return Collections.emptyList();
        }
        List<PyTypeParameterType> result = new ArrayList<>();
        for (PyType it : genericDefinitionType.getTypeArguments()) {
            if (it instanceof PyTypeParameterType typeParameterType) {
                result.add(typeParameterType);
            }
        }
        return result;
    }

    private static List<PyTypeParameterType> aliasTypeParameters(PySubscriptionExpression subscription,
                                                                 PyTargetExpression declaration,
                                                                 TypeEvalContext context) {
        String qualifiedName = declaration.getQualifiedName();
        String builtinName = qualifiedName != null ? PyTypingTypeProvider.BUILTIN_COLLECTION_CLASSES.get(qualifiedName) : null;
        if (builtinName != null) {
            PyClass cls = PyBuiltinCache.getInstance(subscription).getClass(builtinName);
            if (cls != null) {
                return classTypeParameters(cls, context);
            }
        }

        PyExpression assignedValue = PyTypingAliasStubType.getAssignedValueStubLike(declaration);
        if (assignedValue == null) {
            return Collections.emptyList();
        }
        Ref<PyType> assignedValueTypeRef = PyTypingTypeProvider.getType(assignedValue, context);
        @Nullable PyType assignedValueType = assignedValueTypeRef != null ? assignedValueTypeRef.get() : null;
        if (assignedValueType == null) {
            return Collections.emptyList();
        }
        // A bare class reference on the RHS (`Alias = GenericClass`) is followed through in `multiFollowAssignmentsChain`,
        // so `declaration` here is always a genuine alias expression rather than a class reference: collect its generics.
        return new ArrayList<>(new LinkedHashSet<>(PyTypeChecker.collectGenerics(assignedValueType, context).getAllTypeParameters()));
    }
}
