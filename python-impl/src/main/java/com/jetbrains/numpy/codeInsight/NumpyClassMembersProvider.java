// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.numpy.codeInsight;

import com.google.common.collect.Lists;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.PyClassMembersProviderBase;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 *
 * User : ktisha
 */
@ExtensionImpl
public final class NumpyClassMembersProvider extends PyClassMembersProviderBase {
    private static final String BUNCH = "sklearn.datasets.base.Bunch";
    public static final List<String> BUNCH_MEMBERS = Lists.newArrayList("target", "data", "filenames", "target_names", "DESCR");

    @Override
    public Collection<PyCustomMember> getMembers(PyClassType clazz, @Nullable PsiElement location, TypeEvalContext context) {
        if (location != null) {
            PyClass pyClass = clazz.getPyClass();
            if (BUNCH.equals(pyClass.getQualifiedName())) {
                List<PyCustomMember> result = new ArrayList<>();
                for (String member : BUNCH_MEMBERS) {
                    result.add(new PyCustomMember(member, NumpyDocStringTypeProvider.NDARRAY, true));
                }
                return result;
            }
        }
        return Collections.emptyList();
    }
}
