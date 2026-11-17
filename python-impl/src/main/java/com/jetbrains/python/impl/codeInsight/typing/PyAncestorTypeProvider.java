// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typing;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.List;

@ExtensionImpl(id = "pyAncestorTypeProvider")
public class PyAncestorTypeProvider extends PyTypeProviderBase {

    @Override
    public @Nullable Ref<PyType> getParameterType(PyNamedParameter param, PyFunction func, TypeEvalContext context) {
        PyFunctionType superFunctionType = getOverriddenFunctionType(func, context);

        if (superFunctionType != null) {
            PyFunctionType superFunctionTypeRemovedSelf = superFunctionType.dropSelf(context);
            List<PyCallableParameter> parameters = superFunctionTypeRemovedSelf.getParameters(context);
            if (parameters != null) {
                for (PyCallableParameter it : parameters) {
                    String parameterName = it.getName();
                    if (parameterName != null && parameterName.equals(param.getName())) {
                        PyType type = it.getType(context);
                        if (type != null) {
                            return Ref.create(type);
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public @Nullable Ref<PyType> getReturnType(PyCallable callable, TypeEvalContext context) {
        if (callable instanceof PyFunction function) {
            Ref<PyType> typeFromSupertype = getReturnTypeFromSupertype(function, context);
            if (typeFromSupertype != null) {
                return typeFromSupertype;
            }
        }
        return null;
    }

    private static @Nullable PyFunctionType getOverriddenFunctionType(PyFunction function, TypeEvalContext context) {
        PyFunction overriddenFunction = getOverriddenFunction(function, context);
        if (overriddenFunction != null) {
            PyType type = context.getType(overriddenFunction);
            if (type instanceof PyFunctionType functionType) {
                return functionType;
            }
        }
        return null;
    }

    private static @Nullable PyFunction getOverriddenFunction(PyFunction function, TypeEvalContext context) {
        PsiElement superMethod = PySuperMethodsSearch.search(function, context).findFirst();
        if (!(superMethod instanceof PyFunction firstSuperMethod)) {
            return null;
        }

        PyDecoratorList decoratorList = firstSuperMethod.getDecoratorList();
        if (decoratorList != null) {
            for (PyDecorator decorator : decoratorList.getDecorators()) {
                String name = decorator.getName();
                if (name != null && name.equals(PyNames.OVERLOAD)) {
                    return null;
                }
            }
        }

        PyClass superClass = firstSuperMethod.getContainingClass();
        return superClass != null && !PyNames.OBJECT.equals(superClass.getName())
            ? firstSuperMethod
            : null;
    }

    /**
     * Get function return type from supertype.
     * <p>
     * The only source of type information in current implementation is annotation. This is to avoid false positives,
     * that may arise from non direct type estimations (not from annotation, nor form type comments).
     * <p>
     * TODO: switch to return type direct usage when type information source will be available.
     */
    private static @Nullable Ref<PyType> getReturnTypeFromSupertype(PyFunction function, TypeEvalContext context) {
        PyFunction overriddenFunction = getOverriddenFunction(function, context);

        if (overriddenFunction != null) {
            PyExpression superFunctionAnnotation = PyTypingTypeProvider.getReturnTypeAnnotation(overriddenFunction, context);
            if (superFunctionAnnotation != null) {
                Ref<PyType> typeRef = PyTypingTypeProvider.getType(superFunctionAnnotation, context);
                if (typeRef != null && function.isAsync() == overriddenFunction.isAsync()) {
                    return Ref.create(PyTypingTypeProvider.toAsyncIfNeeded(function, typeRef.get()));
                }
            }
        }
        return null;
    }
}
