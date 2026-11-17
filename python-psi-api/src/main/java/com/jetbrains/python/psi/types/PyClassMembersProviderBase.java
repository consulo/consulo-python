// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class PyClassMembersProviderBase implements PyClassMembersProvider {

    /**
     * Get members for this class type only: no need to read its ancestors since it is duty of this method client
     */
    @Override
    public Collection<PyCustomMember> getMembers(PyClassType clazz, PsiElement location, TypeEvalContext context) {
        return Collections.emptyList();
    }

    @Override
    public @Nullable PsiElement resolveMember(PyClassType type,
                                              String name,
                                              @Nullable PsiElement location,
                                              PyResolveContext resolveContext) {
        Collection<PyCustomMember> members = getMembers(type, location, resolveContext.getTypeEvalContext());
        return resolveMemberByName(members, name, type.getPyClass(), resolveContext);
    }

    /**
     * Helper to find member with specified name in collection.
     *
     * @param members collection of members
     * @param name    member name to look for
     * @param context psi element to be used as psi context
     * @return found member or null
     */
    public static @Nullable PsiElement resolveMemberByName(Collection<? extends PyCustomMember> members,
                                                           String name,
                                                           PsiElement context,
                                                           PyResolveContext resolveContext) {
        for (PyCustomMember member : members) {
            if (member.getName().equals(name)) {
                return member.resolve(context, resolveContext);
            }
        }
        return null;
    }
}
