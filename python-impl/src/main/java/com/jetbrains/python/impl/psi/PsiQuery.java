// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi;

import com.google.common.collect.FluentIterable;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.function.Predicates;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * JQuery-like tool that makes PSI navigation easier.
 * You just "drive" your query filtering results, and no need to check result for null.
 * There are 3 axes: ancestors, descendants and siblings. Each axis has N elements which you can filter or map.
 *
 * @param <T> type of current elements
 * @author Ilya.Kazakevich
 */
public final class PsiQuery<T extends PsiElement> {

    private final List<T> myElements;


    public PsiQuery(T elementToStart) {
        this(Collections.singletonList(elementToStart));
    }


    @SuppressWarnings("MethodCanBeVariableArityMethod") // To prevent type safety
    public PsiQuery(T[] elementsToStart) {
        this(Arrays.asList(elementsToStart));
    }

    public PsiQuery(List<? extends T> elementsToStart) {
        myElements = Collections.unmodifiableList(elementsToStart);
    }

    /**
     * Adds elements to query
     */
    public PsiQuery<T> addElements(PsiQuery<? extends T> newElements) {
        List<T> newList = new ArrayList<>(myElements);
        newList.addAll(newElements.myElements);
        return new PsiQuery<>(newList);
    }


    public static <T extends PsiElement> PsiQuery<T> create(T elementToStart) {
        return new PsiQuery<>(elementToStart);
    }

    public static <T extends PsiElement> PsiQuery<T> createFromQueries(List<? extends PsiQuery<? extends T>> queriesWithElements) {
        Set<T> result = new LinkedHashSet<>();
        queriesWithElements.forEach(o -> result.addAll(o.getElements()));
        return new PsiQuery<>(new ArrayList<>(result));
    }

    public <R extends T> PsiQuery<R> filter(PsiFilter<R> filter) {
        return new PsiQuery<>(filter.filter(myElements));
    }

    public <R extends T> PsiQuery<R> filter(Class<R> filterToType) {
        return filter(new PsiFilter<>(filterToType));
    }

    public PsiQuery<T> filter(Predicate<? super T> filter) {
        return new PsiQuery<>(asStream().filter(filter).collect(Collectors.toList()));
    }

    public <R extends PsiElement> PsiQuery<R> map(Function<? super T, ? extends R> map) {
        return new PsiQuery<>(asStream().map(map).collect(Collectors.toList()));
    }

    public <R extends PsiElement, F_R extends T> PsiQuery<R> map(PsiFilter<F_R> preFilter,
                                                                 Function<? super F_R, ? extends R> map) {
        return filter(preFilter).map(map);
    }

    public <R extends PsiElement> PsiQuery<R> descendants(Class<R> type) {
        return descendants(new PsiFilter<>(type));
    }

    public <R extends PsiElement> PsiQuery<R> ancestors(Class<R> type) {
        return ancestors(new PsiFilter<>(type));
    }

    public <R extends PsiElement> PsiQuery<R> siblings(Class<R> type) {
        return siblings(new PsiFilter<>(type));
    }

    public <R extends PsiElement> PsiQuery<R> descendants(PsiFilter<R> filter) {
        return getQueryWithProducer(o -> PsiTreeUtil.<R>findChildrenOfType(o, filter.myClass), filter);
    }

    public <R extends PsiElement> PsiQuery<R> ancestors(PsiFilter<R> filter) {
        return getQueryWithProducer(o -> PsiElementExtKt.<R>getAncestors(o, o.getContainingFile(), filter.myClass), filter);
    }

    public <R extends PsiElement> PsiQuery<R> siblings(PsiFilter<R> filter) {
        return getQueryWithProducer(o -> new ArrayList<R>(PsiTreeUtil.getChildrenOfTypeAsList(o.getParent(), filter.myClass)), filter);
    }

    private <R extends PsiElement> PsiQuery<R> getQueryWithProducer(Function<? super T, ? extends Collection<R>> elementsProducer,
                                                                    PsiFilter<R> filter) {
        Set<R> result = new LinkedHashSet<>(); // Set to get rid of duplicates but preserve order
        asStream()
            .map(o -> ContainerUtil.filter(elementsProducer.apply(o), o2 -> !o2.equals(o)))
            .forEach(result::addAll);
        return new PsiQuery<>(new ArrayList<>(filter.filter(result)));
    }


    public Stream<T> asStream() {
        return myElements.stream();
    }

    public List<T> getElements() {
        return asStream().collect(Collectors.toList());
    }

    public @Nullable T getFirstElement() {
        List<T> elements = getElements();
        return (elements.isEmpty() ? null : elements.get(0));
    }

    public @Nullable T getLastElement() {
        List<T> elements = getElements();
        return (elements.isEmpty() ? null : elements.get(elements.size() - 1));
    }

    public boolean exists() {
        return !getElements().isEmpty();
    }

    public static class PsiFilter<T extends PsiElement> {
        public static final PsiFilter<PsiElement> ANY = new PsiFilter<>(PsiElement.class);
        private final Class<? extends T> myClass;
        private final Predicate<? super T> myPredicate;

        /**
         * Include current element in result set, or not (false by default)
         */

        public PsiFilter(Class<? extends T> aClass, Predicate<? super T> predicate) {
            myClass = aClass;
            myPredicate = predicate;
        }

        public PsiFilter(Class<? extends T> aClass) {
            this(aClass, Predicates.alwaysTrue());
        }

        List<T> filter(Collection<?> list) {
            // IDEA-165420
            //noinspection Guava
            return FluentIterable.from(list).filter(myClass).toList().stream().filter(myPredicate).collect(Collectors.toList());
        }
    }

    /**
     * Filter by name for {@link PsiNamedElement}
     */
    public static class PsiNameFilter<T extends PsiNamedElement> extends PsiFilter<T> {

        public static PsiNameFilter<PsiNamedElement> create(String name) {
            return new PsiNameFilter<>(PsiNamedElement.class, name);
        }

        public PsiNameFilter(Class<? extends T> type, String name) {
            super(type, o -> name.equals(o.getName()));
        }
    }
}
