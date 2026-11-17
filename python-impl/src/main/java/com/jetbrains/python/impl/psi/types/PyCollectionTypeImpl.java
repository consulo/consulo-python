// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.language.psi.PsiElement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;
import org.jspecify.annotations.Nullable;

import java.util.List;
import com.jetbrains.python.psi.types.PyType;

/**
 * Concrete {@link PyClassType} used for parameterized class types (e.g. {@code list[int]}). It behaves exactly like
 * {@link PyClassTypeImpl} but additionally implements the deprecated {@link PyCollectionType} marker, so that
 * {@code instanceof PyCollectionType} checks in third-party plugins keep working. The actual type arguments are stored
 * in {@link PyClassTypeImpl} and exposed via {@link PyClassType#getTypeArguments()}.
 */
@SuppressWarnings("deprecation")
public class PyCollectionTypeImpl extends PyClassTypeImpl implements PyCollectionType {
    public PyCollectionTypeImpl(PyClass source, boolean isDefinition, List<? extends PyType> elementTypes) {
        super(source, isDefinition, elementTypes);
    }

    @Override
    protected PyClassTypeImpl createInstance(PyClass source,
                                                      boolean isDefinition,
                                                      List<? extends PyType> typeArguments) {
        return new PyCollectionTypeImpl(source, isDefinition, typeArguments);
    }

    public static @Nullable PyCollectionTypeImpl createTypeByQName(PsiElement anchor,
                                                                   String classQualifiedName,
                                                                   boolean isDefinition,
                                                                   List<? extends PyType> elementTypes) {
        PyClass pyClass = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(classQualifiedName, anchor);
        if (pyClass == null) {
            return null;
        }
        return new PyCollectionTypeImpl(pyClass, isDefinition, elementTypes);
    }
}
