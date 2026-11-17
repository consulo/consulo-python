// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Represents a type of an expression.
 */
public interface PyType {
    /**
     * Context key for access to a set of names already found by variant search.
     */
    Key<Set<String>> CTX_NAMES = Key.create("Completion variants names");

    /**
     * Returns the declaration element that can be used to refer to this type inside type hints. Normally, it's a symbol
     * that can be imported to mentioned the type in type annotations and comments anywhere else.
     * <p>
     * <p>
     * Typical examples are target expressions in LHS of assignments in `TypeVar` and named tuple definitions, as well as
     * class definitions themselves for plain class and generic types.
     */
    default @Nullable PyQualifiedNameOwner getDeclarationElement() {
        return null;
    }

    /**
     * Resolves an attribute of type.
     *
     * @param name     attribute name
     * @param location the expression of type qualifierType on which the member is being resolved (optional)
     * @return null if name definitely cannot be found (e.g. in a qualified reference),
     * or an empty list if name is not found but other contexts are worth looking at,
     * or a list of elements that define the name, a la multiResolve().
     */
    @Nullable
    List<? extends RatedResolveResult> resolveMember(
        String name,
        @Nullable PyExpression location,
        AccessDirection direction,
        PyResolveContext resolveContext
    );


    default List<PyTypeMember> getAllMembers(PyResolveContext resolveContext) {
        return Collections.emptyList();
    }

    /**
     * Returns a list of members with a given name
     * There can be several members with the same name (for example, methods with @overload)
     */
    default List<PyTypeMember> findMember(String name, PyResolveContext resolveContext) {
        return Collections.emptyList();
    }

    /**
     * Proposes completion variants from type's attributes.
     *
     * @param location the reference on which the completion was invoked
     * @param context  to share state between nested invocations
     * @return completion variants good for {@link consulo.language.psi.PsiReference#getVariants} return value.
     */
    Object[] getCompletionVariants(@Nullable String completionPrefix, PsiElement location, ProcessingContext context);

    @Nullable
    String getName();

    /**
     * @return true if the type is a known built-in type.
     */
    boolean isBuiltin();

    void assertValid(@Nullable String message);

    /**
     * For nullable {@code PyType} instance use {@link PyTypeVisitor#visit}
     * to visit {@code null} values with {@link PyTypeVisitor#visitUnknownType}.
     */
    default <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        return visitor.visitPyType(this);
    }
}
