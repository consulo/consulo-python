// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.Collection;

/**
 * @author Dennis.Ushakov
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyClassMembersProvider {
    ExtensionPointName<PyClassMembersProvider> EP_NAME = ExtensionPointName.create(PyClassMembersProvider.class);

    Collection<PyCustomMember> getMembers(PyClassType clazz, PsiElement location, TypeEvalContext context);

    /**
     * Provides member with specified name for specified class type.
     *
     * @param type           member owner
     * @param name           member name
     * @param location       psi element to be used as psi context
     * @param resolveContext context to be used in resolve
     * @return provided member
     */
    @Nullable
    PsiElement resolveMember(PyClassType type, String name, @Nullable PsiElement location, PyResolveContext resolveContext);
}
