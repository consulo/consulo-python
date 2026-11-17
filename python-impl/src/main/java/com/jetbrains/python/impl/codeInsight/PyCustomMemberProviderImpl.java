// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.codeInsight.PyCustomMemberProvider;
import com.jetbrains.python.codeInsight.PyCustomMemberTypeInfo;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ServiceImpl;
import consulo.language.impl.psi.ASTWrapperPsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.Function;

@ServiceImpl
@Singleton
public final class PyCustomMemberProviderImpl extends PyCustomMemberProvider {
    @Override
    public PyTypedElement createPyCustomMemberTarget(
        PyCustomMember member,
        @Nullable PyClass clazz,
        PsiElement context,
        @Nullable PsiElement resolveTarget,
        @Nullable Function<? super PsiElement, ? extends PyType> typeCallback,
        @Nullable PyCustomMemberTypeInfo<?> customTypeInfo,
        boolean resolveToInstance
    ) {
        return new MyInstanceElement(member, clazz, context, resolveTarget, typeCallback, customTypeInfo, resolveToInstance);
    }

    @Override
    public boolean isReferenceToMe(PsiReference reference, PyCustomMember member) {
        PsiElement element = reference.resolve();
        if (!(element instanceof MyInstanceElement)) {
            return false;
        }
        return ((MyInstanceElement) element).getThis().equals(member);
    }

    private static class MyInstanceElement extends ASTWrapperPsiElement implements PyTypedElement {
        private final PyCustomMember myMember;
        private final @Nullable PyClass myClass;
        private final PsiElement myContext;
        private final @Nullable Function<? super PsiElement, ? extends PyType> myTypeCallback;
        private final @Nullable PyCustomMemberTypeInfo<?> myCustomTypeInfo;
        private final boolean myResolveToInstance;

        MyInstanceElement(
            PyCustomMember member,
            @Nullable PyClass clazz,
            PsiElement context,
            @Nullable PsiElement resolveTarget,
            @Nullable Function<? super PsiElement, ? extends PyType> typeCallback,
            @Nullable PyCustomMemberTypeInfo<?> customTypeInfo, boolean resolveToInstance
        ) {
            super(resolveTarget != null ? resolveTarget.getNode() : clazz.getNode());
            myMember = member;
            myClass = clazz;
            myContext = context;
            myTypeCallback = typeCallback;
            myCustomTypeInfo = customTypeInfo;
            myResolveToInstance = resolveToInstance;
        }

        private PyCustomMember getThis() {
            return myMember;
        }

        @Override
        public boolean isPhysical() {
            return false; // otherwise, it's impossible to create a smart pointer because restoreElement fails
        }

        @Override
        public boolean equals(@Nullable Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MyInstanceElement element = (MyInstanceElement) o;
            return Objects.equals(getThis(), element.getThis()) &&
                Objects.equals(myClass, element.myClass) &&
                Objects.equals(myContext, element.myContext) &&
                Objects.equals(getNode(), element.getNode());
        }

        @Override
        public String toString() {
            return "MyInstanceElement{" +
                "myClass=" + myClass +
                "member=" + getThis() +
                "node=" + getNode() +
                ", myContext=" + myContext +
                '}';
        }

        @Override
        public int hashCode() {
            return Objects.hash(myClass, myContext, getNode(), getThis());
        }

        @Override
        public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
            if (myTypeCallback != null) {
                return myTypeCallback.apply(myContext);
            }
            else if (myClass != null) {
                PyClassType type = PyPsiFacade.getInstance(getProject()).createClassType(myClass, !myResolveToInstance);
                if (myCustomTypeInfo != null) {
                    myCustomTypeInfo.fill(type);
                }
                return type;
            }
            return PyAnyType.getUnknown();
        }
    }
}
