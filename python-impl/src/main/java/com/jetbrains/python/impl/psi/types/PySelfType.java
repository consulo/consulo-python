// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.util.dataholder.Key;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import java.util.function.Predicate;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeMember;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import com.jetbrains.python.psi.types.TypeEvalContext;

public final class PySelfType implements PyTypeParameterType, PyClassType {
    private final PyClassType myScopeClassType;

    public PySelfType(PyClassType scopeClassType) {
        myScopeClassType = scopeClassType;
    }

    @Override
    public @Nullable List<? extends RatedResolveResult> resolveMember(String name,
                                                                      @Nullable PyExpression location,
                                                                      AccessDirection direction,
                                                                      PyResolveContext resolveContext) {
        return myScopeClassType.resolveMember(name, location, direction, resolveContext);
    }

    @Override
    public Object[] getCompletionVariants(String completionPrefix,
                                          PsiElement location,
                                          ProcessingContext context) {
        return myScopeClassType.getCompletionVariants(completionPrefix, location, context);
    }

    @Override
    public String getName() {
        return "Self";
    }

    @Override
    public PyQualifiedNameOwner getScopeOwner() {
        return myScopeClassType.getPyClass();
    }

    public PyClassType getScopeClassType() {
        return myScopeClassType;
    }

    /**
     * @return true if type[Self], false otherwise
     */
    @Override
    public boolean isDefinition() {
        return myScopeClassType.isDefinition();
    }

    @Override
    public PySelfType toInstance() {
        return new PySelfType(myScopeClassType.toInstance());
    }

    @Override
    public PySelfType toClass() {
        return new PySelfType(myScopeClassType.toClass());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PySelfType type = (PySelfType)o;

        if (!Objects.equals(myScopeClassType, type.myScopeClassType)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return myScopeClassType.hashCode();
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public void assertValid(String message) {

    }

    @Override
    public @Nullable String getClassQName() {
        return myScopeClassType.getClassQName();
    }

    @Override
    public List<PyClassLikeType> getSuperClassTypes(TypeEvalContext context) {
        return myScopeClassType.getSuperClassTypes(context);
    }

    @Override
    public @Nullable List<? extends RatedResolveResult> resolveMember(String name,
                                                                      @Nullable PyExpression location,
                                                                      AccessDirection direction,
                                                                      PyResolveContext resolveContext,
                                                                      boolean inherited) {
        return myScopeClassType.resolveMember(name, location, direction, resolveContext, inherited);
    }

    @Override
    public void visitMembers(Predicate<? super PsiElement> processor, boolean inherited, TypeEvalContext context) {
        myScopeClassType.visitMembers(processor, inherited, context);
    }

    @Override
    public Set<String> getMemberNames(boolean inherited, TypeEvalContext context) {
        return myScopeClassType.getMemberNames(inherited, context);
    }

    @Override
    public List<PyTypeMember> getAllMembers(PyResolveContext resolveContext) {
        return myScopeClassType.getAllMembers(resolveContext);
    }

    @Override
    public List<PyTypeMember> findMember(String name, PyResolveContext resolveContext) {
        return myScopeClassType.findMember(name, resolveContext);
    }

    @Override
    public boolean isValid() {
        return false;
    }

    @Override
    public @Nullable PyClassLikeType getMetaClassType(TypeEvalContext context, boolean inherited) {
        return myScopeClassType.getMetaClassType(context, inherited);
    }

    @Override
    public boolean isCallable() {
        return myScopeClassType.isCallable();
    }

    @Override
    public @Nullable PyType getReturnType(TypeEvalContext context) {
        if (isDefinition()) {
            return toInstance();
        }
        else {
            return myScopeClassType.getReturnType(context);
        }
    }

    @Override
    public @Nullable PyType getCallType(TypeEvalContext context, PyElement callSite) {
        if (isDefinition()) {
            return toInstance();
        }
        else {
            return myScopeClassType.getCallType(context, callSite);
        }
    }

    @Override
    public @Nullable List<PyCallableParameter> getParameters(TypeEvalContext context) {
        return myScopeClassType.getParameters(context);
    }

    @Override
    public @Nullable PyCallableParameterVariadicType getParametersType(TypeEvalContext context) {
        return myScopeClassType.getParametersType(context);
    }

    @Override
    public @Nullable PyCallable getCallable() {
        return myScopeClassType.getCallable();
    }

    @Override
    public PyFunction.@Nullable Modifier getModifier() {
        return myScopeClassType.getModifier();
    }

    @Override
    public PyClass getPyClass() {
        return myScopeClassType.getPyClass();
    }

    @Override
    public List<PyType> getTypeArguments() {
        return myScopeClassType.getTypeArguments();
    }

    @Override
    public @Nullable PyType getIteratedItemType() {
        return myScopeClassType.getIteratedItemType();
    }

    @Override
    public <T> @Nullable T getUserData(Key<T> key) {
        return null;
    }

    @Override
    public <T> void putUserData(Key<T> key, @Nullable T value) {

    }

    @Override
    public boolean isAttributeWritable(String name, TypeEvalContext context) {
        return myScopeClassType.isAttributeWritable(name, context);
    }

    @Override
    public <T> T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPySelfType(this);
        }
        return visitor.visitPyType(this);
    }

    @Override
    public List<PyClassLikeType> getAncestorTypes(TypeEvalContext context) {
        return myScopeClassType.getAncestorTypes(context);
    }
}
