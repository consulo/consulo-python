// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyCallableArgument;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeMember;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class PyTypingNewType implements PyClassType {
    private final PyClassType classType;
    private final String name;
    private final PyTargetExpression declarationElement;

    public PyTypingNewType(PyClassType classType, String name, PyTargetExpression declarationElement) {
        this.classType = classType;
        this.name = name;
        this.declarationElement = declarationElement;
    }

    public PyClassType getClassType() {
        return classType;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public PyTargetExpression getDeclarationElement() {
        return declarationElement;
    }

    @Override
    public PyType getCallType(TypeEvalContext context, PyElement callSite) {
        return new PyTypingNewType(classType.toInstance(), name, declarationElement);
    }

    @Override
    public PyTypingNewType toClass() {
        return isDefinition() ? this : new PyTypingNewType(classType.toClass(), name, declarationElement);
    }

    @Override
    public PyTypingNewType toInstance() {
        return isDefinition() ? new PyTypingNewType(classType.toInstance(), name, declarationElement) : this;
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public boolean isCallable() {
        return classType.isCallable() || isDefinition();
    }

    @Override
    public String toString() {
        return "TypingNewType: " + name;
    }

    @Override
    public @Nullable List<PyCallableParameter> getParameters(TypeEvalContext context) {
        if (isCallable()) {
            return List.of(PyCallableParameterImpl.nonPsi(null, classType.toInstance()));
        }
        else {
            return null;
        }
    }

    @Override
    public @Nullable PyCallableParameterVariadicType getParametersType(TypeEvalContext context) {
        List<PyCallableParameter> parameters = getParameters(context);
        return parameters != null ? new PyCallableParameterListTypeImpl(parameters) : null;
    }

    @Override
    public List<@Nullable PyClassLikeType> getSuperClassTypes(TypeEvalContext context) {
        return List.of(classType);
    }

    @Override
    public @Nullable List<? extends RatedResolveResult> resolveMember(String name,
                                                                      @Nullable PyExpression location,
                                                                      AccessDirection direction,
                                                                      PyResolveContext resolveContext,
                                                                      boolean inherited) {
        if (name.equals(PyNames.CLASS_GETITEM)) {
            return new ArrayList<>();
        }
        else {
            return classType.resolveMember(name, location, direction, resolveContext, inherited);
        }
    }

    @Override
    public @Nullable List<? extends RatedResolveResult> resolveMember(String name,
                                                                      @Nullable PyExpression location,
                                                                      AccessDirection direction,
                                                                      PyResolveContext resolveContext) {
        if (name.equals(PyNames.CLASS_GETITEM)) {
            return List.of();
        }
        else {
            return classType.resolveMember(name, location, direction, resolveContext);
        }
    }

    @Override
    public List<@Nullable PyClassLikeType> getAncestorTypes(TypeEvalContext context) {
        List<@Nullable PyClassLikeType> result = new ArrayList<>();
        result.add(classType);
        result.addAll(classType.getAncestorTypes(context));
        return result;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        PyTypingNewType otherType = (PyTypingNewType)other;

        if (!classType.equals(otherType.classType)) {
            return false;
        }
        if (!name.equals(otherType.name)) {
            return false;
        }
        if (!declarationElement.equals(otherType.declarationElement)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return 31 * classType.hashCode() + name.hashCode();
    }

    @Override
    public <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyTypingNewType(this);
        }
        return visitor.visitPyClassType(this);
    }

    // Members delegated to classType (Kotlin "by classType")

    @Override
    public PyClass getPyClass() {
        return classType.getPyClass();
    }

    @Override
    public List<PyType> getTypeArguments() {
        return classType.getTypeArguments();
    }

    @Override
    public boolean isParameterized() {
        return classType.isParameterized();
    }

    @Override
    public @Nullable PyType getIteratedItemType() {
        return classType.getIteratedItemType();
    }

    @Override
    public @Nullable String getClassQName() {
        return classType.getClassQName();
    }

    @Override
    public void visitMembers(Predicate<? super PsiElement> processor, boolean inherited, TypeEvalContext context) {
        classType.visitMembers(processor, inherited, context);
    }

    @Override
    public Set<String> getMemberNames(boolean inherited, TypeEvalContext context) {
        return classType.getMemberNames(inherited, context);
    }

    @Override
    public boolean isValid() {
        return classType.isValid();
    }

    @Override
    public boolean isAttributeWritable(String name, TypeEvalContext context) {
        return classType.isAttributeWritable(name, context);
    }

    @Override
    public @Nullable PyClassLikeType getMetaClassType(TypeEvalContext context, boolean inherited) {
        return classType.getMetaClassType(context, inherited);
    }

    @Override
    public @Nullable PyType getCallType(TypeEvalContext context, @Nullable PyElement callSite, List<PyCallableArgument> arguments) {
        return classType.getCallType(context, callSite, arguments);
    }

    @Override
    public @Nullable List<PyTypeParameterType> getTypeParameters(TypeEvalContext context) {
        return classType.getTypeParameters(context);
    }

    @Override
    public @Nullable PyType getReturnType(TypeEvalContext context) {
        return classType.getReturnType(context);
    }

    @Override
    public @Nullable List<PyCallableParameter> getUnpackedParameters(TypeEvalContext context) {
        return classType.getUnpackedParameters(context);
    }

    @Override
    public @Nullable PyCallable getCallable() {
        return classType.getCallable();
    }

    @Override
    public PyCallableType dropSelf(TypeEvalContext context) {
        return classType.dropSelf(context);
    }

    @Override
    public PyFunction.@Nullable Modifier getModifier() {
        return classType.getModifier();
    }

    @Override
    public boolean isDefinition() {
        return classType.isDefinition();
    }

    @Override
    public List<PyTypeMember> getAllMembers(PyResolveContext resolveContext) {
        return classType.getAllMembers(resolveContext);
    }

    @Override
    public List<PyTypeMember> findMember(String name, PyResolveContext resolveContext) {
        return classType.findMember(name, resolveContext);
    }

    @Override
    public Object[] getCompletionVariants(@Nullable String completionPrefix, PsiElement location, ProcessingContext context) {
        return classType.getCompletionVariants(completionPrefix, location, context);
    }

    @Override
    public void assertValid(@Nullable String message) {
        classType.assertValid(message);
    }

    @Override
    public <T> @Nullable T getUserData(Key<T> key) {
        return classType.getUserData(key);
    }

    @Override
    public <T> void putUserData(Key<T> key, @Nullable T value) {
        classType.putUserData(key, value);
    }
}
