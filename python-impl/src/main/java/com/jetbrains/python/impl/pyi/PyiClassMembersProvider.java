// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.pyi;

import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.PyClassMembersProviderBase;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyOverridingAncestorsClassMembersProvider;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@ExtensionImpl
public final class PyiClassMembersProvider extends PyClassMembersProviderBase implements PyOverridingAncestorsClassMembersProvider {
    @Override
    public Collection<PyCustomMember> getMembers(PyClassType classType,
                                                          PsiElement location,
                                                          TypeEvalContext context) {
        PyClass cls = classType.getPyClass();
        PsiElement pythonStub = PyiUtil.getPythonStub(cls);
        if (pythonStub instanceof PyClass) {
            return getClassMembers((PyClass)pythonStub, classType.isDefinition());
        }
        return Collections.emptyList();
    }

    @Override
    public @Nullable PsiElement resolveMember(PyClassType type,
                                              String name,
                                              @Nullable PsiElement location,
                                              PyResolveContext resolveContext) {
        PyClass cls = type.getPyClass();
        PsiElement pythonStub = PyiUtil.getPythonStub(cls);
        if (pythonStub instanceof PyClass) {
            return findClassMember((PyClass)pythonStub, name, type.isDefinition());
        }
        return null;
    }

    public static @Nullable PsiElement findClassMember(PyClass cls, String name, boolean isDefinition) {
        PyFunction function = cls.findMethodByName(name, false, null);
        if (function != null) {
            PyUtil.MethodFlags methodFlags = PyUtil.MethodFlags.of(function);
            boolean instanceMethod = methodFlags == null || methodFlags.isInstanceMethod();
            if (isDefinition ^ instanceMethod) {
                return function;
            }
        }
        if (!isDefinition) {
            PyTargetExpression instanceAttribute = cls.findInstanceAttribute(name, false);
            if (instanceAttribute != null) {
                return instanceAttribute;
            }
        }
        PyTargetExpression classAttribute = cls.findClassAttribute(name, false, null);
        if (classAttribute != null) {
            return classAttribute;
        }
        return null;
    }

    public static Collection<PyCustomMember> getClassMembers(PyClass cls, boolean isDefinition) {
        List<PyCustomMember> result = new ArrayList<>();
        for (PyFunction function : cls.getMethods()) {
            String name = function.getName();
            PyUtil.MethodFlags methodFlags = PyUtil.MethodFlags.of(function);
            boolean instanceMethod = methodFlags == null || methodFlags.isInstanceMethod();
            if (name != null && (isDefinition ^ instanceMethod)) {
                result.add(new PyCustomMember(name, function));
            }
        }
        if (!isDefinition) {
            for (PyTargetExpression attribute : cls.getInstanceAttributes()) {
                String name = attribute.getName();
                if (name != null) {
                    result.add(new PyCustomMember(name, attribute));
                }
            }
        }
        for (PyTargetExpression attribute : cls.getClassAttributes()) {
            String name = attribute.getName();
            if (name != null) {
                result.add(new PyCustomMember(name, attribute));
            }
        }
        return result;
    }
}
