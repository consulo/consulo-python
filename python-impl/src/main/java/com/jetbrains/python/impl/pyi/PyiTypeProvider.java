// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.pyi;

import consulo.annotation.component.ExtensionImpl;
import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiElement;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.impl.psi.types.PyOverloadType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.jetbrains.python.impl.psi.PyUtil.as;

@ExtensionImpl(id = "pyiTypeProvider", order = "after pyStdlibTypeProvider")
public final class PyiTypeProvider extends PyTypeProviderBase {
    @Override
    public @Nullable Ref<PyType> getParameterType(PyNamedParameter param, PyFunction func, TypeEvalContext context) {
        String name = param.getName();
        if (name != null) {
            PsiElement pythonStub = PyiUtil.getPythonStub(func);
            if (pythonStub instanceof PyFunction functionStub) {
                PyNamedParameter paramSkeleton = functionStub.getParameterList().findParameterByName(name);
                if (paramSkeleton != null) {
                    PyType type = context.getType(paramSkeleton);
                    if (type != null) {
                        return Ref.create(type);
                    }
                }
            }
            // TODO: Allow the stub for a function to be defined as a class or a target expression alias
        }
        return null;
    }

    @Override
    public @Nullable Ref<PyType> getReturnType(PyCallable callable, TypeEvalContext context) {
        PsiElement pythonStub = PyiUtil.getPythonStub(callable);
        if (pythonStub instanceof PyCallable) {
            PyType type = context.getReturnType((PyCallable)pythonStub);
            if (type != null) {
                return Ref.create(type);
            }
        }
        return null;
    }

    @Override
    public @Nullable Ref<PyType> getReferenceType(PsiElement target, TypeEvalContext context, @Nullable PsiElement anchor) {
        if (target instanceof PyElement) {
            PsiElement pythonStub = PyiUtil.getPythonStub((PyElement)target);
            if (pythonStub instanceof PyFunction pyFunction) {
                List<PyCallableType> overloads = ContainerUtil.mapNotNull(PyiUtil.getOverloads(pyFunction, context),
                                                                          f -> as(context.getType(f), PyCallableType.class));
                if (overloads.isEmpty()) {
                    return null;
                }
                return Ref.create(new PyOverloadType(overloads, null));
            }
            if (pythonStub instanceof PyTypedElement) {
                return PyTypeUtil.notNullToRef(context.getType((PyTypedElement)pythonStub));
            }
        }
        return null;
    }

    @Override
    public @Nullable PyType getGenericType(PyClass cls, TypeEvalContext context) {
        PyClass classStub = as(PyiUtil.getPythonStub(cls), PyClass.class);
        if (classStub != null) {
            return new PyTypingTypeProvider().getGenericType(classStub, context);
        }
        return null;
    }

    @Override
    public Map<PyType, PyType> getGenericSubstitutions(PyClass cls, TypeEvalContext context) {
        PyClass classStub = as(PyiUtil.getPythonStub(cls), PyClass.class);
        if (classStub != null) {
            return new PyTypingTypeProvider().getGenericSubstitutions(classStub, context);
        }
        return Collections.emptyMap();
    }
}
