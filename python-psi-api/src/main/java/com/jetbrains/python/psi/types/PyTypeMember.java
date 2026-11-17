// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import org.jspecify.annotations.Nullable;

/**
 * Represents a high-level member of a class
 * It can be a simple attribute, a method, a property, a dataclass attribute and so on
 * <p>
 * For a property, one member represents its getter, setter and deleter
 */
public class PyTypeMember extends RatedResolveResult {
    private final @Nullable PyType myType;
    private final boolean myIsClassVar;
    private final @Nullable PsiElement myGetter;
    private final @Nullable PsiElement mySetter;
    private final @Nullable PsiElement myDeleter;

    public PyTypeMember(
        @Nullable PsiElement mainElement,
        @Nullable PyType type,
        boolean isClassVar,
        @Nullable PsiElement getter,
        @Nullable PsiElement setter,
        @Nullable PsiElement deleter
    ) {
        super(0, mainElement);
        myType = type;
        myIsClassVar = isClassVar;
        myGetter = getter;
        mySetter = setter;
        myDeleter = deleter;
    }

    public PyTypeMember(
        @Nullable PsiElement mainElement,
        @Nullable PyType type,
        boolean isClassVar,
        @Nullable PsiElement getter,
        @Nullable PsiElement setter
    ) {
        this(mainElement, type, isClassVar, getter, setter, getSetterFromMainElement(mainElement));
    }

    public PyTypeMember(@Nullable PsiElement mainElement, @Nullable PyType type, boolean isClassVar, @Nullable PsiElement getter) {
        this(mainElement, type, isClassVar, getter, getSetterFromMainElement(mainElement));
    }

    public PyTypeMember(@Nullable PsiElement mainElement, @Nullable PyType type, boolean isClassVar) {
        this(mainElement, type, isClassVar, mainElement);
    }

    public PyTypeMember(@Nullable PsiElement mainElement, @Nullable PyType type) {
        this(mainElement, type, false);
    }

    public PyTypeMember(Property property, @Nullable PyType type) {
        this(
            property.getGetter().valueOrNull(),
            type,
            false,
            property.getGetter().valueOrNull(),
            property.getSetter().valueOrNull(),
            property.getDeleter().valueOrNull()
        );
    }

    public @Nullable PyType getType() {
        return myType;
    }

    public boolean isClassVar() {
        return myIsClassVar;
    }

    public @Nullable PsiElement getGetter() {
        return myGetter;
    }

    public @Nullable PsiElement getSetter() {
        return mySetter;
    }

    public @Nullable PsiElement getDeleter() {
        return myDeleter;
    }

    public boolean isWritable() {
        return mySetter != null;
    }

    public boolean isDeletable() {
        return myDeleter != null;
    }

    public @Nullable String getName() {
        PsiElement e = getElement();
        return e instanceof PsiNamedElement namedElement ? namedElement.getName() : null;
    }

    private static @Nullable PsiElement getSetterFromMainElement(@Nullable PsiElement element) {
        if (element instanceof PyFunction) {
            return null;
        }
        return element;
    }
}
