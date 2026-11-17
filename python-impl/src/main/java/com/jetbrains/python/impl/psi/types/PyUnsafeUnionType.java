// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
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

/**
 * The unsafe union type is different from the regular "safe" {@code PyUnionType} in that it's considered a subtype of another type if
 * <i>any</i> of its member types is compatible with it.
 * <p>
 * In mathematical terms, {@code UnsafeUnion[T1, ..., Tn] <: T iff ∃ Ti: Ti ∈ {T1, ..., Tn} & Ti <: T}.
 * At the same time, {@code T <: UnsafeUnion[T1, ..., Tn] iff ∃ Ti: Ti ∈ {T1, ..., Tn} & T <: Ti} (similarly to regular "safe" unions).
 * <p>
 * One can also tell that an unsafe union behaves like a regular union when it's considered a supertype of another type,
 * and as an intersection type when it's considered a subtype of another type.
 * <p>
 * In various discussion this type was also named {@code AnyOf}. Indeed, it behaves like a local {@code Any}, i.e.
 * a local top and bottom type on a restricted set of types.
 * <p>
 * In practice, it means not only {@code int} is considered compatible with {@code int |str} (expected for unions),
 * but also {@code int | str} is considered compatible with {@code int} making things like the following acceptable:
 *
 * <pre>
 * x: int | str
 * y: int = x  # no type checker error
 * x.upper()  # no type checker error
 * </pre>
 * <p>
 * Such an inherently unsound entity in the type system is needed to support cases where in the absence of explicit type hints,
 * we have to guess the right type to provide code completion and other code assistance, but don't want to trigger any false positives.
 * <p>
 * One useful concept following from such semantics is the "weak type" that is an unsafe union of a type with {@code Any}.
 * A weak type provides code completion and attribute name resolution just as the type it wraps, but it doesn't trigger any warnings
 * as {@code Any}.
 * <p>
 * Previously, the regular {@code PyUnionType} used to have this semantics. Now it has normal "safe" semantics, and unsafe unions are
 * mostly kept for several heuristics for untyped Python code that we don't want to break.
 * <p>
 * See the relevant discussions about unsafe unions
 * <ul>
 *  <li>https://github.com/python/typing/issues/566</li>
 *  <li>https://github.com/python/mypy/issues/1693#issuecomment-226643848</li>
 *  <li>https://mail.python.org/archives/list/typing-sig@python.org/message/TTPVTIKZ6BFVWZBUYR2FN2SPGB63Z7PH/</li>
 * </ul>
 *
 * @see PyUnionType#createWeakType
 */
public class PyUnsafeUnionType extends PyCompositeTypeBase {
    private final Set<@Nullable PyType> memberSet;
    private final String name;
    private final boolean isBuiltin;

    private PyUnsafeUnionType(Collection<@Nullable PyType> members) {
        memberSet = Collections.unmodifiableSet(new LinkedHashSet<>(members));
        name = memberSet.stream().map(it -> it != null ? it.getName() : "Any").collect(Collectors.joining(", ", "UnsafeUnion[", "]"));
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
            return visitorExt.visitPyUnsafeUnionType(this);
        }
        return visitor.visitPyType(this);
    }

    @Override
    public String toString() {
        return "PyUnsafeUnionType: " + getName();
    }

    public static @Nullable PyType unsafeUnion(@Nullable PyType... types) {
        if (!PyUnionType.isStrictSemanticsEnabled()) {
            return PyUnionType.unionOrUnknown(Arrays.asList(types));
        }
        return unsafeUnion(Arrays.asList(types));
    }

    public static @Nullable PyType unsafeUnion(Collection<@Nullable PyType> types) {
        if (!PyUnionType.isStrictSemanticsEnabled()) {
            return PyUnionType.unionOrUnknown(types);
        }
        Set<@Nullable PyType> newMembers = new LinkedHashSet<>();
        for (PyType member : types) {
            if (member instanceof PyUnsafeUnionType unsafeUnionType) {
                newMembers.addAll(unsafeUnionType.getMembers());
            }
            else {
                newMembers.add(member);
            }
        }
        return newMembers.size() > 1 ? new PyUnsafeUnionType(newMembers) : (newMembers.isEmpty() ? null : newMembers.iterator().next());
    }
}
