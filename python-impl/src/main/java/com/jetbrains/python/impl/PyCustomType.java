// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeMember;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import consulo.language.psi.util.QualifiedName;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Custom (aka dynamic) type that delegates calls to some classes you pass to it.
 * We say that this class <strong>mimics</strong> such classes.
 * To be used for cases like "type()".
 * It optionally filters methods using {@link java.util.function.Predicate}
 *
 * @author Ilya.Kazakevich
 */
public final class PyCustomType implements PyClassLikeType {

    private final List<PyClassLikeType> myTypesToMimic;

    private final java.util.function.@Nullable Predicate<PyElement> myFilter;

    private final boolean myInstanceType;

    private final boolean myTypesToMimicAsSuperTypes;

    private final @Nullable String myQualifiedName;


    /**
     * @param filter                   filter to filter methods from classes (may be null to do no filtering)
     * @param instanceType             if true, then this class implements instance (it reports it is not definition and returns "this
     *                                 for {@link #toInstance()} call). If false, <strong>calling this type creates similar type with instance=true</strong>
     *                                 (like ctor)
     * @param typesToMimicAsSuperTypes if true, types to mimic are considered as supertypes
     * @param typesToMimic             types to "mimic": delegate calls to  (must be one at least!)
     */
    public PyCustomType(@Nullable String qualifiedName,
                        java.util.function.@Nullable Predicate<PyElement> filter,
                        boolean instanceType,
                        boolean typesToMimicAsSuperTypes,
                        PyClassLikeType... typesToMimic) {
        myQualifiedName = qualifiedName;
        myFilter = filter;
        myInstanceType = instanceType;
        myTypesToMimicAsSuperTypes = typesToMimicAsSuperTypes;
        myTypesToMimic = StreamEx
            .of(typesToMimic)
            .nonNull()
            .map(t -> instanceType ? t.toInstance() : t.toClass())
            .toImmutableList();
    }

    /**
     * @return class we mimic (if any). Check class manual for more info.
     */
    public List<PyClassLikeType> getTypesToMimic() {
        return myTypesToMimic;
    }

    @Override
    public boolean isDefinition() {
        return !myInstanceType;
    }

    @Override
    public PyClassLikeType toInstance() {
        return myInstanceType
            ? this
            : new PyCustomType(myQualifiedName, myFilter, true, myTypesToMimicAsSuperTypes, myTypesToMimic.toArray(new PyClassLikeType[0]));
    }


    @Override
    public PyClassLikeType toClass() {
        return myInstanceType
            ? new PyCustomType(myQualifiedName, myFilter, false, myTypesToMimicAsSuperTypes, myTypesToMimic.toArray(new PyClassLikeType[0]))
            : this;
    }

    @Override
    public @Nullable String getClassQName() {
        return myQualifiedName;
    }

    @Override
    public List<@Nullable PyClassLikeType> getSuperClassTypes(TypeEvalContext context) {
        return myTypesToMimicAsSuperTypes ? myTypesToMimic : Collections.emptyList();
    }

    @Override
    public List<? extends RatedResolveResult> resolveMember(String name,
                                                           @Nullable PyExpression location,
                                                           AccessDirection direction,
                                                           PyResolveContext resolveContext,
                                                           boolean inherited) {
        List<RatedResolveResult> globalResult = new ArrayList<>();

        // Delegate calls to classes, we mimic but filter if filter is set.
        for (PyClassLikeType typeToMimic : myTypesToMimic) {
            List<? extends RatedResolveResult> results = typeToMimic.resolveMember(name, location, direction, resolveContext, inherited);

            if (results != null) {
                globalResult.addAll(Collections2.filter(results, new ResolveFilter()));
            }
        }
        return globalResult;
    }

    @Override
    public boolean isValid() {
        return ContainerUtil.and(myTypesToMimic, PyClassLikeType::isValid);
    }

    @Override
    public @Nullable PyClassLikeType getMetaClassType(TypeEvalContext context, boolean inherited) {
        if (inherited) {
            PyClassLikeType typeToMimic = ContainerUtil.getFirstItem(myTypesToMimic);
            if (typeToMimic != null) {
                return typeToMimic.getMetaClassType(context, inherited);
            }
        }
        return null;
    }

    @Override
    public boolean isCallable() {
        return !myInstanceType ||
            PyTypingTypeProvider.CALLABLE.equals(myQualifiedName) ||
            ContainerUtil.or(myTypesToMimic, PyCallableType::isCallable);
    }

    @Override
    public @Nullable PyType getReturnType(TypeEvalContext context) {
        return myInstanceType ? PyAnyType.getUnknown() : toInstance();
    }

    @Override
    public @Nullable PyType getCallType(TypeEvalContext context, PyElement callSite) {
        return getReturnType(context);
    }

    @Override
    public List<? extends RatedResolveResult> resolveMember(String name,
                                                           @Nullable PyExpression location,
                                                           AccessDirection direction,
                                                           PyResolveContext resolveContext) {
        return resolveMember(name, location, direction, resolveContext, true);
    }

    @Override
    public List<PyClassLikeType> getAncestorTypes(TypeEvalContext context) {
        if (!myTypesToMimicAsSuperTypes) {
            return Collections.emptyList();
        }

        Set<PyClassLikeType> result = new LinkedHashSet<>();

        for (PyClassLikeType type : myTypesToMimic) {
            result.add(type);
            result.addAll(type.getAncestorTypes(context));
        }

        return new ArrayList<>(result);
    }

    @Override
    public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
        List<Object> lookupElements = new ArrayList<>();

        for (PyClassLikeType parentType : myTypesToMimic) {
            lookupElements.addAll(Collections2.filter(Arrays.asList(parentType.getCompletionVariants(completionPrefix, location, context)),
                new CompletionFilter()));
        }
        return ArrayUtil.toObjectArray(lookupElements);
    }


    @Override
    public @Nullable String getName() {
        if (myQualifiedName != null) {
            return QualifiedName.fromDottedString(myQualifiedName).getLastComponent();
        }

        List<String> classNames = new ArrayList<>(myTypesToMimic.size());
        for (PyClassLikeType type : myTypesToMimic) {
            String name = type.getName();
            if (name == null && type instanceof PyClassType) {
                name = ((PyClassType)type).getPyClass().getName();
            }
            if (name != null) {
                classNames.add(name);
            }
        }


        return PyLocalize.customTypeMimicName(StringUtil.join(classNames, ",")).get();
    }

    @Override
    public boolean isBuiltin() {
        return false;
    }

    @Override
    public void assertValid(String message) {
        for (PyClassLikeType type : myTypesToMimic) {
            type.assertValid(message);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PyCustomType type)) {
            return false;
        }
        return myInstanceType == type.myInstanceType &&
            myTypesToMimicAsSuperTypes == type.myTypesToMimicAsSuperTypes &&
            Objects.equals(myTypesToMimic, type.myTypesToMimic) &&
            Objects.equals(myQualifiedName, type.myQualifiedName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myInstanceType, myTypesToMimicAsSuperTypes, myQualifiedName, myTypesToMimic);
    }


    /**
     * Predicate that filters resolve candidates using {@link #myFilter}
     */
    private class ResolveFilter implements Predicate<RatedResolveResult> {
        @Override
        public boolean apply(@Nullable RatedResolveResult input) {
            if (input == null) {
                return false;
            }
            if (myFilter == null) {
                return true; // No need to check
            }
            PyElement pyElement = PyUtil.as(input.getElement(), PyElement.class);
            if (pyElement == null) {
                return false;
            }
            return myFilter.test(pyElement);
        }
    }

    @Override
    public void visitMembers(java.util.function.Predicate<? super PsiElement> processor, boolean inherited, TypeEvalContext context) {
        for (PyClassLikeType type : myTypesToMimic) {
            // Only visit methods that are allowed by filter (if any)
            type.visitMembers(t -> {
                if (!(t instanceof PyElement)) {
                    return true;
                }
                if (myFilter == null || myFilter.test((PyElement)t)) {
                    return processor.test(t);
                }
                return true;
            }, inherited, context);
        }
    }

    @Override
    public Set<String> getMemberNames(boolean inherited, TypeEvalContext context) {
        Set<String> result = new LinkedHashSet<>();

        for (PyClassLikeType type : myTypesToMimic) {
            result.addAll(type.getMemberNames(inherited, context));
        }

        return result;
    }

    @Override
    public List<PyTypeMember> getAllMembers(PyResolveContext resolveContext) {
        List<PyTypeMember> result = new ArrayList<>();
        for (PyClassLikeType type : myTypesToMimic) {
            result.addAll(type.getAllMembers(resolveContext));
        }
        return result;
    }

    @Override
    public List<PyTypeMember> findMember(String name, PyResolveContext resolveContext) {
        for (PyClassLikeType type : myTypesToMimic) {
            var members = type.findMember(name, resolveContext);
            if (!members.isEmpty()) {
                return members;
            }
        }
        return List.of();
    }

    /**
     * Predicate that filters completion using {@link #myFilter}
     */
    private class CompletionFilter implements Predicate<Object> {
        @Override
        public boolean apply(@Nullable Object input) {
            if (input == null) {
                return false;
            }
            if (myFilter == null) {
                return true; // No need to check
            }
            if (!(input instanceof LookupElement)) {
                return true; // Do not know how to check
            }
            PyElement pyElement = PyUtil.as(((LookupElement)input).getPsiElement(), PyElement.class);
            if (pyElement == null) {
                return false;
            }
            return myFilter.test(pyElement);
        }
    }

    @Override
    public String toString() {
        return "PyCustomType: " + getName();
    }
}
