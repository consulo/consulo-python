// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.google.common.collect.HashMultimap;
import com.jetbrains.python.impl.psi.types.PyTypeInferenceCspSolverKt.ConstraintProblem;
import com.jetbrains.python.impl.psi.types.PyTypeInferenceCspSolverKt.ConstraintReducer;
import com.jetbrains.python.impl.psi.types.PyTypeInferenceCspSolverKt.InferenceVariablePool;
import com.jetbrains.python.impl.psi.types.PyTypeInferenceCspSolverKt.TypeBoundIncorporator;
import com.jetbrains.python.impl.psi.types.PyTypeInferenceCspSolverKt.TypeBoundResolver;
import com.jetbrains.python.impl.psi.types.PyTypeInferenceCspSolverKt.TypeConstraint;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.PyVariance;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class CspBuilder {
    private final TypeEvalContext context;
    private final HashMap<SubstitutionsIdentifier, PyTypeChecker.GenericSubstitutions> allSubstitutions = new HashMap<>();
    private final LinkedHashSet<TypeConstraint> initialConstraints = new LinkedHashSet<>();
    private final InferenceVariablePool inferenceVars = new InferenceVariablePool();
    private @Nullable ConstraintProblem cp;

    public CspBuilder(TypeEvalContext context) {
        this.context = context;
    }

    public TypeEvalContext getContext() {
        return context;
    }

    public void putSubstitutions(SubstitutionsIdentifier si, PyTypeChecker.GenericSubstitutions substitutions) {
        allSubstitutions.put(si, substitutions);
    }

    public Set<Map.Entry<SubstitutionsIdentifier, PyTypeChecker.GenericSubstitutions>> getAllSubstitutions() {
        return allSubstitutions.entrySet();
    }

    public void addConstraint(@Nullable PyType left, @Nullable PyType right, PyVariance variance, ConstraintPriority priority) {
        addConstraint(left, right, variance, priority, true);
    }

    public void addConstraint(@Nullable PyType left,
                              @Nullable PyType right,
                              PyVariance variance,
                              ConstraintPriority priority,
                              boolean isBinding) {
        if (Objects.equals(left, right)) {
            return;
        }
        PyType leftNoRecursion = removeFromCompositeTypes(left, right, context);
        PyType rightNoRecursion = removeFromCompositeTypes(right, left, context);
        TypeConstraint newConstraint = new TypeConstraint(leftNoRecursion, rightNoRecursion, variance, priority, isBinding);
        initialConstraints.add(newConstraint);
    }

    /**
     * The control flow analysis sometimes computes types that are self-recursive,
     * see test case [RecursionAndFixedPointLoops.types in loop compute fast].
     * <p>
     * This results in type constraints such as {@code T = int | T} that would lead to {@code T} being {@code T | unknown}.
     */
    public @Nullable PyType removeFromCompositeTypes(@Nullable PyType type, @Nullable PyType typeToRemove, TypeEvalContext context) {
        if (!(typeToRemove instanceof PyInferenceVariable inferenceVariable)) {
            return type;
        }

        boolean[] referencesInfVar = {false};
        PyRecursiveTypeVisitor.traverse(type, context, new PyRecursiveTypeVisitor.PyTypeTraverser() {
            @Override
            public PyRecursiveTypeVisitor.Traversal visitPyType(PyType type) {
                if (type.equals(inferenceVariable.getTypeVariable())) {
                    referencesInfVar[0] = true;
                    return PyRecursiveTypeVisitor.Traversal.TERMINATE;
                }
                return PyRecursiveTypeVisitor.Traversal.CONTINUE;
            }
        });
        if (!referencesInfVar[0]) {
            return type;
        }

        return PyCloningTypeVisitor.clone(type, new PyCloningTypeVisitor(context) {
            private @Nullable PyType rebuildCompositeType(Collection<@Nullable PyType> members,
                                                          Function<List<@Nullable PyType>, @Nullable PyType> createComposite) {
                List<@Nullable PyType> remainingMembers = new ArrayList<>();
                for (PyType member : members) {
                    PyType cloned = clone(member);
                    if (!Objects.equals(cloned, inferenceVariable.getTypeVariable())) {
                        remainingMembers.add(cloned);
                    }
                }

                return switch (remainingMembers.size()) {
                    case 0 -> PyAnyType.getUnknown();
                    case 1 -> remainingMembers.get(0);
                    default -> createComposite.apply(remainingMembers);
                };
            }

            @Override
            public @Nullable PyType visitPyUnionType(PyUnionType unionType) {
                return rebuildCompositeType(unionType.getMembers(), PyUnionType::unionOrUnknown);
            }

            @Override
            public @Nullable PyType visitPyUnsafeUnionType(PyUnsafeUnionType unsafeUnionType) {
                return rebuildCompositeType(unsafeUnionType.getMembers(), PyUnsafeUnionType::unsafeUnion);
            }

            @Override
            public @Nullable PyType visitPyIntersectionType(PyIntersectionType intersectionType) {
                return rebuildCompositeType(intersectionType.getMembers(), PyIntersectionType::intersectionOrTop);
            }
        });
    }

    /**
     * No support for {@code Type[ TypeVariable]}.
     * <p>
     * Reason: The current typing model models the 'type of' relation using the {@link com.jetbrains.python.psi.types.PyInstantiableType#isDefinition} flag.
     * This results in modeling both {@code TypeVar} and {@code Type[ TypeVar]} as two {@code PyTypeVarType}s that share the same
     * {@link PyTypeVarType#getDeclarationElement()} but only differ in their {@code isDefinition} flag.
     * <p>
     * Possible solutions:
     * 1) Do not support {@code Type[ TypeVar]} (chosen option)
     * 2) Rectify the type model to model {@code Type[ TypeVar]} as {@code PyTypeType} using {@code PyTypeVarType}
     * 3) Only within the CSP, model {@code Type[ TypeVar]} as {@code PyTypeType} using {@code PyTypeVarType} as the type argument, and substitute when entering/leaving the CSP
     * 4) Within CSP, use two CSP variables to model a {@link PyTypeVarType} with the {@code isDefinition} flag and a {@code PyTypeType} true/false, respectively.
     * Also use additional constraints to make sure that both of these variables are equal except for the {@code isDefinition} flag.
     * <p>
     * Reasoning:
     * 3) Negative impact on performance: During the solving process, a large number of subtype checks can occur, and substitution causes traverses of all related types.
     * 4) Introduction of new Variance: {@code NONVARIANT} would raise the complexity of the constraint solving procedure further.
     */
    public PyInferenceVariable addInferenceVariable(PyTypeVarType typeVariable, SubstitutionsIdentifier si) {
        if (typeVariable.isDefinition()) {
            throw new NotSupportedException();
        }
        return inferenceVars.add(typeVariable, si);
    }

    public void solve() {
        Pair<List<TypeConstraint>, Set<PyInferenceVariable>> discarded = discardUnboundConstraints(initialConstraints);
        List<TypeConstraint> bindingConstraints = discarded.getFirst();
        Set<PyInferenceVariable> unboundIVs = discarded.getSecond();
        inferenceVars.setUnbound(unboundIVs);
        Collection<TypeConstraint> sortedConstraints = getSortedConstraints(bindingConstraints);
        ConstraintProblem cp = new ConstraintProblem(sortedConstraints, inferenceVars, context);
        this.cp = cp;

        for (TypeConstraint constraint : cp.constraints) {
            ConstraintReducer.reduce(constraint, cp);
        }
        TypeBoundIncorporator.incorporate(cp, context);
        TypeBoundResolver.resolve(cp, context);
    }

    private Pair<List<TypeConstraint>, Set<PyInferenceVariable>> discardUnboundConstraints(Set<TypeConstraint> constraints) {
        Set<PyInferenceVariable> unboundIVs = new HashSet<>();
        HashMultimap<PyInferenceVariable, TypeConstraint> ivToConstraintMap = HashMultimap.create();
        int poolSize = inferenceVars.values().size();
        if (poolSize == 0) {
            return Pair.create(new ArrayList<>(constraints), Collections.emptySet());
        }
        for (TypeConstraint constraint : constraints) {
            Set<PyInferenceVariable> found = PyTypeInferenceCspSolverKt.collectInferenceVariables(constraint.left(), context, poolSize);
            found.forEach(it -> ivToConstraintMap.put(it, constraint));
        }
        Set<TypeConstraint> discardedConstraints = new HashSet<>();
        for (PyInferenceVariable iv : inferenceVars.values()) {
            Set<TypeConstraint> ivConstraints = ivToConstraintMap.get(iv);
            boolean boundIV = ivConstraints.stream().anyMatch(c -> Boolean.TRUE.equals(c.isBinding()));
            if (!boundIV) {
                unboundIVs.add(iv);
                discardedConstraints.addAll(ivConstraints);
            }
        }
        List<TypeConstraint> result = new ArrayList<>();
        for (TypeConstraint constraint : constraints) {
            if (!discardedConstraints.contains(constraint)) {
                result.add(constraint);
            }
        }
        return Pair.create(result, unboundIVs);
    }

    private Collection<TypeConstraint> getSortedConstraints(Collection<TypeConstraint> constraints) {
        // Sort by priority
        // but also sort all constraints to the end that contain unions or unsafe unions.
        // The reason for the latter is that this will delay their reduce() calls,
        // which will in turn increase the number of available bounds and improve the heuristics of [reduceDisjunction()].
        // Both of the above sort criteria are stable, i.e., keep the initial order if possible.

        record SortKey(TypeConstraint constraint, @Nullable ConstraintPriority priority, boolean containsUnions) {
        }

        List<SortKey> keys = new ArrayList<>();
        for (TypeConstraint c : constraints) {
            keys.add(new SortKey(c, c.priority(),
                                 containsUnionsOrUnsafeUnions(c.left(), context) || containsUnionsOrUnsafeUnions(c.right(), context)));
        }
        keys.sort(
            Comparator.<SortKey, Boolean>comparing(it -> it.containsUnions()) // containsUnionsOrUnsafeUnions
                .thenComparing(it -> it.priority(), Comparator.nullsFirst(Comparator.naturalOrder())) // priority
        );
        List<TypeConstraint> sortedConstraints = new ArrayList<>(keys.size());
        for (SortKey key : keys) {
            sortedConstraints.add(key.constraint());
        }

        return sortedConstraints;
    }

    private boolean containsUnionsOrUnsafeUnions(@Nullable PyType root, TypeEvalContext context) {
        if (root instanceof PyUnionType || root instanceof PyUnsafeUnionType) {
            return true;
        }
        boolean[] result = {false};
        PyRecursiveTypeVisitor.traverse(root, context, new PyRecursiveTypeVisitor.PyTypeTraverser() {
            @Override
            public PyRecursiveTypeVisitor.Traversal visitPyType(PyType type) {
                if (type instanceof PyUnionType || type instanceof PyUnsafeUnionType) {
                    result[0] = true;
                    return PyRecursiveTypeVisitor.Traversal.TERMINATE;
                }
                return PyRecursiveTypeVisitor.Traversal.CONTINUE;
            }
        });
        return result[0];
    }

    /**
     * Post-computes the instantiation values to a usable mapping from type variables to types.
     */
    public PyCspSolution getSolution() {
        ConstraintProblem cp = Objects.requireNonNull(this.cp);
        Map<PyInferenceVariable, @Nullable PyType> instantiations = cp.failed ? cp.instantiations : cp.solution;
        Map<SubstitutionsIdentifier, Map<PyTypeParameterType, Ref<@Nullable PyType>>> typeVars2TypeRefs = new LinkedHashMap<>();
        for (Map.Entry<PyInferenceVariable, @Nullable PyType> entry : instantiations.entrySet()) {
            PyInferenceVariable iv = entry.getKey();
            Map<PyTypeParameterType, Ref<@Nullable PyType>> nestedCspMap =
                typeVars2TypeRefs.computeIfAbsent(iv.getSubstitutionsIdentifier(), it -> new LinkedHashMap<>());
            nestedCspMap.put(iv.getTypeVariable(), Ref.create(entry.getValue()));
        }
        boolean complete = instantiations.keySet().containsAll(cp.inferenceVars.values());
        return new PyCspSolution(cp.failed, complete, typeVars2TypeRefs);
    }
}
