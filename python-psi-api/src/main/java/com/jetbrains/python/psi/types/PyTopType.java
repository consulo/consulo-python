// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * The top type of the Python type hierarchy: the supertype of every type, equivalent to `builtins.object`.
 * <p>
 * There is only ever one top type, so this is a singleton. Unlike a {@link PyClassType} wrapping `object`, it needs no
 * anchor {@link PsiElement} to be produced.
 */
public final class PyTopType implements PyType {
    public static final PyTopType INSTANCE = new PyTopType();

    private PyTopType() {
    }

    @Override
    public String getName() {
        return "object";
    }

    @Override
    public boolean isBuiltin() {
        return true;
    }

    @Override
    public void assertValid(@Nullable String message) {
    }

    @Override
    public List<? extends RatedResolveResult> resolveMember(
        String name,
        @Nullable PyExpression location,
        AccessDirection direction,
        PyResolveContext resolveContext
    ) {
        PyClassType objectType = objectType(location);
        List<? extends RatedResolveResult> result =
            objectType != null ? objectType.resolveMember(name, location, direction, resolveContext) : null;
        return result != null ? result : Collections.emptyList();
    }

    @Override
    public Object[] getCompletionVariants(@Nullable String completionPrefix, PsiElement location, ProcessingContext context) {
        PyClassType objectType = objectType(location);
        return objectType != null ? objectType.getCompletionVariants(completionPrefix, location, context) : ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public List<PyTypeMember> getAllMembers(PyResolveContext resolveContext) {
        PyClassType objectType = objectType(resolveContext.getTypeEvalContext().getOrigin());
        return objectType != null ? objectType.getAllMembers(resolveContext) : Collections.emptyList();
    }

    @Override
    public List<PyTypeMember> findMember(String name, PyResolveContext resolveContext) {
        PyClassType objectType = objectType(resolveContext.getTypeEvalContext().getOrigin());
        return objectType != null ? objectType.findMember(name, resolveContext) : Collections.emptyList();
    }

    private static @Nullable PyClassType objectType(@Nullable PsiElement anchor) {
        if (anchor == null) {
            return null;
        }
        PyPsiFacade facade = PyPsiFacade.getInstance(anchor.getProject());
        PyClass objectClass = facade.createClassByQName(PyNames.OBJECT, anchor);
        if (objectClass == null) {
            return null;
        }
        return facade.createClassType(objectClass, false);
    }

    @Override
    public <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        return visitor.visitPyTopType(this);
    }

    @Override
    public String toString() {
        return getName();
    }
}
