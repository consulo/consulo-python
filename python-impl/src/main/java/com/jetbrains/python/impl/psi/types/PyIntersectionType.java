// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyTopType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.SmartList;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PyIntersectionType extends PyCompositeTypeBase {
    private final Set<@Nullable PyType> memberSet;
    private final String name;
    private final boolean isBuiltin;

    private PyIntersectionType(Collection<@Nullable PyType> members) {
        memberSet = Collections.unmodifiableSet(new LinkedHashSet<>(members));
        name = memberSet.stream().map(it -> it != null ? it.getName() : "Any").collect(Collectors.joining(" & "));
        isBuiltin = memberSet.stream().allMatch(it -> it != null && it.isBuiltin());
    }

    @Override
    protected Set<@Nullable PyType> getMemberSet() {
        return memberSet;
    }

    @Override
    public Set<@Nullable PyType> getMembers() {
        return memberSet;
    }

    @Override
    public @Nullable List<RatedResolveResult> resolveMember(String name,
                                                            @Nullable PyExpression location,
                                                            AccessDirection direction,
                                                            PyResolveContext resolveContext) {
        SmartList<RatedResolveResult> ret = new SmartList<>();
        boolean allNulls = true;
        for (PyType member : getMembers()) {
            if (member != null) {
                List<? extends RatedResolveResult> result = member.resolveMember(name, location, direction, resolveContext);
                if (result != null) {
                    allNulls = false;
                    ret.addAll(result);
                }
            }
        }
        return allNulls ? null : ret;
    }

    @Override
    public Object[] getCompletionVariants(@Nullable String completionPrefix, PsiElement location, ProcessingContext context) {
        return getMembers().stream()
            .flatMap(it -> it != null ? Arrays.stream(it.getCompletionVariants(completionPrefix, location, context)) : java.util.stream.Stream.empty())
            .distinct()
            .toArray();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isBuiltin() {
        return isBuiltin;
    }

    @Override
    public void assertValid(@Nullable String message) {
        for (PyType member : getMembers()) {
            if (member != null) {
                member.assertValid(message);
            }
        }
    }

    @Override
    public <T> @Nullable T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyIntersectionType(this);
        }
        return visitor.visitPyType(this);
    }

    @Override
    public String toString() {
        return "PyIntersectionType: " + getName();
    }

    public static @Nullable PyType intersectionOrTop(@Nullable PyType... types) {
        return intersectionOrTop(Arrays.asList(types));
    }

    /**
     * Constructs an intersection of the given types.
     * <p>
     * An intersection of no types constrains nothing, so an empty result collapses to {@link PyTopType}.
     */
    public static @Nullable PyType intersectionOrTop(Collection<@Nullable PyType> types) {
        return intersectionOrDefault(types, PyTopType.INSTANCE);
    }

    /**
     * Constructs an intersection of the given types, but collapses an empty result to Unknown instead of {@link PyTopType}.
     * <p>
     * Use this when an empty result means "nothing was found", not "nothing constrains the type".
     */
    public static @Nullable PyType intersectionOrUnknown(Collection<@Nullable PyType> types) {
        return intersectionOrDefault(types, PyAnyType.getUnknown());
    }

    private static @Nullable PyType intersectionOrDefault(Collection<@Nullable PyType> types, @Nullable PyType defaultResult) {
        Set<@Nullable PyType> newMembers = new LinkedHashSet<>();
        for (PyType member : types) {
            if (member instanceof PyNeverType) {
                return member;
            }
            if (member instanceof PyIntersectionType intersectionType) {
                newMembers.addAll(intersectionType.getMembers());
            }
            else {
                newMembers.add(member);
            }
        }
        if (newMembers.size() > 1) {
            return new PyIntersectionType(newMembers);
        }
        else if (newMembers.isEmpty()) {
            return defaultResult;
        }
        else {
            return newMembers.iterator().next();
        }
    }
}
