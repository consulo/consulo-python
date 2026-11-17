// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import com.jetbrains.python.debugger.PySignature;

import consulo.annotation.component.ExtensionImpl;
import consulo.util.lang.ref.Ref;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.impl.psi.types.PyDynamicallyEvaluatedType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyLegacyDocstringTypeParser;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.TypeEvalContext;

@ExtensionImpl(id = "pyCallSignatureTypeProvider", order = "after pyMockerFixtureTypeProvider")
public final class PyCallSignatureTypeProvider extends PyTypeProviderBase {
    @Override
    public Ref<PyType> getParameterType(PyNamedParameter param,
                                        PyFunction func,
                                        TypeEvalContext context) {
        String name = param.getName();
        if (name != null) {
            String typeName = PySignatureCacheManager.getInstance(param.getProject()).findParameterType(func, name);
            if (typeName != null) {
                PyType type = PyLegacyDocstringTypeParser.getTypeByName(param, typeName, context);
                if (type != null) {
                    return Ref.create(PyDynamicallyEvaluatedType.create(type));
                }
            }
        }
        return null;
    }

    @Override
    public Ref<PyType> getReturnType(PyCallable callable, TypeEvalContext context) {
        if (callable instanceof PyFunction function) {
            PySignature signature = PySignatureCacheManager.getInstance(function.getProject()).findSignature(function);
            if (signature != null && signature.getReturnType() != null) {
                String typeName = signature.getReturnType().getTypeQualifiedName();
                if (typeName != null) {
                    PyType type = PyLegacyDocstringTypeParser.getTypeByName(function, typeName, context);
                    if (type != null) {
                        return Ref.create(PyDynamicallyEvaluatedType.create(type));
                    }
                }
            }
        }
        return null;
    }
}
