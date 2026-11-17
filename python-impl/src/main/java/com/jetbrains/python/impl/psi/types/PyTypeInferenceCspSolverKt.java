// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeMember;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyTypeVarType;
import com.jetbrains.python.psi.types.PyVariance;
import com.jetbrains.python.psi.types.PyVarianceIntermediateResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.util.RecursionGuard;
import consulo.application.util.RecursionManager;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import consulo.util.lang.StackOverflowPreventedException;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

import static com.jetbrains.python.impl.codeInsight.typing.PyProtocolsKt.isProtocol;

/**
 * JB: the file-private classes and the top-level functions of {@code PyTypeInferenceCspSolver.kt}.
 */
public final class PyTypeInferenceCspSolverKt {
    private PyTypeInferenceCspSolverKt() {
    }

    /**
     * A type constraint of the form {@code left variance right} where {@code left}, and {@code right} are arbitrary type arguments,
     * and {@code variance} is one of {@code <:}, {@code :>}, or {@code =}.
     * <p>
     * No restrictions apply whether inference variables appear on none, one, or both sides.
     */
    record TypeConstraint(@Nullable PyType left,
                          @Nullable PyType right,
                          PyVariance variance,
                          @Nullable ConstraintPriority priority,
                          @Nullable Boolean isBinding) {
        static final TypeConstraint TRUE = new TypeConstraint(null, null, PyVariance.INVARIANT);
        static final TypeConstraint FALSE = new TypeConstraint(null, null, PyVariance.INFER_VARIANCE);

        TypeConstraint(@Nullable PyType left, @Nullable PyType right, PyVariance variance) {
            this(left, right, variance, null, null);
        }

        @Override
        public String toString() {
            String varianceStr = switch (variance) {
                case INVARIANT -> "=";
                case COVARIANT -> "<:";
                case CONTRAVARIANT -> ":>";
                default -> "?";
            };
            return left + " " + varianceStr + " " + right;
        }
    }

    static final class InferenceVariablePool {
        private final InferenceVariableNameGenerator nameGenerator = new InferenceVariableNameGenerator();
        private final LinkedHashMap<String, PyInferenceVariable> pool = new LinkedHashMap<>();
        private final LinkedHashSet<PyInferenceVariable> unboundIVs = new LinkedHashSet<>();

        PyInferenceVariable add(PyTypeVarType typeVar, SubstitutionsIdentifier si) {
            PyInferenceVariable infVar = new PyInferenceVariable(typeVar, si, nameGenerator.nextName());
            pool.put(infVar.getName(), infVar);
            return infVar;
        }

        Collection<PyInferenceVariable> values() {
            return pool.values();
        }

        void setUnbound(Collection<PyInferenceVariable> infVars) {
            unboundIVs.addAll(infVars);
        }

        boolean isUnbound(PyInferenceVariable infVar) {
            return unboundIVs.contains(infVar);
        }
    }

    private static final class InferenceVariableNameGenerator {
        private static final char RANGE_START = 'α';
        private static final char RANGE_END = 'ι';
        private final int rangeSize = RANGE_END - RANGE_START + 1;
        private int index = 0;
        private int quoteCount = 0;

        String nextName() {
            String name = (char) (RANGE_START + index++) + "'".repeat(quoteCount);
            if (index == rangeSize) {
                index = 0;
                quoteCount++;
            }
            return name;
        }
    }

    /**
     * Type bounds are similar to {@link TypeConstraint}, but are required to be of a simpler, more unified form:
     * the LHS must always be an inference variable. The RHS, however, may be a proper or improper type.
     */
    record TypeBound(PyInferenceVariable left,
                     @Nullable PyType right,
                     PyVariance variance,
                     Set<PyInferenceVariable> referencedInfVars) {
        TypeBound(PyInferenceVariable left, @Nullable PyType right, PyVariance variance, TypeEvalContext context) {
            this(left, right, variance, collectInferenceVariables(right, context));
        }
    }

    static final class ConstraintProblem {
        final RecursionGuard<Pair<@Nullable PyType, @Nullable PyType>> recursionGuard =
            RecursionManager.createGuard("ConstraintProblem_ReduceStructualType");

        final TypeEvalContext context;

        final InferenceVariablePool inferenceVars;

        /**
         * Initial set of constraints
         */
        final Collection<TypeConstraint> constraints;

        /**
         * True iff a constraint/bound has been added that renders this problem unsolvable.
         */
        boolean failed = false;

        /**
         * All bounds of this constraint problem
         */
        final LinkedHashSet<TypeBound> bounds = new LinkedHashSet<>();

        /**
         * Snapshot of all bounds of this constraint problem right before the resolution started
         */
        final LinkedHashSet<TypeBound> preResolutionBounds = new LinkedHashSet<>();

        /**
         * Bounds within this bound set, stored per inference variable.
         */
        final SetMultimap<PyInferenceVariable, TypeBound> boundsPerInfVar = LinkedHashMultimap.create();

        /**
         * Reverse index: for each inf-var γ, which bounds' RHS mention γ
         */
        final SetMultimap<PyInferenceVariable, TypeBound> boundsReferencingInfVar = LinkedHashMultimap.create();

        /**
         * Instantiations among the bounds of this set, i.e., bounds of the form {@code α = P} with P being a proper type.
         */
        final Map<PyInferenceVariable, @Nullable PyType> instantiations = new LinkedHashMap<>();

        /**
         * Instantiations at the end of a successful resolving step.
         */
        final Map<PyInferenceVariable, @Nullable PyType> solution = new LinkedHashMap<>();

        /**
         * Used to keep track of what bounds have already been incorporated
         */
        final Set<TypeBound> incorporatedBounds = new LinkedHashSet<>();

        /**
         * Incremental worklists
         */
        final ArrayDeque<TypeBound> pendingBounds = new ArrayDeque<>();
        final LinkedHashSet<PyInferenceVariable> pendingInstantiations = new LinkedHashSet<>();

        /**
         * IDs for structurally equal bounds so we can memoize processed pairs cheaply
         */
        private final HashMap<TypeBound, Integer> boundIds = new HashMap<>();

        private int nextBoundId = 1;

        /**
         * Memoization: each pair combined at most once
         */
        final Set<Long> seenBoundPairs = new HashSet<>();

        ConstraintProblem(Collection<TypeConstraint> pConstraints, InferenceVariablePool pInferenceVars, TypeEvalContext pContext) {
            context = pContext;
            inferenceVars = pInferenceVars;
            constraints = pConstraints;
        }

        /**
         * True iff the argument appears on the left-hand side of one or more bounds
         */
        boolean isBounded(@Nullable PyInferenceVariable infVar) {
            return boundsPerInfVar.containsKey(infVar);
        }

        /**
         * True iff an instantiation was recorded via method {@link #addBound} for the given inference variable.
         */
        boolean isInstantiated(@Nullable PyInferenceVariable infVar) {
            return infVar != null && instantiations.containsKey(infVar);
        }

        /**
         * True iff the first argument is constrained by the second (irrespective of whether that second argument is instantiated).
         */
        boolean dependsOnResolutionOf(PyInferenceVariable infVar, PyInferenceVariable candidate) {
            for (TypeBound currBound : boundsPerInfVar.get(infVar)) {
                if (currBound.referencedInfVars().contains(candidate)) {
                    return true;
                }
            }
            return false;
        }

        int idOf(TypeBound bound) {
            return boundIds.computeIfAbsent(bound, it -> nextBoundId++);
        }

        /**
         * Removes the given type bound, identified by object identity.
         * <p>
         * For internal use only.
         * Provided for performance reasons to allow removal of type bounds that do no longer have any effect, i.e., are redundant.
         */
        void removeBound(TypeBound bound) {
            bounds.remove(bound);
            boundsPerInfVar.remove(bound.left(), bound);
            incorporatedBounds.remove(bound);
            for (PyInferenceVariable ref : bound.referencedInfVars()) {
                boundsReferencingInfVar.remove(ref, bound);
            }
        }

        void addBound(PyInferenceVariable left, @Nullable PyType right, PyVariance variance) {
            if (left.equals(right)) {
                return;
            }
            if (PyTypeUtilKt.isUnknown(right)) {
                return;
            }
            TypeBound newBound = new TypeBound(left, right, variance, context);
            boolean boundWasAdded = bounds.add(newBound);
            if (!boundWasAdded) {
                return;
            }

            boundsPerInfVar.put(left, newBound);
            for (PyInferenceVariable ref : newBound.referencedInfVars()) {
                boundsReferencingInfVar.put(ref, newBound);
            }
            pendingBounds.addLast(newBound);

            if (variance == PyVariance.INVARIANT && isProper(right, context)) {
                if (!instantiations.containsKey(left) || PyTypeUtilKt.isAnyOrUnknown(instantiations.get(left))) {
                    instantiations.put(left, right);
                }
                pendingInstantiations.addLast(left);
            }
        }

        void savePreResolutionTypeBounds() {
            if (preResolutionBounds.isEmpty()) {
                preResolutionBounds.addAll(bounds);
            }
        }

        void saveSolution() {
            solution.putAll(instantiations);
        }

        // TODO: add reason as parameter, e.g. the current stack of constraints
        void fail() {
            failed = true;
        }
    }

    /**
     * This element is based on an EPL-1.0-licensed Java source by NumberFour AG (2016).
     * (@link https://github.com/eclipse-n4js/n4js/blob/master/plugins/org.eclipse.n4js/src/org/eclipse/n4js/typesystem/constraints/Reducer.java).
     * Ported to Kotlin; structure and semantics remain substantially the same.
     */
    static final class ConstraintReducer {
        private ConstraintReducer() {
        }

        // TODO: add traceability e.g. by storing/stacking information about original constraints and before/after checking if the cp is failed
        static void reduce(TypeConstraint constraint, ConstraintProblem cp) {
            if (constraint.equals(TypeConstraint.TRUE)) {
                return;
            }
            if (constraint.equals(TypeConstraint.FALSE)) {
                cp.fail();
                return;
            }
            reduce(constraint.left(), constraint.right(), constraint.variance(), cp);
        }

        private static void reduce(@Nullable PyType left, @Nullable PyType right, PyVariance variance, ConstraintProblem cp) {
            if (variance == PyVariance.INFER_VARIANCE) {
                cp.fail();
                return;
            }

            if (isProper(left, cp.context) && isProper(right, cp.context)) {
                reduceProperType(left, right, variance, cp);
                return;
            }

            if (left instanceof PyUnsafeUnionType unsafeUnionType) {
                reduceDisjunction(right, filterNotNull(unsafeUnionType.getMembers()), variance.not(), cp);
                return;
            }
            if (right instanceof PyUnsafeUnionType unsafeUnionType) {
                reduceDisjunction(left, filterNotNull(unsafeUnionType.getMembers()), variance, cp);
                return;
            }

            if (left instanceof PyInferenceVariable leftVariable) {
                cp.addBound(leftVariable, right, variance);
                return;
            }
            if (right instanceof PyInferenceVariable rightVariable) {
                cp.addBound(rightVariable, left, variance.not());
                return;
            }

            // check top and bottom types. Doing this up-front here simplifies the handling of other special cases
            boolean isBottomType = isBottomType(variance == PyVariance.CONTRAVARIANT ? right : left);
            boolean isTopType = isTopType(variance == PyVariance.CONTRAVARIANT ? left : right, cp.context);
            if (isBottomType || isTopType) {
                return;
            }

            if (left instanceof PyCompositeType leftComposite) {
                reduceCompositeType(right, leftComposite, variance.not(), cp);
                return;
            }
            if (right instanceof PyCompositeType rightComposite) {
                reduceCompositeType(left, rightComposite, variance, cp);
                return;
            }

            boolean isLeftStructural = isStructural(left, cp.context);
            boolean isRightStructural = isStructural(right, cp.context);
            if ((isLeftStructural && (variance == PyVariance.CONTRAVARIANT || variance == PyVariance.INVARIANT))
                || (isRightStructural && (variance == PyVariance.COVARIANT || variance == PyVariance.INVARIANT))) {
                reduceStructuralTypeGuarded(left, right, variance, cp);
                return;
            }


            if (left instanceof PyTypeFormType || right instanceof PyTypeFormType) {
                reduceTypeFormType(left, right, variance, cp);
                return;
            }

            if (left instanceof PyClassLikeType && right instanceof PyClassLikeType) {
                reduceNominalType(left, right, variance, cp);
                return;
            }

            if (left instanceof PyCallableType leftCallable && right instanceof PyCallableType rightCallable &&
                leftCallable.isCallable() && rightCallable.isCallable()) {
                reduceCallableType(leftCallable, rightCallable, variance, cp);
                return;
            }

            reduceProperType(left, right, variance, cp);
        }

        private static void reduceStructuralTypeGuarded(@Nullable PyType left, @Nullable PyType right, PyVariance variance, ConstraintProblem cp) {
            // iterating over all members (and their types) often leads to recursive checks and reduce calls
            try {
                cp.recursionGuard.doPreventingRecursion(Pair.create(left, right), false, () -> {
                    reduceStructuralType(left, right, variance, cp);
                    return null;
                });
            }
            catch (StackOverflowPreventedException e) {
                // this is supposed to happen in recursive situations and a safe exit. No need to bother tests.
            }
        }

        private static void reduceStructuralType(@Nullable PyType left, @Nullable PyType right, PyVariance variance, ConstraintProblem cp) {
            if (Objects.equals(left, right)) {
                return;
            }
            if (left == null || right == null) {
                return;
            }
            if (variance == PyVariance.CONTRAVARIANT) {
                reduceStructuralType(right, left, PyVariance.COVARIANT, cp);
                return;
            }
            if (!(right instanceof PyClassType rightClass) || !isProtocol(rightClass, cp.context)) {
                throw UNREACHABLE();
            }
            if (!(left instanceof PyClassType leftClass)) {
                cp.fail(); // TODO
                return;
            }

            // shortcut
            List<PyClassLikeType> leftSuperTypes = new ArrayList<>();
            leftSuperTypes.add(leftClass);
            leftSuperTypes.addAll(leftClass.getAncestorTypes(cp.context));
            for (PyClassLikeType leftSuperType : leftSuperTypes) {
                if (!(leftSuperType instanceof PyClassType leftSuperClassType)) {
                    continue;
                }
                if (!leftSuperClassType.getPyClass().equals(rightClass.getPyClass())) {
                    continue;
                }

                PyClassType genericType =
                    PyTypeChecker.findGenericDefinitionType(leftSuperClassType.getPyClass(), leftSuperClassType.isDefinition(), cp.context);
                boolean hasGenerics = PyTypeChecker.hasGenerics(genericType, cp.context);
                if (hasGenerics) {
                    reduceNominalType(left, right, variance, cp);
                }
                return;
            }

            SubtypeJudgement.ReversibleSubstitutionData reversibleSubstitutionData =
                SubtypeJudgement.getReversibleSubstitutionData(left, right, cp.context);
            List<Pair<PyTypeMember, List<PyTypeMember>>> matchingMembers = PyTypeChecker.getMatchingProtocolMembers(
                (PyClassType) Objects.requireNonNull(reversibleSubstitutionData.rightNoIVs),
                (PyClassType) Objects.requireNonNull(reversibleSubstitutionData.leftNoIVs),
                new PyTypeChecker.GenericSubstitutions(),
                cp.context);

            PyTypeChecker.GenericSubstitutions leftSubstitutions = PyTypeChecker.unifyReceiver(left, cp.context)
                .addToCopy(reversibleSubstitutionData.leftBackSubstitutions.getTypeVars(), null, null);
            PyTypeChecker.GenericSubstitutions rightSubstitutions = PyTypeChecker.unifyReceiver(right, cp.context)
                .addToCopy(reversibleSubstitutionData.rightBackSubstitutions.getTypeVars(), null, null);

            for (Pair<PyTypeMember, List<PyTypeMember>> pair : matchingMembers) {
                PyTypeMember rightMember = pair.getFirst();
                List<PyTypeMember> leftMembers = pair.getSecond();
                PyType rightMemberType = PyTypeChecker.substitute(rightMember.getType(), rightSubstitutions, cp.context);
                Set<@Nullable PyType> leftMemberTypes = new LinkedHashSet<>();
                for (PyTypeMember leftMember : leftMembers) {
                    if (variance == PyVariance.INVARIANT) {
                        var leftMemberElement = leftMember.getElement();
                        var rightMemberElement = rightMember.getElement();
                        boolean leftIsReadOnly = leftMemberElement instanceof PyTargetExpression leftTarget &&
                            PyTypeUtil.isReadOnlyAttribute(leftTarget, cp.context);
                        boolean rightIsReadOnly = rightMemberElement instanceof PyTargetExpression rightTarget &&
                            PyTypeUtil.isReadOnlyAttribute(rightTarget, cp.context);
                        if (leftIsReadOnly != rightIsReadOnly) {
                            continue;
                        }
                    }
                    PyType leftMemberType = PyTypeChecker.substitute(leftMember.getType(), leftSubstitutions, cp.context);
                    leftMemberTypes.add(leftMemberType);
                }
                switch (leftMemberTypes.size()) {
                    case 0 -> {
                        cp.fail();
                        return;
                    }
                    case 1 -> {
                        PyType leftMemberType = leftMemberTypes.iterator().next();
                        reduce(new TypeConstraint(leftMemberType, rightMemberType, variance), cp);
                    }
                    default -> {
                        PyType leftMemberType = PyIntersectionType.intersectionOrTop(leftMemberTypes);
                        TypeConstraint combinedConstraint = new TypeConstraint(leftMemberType, rightMemberType, variance);
                        reduce(combinedConstraint, cp);
                    }
                }

                // FIXME: process remaining cases for INVARIANCE
            }
        }

        private static void reduceProperType(@Nullable PyType left, @Nullable PyType right, PyVariance variance, ConstraintProblem cp) {
            switch (variance) {
                case COVARIANT -> {
                    if (!SubtypeJudgement.isSubtype(left, right, cp.context)) {
                        cp.fail();
                        return;
                    }
                }
                case CONTRAVARIANT -> {
                    if (!SubtypeJudgement.isSubtype(right, left, cp.context)) {
                        cp.fail();
                        return;
                    }
                }
                // actually this should never happen. However, we fail gracefully and fall back to invariant
                case INFER_VARIANCE, INVARIANT -> {
                    if (!SubtypeJudgement.sameTypes(left, right, cp.context)) {
                        cp.fail();
                        return;
                    }
                }
                case BIVARIANT -> {
                    // success
                }
            }
            // success
        }

        /**
         * Reduction for two given callable types will be applied on all their parameters and their return type in a pair-wise manner.
         */
        private static void reduceCallableType(PyCallableType left, PyCallableType right, PyVariance variance, ConstraintProblem cp) {
            // derive constraints for types of parameters
            List<PyCallableParameter> lParams = left.getParameters(cp.context);
            List<PyCallableParameter> rParams = right.getParameters(cp.context);

            if (lParams != null && rParams != null) {
                int size = Math.min(lParams.size(), rParams.size());
                for (int i = 0; i < size; i++) {
                    PyCallableParameter lParam = lParams.get(i);
                    PyCallableParameter rParam = rParams.get(i);
                    if (lParam.isSelf() || rParam.isSelf()) {
                        continue;
                    }
                    PyType lParamType = lParam.getType(cp.context);
                    PyType rParamType = rParam.getType(cp.context);
                    if (lParamType != null && rParamType != null) {
                        PyVariance paramVariance = variance.times(PyVariance.CONTRAVARIANT);
                        reduce(lParamType, rParamType, paramVariance, cp);
                    }
                }
            }

            PyType lReturnType = left.getReturnType(cp.context);
            PyType rReturnType = right.getReturnType(cp.context);
            PyVariance retTypeVariance = variance.times(PyVariance.COVARIANT);
            reduce(lReturnType, rReturnType, retTypeVariance, cp);
        }

        /**
         * Reduces a PEP 747 {@code TypeForm} constraint to a constraint between the represented types.
         */
        private static void reduceTypeFormType(@Nullable PyType left, @Nullable PyType right, PyVariance variance, ConstraintProblem cp) {
            PyType leftRepr = PyTypeFormType.representedTypeOf(left);
            PyType rightRepr = PyTypeFormType.representedTypeOf(right);
            if (leftRepr == null || rightRepr == null) {
                cp.fail();
                return;
            }
            reduce(leftRepr, rightRepr, variance, cp);
        }

        /**
         * Reduction for parameterized type references according to nominal subtyping rules.
         * <p>
         * We have a situation like ⟨ G&lt;IV&gt; Φ G&lt;string&gt; ⟩ (with IV being an inference variable) which may result
         * from code such as:
         *
         * <pre>{@code
         * class G[T]: ...
         * def f[IV](p: G[IV]) : ...
         * gStr : G[str]
         * f(gStr) # "expected type :> actual type" will lead to "G[IV] :> G[str]"
         * }</pre>
         * <p>
         * Resulting in the constraint "IV = string".
         * <p>
         * However, it is not always that simple. The type argument corresponding to the
         * inference variable ('string' corresponding to 'IV' in the above example) might
         * not be held by the ParameterizedTypeRef but may instead be contained somewhere
         * in the inheritance hierarchy of 'value'. For example:
         *
         * <pre>{@code
         * class G[T]: ...
         * def f[IV](p: G[IV]) : ...
         * class C(G[str]) : ...
         * c : C
         * f(c) # will lead to "G[IV] :> C"
         * }</pre>
         * <p>
         * Of course, the inheritance hierarchy might be arbitrarily complex.
         * <p>
         * Solution:
         * We derive a constraint for the above situations in several steps
         * Start with:
         * <pre>{@code
         * G[IV] :> C
         * }</pre>
         * Now, map each type argument of 'left' to the corresponding type parameter of 'left':
         * IV &lt;-&gt; T
         * Then, perform type variable substitution on the current right-hand side ("T" in our example) based on the
         * substitutions defined by the original right-hand side ("C" in our example):
         * IV &lt;-&gt; string
         */
        private static void reduceNominalType(@Nullable PyType pLeft, @Nullable PyType pRight, PyVariance pVariance, ConstraintProblem cp) {
            if ((pVariance == PyVariance.COVARIANT && !SubtypeJudgement.isRawSubtype(pLeft, pRight, cp.context))
                || (pVariance == PyVariance.CONTRAVARIANT && !SubtypeJudgement.isRawSubtype(pRight, pLeft, cp.context))
                || (pVariance == PyVariance.INVARIANT &&
                !(SubtypeJudgement.isRawSubtype(pLeft, pRight, cp.context) || SubtypeJudgement.isRawSubtype(pRight, pLeft, cp.context)))) {
                cp.fail();
            }
            // Illustrating example:
            // f[T](arg: tuple[T, str]) ---> pLeft  is tuple[IV1, str]
            // f((1, "s"))              ---> pRight is tuple[Literal[1], Literal["s"]]

            PyType left;
            PyType right;
            if (pVariance == PyVariance.COVARIANT) {
                // normalize ⟨ B <: A ⟩ to ⟨ A :> B ⟩ to make sure the (raw) subtype is on the right-hand side
                left = pRight;
                right = pLeft;
            }
            else {
                left = pLeft;
                right = pRight;
            }

            // Here we check supertypes of C, see the example in KDoc above
            // G[IV] :> C
            PyType commonSuperClass = findSmallestUpperBound(left, right, cp.context);
            PyTypeChecker.GenericSubstitutions substitutionsCommonSuperClass = PyTypeChecker.unifyReceiver(commonSuperClass, cp.context);
            PyTypeChecker.GenericSubstitutions substitutionsLeft =
                subtract(PyTypeChecker.unifyReceiver(left, cp.context), substitutionsCommonSuperClass);
            PyTypeChecker.GenericSubstitutions substitutionsRight =
                subtract(PyTypeChecker.unifyReceiver(right, cp.context), substitutionsCommonSuperClass);
            for (PyTypeVarType leftTV : substitutionsLeft.getTypeVars().keySet()) {
                Ref<@Nullable PyType> leftTypeArg = substitutionsLeft.getTypeVars().get(leftTV);
                if (leftTypeArg == null) {
                    continue;
                }
                if (substitutionsRight.getTypeVars().containsKey(leftTV)) {
                    PyType typeArgSubst = PyTypeChecker.substitute(leftTypeArg.get(), substitutionsRight, cp.context);
                    PyType typeVarSubst = PyTypeChecker.substitute(leftTV, substitutionsRight, cp.context);
                    PyVariance inferredVariance = PyInferredVarianceJudgment.getDeclaredOrInferredVariance(leftTV, cp.context);
                    PyVariance defSiteVariance = inferredVariance == PyVariance.BIVARIANT ? PyVariance.COVARIANT : inferredVariance;
                    reduce(typeVarSubst, typeArgSubst, defSiteVariance, cp);
                }
            }
        }

        /**
         * Returns the first common superclass of {@code leftType} and {@code rightType}.
         * <p>
         * Assume {@code leftType} is {@code tuple[IV1, str]} (IV1 is an inference variable) and {@code rightType} is {@code list[str|bool]}:
         * The superclasses of {@code tuple} and {@code list} are {@code Sequence}, then {@code Collection}, then {@code Iterable} (among others).
         * Their first common superclass i.e., smallest supper bound, is {@code Sequence[_T_co]}.
         */
        private static @Nullable PyType findSmallestUpperBound(@Nullable PyType leftType, @Nullable PyType rightType, TypeEvalContext context) {
            if (!(leftType instanceof PyClassType leftClassType)) {
                return null;
            }
            if (!(rightType instanceof PyClassType rightClassType)) {
                return null;
            }
            PyClass leftClass = leftClassType.getPyClass();
            PyClass rightClass = rightClassType.getPyClass();
            List<PyClass> leftAncestors = new ArrayList<>();
            leftAncestors.add(leftClass);
            leftAncestors.addAll(leftClass.getAncestorClasses(context));
            Set<PyClass> rightAncestors = new HashSet<>();
            rightAncestors.add(rightClass);
            rightAncestors.addAll(rightClass.getAncestorClasses(context));
            PyClass common = null;
            for (PyClass candidate : leftAncestors) {
                if (rightAncestors.contains(candidate)) {
                    common = candidate;
                    break;
                }
            }
            if (common == null) {
                return null;
            }
            return context.getType(common);
        }

        /**
         * Returns a copy of this substitution map without the entries that {@code other} also contains.
         * The common superclass entries stay because the caller relates the two sides through that class.
         * <p>
         * Given two types {@code S = tuple[IV1, str]} and {@code T = list[str|bool]}: We get substitutions that contain all type parameters of tuple,
         * Sequence, Collection, Iterable, etc. for {@code tuple}, and all type parameters of list, Sequence, Collection, Iterable, etc. for {@code list}.
         * This method removes all duplicates (i.e., from Collection, Iterable, etc.) except for those that belong to the common superclass
         * where S and T meet in the type hierarchy (i.e., from Sequence).
         * <p>
         * The motivation to remove duplicates is (1) a slight performance improvement, and (2) to deal with a specific case of tuple types:
         * {@code tuples} are declared as {@code class tuple(Sequence[_T_co]): ...} but treated in PyCharm type checker differently: In method
         * {@link PyTypeChecker#expandTupleTypeParameters}, we expand {@code _T_co} so that we end up with parameters {@code _T_co} and {@code _T_co#1}, {@code _T_co#2}, etc.
         * The reason is to not lose positional information. However, {@code _T_co} still is available and typed as the union of all {@code _T_co#n}
         * type parameters to support the upwards type hierarchy. The upwards type hierarchy is needed to support reduction of types that have
         * {@code Sequence} as a common superclass, e.g., when reducing {@code tuple[str, str]} and {@code list[str]}.
         */
        private static PyTypeChecker.GenericSubstitutions subtract(PyTypeChecker.GenericSubstitutions self,
                                                                   PyTypeChecker.GenericSubstitutions other) {
            PyClass commonSuperClass = other.getSelfType() instanceof PyClassType selfClassType ? selfClassType.getPyClass() : null;
            Predicate<PyTypeParameterType> belongsToSelfClass =
                typeParameter -> commonSuperClass != null && commonSuperClass.equals(typeParameter.getScopeOwner());
            Map<PyTypeVarType, @Nullable Ref<@Nullable PyType>> typeVars = new LinkedHashMap<>();
            self.getTypeVars().forEach((key, value) -> {
                if (!other.getTypeVars().containsKey(key) || belongsToSelfClass.test(key)) {
                    typeVars.put(key, value);
                }
            });
            Map<com.jetbrains.python.psi.types.PyTypeVarTupleType, com.jetbrains.python.psi.types.@Nullable PyPositionalVariadicType> typeVarTuples =
                new LinkedHashMap<>();
            self.getTypeVarTuples().forEach((key, value) -> {
                if (!other.getTypeVarTuples().containsKey(key) || belongsToSelfClass.test(key)) {
                    typeVarTuples.put(key, value);
                }
            });
            Map<PyParamSpecType, com.jetbrains.python.psi.types.@Nullable PyCallableParameterVariadicType> paramSpecs = new LinkedHashMap<>();
            self.getParamSpecs().forEach((key, value) -> {
                if (!other.getParamSpecs().containsKey(key) || belongsToSelfClass.test(key)) {
                    paramSpecs.put(key, value);
                }
            });
            return new PyTypeChecker.GenericSubstitutions(
                typeVars,
                typeVarTuples,
                paramSpecs,
                self.getSelfType()
            );
        }


        private static void reduceCompositeType(@Nullable PyType left,
                                                PyCompositeType right, // COMPOSED TYPE
                                                PyVariance variance,
                                                ConstraintProblem cp) {
            switch (variance) {
                case INVARIANT -> {
                    reduceCompositeType(left, right, PyVariance.COVARIANT, cp);
                    reduceCompositeType(left, right, PyVariance.CONTRAVARIANT, cp);
                }
                case COVARIANT -> {
                    if (right instanceof PyUnionType unionType) {
                        // semantics: disjunction of right with each union member
                        reduceDisjunction(left, unionType.getMembers(), variance, cp);
                    }
                    if (right instanceof PyIntersectionType intersectionType) {
                        // semantics: conjunction of right with each union member
                        for (PyType member : intersectionType.getMembers()) {
                            if (member != null) {
                                reduce(left, member, variance, cp);
                            }
                        }
                    }
                }
                case CONTRAVARIANT -> {
                    if (right instanceof PyUnionType unionType) {
                        // semantics: conjunction of right with each intersection member
                        for (PyType member : unionType.getMembers()) {
                            if (member != null) {
                                reduce(left, member, variance, cp);
                            }
                        }
                    }
                    if (right instanceof PyIntersectionType intersectionType) {
                        // semantics: disjunction of right with each intersection member
                        reduceDisjunction(left, intersectionType.getMembers(), variance, cp);
                    }
                }
                default -> throw UNREACHABLE();
            }
        }

        /**
         * Most tricky part. The goal is an implementation that works without back-tracking.
         * The problem this method solves is that the left type must be a subtype (wrt. invariance)
         * of one of the members of the right type. Depending on which right type member is used,
         * new bounds will be added to the constraint problem. If the resulting constraint problem
         * turns out to be unsolvable, there will be no back-tracking to try another type from
         * the given set of member types here. To decide which type is the most promising, this
         * method makes some assumptions taking structural properties into account.
         */
        private static void reduceDisjunction(@Nullable PyType left, Collection<@Nullable PyType> members, PyVariance variance, ConstraintProblem cp) {
            if (members.isEmpty()) {
                return;
            }
            if (members.size() == 1) {
                reduce(left, members.iterator().next(), variance, cp);
                return;
            }
            // if the left side is a ComposedType that would lead to a conjunction, we have to tear it apart
            // first to avoid incorrect results (for example, A|B <: [A, B, C] as a disjunction must be
            // handled as A <: [A, B, C] AND B <: [A, B, C], both as a disjunction), not as A|B <: X with
            // X being the choice of "most promising" option out of A, B, C).
            if ((variance == PyVariance.COVARIANT && left instanceof PyUnionType) ||
                (variance == PyVariance.CONTRAVARIANT && left instanceof PyIntersectionType)) {
                List<PyType> leftMembers = filterNotNull(((PyCompositeType) left).getMembers());
                for (PyType currLeft : leftMembers) {
                    reduceDisjunction(currLeft, members, variance, cp);
                }
                return;
            }

            // choose the "most promising" of the disjoint constraints and continue with that
            // (and simply ignore the other possible paths)
            List<@Nullable PyType> memberList = new ArrayList<>(members);
            int idx = -1;

            if (left instanceof PyCallableType) {
                TreeMap<Integer, Integer> bestMatchIdx = new TreeMap<>();
                for (int i = 0; i < memberList.size(); i++) {
                    PyType currRight = memberList.get(i);
                    PyType subType = variance == PyVariance.CONTRAVARIANT ? currRight : left;
                    PyType superType = variance == PyVariance.CONTRAVARIANT ? left : currRight;
                    boolean isRawSubtype = SubtypeJudgement.isRawSubtype(subType, superType, cp.context);
                    if (isRawSubtype) {
                        int currMatchCnt = computeMatchCount(left, memberList.get(i), variance, cp);
                        bestMatchIdx.put(currMatchCnt, i);
                    }
                }
                if (!bestMatchIdx.isEmpty()) {
                    idx = bestMatchIdx.lastEntry().getValue();
                }
            }
            if (left instanceof PyInferenceVariable) {
                // choose the first that fits the current bounds
                for (int i = 0; i < memberList.size(); i++) {
                    if (mightSufficeExistingBounds(left, memberList.get(i), variance, cp)) {
                        idx = i;
                        break;
                    }
                }
            }
            if (idx == -1) {
                // choose the first naked inference variable (if present)
                idx = indexOfFirst(memberList, it -> it instanceof PyInferenceVariable);
            }
            if (idx == -1 && variance == PyVariance.COVARIANT) {
                // choose the top type 'any' or one of the pseudo-top types: Object, N4Object
                idx = indexOfFirst(memberList, it -> isTopType(it, cp.context));
            }
            if (idx == -1 && variance == PyVariance.CONTRAVARIANT) {
                // choose the bottom type 'undefined' or one of the pseudo-bottom types: null
                idx = indexOfFirst(memberList, PyTypeInferenceCspSolverKt::isBottomType);
            }
            if (idx == -1) {
                // choose the first member type (yes, we're pretty desperate at this point)
                idx = 0;
            }
            reduce(left, memberList.get(idx), variance, cp);
        }

        private static int computeMatchCount(@Nullable PyType left, @Nullable PyType right, PyVariance variance, ConstraintProblem cp) {
            int matchCount = 1000;

            if (left instanceof PyClassType leftClass && right instanceof PyClassType rightClass && leftClass.getPyClass() == rightClass.getPyClass()) {
                matchCount += 1000;

                if (leftClass.isParameterized() && rightClass.isParameterized()) {
                    List<@Nullable PyType> lTArgs = leftClass.getTypeArguments();
                    List<@Nullable PyType> rTArgs = rightClass.getTypeArguments();
                    if (lTArgs.size() == rTArgs.size()) {
                        matchCount += 100;
                        for (int i = 0; i < lTArgs.size(); i++) {
                            if (mightSufficeExistingBounds(lTArgs.get(i), rTArgs.get(i), variance, cp)) {
                                matchCount += 10;
                            }
                        }
                    }
                }
                return matchCount;
            }

            PyType superType = variance == PyVariance.COVARIANT ? left : right;
            PyType subType = variance == PyVariance.COVARIANT ? right : left;
            if (superType instanceof PyClassLikeType superClassLike) {
                // choose the first supertype of left
                List<PyClassLikeType> lSTs = superClassLike.getAncestorTypes(cp.context);
                for (PyClassLikeType leftSuperType : lSTs) {
                    if (Objects.equals(leftSuperType, subType)) {
                        return matchCount;
                    }
                    matchCount--;
                }
            }
            if (left instanceof PyCallableType && right instanceof PyCallableType) {
                return 100;
            }
            if (left instanceof PyStructuralType && right instanceof PyStructuralType) {
                return 100;
            }
            return 0;
        }

        private static boolean mightSufficeExistingBounds(@Nullable PyType pLeft, @Nullable PyType pRight, PyVariance pVariance, ConstraintProblem cp) {
            PyType left = pLeft;
            PyType right = pRight;
            PyVariance variance = pVariance;
            if (!(left instanceof PyInferenceVariable)) {
                if (!(right instanceof PyInferenceVariable)) {
                    return false;
                }
                PyType tmp = left;
                left = right;
                right = tmp;
                variance = variance.not();
            }
            // left is InferenceVariable

            if (right instanceof PyType) {
                TypeBound newTypeBound = new TypeBound((PyInferenceVariable) left, right, variance, cp.context);
                List<TypeConstraint> newConstraints = TypeBoundCombiner.combineAll(newTypeBound, cp);
                for (TypeConstraint newConstraint : newConstraints) {
                    if (newConstraint.variance() == PyVariance.CONTRAVARIANT) {
                        if (!SubtypeJudgement.isRawSubtype(newConstraint.right(), newConstraint.left(), cp.context)) {
                            return false;
                        }
                    }
                    else if (newConstraint.variance() == PyVariance.COVARIANT) {
                        if (!SubtypeJudgement.isRawSubtype(newConstraint.left(), newConstraint.right(), cp.context)) {
                            return false;
                        }
                    }
                }
                return true;
            }
            return false;
        }
    }

    /**
     * This element is based on an EPL-1.0-licensed Java source by NumberFour AG (2016)
     * (@link https://github.com/eclipse-n4js/n4js/blob/master/plugins/org.eclipse.n4js/src/org/eclipse/n4js/typesystem/constraints/TypeBoundCombiner.java).
     * Ported to Kotlin; structure and semantics remain substantially the same.
     */
    static final class TypeBoundCombiner {
        private TypeBoundCombiner() {
        }

        /**
         * Side effect free, i.e., no bounds will be removed from this {@link ConstraintProblem} (as an optimization) when calling
         * this method.
         */
        static List<TypeConstraint> combineAll(TypeBound newBound, ConstraintProblem cp) {
            List<TypeConstraint> result = new ArrayList<>();
            Set<TypeBound> bounds = cp.boundsPerInfVar.get(newBound.left());
            for (TypeBound tBound : bounds) {
                TypeConstraint newConstraint = combine(newBound, tBound, null, cp.context);
                if (newConstraint != null) {
                    result.add(newConstraint);
                }
            }
            return result;
        }

        /**
         * In terms of JLS8, this method embodies the implication rules listed in Sec. 18.3.1 (the other implication rules
         * in JLS8 take as input capture conversion constraints).
         */
        static @Nullable TypeConstraint combine(TypeBound boundI, TypeBound boundJ, @Nullable ConstraintProblem cp, TypeEvalContext context) {
            switch (boundI.variance()) {
                case INVARIANT -> {
                    return switch (boundJ.variance()) {
                        case INVARIANT -> combineInvInv(boundI, boundJ, cp, context);
                        case COVARIANT, CONTRAVARIANT -> combineInvVar(boundI, boundJ, cp, context);
                        default -> throw UNREACHABLE();
                    };
                }
                case COVARIANT -> {
                    return switch (boundJ.variance()) {
                        case INVARIANT -> combineInvVar(boundJ, boundI, cp, context); // note: reversed arguments!
                        case CONTRAVARIANT -> combineContraCo(boundJ, boundI); // note: reversed arguments!
                        case COVARIANT -> combineBothCoOrBothContra(boundI, boundJ);
                        default -> throw UNREACHABLE();
                    };
                }
                case CONTRAVARIANT -> {
                    return switch (boundJ.variance()) {
                        case INVARIANT -> combineInvVar(boundJ, boundI, cp, context); // note: reversed arguments!
                        case COVARIANT -> combineContraCo(boundI, boundJ);
                        case CONTRAVARIANT -> combineBothCoOrBothContra(boundI, boundJ);
                        default -> throw UNREACHABLE();
                    };
                }
                default -> throw UNREACHABLE();
            }
        }

        /**
         * Case: both bounds are equalities.
         */
        private static @Nullable TypeConstraint combineInvInv(TypeBound boundS, TypeBound boundT, @Nullable ConstraintProblem cp, TypeEvalContext context) {
            if (boundS.left() == boundT.left()) {
                // `α = S` and `α = T` implies `S = T`
                return new TypeConstraint(boundS.right(), boundT.right(), PyVariance.INVARIANT);
            }
            // inference variables are different
            // -> try to substitute a proper RHS in the RHS of the other bound, to make it a proper type itself
            TypeConstraint newConstraint = combineInvInvWithProperType(boundS, boundT, cp, context);
            if (newConstraint != null) {
                return newConstraint;
            }
            newConstraint = combineInvInvWithProperType(boundT, boundS, cp, context);
            if (newConstraint != null) {
                return newConstraint;
            }
            return null;
        }

        /**
         * Given two type bounds {@code α = U} and {@code β = T} with α ≠ β, will return a new constraint {@code β = T[α:=U]} if
         * <ul>
         *  <li>U is proper, and</li>
         *  <li>T mentions α.</li>
         * </ul>
         * Otherwise, {@code null} is returned.
         */
        private static @Nullable TypeConstraint combineInvInvWithProperType(TypeBound boundWithProperRHS,
                                                                            TypeBound boundOther,
                                                                            @Nullable ConstraintProblem cp,
                                                                            TypeEvalContext context) {

            PyInferenceVariable alpha = boundWithProperRHS.left();
            PyType typeU = boundWithProperRHS.right();
            PyType typeT = boundOther.right();
            if (isProper(typeU, context) && boundOther.referencedInfVars().contains(alpha)) {
                PyInferenceVariable beta = boundOther.left();
                PyType substT = substituteInferenceVariable(typeT, alpha, typeU, context); // returns T[α:=U]
                if (cp != null) {
                    cp.removeBound(boundOther);
                }
                return new TypeConstraint(beta, substT, PyVariance.INVARIANT);
            }
            return null;
        }

        /**
         * Case: first bound is an equality, while the second isn't: {@code α = S} and {@code β Φ T} with Φ either {@code <:} or {@code :>}.
         */
        private static @Nullable TypeConstraint combineInvVar(TypeBound boundS, TypeBound boundT, @Nullable ConstraintProblem cp, TypeEvalContext context) {
            PyInferenceVariable alpha = boundS.left();
            PyInferenceVariable beta = boundT.left();
            PyType typeS = boundS.right();
            PyType typeT = boundT.right();
            PyVariance phi = boundT.variance();
            if (alpha == beta) {
                // (1) `α = S` and `α Φ T` implies `S Φ T`
                return new TypeConstraint(typeS, typeT, phi);
            }
            // both bounds have different inference variables, i.e., α != β
            if (alpha == typeT) {
                // (2) `α = S` and `β Φ α` implies `β Φ S`
                return new TypeConstraint(beta, typeS, phi);
            }
            if (typeS instanceof PyInferenceVariable gamma) {
                // the first bound is of the form `α = γ` (with γ being another inference variable)
                if (gamma == beta) {
                    // (3) `α = β` and `β Φ T` implies `α Φ T`
                    return new TypeConstraint(alpha, typeT, phi);
                }
                if (gamma == typeT) {
                    // (4) `α = γ` and `β Φ γ` implies `β Φ α`
                    return new TypeConstraint(beta, alpha, phi);
                }
            }
            // so, S is not an inference variable
            if (isProper(typeS, context) && boundT.referencedInfVars().contains(alpha)) {
                // (5) `α = S` (where S is proper) and `β Φ T` implies `β Φ T[α:=U]`
                PyType substT = substituteInferenceVariable(typeT, alpha, typeS, context); // returns T[α:=U]
                if (cp != null) {
                    cp.removeBound(boundT);
                }
                return new TypeConstraint(beta, substT, phi);
            }
            return null;
        }

        /**
         * Case: {@code α :> S} and {@code β <: T}.
         */
        private static @Nullable TypeConstraint combineContraCo(TypeBound boundS, TypeBound boundT) {
            PyInferenceVariable alpha = boundS.left();
            PyInferenceVariable beta = boundT.left();
            PyType typeS = boundS.right();
            PyType typeT = boundT.right();
            if (alpha == beta) {
                // transitivity, using LHS as bridge:
                // α :> S and α <: T implies S <: T
                return new TypeConstraint(typeS, typeT, PyVariance.COVARIANT);
            }
            // so, α and β are different
            if (typeS instanceof PyInferenceVariable gamma) {
                if (gamma == typeT) {
                    // transitivity, using RHS as bridge:
                    // α :> γ and β <: γ implies α :> β
                    return new TypeConstraint(alpha, beta, PyVariance.CONTRAVARIANT);
                }
            }
            return null;
        }

        /**
         * Case: inequalities of the same direction, i.e. {@code α <: S ^ β <: T}, or {@code α :> S ^ β :> T}.
         */
        private static @Nullable TypeConstraint combineBothCoOrBothContra(TypeBound boundS, TypeBound boundT) {
            PyInferenceVariable alpha = boundS.left();
            PyInferenceVariable beta = boundT.left();
            PyType typeS = boundS.right();
            PyType typeT = boundT.right();
            if (alpha == typeT) {
                // α <: S and β <: α implies β <: S
                // α :> S and β :> α implies β :> S
                return new TypeConstraint(beta, typeS, boundS.variance());
            }
            if (typeS == beta) {
                // α <: β and β <: T implies α <: T
                // α :> β and β :> T implies α :> T
                return new TypeConstraint(alpha, typeT, boundS.variance());
            }
            return null;
        }
    }


    static final class TypeBoundIncorporator {
        private TypeBoundIncorporator() {
        }

        /**
         * Incremental incorporation:
         * - Apply newly discovered instantiations only to bounds that reference them
         * - Combine only pairs that are "relevant" to newly added bounds
         * - Never do full quadratic passes unless the problem genuinely introduces O(B^2) unique pairs
         */
        static void incorporate(ConstraintProblem cp, TypeEvalContext context) {
            if (cp.failed) {
                return;
            }

            // Process until fixpoint: queues become empty.
            while (!cp.failed && (!cp.pendingInstantiations.isEmpty() || !cp.pendingBounds.isEmpty())) {
                // 1) Apply new instantiations to affected bounds (targeted, not global)
                processPendingInstantiations(cp, context);
                if (cp.failed) {
                    return;
                }

                // 2) Incorporate newly added bounds (incremental pairwise combination)
                processPendingBounds(cp, context);
            }
        }

        /**
         * Substitute newly instantiated inference variables into bounds that mention them.
         */
        private static void processPendingInstantiations(ConstraintProblem cp, TypeEvalContext context) {
            while (!cp.failed && !cp.pendingInstantiations.isEmpty()) {
                PyInferenceVariable instantiatedVar = cp.pendingInstantiations.removeFirst();
                if (!cp.instantiations.containsKey(instantiatedVar)) {
                    continue;
                }

                // Copy because we'll remove bounds while iterating.
                List<TypeBound> impactedBounds = new ArrayList<>(cp.boundsReferencingInfVar.get(instantiatedVar));

                for (TypeBound bound : impactedBounds) {
                    if (cp.failed) {
                        return;
                    }
                    // Bound might have been removed already by another substitution
                    if (!cp.bounds.contains(bound)) {
                        continue;
                    }

                    // Substitute *all currently instantiated vars* mentioned by this bound, not just the one we popped.
                    PyType newRight = bound.right();
                    for (PyInferenceVariable ref : bound.referencedInfVars()) {
                        PyType refInst = cp.instantiations.get(ref);
                        if (refInst == null) {
                            continue;
                        }
                        PyType refInstWidened = widenOrKeepBounds(ref.getTypeVariable(), refInst, context);
                        newRight = substituteInferenceVariable(newRight, ref, refInstWidened, context);
                    }

                    // If nothing changed, skip.
                    if (newRight == bound.right()) {
                        continue;
                    }

                    // Remove the old bound and re-reduce as a constraint.
                    cp.removeBound(bound);
                    ConstraintReducer.reduce(new TypeConstraint(bound.left(), newRight, bound.variance()), cp);
                }
            }
        }

        /**
         * For each newly added bound b, combine it only with "relevant" bounds and reduce any implied constraints.
         * Each unordered pair is combined at most once globally (seenBoundPairs).
         */
        private static void processPendingBounds(ConstraintProblem cp, TypeEvalContext context) {
            while (!cp.failed && !cp.pendingBounds.isEmpty()) {
                TypeBound boundI = cp.pendingBounds.removeFirst();
                if (!cp.bounds.contains(boundI)) {
                    continue; // stale entry
                }

                int idI = cp.idOf(boundI);

                // Build a small candidate set rather than "all bounds":
                // - bounds with the same LHS (common case)
                // - bounds whose LHS is referenced by RHS of I
                // - bounds that reference I.left on their RHS (enables bridge rules)
                LinkedHashSet<TypeBound> candidates = new LinkedHashSet<>();

                candidates.addAll(cp.boundsPerInfVar.get(boundI.left()));

                for (PyInferenceVariable ref : boundI.referencedInfVars()) {
                    candidates.addAll(cp.boundsPerInfVar.get(ref));
                }

                candidates.addAll(cp.boundsReferencingInfVar.get(boundI.left()));

                // Combine boundI with each candidate boundJ exactly once.
                for (TypeBound boundJ : candidates) {
                    if (cp.failed) {
                        return;
                    }
                    if (boundJ == boundI) {
                        continue;
                    }
                    if (!cp.bounds.contains(boundJ)) {
                        continue;
                    }

                    int idJ = cp.idOf(boundJ);

                    long pairKey = pairKey(idI, idJ);
                    if (!cp.seenBoundPairs.add(pairKey)) {
                        continue; // already processed
                    }

                    TypeConstraint newConstraint = TypeBoundCombiner.combine(boundI, boundJ, cp, context);
                    if (newConstraint != null) {
                        ConstraintReducer.reduce(newConstraint, cp);
                    }
                }
            }
        }

        /**
         * Pack an unordered pair of 32-bit ids into a single Long key.
         */
        private static long pairKey(int a, int b) {
            int lo = Math.min(a, b);
            int hi = Math.max(a, b);
            return (((long) hi) << 32) | (((long) lo) & 0xFFFF_FFFFL);
        }
    }


    /**
     * This element is based on an EPL-1.0-licensed Java source by NumberFour AG (2016)
     * (@link https://github.com/eclipse-n4js/n4js/blob/master/plugins/org.eclipse.n4js/src/org/eclipse/n4js/typesystem/constraints/InferenceContext.java).
     * Ported to Kotlin; structure and semantics remain substantially the same.
     */
    static final class TypeBoundResolver {
        private TypeBoundResolver() {
        }

        /**
         * Performs resolution of all inference variables of the receiving inference context. This might trigger further
         * reduction and incorporation steps.
         * <p>
         * If a solution is found, {@link ConstraintProblem#bounds} will contain instantiations for all inference variables,
         * and {@link ConstraintProblem#solution} will contain instantiations for all inference variables. Otherwise, {@link ConstraintProblem#bounds}
         * will be in an undefined state, and {@link ConstraintProblem#solution} will be {@code null}. In both cases,
         * {@link ConstraintProblem#preResolutionBounds} will contain the state of {@link ConstraintProblem#bounds} right before resolution started.
         */
        static void resolve(ConstraintProblem cp, TypeEvalContext context) {
            cp.savePreResolutionTypeBounds();
            if (cp.failed) {
                return;
            }
            while (true) {
                Set<PyInferenceVariable> currVariableSet = getSmallestVariableSet(cp, context);
                if (currVariableSet == null) {
                    break;
                }

                for (PyInferenceVariable currVariable : currVariableSet) {
                    PyType instantiation = chooseInstantiation(cp, currVariable, context);
                    instantiate(cp, currVariable, instantiation, context);
                    TypeBoundIncorporator.incorporate(cp, context);
                    if (cp.failed) {
                        return;
                    }
                }
            }
            cp.saveSolution();
        }

        /**
         * Given a set {@code infVars} of inference variables of the receiving inference context, this method returns
         * the smallest, non-empty set S such that:
         * <ul>
         *  <li>all α ∈ S are uninstantiated inference variables of the receiving inference context, and</li>
         *  <li>one of the elements in S is also in {@code infVars}: ∃ α ∈ S: α ∈ {@code infVars}, and</li>
         *  <li>if an α ∈ S depends on the resolution of another variable β, then either β is instantiated or β ∈ S, and</li>
         *  <li>there exists no non-empty proper subset of S with this property.</li>
         * </ul>
         * Returns {@code null} if no such set exists, e.g., because {@code infVars} was empty or did not contain
         * any uninstantiated inference variables.
         * <p>
         * The returned set, if non-null, is guaranteed to be non-empty.
         */
        private static @Nullable Set<PyInferenceVariable> getSmallestVariableSet(ConstraintProblem cp, TypeEvalContext context) {
            Set<PyInferenceVariable> result = null;
            Set<PyInferenceVariable> deferred = new HashSet<>();
            int min = Integer.MAX_VALUE;
            Collection<PyInferenceVariable> infVars = cp.inferenceVars.values();
            for (PyInferenceVariable currentVariable : infVars) {
                if (cp.isInstantiated(currentVariable)) {
                    continue;
                }
                // Defer an unbounded currentVariable IFF all other iv that depend on currentVariable have
                // proper bounds AND only proper bounds.
                //
                // For example,
                // α :> β
                // α :> A
                // Because α depends on β, we would first instantiate β (to any) and then choose α=intersection[A, Any].
                // With the following code, we defer processing of β until α has been instantiated to A.

                if (!cp.isBounded(currentVariable)) {
                    boolean gotAtLeastOneDependantIV = infVars.stream().anyMatch(iv -> cp.dependsOnResolutionOf(iv, currentVariable));
                    if (gotAtLeastOneDependantIV) {
                        boolean defer = infVars.stream()
                            .filter(iv -> cp.dependsOnResolutionOf(iv, currentVariable))
                            .allMatch(iv -> {
                                List<TypeBound> bs = new ArrayList<>();
                                for (TypeBound b : cp.boundsPerInfVar.get(iv)) {
                                    if (!(b.left() == iv && b.right() == currentVariable)) {
                                        bs.add(b);
                                    }
                                }
                                return !bs.isEmpty() && bs.stream().allMatch(b -> isProper(b.right(), context));
                            });
                        if (defer) {
                            deferred.add(currentVariable);
                            continue;
                        }
                    }
                }

                Set<PyInferenceVariable> set = new LinkedHashSet<>();
                if (addDependencies(cp, currentVariable, min, set, context)) {
                    int curr = set.size();
                    if (curr == 1) {
                        return set; // 'set' contains only currentVariable -> no need to remove deferred variables
                    }
                    if (curr < min) {
                        result = set;
                        min = curr;
                    }
                }
            }
            if (result == null || result.isEmpty()) {
                return null;
            }

            // deferred variables may have been added via #addDependencies() above, so remove them
            // note: because deferred variables can only end up in 'result' via #addDependencies(), we can safely
            // remove all deferred variables and be sure that 'result' won't be empty afterward
            result.removeAll(deferred);

            return result;
        }

        /**
         * <b>IMPORTANT:</b> Given inference variable {@code infVar} must be uninstantiated; this will <i>not</i> be checked
         * (again) by this method!
         * <p>
         * For the given, uninstantiated inference variable {@code infVar}, this method will add to the given
         * answer-set {@code addHere} the transitive closure of all uninstantiated inference variables of the receiving
         * inference context on which {@code infVar} depends directly or indirectly.
         * <p>
         * In addition, a {@code limit} for the answer-set's size is supplied, which means the collection of
         * dependencies will be interrupted immediately when {@code addHere}'s size reaches the given limit.
         * <p>
         * Returns {@code true} on success or {@code false} if the collection of dependencies was interrupted due to
         * reaching the {@code limit}. In the latter case, the answer-set will be in an invalid state (contains a
         * random subset of the transitive closure of dependencies) and should be discarded.
         */
        private static boolean addDependencies(ConstraintProblem cp,
                                               PyInferenceVariable infVar,
                                               int limit,
                                               Set<PyInferenceVariable> addHere,
                                               TypeEvalContext context) {

            if (addHere.size() >= limit) {
                // the candidate answer-set is already not better than the best-yet answer-set (i.e., larger or same size)
                return false;
            }
            // add infVar
            if (!addHere.add(infVar)) {
                // candidate variable was already visited for this answer-set; we assume also its dependencies have already
                // been added -> so nothing else to be done here
                return true;
            }
            // for all (uninstantiated) variables on which the current one depends, recurse
            for (TypeBound bound : cp.boundsPerInfVar.get(infVar)) {
                for (PyInferenceVariable dependencyInfVar : bound.referencedInfVars()) {
                    if (!(cp.isInstantiated(dependencyInfVar))) {
                        if (!addDependencies(cp, dependencyInfVar, limit, addHere, context)) {
                            // a single uninstantiated variable too many (that currentVariable depends on) causes the current
                            // answer-set to be discarded
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        /**
         * Add bound {@code variable = proper} and perform reduction (which might trigger incorporation).
         */
        private static void instantiate(ConstraintProblem cp, PyInferenceVariable infVar, @Nullable PyType proper, TypeEvalContext context) {
            assert !(cp.isInstantiated(infVar)) : "attempt to re-instantiate var " + infVar.getName();
            assert isProper(proper, context);
            // add bound `infVar = proper`
            ConstraintReducer.reduce(new TypeConstraint(infVar, proper, PyVariance.INVARIANT), cp);
            // check if 'proper' was accepted by BoundSet 'currentBounds' as an instantiation/solution of 'infVar'
            if (!cp.isInstantiated(infVar)) {
                // No! This should never happen except if 'proper' is not a proper type (and the above assertions are turned off).
                // OR except if 'proper' is a raw type, which is invalid but may occur when dealing with corrupted data (broken AST, etc.).
                // Since 'proper' was intended as an instantiation/solution when this method is called, not having it
                // accepted is a problem, and we should give up on this constraint system:
                cp.fail();
            }
        }

        /**
         * Returns a valid solution for the given inference variable <b>without</b> taking into account any dependencies it
         * might have on other inference variables. I.e., this method will consider only {@link TypeBound}s where the LHS is
         * the given inference variable and the RHS is a proper type.
         * <p>
         * Background: this method is safe to use even for an inference variable α with a dependency on another inference
         * variable β iff β has already been instantiated and its instantiation has been properly reduced and incorporated,
         * because during reduction/incorporation a type bound α &lt;: P or α &gt;: P or α = P (with P being a proper type)
         * will have been created that reflects the impact of the instantiation of β on α (without mentioning β).
         */
        private static @Nullable PyType chooseInstantiation(ConstraintProblem cp, PyInferenceVariable infVar, TypeEvalContext context) {
            PyType[] concreteBounds = collectProperBounds(cp, infVar, context, b -> b.variance() == PyVariance.INVARIANT);
            if (concreteBounds.length == 1) {
                return concreteBounds[0];
            }
            else if (concreteBounds.length > 1) {
                cp.fail();
            }

            PyTypeVarType typeVariable = infVar.getTypeVariable();
            if (cp.inferenceVars.isUnbound(infVar)) {
                Ref<? extends PyType> defaultType = typeVariable.getDefaultType();
                if (defaultType != null) {
                    return defaultType.get();
                }
                return typeVariable;
            }

            PyType[] lowerBounds =
                collectProperBounds(cp, infVar, context, b -> b.variance() == PyVariance.CONTRAVARIANT && !PyTypeUtilKt.isUnknown(b.right()));
            PyType[] upperBounds =
                collectProperBounds(cp, infVar, context, b -> b.variance() == PyVariance.COVARIANT && !isTopType(b.right(), context));

            // All cases are declared explicitly for systematic reasons
            if (lowerBounds.length == 0 && upperBounds.length == 0) {
                // neither lower nor upper bounds found -> typeVar is unconstrained
                Ref<? extends PyType> defaultType = typeVariable.getDefaultType();
                if (defaultType != null) {
                    return defaultType.get();
                }
                return PyAnyType.getUnknown();
            }
            else if (lowerBounds.length != 0 && upperBounds.length == 0) {
                List<@Nullable PyType> lowerBoundsWidened = widenOrKeepBounds(typeVariable, lowerBounds, cp.context);
                return PyUnionType.unionOrUnknown(lowerBoundsWidened);
            }
            else if (lowerBounds.length == 0 && upperBounds.length != 0) {
                if (upperBounds.length == 1 && typeVariable.getBound() == null && Objects.equals(upperBounds[0], typeVariable.getBound())) {
                    // special case: the type variable is constrained only by its bound (i.e., `[T: int]`).
                    // Therefore, we treat this type variable as unconstrained and use its bound when necessary based on `#keepUnconstrained`.
                    return typeVariable;
                }

                // It is debatable whether we should just return PyIntersectionType.intersectionOrTop(*upperBounds)
                // Note, however, that intersection types are not part of Python (as of 2026).
                // Hence, the following logic makes it mandatory that the user declares a common subtype at some point.
                Ref<@Nullable PyType> commonSubtype = findCommonSubtype(context, upperBounds);
                if (commonSubtype == null) {
                    cp.fail();
                    return PyNeverType.NEVER;
                }
                else {
                    return commonSubtype.get();
                }
            }
            else {
                if (typeVariable.getConstraints().isEmpty()) {
                    List<@Nullable PyType> lowerBoundsWidened = widenOrKeepBounds(typeVariable, lowerBounds, cp.context);
                    List<@Nullable PyType> mergedBounds = new ArrayList<>();
                    for (PyType lowerBound : lowerBoundsWidened) {
                        if (isSubtypeOfAll(context, lowerBound, upperBounds)) {
                            mergedBounds.add(lowerBound);
                        }
                    }
                    if (mergedBounds.isEmpty() && upperBounds.length != 0) {
                        return PyUnionType.unionOrUnknown(Arrays.asList(upperBounds));
                    }
                    return PyUnionType.unionOrUnknown(mergedBounds);
                }
                else {
                    List<@Nullable PyType> validConstraintMembers = new ArrayList<>();
                    for (PyType tvConstraint : typeVariable.getConstraints()) {
                        if (isSubtypeOfAll(context, tvConstraint, upperBounds) && isSupertypeOfAll(context, tvConstraint, lowerBounds)) {
                            validConstraintMembers.add(tvConstraint);
                        }
                    }
                    if (validConstraintMembers.isEmpty()) {
                        cp.fail();
                        return PyNeverType.NEVER;
                    }
                    if (typeVariable.getConstraints().equals(validConstraintMembers)) {
                        return typeVariable;
                    }
                    if (validConstraintMembers.size() > 1) {
                        // we found multiple solutions. Use bounds as indicators for the chosen member type(s).
                        Set<@Nullable PyType> membersIndicatedByLBs = new LinkedHashSet<>(validConstraintMembers);
                        membersIndicatedByLBs.retainAll(new HashSet<>(Arrays.asList(lowerBounds)));
                        Set<@Nullable PyType> membersIndicatedByUBs = new LinkedHashSet<>(validConstraintMembers);
                        membersIndicatedByUBs.retainAll(new HashSet<>(Arrays.asList(upperBounds)));
                        Set<@Nullable PyType> membersIndicatedByABs = new LinkedHashSet<>(membersIndicatedByLBs);
                        membersIndicatedByABs.addAll(membersIndicatedByUBs);
                        if (!membersIndicatedByABs.isEmpty()) {
                            return PyUnionType.unionOrUnknown(membersIndicatedByABs);
                        }
                    }
                    return PyUnionType.unionOrUnknown(validConstraintMembers);
                }
            }
        }

        private static PyType[] collectProperBounds(ConstraintProblem cp,
                                                    PyInferenceVariable infVar,
                                                    TypeEvalContext context,
                                                    Predicate<TypeBound> predicate) {

            Set<TypeBound> infVarBounds = cp.boundsPerInfVar.get(infVar);
            List<@Nullable PyType> result = new ArrayList<>();
            for (TypeBound bound : infVarBounds) {
                if (!predicate.test(bound)) {
                    continue;
                }
                PyType boundType = bound.right();
                // It may happen that inference variables depend on each other, hence create a cycle
                // In this case it is necessary to substitute these inference variables by Any type
                PyType substitutedBoundType = substituteInferenceVariablesBy(boundType, context);
                result.add(substitutedBoundType);
            }
            return result.toArray(new PyType[0]);
        }

        private static boolean isSubtypeOfAll(TypeEvalContext context, @Nullable PyType left, @Nullable PyType... rights) {
            return Arrays.stream(rights).allMatch(right -> SubtypeJudgement.isSubtype(left, right, context));
        }

        private static boolean isSupertypeOfAll(TypeEvalContext context, @Nullable PyType left, @Nullable PyType... rights) {
            return Arrays.stream(rights).allMatch(right -> SubtypeJudgement.isSubtype(right, left, context));
        }

        /**
         * Returns null iff there is no common supertype
         */
        static @Nullable Ref<@Nullable PyType> findCommonSubtype(TypeEvalContext context, @Nullable PyType... types) {
            if (types.length == 0) {
                return null;
            }
            if (types.length == 1) {
                return Ref.create(types[0]);
            }

            List<@Nullable PyType> nonAnyTypes = new ArrayList<>();
            for (PyType type : types) {
                if (!(type instanceof PyAnyType)) {
                    nonAnyTypes.add(type);
                }
            }
            var it = nonAnyTypes.iterator();
            PyType candidate = it.next();
            while (it.hasNext()) {
                PyType type = it.next();
                if (type == candidate) {
                    continue;
                }

                if (SubtypeJudgement.isSubtype(candidate, type, context)) {
                    // the candidate is ok
                }
                else if (SubtypeJudgement.isSubtype(type, candidate, context)) {
                    candidate = type;
                }
                else {
                    // Incomparable candidate. We might fail verification and then fall back.
                }
            }

            // Verify that the candidate is a supertype of all inputs.
            for (PyType type : types) {
                if (!SubtypeJudgement.isSubtype(candidate, type, context)) {
                    return null;
                }
            }
            return Ref.create(candidate);
        }
    }

    static final class SubtypeJudgement {
        private SubtypeJudgement() {
        }

        /**
         * True iff left is a subtype of right. Inference variables, type variables, and alike are replaced by Any
         */
        static boolean isRawSubtype(@Nullable PyType left, @Nullable PyType right, TypeEvalContext context) {
            // Replace [InferenceVariable]s by their type variables
            ReversibleSubstitutionData rawLRS = getReversibleSubstitutionData(left, right, context);
            return PyTypeChecker.match(rawLRS.rightNoIVs, rawLRS.leftNoIVs, context, new PyTypeChecker.GenericSubstitutions());
        }

        static final class ReversibleSubstitutionData {
            final @Nullable PyType leftNoIVs;
            final @Nullable PyType rightNoIVs;
            final PyTypeChecker.GenericSubstitutions leftBackSubstitutions;
            final PyTypeChecker.GenericSubstitutions rightBackSubstitutions;

            ReversibleSubstitutionData(@Nullable PyType leftNoIVs,
                                       @Nullable PyType rightNoIVs,
                                       PyTypeChecker.GenericSubstitutions leftBackSubstitutions,
                                       PyTypeChecker.GenericSubstitutions rightBackSubstitutions) {
                this.leftNoIVs = leftNoIVs;
                this.rightNoIVs = rightNoIVs;
                this.leftBackSubstitutions = leftBackSubstitutions;
                this.rightBackSubstitutions = rightBackSubstitutions;
            }
        }

        static ReversibleSubstitutionData getReversibleSubstitutionData(@Nullable PyType left, @Nullable PyType right, TypeEvalContext context) {
            Map<PyTypeParameterType, @Nullable PyType> leftBackSubstitutions = new HashMap<>();
            Map<PyTypeParameterType, @Nullable PyType> rightBackSubstitutions = new HashMap<>();
            PyType leftNoIVs = toRawType(left, leftBackSubstitutions, context);
            PyType rightNoIVs = toRawType(right, rightBackSubstitutions, context);

            return new ReversibleSubstitutionData(leftNoIVs, rightNoIVs, new PyTypeChecker.GenericSubstitutions(leftBackSubstitutions),
                                                  new PyTypeChecker.GenericSubstitutions(rightBackSubstitutions));
        }

        /**
         * Returns a raw version of {@code type} and records in {@code backSubstitutions} how to get back to the original type:
         * - For class types, the generic definition type is returned, and its type parameters are mapped back to the original type arguments.
         * - For callable types, all inference variables are replaced by their type variables, and these type variables are mapped back
         * to the original inference variables.
         * <p>
         * Note: {@link PyClassType} is a {@link PyCallableType}, hence the class type case must be checked first.
         */
        private static @Nullable PyType toRawType(@Nullable PyType type,
                                                  Map<PyTypeParameterType, @Nullable PyType> backSubstitutions,
                                                  TypeEvalContext context) {
            if (type instanceof PyClassType classType) {
                PyClassType genericDefinitionType =
                    PyTypeChecker.findGenericDefinitionType(classType.getPyClass(), classType.isDefinition(), context);
                PyClassType rawType = genericDefinitionType != null ? genericDefinitionType : classType;
                PyTypeParameterMapping mapping = PyTypeParameterMapping.mapByShape(rawType.getTypeArguments(), classType.getTypeArguments());
                if (mapping != null) {
                    for (Couple<PyType> pair : mapping.getMappedTypes()) {
                        PyType typeVar = pair.getFirst();
                        if (typeVar instanceof PyTypeParameterType typeParameterType) {
                            backSubstitutions.put(typeParameterType, pair.getSecond());
                        }
                    }
                }
                return rawType;
            }
            if (type instanceof PyCallableType callableType) {
                return replaceInferenceVariablesByTypeVariables(callableType, backSubstitutions, context);
            }
            return type;
        }

        /**
         * Replaces all inference variables in {@code type} by their type variables and records the reverse mapping in {@code backSubstitutions}.
         * Returns {@code type} itself if it does not reference any inference variables (keeps, e.g., {@link com.jetbrains.python.psi.types.PyFunctionType} instances intact).
         */
        private static @Nullable PyType replaceInferenceVariablesByTypeVariables(PyType type,
                                                                                 Map<PyTypeParameterType, @Nullable PyType> backSubstitutions,
                                                                                 TypeEvalContext context) {
            if (isProper(type, context)) {
                return type;
            }

            return PyCloningTypeVisitor.clone(type, new PyCloningTypeVisitor(context) {
                @Override
                public @Nullable PyType visitPyType(PyType type) {
                    if (type instanceof PyInferenceVariable inferenceVariable) {
                        backSubstitutions.putIfAbsent(inferenceVariable.getTypeVariable(), inferenceVariable);
                        return inferenceVariable.getTypeVariable();
                    }
                    return super.visitPyType(type);
                }
            });
        }

        /**
         * Returns true iff typeA &lt;: typeB and typeB &lt;: typeA.
         */
        static boolean sameTypes(@Nullable PyType typeA, @Nullable PyType typeB, TypeEvalContext context) {
            return isSubtype(typeA, typeB, context) && isSubtype(typeB, typeA, context);
        }

        /**
         * True iff left is a subtype of right
         */
        static boolean isSubtype(@Nullable PyType left, @Nullable PyType right, TypeEvalContext context) {
            return PyTypeChecker.match(right, left, context);
        }
    }

    /**
     * Returns a copy of {@code typeRef} in which {@code infVar} is substituted by {@code typeArg} or {@code typeRef}
     * itself if no change has occurred.
     *
     * @return typeRef[infVar:=typeArg]
     */
    private static @Nullable PyType substituteInferenceVariable(@Nullable PyType typeRef,
                                                                PyInferenceVariable infVar,
                                                                @Nullable PyType typeArg,
                                                                TypeEvalContext context) {
        if (typeRef == null || typeRef.equals(typeArg)) {
            return typeRef;
        }

        // Fast path: if the type doesn't reference the inference variable, return as is
        boolean[] referencesInfVar = {false};
        PyRecursiveTypeVisitor.traverse(typeRef, context, new PyRecursiveTypeVisitor.PyTypeTraverser() {
            @Override
            public PyRecursiveTypeVisitor.Traversal visitPyType(PyType type) {
                if (type.equals(infVar)) {
                    referencesInfVar[0] = true;
                    return PyRecursiveTypeVisitor.Traversal.TERMINATE;
                }
                return PyRecursiveTypeVisitor.Traversal.CONTINUE;
            }
        });
        if (!referencesInfVar[0]) {
            return typeRef;
        }

        return PyCloningTypeVisitor.clone(typeRef, new PyCloningTypeVisitor(context) {
            @Override
            public @Nullable PyType visitPyType(PyType type) {
                if (type.equals(infVar)) {
                    return typeArg;
                }
                return super.visitPyType(type);
            }
        });
    }

    private static @Nullable PyType substituteInferenceVariablesBy(@Nullable PyType typeRef, TypeEvalContext context) {
        if (PyTypeUtilKt.isUnknown(typeRef)) {
            return typeRef;
        }

        // Fast path: if the type doesn't reference the inference variable, return as is
        boolean[] referencesInfVar = {false};
        PyRecursiveTypeVisitor.traverse(typeRef, context, new PyRecursiveTypeVisitor.PyTypeTraverser() {
            @Override
            public PyRecursiveTypeVisitor.Traversal visitPyType(PyType type) {
                if (type instanceof PyInferenceVariable) {
                    referencesInfVar[0] = true;
                    return PyRecursiveTypeVisitor.Traversal.TERMINATE;
                }
                return PyRecursiveTypeVisitor.Traversal.CONTINUE;
            }
        });
        if (!referencesInfVar[0]) {
            return typeRef;
        }

        return PyCloningTypeVisitor.clone(typeRef, new PyCloningTypeVisitor(context) {
            @Override
            public @Nullable PyType visitPyType(PyType type) {
                if (type instanceof PyInferenceVariable inferenceVariable) {
                    return PyTypeUtil.derefOrUnknown(inferenceVariable.getTypeVariable().getDefaultType());
                }
                return super.visitPyType(type);
            }
        });
    }

    private static boolean isProper(@Nullable PyType type, TypeEvalContext context) {
        return collectInferenceVariables(type, context).isEmpty();
    }

    private static boolean isStructural(@Nullable PyType type, TypeEvalContext context) {
        return type instanceof PyClassType classType && isProtocol(classType, context);
    }

    static Set<PyInferenceVariable> collectInferenceVariables(@Nullable PyType root, TypeEvalContext context) {
        return collectInferenceVariables(root, context, Integer.MAX_VALUE);
    }

    /**
     * Collects all inference variables in {@code root} up to {@code maxSize}. Use {@code maxSize} for early termination and performance optimization.
     */
    static Set<PyInferenceVariable> collectInferenceVariables(@Nullable PyType root, TypeEvalContext context, int maxSize) {
        Set<PyInferenceVariable> result = new HashSet<>();
        if (root instanceof PyInferenceVariable inferenceVariable) {
            result.add(inferenceVariable);
        }
        else {
            PyRecursiveTypeVisitor.traverse(root, context, new PyRecursiveTypeVisitor.PyTypeTraverser() {
                @Override
                public PyRecursiveTypeVisitor.Traversal visitPyType(PyType type) {
                    if (type instanceof PyInferenceVariable inferenceVariable) {
                        result.add(inferenceVariable);
                        if (result.size() >= maxSize) {
                            return PyRecursiveTypeVisitor.Traversal.TERMINATE;
                        }
                    }
                    return PyRecursiveTypeVisitor.Traversal.CONTINUE;
                }
            });
        }
        return result;
    }

    /**
     * Keeps literal {@code bound} only if the variance of {@code typeParam} is covariant, otherwise widens to its class type.
     * <p>
     * Note that for type parameters of functions, the variance is only determined by the return type. Additionally, the special case of
     * returning a {@code Callable[[T], T]} with {@code T} being an unbound type parameter, we treat that callable as the effective scope owner of {@code T}
     * (see {@link PyInferredVarianceJudgment#getFunctionReturnVariance}).
     */
    static @Nullable PyType widenOrKeepBounds(PyTypeParameterType typeParam, @Nullable PyType bound, TypeEvalContext context) {
        List<@Nullable PyType> widened = widenOrKeepBounds(typeParam, new PyType[]{bound}, context);
        return widened.isEmpty() ? null : widened.get(0);
    }

    private static List<@Nullable PyType> widenOrKeepBounds(PyTypeParameterType typeParam, @Nullable PyType[] bounds, TypeEvalContext context) {
        if (isWidenBoundsCase(typeParam, context)) {
            // widen literal types
            List<@Nullable PyType> result = new ArrayList<>(bounds.length);
            for (PyType lowerBound : bounds) {
                result.add(PyLiteralType.upcastLiteralToClassDeep(lowerBound, context));
            }
            return result;
        }
        return Arrays.asList(bounds); // keep literal types
    }

    static boolean isWidenBoundsCase(PyTypeParameterType typeParam, TypeEvalContext context) {
        boolean tpFromFunction = typeParam.getScopeOwner() instanceof PyCallable;
        PyVarianceIntermediateResult variance;
        if (tpFromFunction) {
            variance = PyInferredVarianceJudgment.getFunctionReturnVariance(typeParam, context);
        }
        else {
            variance = PyInferredVarianceJudgment.getDeclaredOrInferredVariance(typeParam, context);
        }
        return variance != PyVariance.COVARIANT;
    }

    /**
     * Returns true iff this type is Any or object
     */
    private static boolean isTopType(@Nullable PyType type, TypeEvalContext context) {
        if (type == null) {
            return true; // Any
        }

        if (!(type instanceof PyClassType cls)) {
            return false;
        }
        PyClassType objectType = PyBuiltinCache.getInstance(cls.getPyClass()).getObjectType();
        return objectType != null && SubtypeJudgement.sameTypes(cls, objectType, context);
    }

    private static boolean isBottomType(@Nullable PyType type) {
        return PyTypeUtilKt.isAnyOrUnknown(type) || type instanceof PyNeverType; // Any or Never
    }

    private static List<PyType> filterNotNull(Collection<@Nullable PyType> types) {
        List<PyType> result = new ArrayList<>();
        for (PyType type : types) {
            if (type != null) {
                result.add(type);
            }
        }
        return result;
    }

    private static int indexOfFirst(List<@Nullable PyType> types, Predicate<@Nullable PyType> predicate) {
        for (int i = 0; i < types.size(); i++) {
            if (predicate.test(types.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * The author thought, we never get here.
     */
    @SuppressWarnings("FunctionName")
    private static IllegalStateException UNREACHABLE() {
        return UNREACHABLE("Unreachable");
    }

    @SuppressWarnings("FunctionName")
    private static IllegalStateException UNREACHABLE(String msg) {
        return new IllegalStateException(msg);
    }
}
