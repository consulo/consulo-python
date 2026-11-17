// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyWithAncestors;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public interface PyClassLikeType extends PyCallableType, PyWithAncestors, PyInstantiableType<PyClassLikeType> {

    @Nullable
    String getClassQName();

    List<@Nullable PyClassLikeType> getSuperClassTypes(TypeEvalContext context);

    @Nullable
    List<? extends RatedResolveResult> resolveMember(
        String name,
        @Nullable PyExpression location,
        AccessDirection direction,
        PyResolveContext resolveContext,
        boolean inherited
    );

    // TODO: Pull to PyType at next iteration

    /**
     * Visits all class members. This method is better then bare class since it uses type info and supports not only classes but
     * class-like structures as well. Consider using user-friendly wrapper {@code PyTypeUtil#getMembersOfType(PyClassLikeType, Class, boolean, TypeEvalContext)}
     *
     * @param processor visitor
     * @param inherited call on parents too
     * @param context   context to be used to resolve types
     * @see com.jetbrains.python.impl.psi.types.PyTypeUtil#getMembersOfType(PyClassLikeType, Class, boolean, TypeEvalContext)
     */
    void visitMembers(Predicate<? super PsiElement> processor, boolean inherited, TypeEvalContext context);

    Set<String> getMemberNames(boolean inherited, TypeEvalContext context);

    boolean isValid();

    /**
     * @param name    name to check
     * @param context type evaluation context
     * @return true if attribute with the specified name could be created or updated.
     * Does not take `typing.Final` into account.
     * @see PyClass#getSlots(TypeEvalContext)
     */
    default boolean isAttributeWritable(String name, TypeEvalContext context) {
        return true;
    }

    @Nullable
    PyClassLikeType getMetaClassType(TypeEvalContext context, boolean inherited);

    @Override
    default <T> T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        return visitor.visitPyClassLikeType(this);
    }

    default @Nullable PyType getCallType(TypeEvalContext context, PyElement callSite) {
        return getReturnType(context);
    }

    @Override
    default @Nullable PyType getCallType(TypeEvalContext context, @Nullable PyElement callSite, List<PyCallableArgument> arguments) {
        return callSite != null ? getCallType(context, callSite) : getReturnType(context);
    }
}
