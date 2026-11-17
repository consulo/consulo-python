// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.dataflow.scope.impl;

import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeVariable;
import consulo.language.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;

public class ScopeVariableImpl implements ScopeVariable {
    private final String myName;
    private final Collection<PsiElement> myDeclarations;
    private final boolean isParameter;

    public ScopeVariableImpl(String name, boolean parameter, Collection<PsiElement> declarations) {
        myName = name;
        myDeclarations = declarations;
        isParameter = parameter;
    }

    public ScopeVariableImpl(String name, boolean parameter, PsiElement declaration) {
        this(name, parameter, Collections.singletonList(declaration));
    }

    @Override
    public String getName() {
        return myName;
    }

    @Override
    public Collection<PsiElement> getDeclarations() {
        return myDeclarations;
    }

    @Override
    public boolean isParameter() {
        return isParameter;
    }

    @Override
    public String toString() {
        return (isParameter() ? "par" : "var") + "(" + myName + ")[" + myDeclarations.size() + "]";
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ScopeVariableImpl that)) {
            return false;
        }

        if (isParameter != that.isParameter) {
            return false;
        }
        if (myDeclarations != null ? !myDeclarations.equals(that.myDeclarations) : that.myDeclarations != null) {
            return false;
        }
        if (myName != null ? !myName.equals(that.myName) : that.myName != null) {
            return false;
        }

        return true;
    }
}
