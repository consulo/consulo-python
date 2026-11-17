// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.docstrings;

import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.PyCloningTypeVisitor;
import com.jetbrains.python.impl.psi.types.PyLegacyDocstringTypeParser;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyTypeVarTypeImpl;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/**
 * @author Mikhail Golubev
 */
@ExtensionImpl(id = "pythonDocstringTypeProvider", order = "after numpyDocStringTypeProvider")
public final class PyDocStringTypeProvider extends PyTypeProviderBase {
    @Override
    public @Nullable Ref<PyType> getParameterType(PyNamedParameter param, PyFunction func, TypeEvalContext context) {
        StructuredDocString docString = func.getStructuredDocString();
        if (docString == null) {
            final PyClass pyClass = PyUtil.turnConstructorIntoClass(func);
            if (pyClass != null) {
                docString = pyClass.getStructuredDocString();
            }
        }
        if (docString != null) {
            final String typeText = docString.getParamType(param.getName());
            if (StringUtil.isNotEmpty(typeText)) {
                final Ref<PyType> typeRef = parseType(func, typeText, context);

                if (param.isPositionalContainer()) {
                    final PyType positionalContainerType = PyTypeUtil.toPositionalContainerType(param, typeRef.get());
                    return Ref.create(positionalContainerType != null ? positionalContainerType : PyAnyType.getUnknown());
                }

                if (param.isKeywordContainer()) {
                    final PyType keywordContainerType = PyTypeUtil.toKeywordContainerType(param, typeRef.get());
                    return Ref.create(keywordContainerType != null ? keywordContainerType : PyAnyType.getUnknown());
                }

                return typeRef;
            }
        }
        return null;
    }

    @Override
    public @Nullable Ref<PyType> getReturnType(PyCallable callable, TypeEvalContext context) {
        if (callable instanceof PyDocStringOwner) {
            final StructuredDocString docString = ((PyDocStringOwner) callable).getStructuredDocString();
            if (docString != null) {
                final String typeText = docString.getReturnType();
                if (StringUtil.isNotEmpty(typeText)) {
                    final Ref<PyType> typeRef = parseType(callable, typeText, context);
                    if (callable instanceof PyFunction) {
                        return Ref.create(PyTypingTypeProvider.toAsyncIfNeeded((PyFunction) callable, typeRef.get()));
                    }

                    return typeRef;
                }
            }
        }
        return null;
    }

    private Ref<PyType> parseType(PyCallable callable, String typeText, TypeEvalContext context) {
        final PyType type = PyLegacyDocstringTypeParser.getTypeByName(callable, typeText, context);
        if (type != null) {
            type.assertValid("from docstring");
        }
        if (callable instanceof PyFunction pyFunction) {
            return Ref.create(PyCloningTypeVisitor.clone(type, new PyCloningTypeVisitor(context) {
                @Override
                public PyType visitPyTypeVarType(PyTypeVarType typeVarType) {
                    if (typeVarType instanceof PyTypeVarTypeImpl impl) {
                        return impl.withScopeOwner(findScopeOwner(typeVarType, pyFunction, context));
                    }
                    return typeVarType;
                }
            }));
        }
        return Ref.create(type);
    }

    /**
     * Unify generics in the constructor according to the legacy type hints syntax.
     */
    @Override
    public @Nullable PyType getGenericType(PyClass cls, TypeEvalContext context) {
        PyFunction init = cls.findInitOrNew(true, context);
        if (init != null) {
            // TODO consulo: Ref.deref missing
            Ref<PyType> returnTypeRef = getReturnType(init, context);
            PyType returnType = returnTypeRef != null ? returnTypeRef.get() : null;
            if (returnType instanceof PyClassType pyClassType && pyClassType.isParameterized()) {
                return returnType;
            }
        }

        return null;
    }

    private @Nullable PyQualifiedNameOwner findScopeOwner(PyTypeVarType typeVar,
                                                          PyFunction function,
                                                          TypeEvalContext context) {
        PyClass pyClass = function.getContainingClass();
        if (PyUtil.isInitOrNewMethod(function)) {
            return pyClass;
        }
        else if (pyClass != null) {
            PyType classGenericType = getGenericType(pyClass, context);
            if (classGenericType != null) {
                PyTypeChecker.Generics classTypeParameters = PyTypeChecker.collectGenerics(classGenericType, context);
                Set<String> classTypeVarNames = ContainerUtil.map2Set(classTypeParameters.getTypeVars(), PyTypeVarType::getName);
                return classTypeVarNames.contains(typeVar.getName()) ? pyClass : function;
            }
        }
        return function;
    }
}
