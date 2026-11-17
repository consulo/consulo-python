// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.util.lang.Couple;
import consulo.util.lang.ref.Ref;
import consulo.util.lang.StringUtil;
import consulo.util.collection.ContainerUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameterListType;
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyPositionalVariadicType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeVarTupleType;
import com.jetbrains.python.psi.types.PyUnpackedTupleType;
import com.jetbrains.python.psi.types.PyVariadicType;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isAnyOrUnknown;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

public final class PyTypeParameterMapping {
    private final List<Couple<PyType>> myMappedTypes;

    private PyTypeParameterMapping(List<Couple<PyType>> mapping) {
        for (Couple<PyType> couple : mapping) {
            PyType expectedType = couple.getFirst();
            PyType actualType = couple.getSecond();
            if (!isAnyOrUnknown(expectedType) && !isAnyOrUnknown(actualType) && haveIncompatibleVariadicKinds(expectedType, actualType)) {
                throw new IllegalArgumentException("Mapping of incompatible types: " + expectedType + " -> " + actualType);
            }
        }
        myMappedTypes = mapping;
    }

    private static boolean haveIncompatibleVariadicKinds(PyType expectedType, PyType actualType) {
        return expectedType instanceof PyPositionalVariadicType ^ actualType instanceof PyPositionalVariadicType ||
               expectedType instanceof PyCallableParameterVariadicType ^ actualType instanceof PyCallableParameterVariadicType;
    }

    public static @Nullable PyTypeParameterMapping mapByShape(List<? extends PyType> expectedTypes,
                                                              List<? extends PyType> actualTypes,
                                                              Option ... options) {
        EnumSet<Option> optionSet = EnumSet.noneOf(Option.class);
        optionSet.addAll(Arrays.asList(options));

        List<PyType> normalizedExpectedTypes = flattenUnpackedTupleTypes(expectedTypes);
        List<PyType> normalizedActualTypes =
            replaceExpectedTypesWithParameterList(normalizedExpectedTypes, flattenUnpackedTupleTypes(actualTypes));

        NullTolerantDeque<PyType> expectedTypesDeque = new NullTolerantDeque<>(normalizedExpectedTypes);
        NullTolerantDeque<PyType> actualTypesDeque = new NullTolerantDeque<>(normalizedActualTypes);

        List<Couple<PyType>> leftMappedTypes = new ArrayList<>();
        List<Couple<PyType>> centerMappedTypes = new ArrayList<>();
        List<Couple<PyType>> rightMappedTypes = new ArrayList<>();

        boolean splittingTypeVarTuple = false;
        while (expectedTypesDeque.size() != 0 && actualTypesDeque.size() != 0) {
            PyType leftmostExpected = expectedTypesDeque.peekFirst();
            // Either a variadic type parameter *Ts or an unbounded unpacked tuple *tuple[int, ...] 
            if (leftmostExpected instanceof PyPositionalVariadicType) {
                break;
            }
            PyType leftmostActual = actualTypesDeque.peekFirst();
            if (leftmostExpected != null &&
                !isAnyOrUnknown(leftmostActual) &&
                leftmostExpected instanceof PyCallableParameterVariadicType ^ leftmostActual instanceof PyCallableParameterVariadicType) {
                break;
            }
            // The leftmost expected type is a regular type, not variadic
            if (leftmostActual instanceof PyPositionalVariadicType) {
                break;
            }
            expectedTypesDeque.removeFirst();
            actualTypesDeque.removeFirst();
            leftMappedTypes.add(Couple.of(leftmostExpected, leftmostActual instanceof PyType first ? first : PyAnyType.getUnknown()));
        }

        while (expectedTypesDeque.size() != 0 && actualTypesDeque.size() != 0) {
            PyType rightmostExpected = expectedTypesDeque.peekLast();
            if (rightmostExpected instanceof PyPositionalVariadicType) {
                break;
            }
            PyType rightmostActual = actualTypesDeque.peekLast();
            if (rightmostExpected != null &&
                !isAnyOrUnknown(rightmostActual) &&
                rightmostExpected instanceof PyCallableParameterVariadicType ^ rightmostActual instanceof PyCallableParameterVariadicType) {
                break;
            }
            expectedTypesDeque.removeLast();
            if (rightmostActual instanceof PyPositionalVariadicType rightmostActualVariadic) {
                // [T1, T2] <- [*tuple[T3, ...]]
                if (rightmostActualVariadic instanceof PyUnpackedTupleType unpackedTupleType && unpackedTupleType.isUnbound()) {
                    PyType repeatedActualType = unpackedTupleType.getElementTypes().get(0);
                    rightMappedTypes.add(Couple.of(rightmostExpected, repeatedActualType));
                }
                // [T1, T2] <- [*Ts]
                else {
                    splittingTypeVarTuple = true;
                    break;
                }
            }
            else {
                actualTypesDeque.removeLast();
                rightMappedTypes.add(Couple.of(rightmostExpected, rightmostActual));
            }
        }

        // [T1, T2] <- [*tuple[T3, ...]]
        if (expectedTypesDeque.size() != 0 && actualTypesDeque.size() != 0
            && !(expectedTypesDeque.peekFirst() instanceof PyVariadicType)
            && actualTypesDeque.peekFirst() instanceof PyPositionalVariadicType actualPositionalVariadic) {
            if (actualPositionalVariadic instanceof PyUnpackedTupleType actualUnpackedTupleType && actualUnpackedTupleType.isUnbound()) {
                while (expectedTypesDeque.size() != 0 && !(expectedTypesDeque.peekFirst() instanceof PyVariadicType)) {
                    PyType repeatedActualType = actualUnpackedTupleType.getElementTypes().get(0);
                    leftMappedTypes.add(Couple.of(expectedTypesDeque.peekFirst(), repeatedActualType));
                    expectedTypesDeque.removeFirst();
                }
            }
            else {
                splittingTypeVarTuple = true;
            }
        }

        if (splittingTypeVarTuple) {
            return null;
        }

        if (expectedTypesDeque.size() != 0 && expectedTypesDeque.peekFirst() instanceof PyPositionalVariadicType expectedPositionalVariadic) {
            // [*Ts] <- [*Ts] or [*Ts] <- [*tuple[T1, ...]]
            if (actualTypesDeque.size() == 1 && actualTypesDeque.peekFirst() instanceof PyPositionalVariadicType variadicType) {
                expectedTypesDeque.removeFirst();
                actualTypesDeque.removeFirst();
                centerMappedTypes.add(Couple.of(expectedPositionalVariadic, variadicType));
            }
            // [*Ts=Unpacked[tuple[int, str]]] <- []
            else if (actualTypesDeque.size() == 0 && optionSet.contains(Option.USE_DEFAULTS)
                     && expectedPositionalVariadic instanceof PyTypeVarTupleType typeVarTupleType
                     && typeVarTupleType.getDefaultType() != null) {
                expectedTypesDeque.removeFirst();
                // TODO consulo: Ref.deref missing
                Ref<? extends PyType> defaultTypeRef = typeVarTupleType.getDefaultType();
                centerMappedTypes.add(Couple.of(expectedPositionalVariadic, defaultTypeRef != null ? defaultTypeRef.get() : null));
            }
            // [*Ts] <- [T1, *Ts[T2, ...], T2, ...]
            else {
                List<PyType> nonParamVariadicActualTypes = new ArrayList<>();
                while (actualTypesDeque.size() != 0 && !(actualTypesDeque.peekFirst() instanceof PyCallableParameterVariadicType)) {
                    nonParamVariadicActualTypes.add(actualTypesDeque.peekFirst());
                    actualTypesDeque.removeFirst();
                }
                expectedTypesDeque.removeFirst();
                centerMappedTypes.add(Couple.of(expectedPositionalVariadic, PyUnpackedTupleTypeImpl.create(nonParamVariadicActualTypes)));
            }
        }

        boolean sizeMismatch = true;
        if (expectedTypesDeque.size() == 0) {
            boolean allActualTypesMatched = actualTypesDeque.size() == 0;
            boolean onlySingleActualVariadicLeft = actualTypesDeque.size() == 1 &&
                                                   actualTypesDeque.peekFirst() instanceof PyPositionalVariadicType;
            sizeMismatch = !(allActualTypesMatched || onlySingleActualVariadicLeft);
        }
        else if (actualTypesDeque.size() == 0) {
            // [T1, T2, ...] <- []
            boolean allMapped = true;
            for (PyType unmatchedType : expectedTypesDeque.toList()) {
                Couple<PyType> fallbackMapping = mapToFallback(unmatchedType, optionSet);
                allMapped = fallbackMapping != null;
                if (!allMapped) {
                    break;
                }
                centerMappedTypes.add(fallbackMapping);
            }
            sizeMismatch = !allMapped;
        }
        if (sizeMismatch) {
            return null;
        }
        List<Couple<PyType>> resultMapping = new ArrayList<>(leftMappedTypes);
        resultMapping.addAll(centerMappedTypes);
        Collections.reverse(rightMappedTypes);
        resultMapping.addAll(rightMappedTypes);
        return new PyTypeParameterMapping(resultMapping);
    }

    // [**P] <- [int, str, bool] is equivalent to [**P] <- [[int, str, bool]]
    // [**P] <- [...] keeps a single unknown type argument, which stands for any parameters
    // See https://typing.readthedocs.io/en/latest/spec/generics.html#user-defined-generic-classes
    private static List<PyType> replaceExpectedTypesWithParameterList(List<PyType> expectedTypes,
                                                                               List<PyType> actualTypes) {
        if (ContainerUtil.getOnlyItem(expectedTypes) instanceof PyParamSpecType &&
            !actualTypes.isEmpty() && !ContainerUtil.exists(actualTypes, o -> o instanceof PyVariadicType) &&
            !(actualTypes.size() == 1 && isUnknown(actualTypes.getFirst()))) {
            PyCallableParameterListType callableParameterListType =
                new PyCallableParameterListTypeImpl(ContainerUtil.map(actualTypes, PyCallableParameterImpl::nonPsi));
            return Collections.singletonList(callableParameterListType);
        }
        return actualTypes;
    }

    private static @Nullable Couple<PyType> mapToFallback(@Nullable PyType unmatchedExpectedType, EnumSet<Option> optionSet) {
        if (optionSet.contains(Option.USE_DEFAULTS) &&
            unmatchedExpectedType instanceof PyTypeParameterType typeParameterType &&
            typeParameterType.getDefaultType() != null) {
            // TODO consulo: Ref.deref missing
            Ref<? extends PyType> defaultTypeRef = typeParameterType.getDefaultType();
            PyType defaultType = defaultTypeRef != null ? defaultTypeRef.get() : null;
            // A malformed default (e.g. a parameter list `[int]` used as the default of a regular TypeVar) would yield a
            // variadic-incompatible mapping that the constructor rejects; ignore such a default and fall back below.
            if (defaultType == null || isUnknown(defaultType) || !haveIncompatibleVariadicKinds(unmatchedExpectedType, defaultType)) {
                return Couple.of(unmatchedExpectedType, defaultType);
            }
        }
        if (optionSet.contains(Option.MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY)) {
            return Couple.of(unmatchedExpectedType, PyAnyType.getUnknown());
        }
        return null;
    }

    private static List<PyType> flattenUnpackedTupleTypes(List<? extends PyType> types) {
        // TODO consulo: ContainerUtil.flatMap missing
        return types.stream().<PyType>flatMap(type -> {
            if (type instanceof PyUnpackedTupleType unpackedTupleType && !unpackedTupleType.isUnbound()) {
                return flattenUnpackedTupleTypes(unpackedTupleType.getElementTypes()).stream();
            }
            return Collections.<PyType>singletonList(type).stream();
        }).toList();
    }

    public List<Couple<PyType>> getMappedTypes() {
        return Collections.unmodifiableList(myMappedTypes);
    }

    public enum Option {
        MAP_UNMATCHED_EXPECTED_TYPES_TO_ANY,
        USE_DEFAULTS,
    }

    private static final class NullTolerantDeque<T> {
        private final Deque<Ref<T>> myDeque;

        private NullTolerantDeque(Collection<? extends @Nullable T> collection) {
            myDeque = new ArrayDeque<>(ContainerUtil.map(collection, Ref::create));
        }

        public @Nullable T peekFirst() {
            if (myDeque.isEmpty()) {
                throw new NoSuchElementException();
            }
            // TODO consulo: Ref.deref missing
            Ref<T> first = myDeque.peekFirst();
            return first != null ? first.get() : null;
        }

        public @Nullable T peekLast() {
            if (myDeque.isEmpty()) {
                throw new NoSuchElementException();
            }
            // TODO consulo: Ref.deref missing
            Ref<T> last = myDeque.peekLast();
            return last != null ? last.get() : null;
        }

        public void removeFirst() {
            if (myDeque.isEmpty()) {
                throw new NoSuchElementException();
            }
            myDeque.removeFirst();
        }

        public void removeLast() {
            if (myDeque.isEmpty()) {
                throw new NoSuchElementException();
            }
            myDeque.removeLast();
        }

        public int size() {
            return myDeque.size();
        }

        public List<@Nullable T> toList() {
            // TODO consulo: Ref.deref missing
            return ContainerUtil.map(myDeque, ref -> ref != null ? ref.get() : null);
        }
    }

    @Override
    public String toString() {
        return StringUtil.join(myMappedTypes, pair -> pair.first + " -> " + pair.second, ", ");
    }
}
