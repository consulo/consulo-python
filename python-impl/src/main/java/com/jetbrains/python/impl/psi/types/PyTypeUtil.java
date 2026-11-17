// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.stdlib.PyNamedTupleTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.CodifiedParam;
import com.jetbrains.python.impl.inspections.PyInspectionMessages.ProblemMessage;
import com.jetbrains.python.impl.inspections.PyInspectionMessages;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyTargetExpressionImpl;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyPossibleClassMember;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterListType;
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyFunctionType;
import com.jetbrains.python.psi.types.PyInstantiableType;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeMember;
import com.jetbrains.python.psi.types.PyTypeVarTupleType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolder;
import consulo.util.lang.ThreeState;
import consulo.util.lang.ref.Ref;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import static com.jetbrains.python.impl.codeInsight.PyDataclassesKt.isFrozenAttribute;
import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;

/**
 * Tools and wrappers around {@link PyType} inheritors
 *
 * @author Ilya.Kazakevich
 */
public final class PyTypeUtil {
    private PyTypeUtil() {
    }

    /**
     * Checks if two types are both assignable to each other, does not check if two types are equal
     */
    public static boolean isSameType(@Nullable PyType type1, @Nullable PyType type2, TypeEvalContext context) {
        if ((type1 == null || type2 == null) && type1 != type2) {
            return false;
        }

        return PyTypeChecker.match(type1, type2, context)
            && PyTypeChecker.match(type2, type1, context);
    }

    /**
     * Checks if two types have a direct inheritance relationship, meaning one is a
     * subtype or supertype of the other.
     * <p>
     * This method handles {@link PyUnionType} by distributing the check across its
     * members. The types are considered subtype-related if the condition holds for
     * any pair of members.
     */
    public static boolean isSubtypeRelated(@Nullable PyType type1, @Nullable PyType type2, TypeEvalContext context) {
        if (type1 instanceof PyUnionType || type1 instanceof PyUnsafeUnionType) {
            return ((PyCompositeType)type1).getMembers().stream().anyMatch(it -> isSubtypeRelated(it, type2, context));
        }
        if (type2 instanceof PyUnionType || type2 instanceof PyUnsafeUnionType) {
            return ((PyCompositeType)type2).getMembers().stream().anyMatch(it -> isSubtypeRelated(type1, it, context));
        }
        return PyTypeChecker.match(type1, type2, context)
            || PyTypeChecker.match(type2, type1, context);
    }

    /**
     * Whether the metaclass of {@code cls} defines {@code __getitem__}, as {@code EnumMeta} does.
     * At runtime, the metaclass {@code __getitem__} wins over {@code __class_getitem__}.
     * So a subscription of the class object, such as {@code MyEnum["A"]}, is a call and not a parameterization.
     */
    public static boolean hasMetaclassGetItem(PyClass cls, TypeEvalContext context) {
        if (!(cls.getMetaClassType(true, context) instanceof PyClassType metaClassType)) {
            return false;
        }
        PyClass metaClass = metaClassType.getPyClass();
        return metaClass.findMethodByName(PyNames.GETITEM, true, context) != null;
    }

    /**
     * Returns members of certain type from {@link PyClassLikeType}.
     */
    public static <T extends PsiElement> List<T> getMembersOfType(PyClassLikeType type,
                                                                  Class<T> expectedMemberType,
                                                                  boolean inherited,
                                                                  TypeEvalContext context) {
        List<T> result = new ArrayList<>();
        type.visitMembers(it -> {
            if (expectedMemberType.isInstance(it)) {
                // Already checked
                result.add(expectedMemberType.cast(it));
            }
            return true;
        }, inherited, context);
        return result;
    }

    /**
     * Search for data in dataholder or members of union recursively
     *
     * @param type start point
     * @param key  key to search
     * @param <T>  result tyoe
     * @return data or null if not found
     */
    public static <T> @Nullable T findData(PyType type, Key<T> key) {
        if (type instanceof UserDataHolder userDataHolder) {
            return userDataHolder.getUserData(key);
        }
        if (type instanceof PyUnionType unionType) {
            for (PyType memberType : unionType.getMembers()) {
                if (memberType == null) {
                    continue;
                }
                T result = findData(memberType, key);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    public static @Nullable PyTupleType toPositionalContainerType(PsiElement element, @Nullable PyType elementType) {
        if (elementType instanceof PyUnpackedTupleTypeImpl unpackedTupleType) {
            return unpackedTupleType.asTupleType(element);
        }
        else if (elementType instanceof PyTypeVarTupleType) {
            return PyTupleType.create(element, List.of(elementType));
        }
        return PyTupleType.createHomogeneous(element, elementType);
    }

    public static @Nullable PyClassType toKeywordContainerType(PsiElement element, @Nullable PyType valueType) {
        PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);

        PyClassType dictType = builtinCache.getDictType();
        PyClass dictClass = dictType != null ? dictType.getPyClass() : null;
        return dictClass != null ? new PyCollectionTypeImpl(dictClass, false, java.util.Arrays.asList(builtinCache.getStrType(), valueType)) : null;
    }

    /**
     * Given a type creates a stream of all its members if it's a {@link PyCompositeType} or of only the type itself otherwise.
     * <p>
     * It lets a composite type (e.g. the union produced when a reference resolves to several declarations) be processed
     * uniformly with a non-composite one.
     *
     * @deprecated Use the high level composite api instead {@code PyType.compositeX}
     */
    @Deprecated
    public static StreamEx<@Nullable PyType> toStream(@Nullable PyType type) {
        if (type instanceof PyCompositeType compositeType) {
            return StreamEx.of(compositeType.getMembers());
        }
        else {
            return StreamEx.of(type);
        }
    }

    /**
     * Returns a sequence of all the union members if it's a union type, or of only the type itself otherwise.
     */
    public static List<@Nullable PyType> asUnionSequence(@Nullable PyType type) {
        if (type instanceof PyUnionType || type instanceof PyUnsafeUnionType) {
            return new ArrayList<>(((PyCompositeType)type).getMembers());
        }
        else {
            return Collections.singletonList(type);
        }
    }

    public static @Nullable Ref<PyType> notNullToRef(@Nullable PyType type) {
        return type == null ? null : Ref.create(type);
    }

    public static @Nullable PyType derefOrUnknown(@Nullable Ref<? extends @Nullable PyType> ref) {
        if (ref == null) {
            return PyAnyType.getUnknown();
        }
        PyType type = ref.get();
        PyAnyType.validate(type);
        return type;
    }

    /**
     * Returns a collector that combines a stream of {@code Ref<PyType>} back into a single {@code Ref<PyType>}
     * using {@link PyUnionType#union}.
     *
     * @see #toUnion()
     */
    public static Collector<@Nullable Ref<@Nullable PyType>, ?, @Nullable Ref<@Nullable PyType>> toUnionFromRef() {
        return toUnionFromRef((type1, type2) -> PyUnionType.unionOrUnknown(type1, type2));
    }

    public static Collector<@Nullable Ref<@Nullable PyType>, ?, @Nullable Ref<@Nullable PyType>> toUnsafeUnionFromRef() {
        return toUnionFromRef((type1, type2) -> PyUnsafeUnionType.unsafeUnion(type1, type2));
    }

    public static Collector<@Nullable Ref<@Nullable PyType>, ?, @Nullable Ref<@Nullable PyType>> toUnionFromRef(@Nullable PyType streamSource) {
        if (streamSource instanceof PyUnsafeUnionType) {
            return toUnionFromRef((type1, type2) -> PyUnsafeUnionType.unsafeUnion(type1, type2));
        }
        else {
            return toUnionFromRef((type1, type2) -> PyUnionType.unionOrUnknown(type1, type2));
        }
    }

    private static Collector<@Nullable Ref<@Nullable PyType>, ?, @Nullable Ref<@Nullable PyType>> toUnionFromRef(
        BinaryOperator<@Nullable PyType> unionReduction
    ) {
        return Collectors.reducing(null, (accType, hintType) -> {
            if (hintType == null) {
                return accType;
            }
            else if (accType == null) {
                return hintType;
            }
            else {
                return Ref.create(unionReduction.apply(accType.get(), hintType.get()));
            }
        });
    }

    /**
     * Returns a collector that combines a stream of types back into a single {@code PyType}
     * using {@link PyUnionType#union}.
     * <p>
     * Note that it's different from using {@code foldLeft(PyUnionType::unionOrUnknown)} because the latter returns {@code Optional<PyType>},
     * and it doesn't support {@code null} values throwing {@code NullPointerException} if the final result of
     * {@link PyUnionType#union} was {@code null}.
     * <p>
     * This method doesn't distinguish between an empty stream and a stream containing only {@code null} returning {@code null} for both cases.
     *
     * @see #toUnionFromRef()
     */
    public static Collector<@Nullable PyType, ?, @Nullable PyType> toUnion() {
        return Collectors.collectingAndThen(Collectors.toList(), members -> PyUnionType.unionOrUnknown(members));
    }

    public static Collector<@Nullable PyType, ?, @Nullable PyType> toUnsafeUnion() {
        return Collectors.collectingAndThen(Collectors.toList(), members -> PyUnsafeUnionType.unsafeUnion(members));
    }

    public static Collector<@Nullable PyType, ?, @Nullable PyType> toIntersection() {
        return Collectors.collectingAndThen(Collectors.toList(), members -> PyIntersectionType.intersectionOrTop(members));
    }

    public static Collector<@Nullable PyType, ?, @Nullable PyType> toUnion(@Nullable PyType streamSource) {
        if (streamSource instanceof PyUnsafeUnionType) {
            return toUnion(it -> PyUnsafeUnionType.unsafeUnion(it));
        }
        else {
            return toUnion(members -> PyUnionType.unionOrUnknown(members));
        }
    }

    public static List<@Nullable PyType> getCompositeComponents(@Nullable PyType type) {
        return type instanceof PyCompositeType compositeType ? new ArrayList<>(compositeType.getMembers()) : Collections.singletonList(type);
    }

    public static List<@Nullable PyType> getCompositeComponentSequence(@Nullable PyType type) {
        return type instanceof PyCompositeType compositeType ? new ArrayList<>(compositeType.getMembers()) : Collections.singletonList(type);
    }

    /**
     * Decomposes {@code type} into the list of its members (or a single-element list for a non-composite type),
     * hands that list to {@code transform}, and reassembles the result into a composite type <i>of the same kind</i>
     * ({@link PyUnionType}, {@link PyUnsafeUnionType} or {@link PyIntersectionType}); a non-composite type is rebuilt as a union,
     * which collapses a single remaining member back to itself.
     * <p>
     * It lets one uniformly deconstruct a composite type, process its members and build it back without branching
     * on the concrete composite kind:
     * <pre>
     * val transformed = someType.compositeTransform { members ->
     *   members.map { ... }.filter { ... }.mapNotNull { ... }
     * }
     * </pre>
     * <p>
     * If {@code transform} returns an empty list, the result is the identity element of the composite kind:
     * {@link PyNeverType#NEVER} for a union or an unsafe union (bottom), and {@code PyTopType} for an intersection (top).
     *
     * @see #compositeMap
     * @see #compositeMatchesAsSubtype
     * @see #compositeMatchesAsSupertype
     */
    public static @Nullable PyType compositeTransform(@Nullable PyType type, Function<List<@Nullable PyType>, List<@Nullable PyType>> transform) {
        return rebuildLike(type, transform.apply(getCompositeComponents(type)));
    }

    /**
     * Applies {@code mapper} to every leaf member of {@code type} composite type and reassembles the results into composite types
     * of the same kinds; a non-composite type is passed to {@code mapper} directly.
     * <p>
     * Recurses through nested composites ({@link PyUnionType}, {@link PyUnsafeUnionType}, {@link PyIntersectionType}) so that {@code mapper}
     * always receives a non-composite type, e.g. {@code Union[Intersection[A, B], C]} is mapped to
     * {@code Union[Intersection[f(A), f(B)], f(C)]}. Use {@link #compositeTransform} instead when you need the flat list of the
     * <i>direct</i> members without recursion.
     *
     * @see #compositeTransform
     * @see PyUnionType#map
     */
    public static @Nullable PyType compositeMap(@Nullable PyType type, Function<@Nullable PyType, @Nullable PyType> mapper) {
        if (type instanceof PyCompositeType) {
            return compositeTransform(type, members -> {
                List<@Nullable PyType> result = new ArrayList<>();
                for (PyType it : members) {
                    result.add(compositeMap(it, mapper));
                }
                return result;
            });
        }
        else {
            return mapper.apply(type);
        }
    }

    /**
     * Tests {@code predicate} against the members of {@code type} composite type with the quantifier that decides whether the
     * <i>whole composite is a subtype</i> of some other type: since {@code Union[Ts] <: T} iff every {@code Ti <: T}, <i>all</i> members of a
     * {@link PyUnionType} must match; since {@code Intersection[Ts] <: T} (and {@code UnsafeUnion[Ts] <: T}) iff some {@code Ti <: T}, <i>any</i>
     * member of a {@link PyIntersectionType} or a {@link PyUnsafeUnionType} is enough. For a non-composite type {@code predicate} is
     * applied directly.
     * <p>
     * {@code predicate} is expected to test the member on the subtype side, e.g. {@code compositeMatchesAsSubtype { match(it, target) }}.
     *
     * @see #compositeMatchesAsSupertype
     */
    public static boolean compositeMatchesAsSubtype(@Nullable PyType type, Predicate<@Nullable PyType> predicate) {
        if (type instanceof PyUnionType unionType) {
            return unionType.getMembers().stream().allMatch(predicate);
        }
        else if (type instanceof PyIntersectionType || type instanceof PyUnsafeUnionType) {
            return ((PyCompositeType)type).getMembers().stream().anyMatch(predicate);
        }
        else {
            return predicate.test(type);
        }
    }

    /**
     * The mirror of {@link #compositeMatchesAsSubtype}: the quantifier that decides whether some other type is a <i>subtype of the whole
     * composite</i>. Since {@code T <: Union[Ts]} iff some {@code T <: Ti}, <i>any</i> member of a {@link PyUnionType} (or a {@link PyUnsafeUnionType})
     * suffices; since {@code T <: Intersection[Ts]} iff {@code T <: Ti} for every member, <i>all</i> members of a {@link PyIntersectionType}
     * must match. For a non-composite type {@code predicate} is applied directly.
     * <p>
     * {@code predicate} is expected to test the member on the supertype side, e.g. {@code compositeMatchesAsSupertype { match(target, it) }}.
     *
     * @see #compositeMatchesAsSubtype
     */
    public static boolean compositeMatchesAsSupertype(@Nullable PyType type, Predicate<@Nullable PyType> predicate) {
        if (type instanceof PyUnionType || type instanceof PyUnsafeUnionType) {
            return ((PyCompositeType)type).getMembers().stream().anyMatch(predicate);
        }
        else if (type instanceof PyIntersectionType intersectionType) {
            return intersectionType.getMembers().stream().allMatch(predicate);
        }
        else {
            return predicate.test(type);
        }
    }

    /**
     * Rebuilds a composite type of the same kind as {@code type} from {@code members}, folding an empty {@code members} to the identity
     * element of that kind: {@link PyNeverType#NEVER} for unions/unsafe unions, {@code PyTopType} for intersections.
     */
    private static @Nullable PyType rebuildLike(@Nullable PyType type, List<@Nullable PyType> members) {
        if (type instanceof PyIntersectionType) {
            return PyIntersectionType.intersectionOrTop(members);
        }
        else if (type instanceof PyUnsafeUnionType) {
            return members.isEmpty() ? PyNeverType.NEVER : PyUnsafeUnionType.unsafeUnion(members);
        }
        else {
            return PyUnionType.unionOrNever(members);
        }
    }

    private static Collector<@Nullable PyType, ?, @Nullable PyType> toUnion(Function<List<@Nullable PyType>, @Nullable PyType> unionFactory) {
        return Collectors.collectingAndThen(Collectors.toList(), unionFactory);
    }

    public static boolean isDict(@Nullable PyType type) {
        return type instanceof PyClassType classType && classType.isParameterized() && "dict".equals(classType.getName());
    }

    public static @Nullable PyType convertToType(@Nullable PyType type,
                                                 String superTypeName,
                                                 PsiElement anchor,
                                                 TypeEvalContext context) {
        PyClass superClass = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(superTypeName, anchor);
        if (superClass == null) {
            return null;
        }
        PyClassType genericDefinitionType = PyTypeChecker.findGenericDefinitionType(superClass, context);
        PyClassType superClassType = genericDefinitionType != null ? genericDefinitionType : new PyClassTypeImpl(superClass, false);
        return PyTypeChecker.convertToType(type, superClassType, context);
    }

    public static boolean inheritsAny(@Nullable PyType type, TypeEvalContext context) {
        return type instanceof PyClassLikeType classLikeType && classLikeType.getAncestorTypes(context).contains(null);
    }

    /**
     * Collects a set of types that participate in the textual type hint representation of {@code type}.
     * The returned set preserves a stable DFS order and is unmodifiable.
     */
    public static Set<@Nullable PyType> collectTypeComponents(@Nullable PyType type, TypeEvalContext context) {
        Set<@Nullable PyType> result = new LinkedHashSet<>();

        PyRecursiveTypeVisitor.traverse(type, context, new PyRecursiveTypeVisitor.PyTypeTraverser() {
            @Override
            public PyRecursiveTypeVisitor.Traversal visitPyType(PyType pyType) {
                result.add(pyType);
                return super.visitPyType(pyType);
            }

            @Override
            public PyRecursiveTypeVisitor.Traversal visitPyLiteralType(PyLiteralType literalType) {
                PyType literalClassType = literalType.getPyClass().getType(context);
                if (literalClassType != null) {
                    // Adds eg. signal.Handler when the given type was Literal[Handlers.SIG_DFL]
                    result.add(literalClassType);
                }
                return super.visitPyLiteralType(literalType);
            }

            @Override
            public PyRecursiveTypeVisitor.Traversal visitUnknownType() {
                result.add(null); // add Any type
                return super.visitUnknownType();
            }
        });

        return Collections.unmodifiableSet(result);
    }

    /**
     * Extracts string literal values from a {@code tuple[Literal["a"], Literal["b"], ...]} type.
     * Returns {@code null} if the type is not a non-homogeneous tuple of string literals.
     *
     * @see #createTupleOfLiteralStringsType
     */
    public static @Nullable List<String> extractStringLiteralsFromTupleType(@Nullable PyType type) {
        if (!(type instanceof PyTupleType tupleType) || tupleType.isHomogeneous()) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (PyType elementType : tupleType.getElementTypes()) {
            if (!(elementType instanceof PyLiteralType literalType)) {
                return null;
            }
            String str = literalType.getStringValue();
            if (str == null) {
                return null;
            }
            result.add(str);
        }
        return result;
    }

    /**
     * Creates a {@code tuple[Literal["name1"], Literal["name2"], ...]} type from a list of strings.
     * Useful for creating types for synthetic members (e.g. {@code __match_args__}, {@code __slots__} in a dataclasses).
     *
     * @see #extractStringLiteralsFromTupleType
     */
    public static @Nullable PyTupleType createTupleOfLiteralStringsType(PsiElement anchor, List<String> fieldNames) {
        List<@Nullable PyType> literalTypes = new ArrayList<>();
        for (String it : fieldNames) {
            PyLiteralType literalType = PyLiteralType.stringLiteral(anchor, it);
            if (literalType != null) {
                literalTypes.add(literalType);
            }
        }
        return PyTupleType.create(anchor, literalTypes);
    }

    public static @Nullable PyType widenLiteralAndNumeric(@Nullable PyType type) {
        PyType widened = PyTypeUtilKt.widenTupleLiterals(type);
        widened = PyLiteralType.upcastLiteralToClass(widened);
        return PyNumericTowerUtil.enrich(widened);
    }

    public static @Nullable PyType mapCallableType(@Nullable PyType functionType, Function<PyCallableType, @Nullable PyCallableType> mapper) {
        if (functionType instanceof PyCallableType callableType) {
            return mapper.apply(callableType);
        }
        else if (functionType instanceof PyOverloadType overloadType) {
            return PyOverloadTypeKt.map(overloadType, mapper);
        }
        else {
            return functionType;
        }
    }

    public static List<PyCallableType> getCallableItems(@Nullable PyType functionType) {
        if (functionType instanceof PyCallableType callableType) {
            return List.of(callableType);
        }
        else if (functionType instanceof PyOverloadType overloadType) {
            return overloadType.getItems();
        }
        else {
            return List.of();
        }
    }

    public static @Nullable PyClass getContainingClass(List<? extends RatedResolveResult> resolveResults) {
        for (RatedResolveResult result : resolveResults) {
            if (result.getElement() instanceof PyPossibleClassMember member) {
                PyClass containingClass = member.getContainingClass();
                if (containingClass != null) {
                    return containingClass;
                }
            }
        }
        return null;
    }

    public static @Nullable PyType getTypeOfBoundMember(PyClassLikeType classType,
                                                        @Nullable List<? extends RatedResolveResult> memberResolveResults,
                                                        TypeEvalContext context) {
        return getTypeOfBoundMember(classType, memberResolveResults, context, null);
    }

    public static @Nullable PyType getTypeOfBoundMember(PyClassLikeType classType,
                                                        @Nullable List<? extends RatedResolveResult> memberResolveResults,
                                                        TypeEvalContext context,
                                                        @Nullable List<ProblemMessage> errors) {
        return getTypeOfBoundMember(classType, memberResolveResults, context, errors, classType);
    }

    public static @Nullable PyType getTypeOfBoundMember(PyClassLikeType classType,
                                                        @Nullable List<? extends RatedResolveResult> memberResolveResults,
                                                        TypeEvalContext context,
                                                        @Nullable List<ProblemMessage> errors,
                                                        PyInstantiableType<?> selfType) {
        if (memberResolveResults == null || memberResolveResults.isEmpty()) {
            return PyAnyType.getUnknown();
        }
        PyType memberType = getTypeOfMember(memberResolveResults, context);
        PyType specializedMemberType = specializeMemberType(classType, selfType, memberType, context);
        // An annotated instance attribute holding a callable is not a descriptor, so it must not be bound to `self`.
        if (!selfType.isDefinition() && isInstanceMember(memberResolveResults, context)) {
            return specializedMemberType;
        }
        PyClass memberOwner = getContainingClass(memberResolveResults);
        return bindFunction(selfType, specializedMemberType, memberOwner, context, errors);
    }

    public static boolean isInstanceMember(List<? extends RatedResolveResult> memberResolveResults, TypeEvalContext context) {
        List<PsiElement> elements = new ArrayList<>();
        for (RatedResolveResult result : memberResolveResults) {
            PsiElement element = result.getElement();
            if (element != null) {
                elements.add(element);
            }
        }
        if (elements.isEmpty()) {
            return false;
        }
        PsiElement first = elements.get(0);
        return !(ScopeUtil.getScopeOwner(first) instanceof PyClass) ||
            elements.stream().anyMatch(it ->
                                           it instanceof PyTargetExpression target &&
                                               (target.getAnnotationValue() != null || target.getTypeCommentAnnotation() != null) &&
                                               !PyTypingTypeProvider.isClassVar(target, context) &&
                                               !(PyTypingTypeProvider.isFinal(target, context) && target.hasAssignedValue())
            );
    }

    public static @Nullable PyType getTypeOfMember(List<? extends RatedResolveResult> memberResolveResults, TypeEvalContext context) {
        return getTypeOfMember(memberResolveResults, context, null);
    }

    public static @Nullable PyType getTypeOfMember(List<? extends RatedResolveResult> memberResolveResults,
                                                   TypeEvalContext context,
                                                   @Nullable PsiElement anchor) {
        List<PyTypedElement> resolvedElements = new ArrayList<>();
        for (RatedResolveResult result : memberResolveResults) {
            if (result.getElement() instanceof PyTypedElement typedElement) {
                resolvedElements.add(typedElement);
            }
        }
        if (resolvedElements.isEmpty()) {
            return PyAnyType.getUnknown();
        }

        // Element with a declared type takes precedence.
        List<PyTypedElement> elementsWithDeclaredType = new ArrayList<>();
        for (PyTypedElement it : resolvedElements) {
            if (it instanceof PyClass ||
                it instanceof PyFunction ||
                it instanceof PyTargetExpression target && (target.getAnnotationValue() != null || target.getTypeCommentAnnotation() != null)) {
                elementsWithDeclaredType.add(it);
            }
        }
        List<PyTypedElement> elements = elementsWithDeclaredType.isEmpty() ? resolvedElements : elementsWithDeclaredType;

        PyTypedElement last = elements.get(elements.size() - 1);
        PyType lastType = getResolvedElementType(last, context, anchor);
        if (!(lastType instanceof PyFunctionType lastFunctionType)) {
            PyType memberType = last instanceof PyTargetExpression target && target.isQualified()
                ? upcastAttributeLiteral(target, lastType, context)
                : lastType;
            if (elementsWithDeclaredType.isEmpty() && isNoneType(memberType)) {
                /* we support a special case where we convert an unannotated attribute of `None` to `UnsafeUnion[None, Unknown]`
                  this is because there are frequently cases in real code where inferring `None` would lead to undesirable false positives:
                  ```py
                  class C:
                      def __init__(self):
                          self.a = None  # user intends `int | None` / `late int`
                      def set_a(self):
                          self.a = 1
                  def f(c: C):
                      c.a + 1  # FP here
                  ```

                  we use `UnsafeUnion` to avoid cases where the `None` doesn't typically surface to usages,
                  if the user is interested in typing they should always annotate an attribute that is initialised with `None`

                  there is also a consideration for the case where a base class sets an attribute with `None`, expecting it to be
                  overridden with a value
                */
                return PyUnsafeUnionType.unsafeUnion(memberType, PyAnyType.getUnknown());
            }
            return memberType;
        }

        List<PyCallableType> overloads = new ArrayList<>();
        Ref<@Nullable PyType> impl = null;
        if (PyiUtil.isOverload(last, context)) {
            overloads.add(lastFunctionType);
        }
        else {
            impl = Ref.create(lastType);
        }
        for (int i = elements.size() - 2; i >= 0; i--) {
            PyTypedElement el = elements.get(i);
            if (PyiUtil.isOverload(el, context)) {
                PyType type = getResolvedElementType(el, context, anchor);
                if (type instanceof PyCallableType callableType) {
                    overloads.add(callableType);
                    continue;
                }
            }
            break;
        }
        if (overloads.isEmpty()) {
            return lastType;
        }

        Collections.reverse(overloads);
        return new PyOverloadType(overloads, impl);
    }

    /**
     * Widens the literal {@code type} of the mutable attribute {@code target} to its class.
     * A {@code Final} attribute keeps its literal type.
     * An unannotated attribute that overrides an annotated attribute of an ancestor class also keeps its literal type.
     * Such an attribute has the declared type of the ancestor attribute.
     * So its literal type comes from that declaration, not from the assigned value.
     * For example, {@code self.x = 5} in a subclass keeps the type {@code Literal[5]} of {@code x: Literal[5]} in the base class.
     * The widened type {@code int} would contradict the declaration in the base class.
     */
    public static @Nullable PyType upcastAttributeLiteral(PyTargetExpression target, @Nullable PyType type, TypeEvalContext context) {
        if (PyTypingTypeProvider.isFinal(target, context)) {
            return type;
        }
        PyType widened = PyLiteralType.upcastLiteralToClass(type);
        if (Objects.equals(widened, type)) {
            return type;
        }
        if (target instanceof PyTargetExpressionImpl targetImpl && targetImpl.getInheritedDeclaredAttributeType(context) != null) {
            return type;
        }
        return widened;
    }

    /**
     * Returns true if no assignment can change {@code attribute} after its initialization.
     * A {@code Final} or {@code ReadOnly} attribute, an attribute of a frozen dataclass, and a field of a typing {@code NamedTuple} are read-only.
     */
    public static boolean isReadOnlyAttribute(PyTargetExpression attribute, TypeEvalContext context) {
        return PyTypingTypeProvider.isFinal(attribute, context) ||
            PyTypingTypeProvider.isReadOnly(attribute, context) ||
            isFrozenAttribute(attribute, context) ||
            PyNamedTupleTypeProvider.Helper.isNamedTupleField(attribute, context);
    }

    /**
     * Returns true if an assignment can change {@code member}.
     * The member must have a setter, and it must not be a read-only attribute.
     *
     * @see #isReadOnlyAttribute
     */
    public static boolean isSettable(PyTypeMember member, TypeEvalContext context) {
        if (!member.isWritable()) {
            return false;
        }
        PsiElement element = member.getElement();
        return !(element instanceof PyTargetExpression target) || !isReadOnlyAttribute(target, context);
    }

    private static @Nullable PyType getResolvedElementType(PyTypedElement element, TypeEvalContext context, @Nullable PsiElement anchor) {
        // `PyTargetExpression.getType()` will call `PyTypeProvider.getReferenceType()` itself
        if (!(element instanceof PyTargetExpression)) {
            Ref<PyType> type = PyTypeProvider.EP_NAME.computeSafeIfAny(it -> it.getReferenceType(element, context, anchor));
            if (type != null) {
                return type.get();
            }
        }
        return context.getType(element);
    }

    public static @Nullable PyType specializeMemberType(PyClassLikeType classType,
                                                        PyInstantiableType<?> selfType,
                                                        @Nullable PyType memberType,
                                                        TypeEvalContext context) {
        if (PyTypeChecker.hasGenerics(memberType, context)) {
            PyTypeChecker.GenericSubstitutions substitutions = classType instanceof PyClassType classTypeAsClass
                ? PyTypeChecker.collectTypeSubstitutions(classTypeAsClass, context)
                : new PyTypeChecker.GenericSubstitutions();
            substitutions.setSelfType(selfType);
            return PyTypeChecker.substitute(memberType, substitutions, context);
        }
        else {
            return memberType;
        }
    }

    /**
     * If {@code memberType} is an instance or class method, returns its specialized bound type,
     * or {@link PyAnyType#getUnknown()} if binding failed (the type of its first parameter does not match {@code classType});
     * otherwise returns {@code memberType} as is.
     */
    public static @Nullable PyType bindFunction(PyInstantiableType<?> classType,
                                                @Nullable PyType memberType,
                                                @Nullable PyClass memberOwner,
                                                TypeEvalContext context,
                                                @Nullable List<ProblemMessage> errors) {
        if (memberType instanceof PyClassLikeType) {
            return memberType;
        }

        List<PyCallableType> signatures = getCallableItems(memberType);
        if (signatures.isEmpty()) {
            return memberType;
        }

        boolean isStaticMethod = signatures.stream().allMatch(it -> it.getModifier() == PyAstFunction.Modifier.STATICMETHOD);
        if (isStaticMethod) {
            return memberType;
        }

        boolean isClassMethod = signatures.stream().allMatch(it -> it.getModifier() == PyAstFunction.Modifier.CLASSMETHOD);

        boolean shouldBind = false;
        if (!classType.isDefinition() || isClassMethod) {
            shouldBind = true;
        }
        else if (memberOwner != null && classType instanceof PyClassType classTypeAsClass) {
            // If a member's owner is not an ancestor of the class,
            // the member must have come from the metaclass -> bind it to the class.
            // TODO: This condition should be computed by the calling code when resolving `memberType`
            shouldBind = !classTypeAsClass.getPyClass().isSubclass(memberOwner, context);
        }

        if (shouldBind) {
            PyInstantiableType<?> selfType = isClassMethod ? classType.toClass() : classType;
            PyType boundMethodType = mapCallableType(memberType, it -> {
                FunctionBindingResult result = bindFunction(it, selfType, context);
                return result != null ? result.getBoundMethodType() : null;
            });
            if (boundMethodType == null) {
                if (errors != null) {
                    errors.add(methodBindingErrorMessage(selfType, memberType, context));
                }
                return PyAnyType.getUnknown();
            }
            return boundMethodType;
        }

        return memberType;
    }

    public static ProblemMessage methodBindingErrorMessage(PyType selfType, @Nullable PyType memberType, TypeEvalContext context) {
        PyType methodType = memberType instanceof PyOverloadType overloadType
            ? (overloadType.getItems().isEmpty() ? null : overloadType.getItems().get(0))
            : memberType;
        PyCallable callable = methodType instanceof PyCallableType callableType ? callableType.getCallable() : null;
        // Any element in a Python file works as the link-resolution scope; the type names link to global declarations.
        PsiElement anchor = callable != null ? callable : selfType instanceof PyClassType classType ? classType.getPyClass() : null;
        Object selfTypeParam = toCodeSpan(selfType, anchor, context);
        Object methodTypeParam = toCodeSpan(methodType, anchor, context);
        String methodName = null;
        if (callable != null && callable.getName() != null) {
            String name = callable.getName();
            PyFunction method = callable.asMethod();
            PyClass containingClassElement = method != null ? method.getContainingClass() : null;
            String containingClass = containingClassElement != null ? containingClassElement.getName() : null;
            methodName = containingClass != null ? containingClass + "." + name : name;
        }
        if (methodName != null) {
            return PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerInvalidSelfArgumentToNamedMethodWithType(params[0], params[1], params[2]),
                selfTypeParam, methodName, methodTypeParam
            );
        }
        else {
            return PyInspectionMessages.problemMessage(
                params -> PyLocalize.inspTypeCheckerInvalidSelfArgumentToMethodWithType(params[0], params[1]),
                selfTypeParam, methodTypeParam
            );
        }
    }

    /**
     * Renders {@code type} as a code span for an inspection message: a {@link CodifiedParam} with clickable type links when an
     * {@code anchor} is available, otherwise the plain type name (the message template still renders it as a code span).
     */
    private static Object toCodeSpan(@Nullable PyType type, @Nullable PsiElement anchor, TypeEvalContext context) {
        if (anchor != null) {
            return CodifiedParam.ofType(type, anchor, context);
        }
        else {
            return PythonDocumentationProvider.getTypeName(type, context);
        }
    }

    public static @Nullable FunctionBindingResult bindFunction(PyCallableType callableType,
                                                               PyInstantiableType<?> selfType,
                                                               TypeEvalContext context) {
        if (callableType.getParametersType(context) == null) {
            // `typing.Callable[..., R]` - treat as `(*args, **kwargs)`.
            return new FunctionBindingResult(callableType, PyAnyType.getAny());
        }
        List<PyCallableParameter> parameters = callableType.getParameters(context);
        PyCallableParameter firstParam = parameters != null && !parameters.isEmpty() ? parameters.get(0) : null;
        if (firstParam != null && !firstParam.isPositionOnlySeparator() && !firstParam.isKeywordOnlySeparator()) {
            PyType firstParamType = firstParam.getArgumentType(context);
            PyTypeChecker.GenericSubstitutions substitutions = new PyTypeChecker.GenericSubstitutions();
            substitutions.setSelfType(selfType);
            if (!(firstParamType instanceof PySelfType)) {
                if (!PyTypeChecker.match(firstParamType, selfType, context, substitutions)) {
                    return null;
                }
            }
            PyCallableType specializedCallableType = (PyCallableType)PyTypeChecker.substitute(callableType, substitutions, context);
            PyType specializedFirstParamType = Objects.requireNonNull(specializedCallableType.getParameters(context)).get(0).getArgumentType(context);
            return new FunctionBindingResult(dropFirstParam(specializedCallableType, context), specializedFirstParamType);
        }
        return null;
    }

    public record FunctionBindingResult(PyCallableType boundMethodType, @Nullable PyType specializedFirstParamType) {
        public PyCallableType getBoundMethodType() {
            return boundMethodType;
        }

        public @Nullable PyType getSpecializedFirstParamType() {
            return specializedFirstParamType;
        }
    }

    private static PyCallableType dropFirstParam(PyCallableType callableType, TypeEvalContext context) {
        if (callableType instanceof PyFunctionType) {
            List<PyCallableParameter> params = callableType.getParameters(context);
            if (params != null && !params.isEmpty()) {
                PyCallableParameter firstParam = params.get(0);
                if (!firstParam.isPositionalContainer() && !firstParam.isKeywordContainer()) {
                    return new PyFunctionTypeImpl(Objects.requireNonNull(callableType.getCallable()), params.subList(1, params.size()));
                }
            }
            return callableType;
        }
        else {
            PyCallableParameterVariadicType parametersType = callableType.getParametersType(context);
            if (parametersType instanceof PyCallableParameterListType parameterListType) {
                List<PyCallableParameter> params = parameterListType.getParameters();
                if (!params.isEmpty()) {
                    PyCallableParameter firstParam = params.get(0);
                    if (!firstParam.isPositionalContainer() && !firstParam.isKeywordContainer()) {
                        return new PyCallableTypeImpl(callableType.getTypeParameters(context),
                                                      new PyCallableParameterListTypeImpl(new ArrayList<>(params.subList(1, params.size()))),
                                                      callableType.getReturnType(context),
                                                      callableType.getCallable(),
                                                      callableType.getModifier()
                        );
                    }
                }
            }
            return callableType;
        }
    }

    /**
     * Determines whether a value of the given {@code type} is always truthy ({@link ThreeState#YES}),
     * always falsy ({@link ThreeState#NO}), or statically undetermined ({@link ThreeState#UNSURE})
     * when used in a boolean context ({@code if x}, {@code x and y}, {@code x or y}, ...).
     * <p>
     * Recognizes:
     * <ul>
     *  <li>{@code None} (always falsy);</li>
     *  <li>boolean, integer and string literal types (a false literal, a zero-valued integer literal, an empty string, ...);</li>
     *  <li>classes whose {@code __bool__} returns a literal {@code bool} (e.g. a false literal);</li>
     *  <li>classes that have no {@code __bool__} but whose {@code __len__} returns a literal {@code int}.</li>
     * </ul>
     * <p>
     * For a union the result is conclusive only if all of its members agree.
     */
    public static ThreeState getTypeTruthiness(@Nullable PyType type, @Nullable PsiElement location, TypeEvalContext context) {
        if (type instanceof PyUnionType unionType) {
            return combineMemberTruthiness(unionType, it -> getTypeTruthiness(it, location, context));
        }
        if (type == null) {
            return ThreeState.UNSURE;
        }
        if (isNoneType(type)) {
            return ThreeState.NO;
        }
        if (type instanceof PyLiteralType literalType) {
            Boolean boolValue = literalType.getBoolValue();
            if (boolValue != null) {
                return ThreeState.fromBoolean(boolValue);
            }
            java.math.BigInteger intValue = literalType.getIntValue();
            if (intValue != null) {
                return ThreeState.fromBoolean(intValue.signum() != 0);
            }
            String stringValue = literalType.getStringValue();
            if (stringValue != null) {
                return ThreeState.fromBoolean(!stringValue.isEmpty());
            }
            return ThreeState.UNSURE;
        }
        if (type instanceof PyClassType classType) {
            // A class object (`if SomeClass:`, `SomeClass or x`) is always truthy: its truthiness comes from the
            // metaclass, not from the instance `__bool__`/`__len__`. Don't consult the instance dunders here, otherwise
            // a class with a literal-returning instance `__bool__` would be wrongly treated as always falsy.
            if (classType.isDefinition()) {
                return ThreeState.UNSURE;
            }
            PyExpression anchor = location instanceof PyExpression expression ? expression : null;
            // `__bool__` takes precedence over `__len__`, exactly like at runtime.
            Ref<@Nullable PyType> boolReturnType = getDunderReturnType(classType, PyNames.BOOL, anchor, context);
            if (boolReturnType != null) {
                return literalReturnTruthiness(boolReturnType.get(), PyLiteralType::getBoolValue);
            }
            Ref<@Nullable PyType> lenReturnType = getDunderReturnType(classType, PyNames.LEN, anchor, context);
            if (lenReturnType != null) {
                return literalReturnTruthiness(lenReturnType.get(), it -> {
                    java.math.BigInteger intValue = it.getIntValue();
                    return intValue != null ? intValue.signum() != 0 : null;
                });
            }
        }
        return ThreeState.UNSURE;
    }

    /**
     * Keeps only the members of {@code type} (treated as a union of one or more types) whose truthiness
     * is compatible with {@code keepTruthy}. Members with undetermined truthiness are always kept.
     * <p>
     * Returns:
     * <ul>
     *  <li>{@code Ref(narrowedType)} with the kept members (the wrapped type may be {@code null}, meaning unknown);</li>
     *  <li>{@code null} if no member is kept, i.e. the value can never be {@code keepTruthy}.</li>
     * </ul>
     * <p>
     * Used to infer the type of {@code or}/{@code and} expressions: in {@code a or b} the value is {@code a} only when {@code a}
     * is truthy, and in {@code a and b} it is {@code a} only when {@code a} is falsy.
     */
    public static @Nullable Ref<@Nullable PyType> narrowTypeByTruthiness(@Nullable PyType type,
                                                                         boolean keepTruthy,
                                                                         @Nullable PsiElement location,
                                                                         TypeEvalContext context) {
        if (type == null) {
            return Ref.create(null);
        }
        ThreeState toDrop = keepTruthy ? ThreeState.NO : ThreeState.YES;
        List<@Nullable PyType> members = toStream(type).toList();
        List<@Nullable PyType> kept = new ArrayList<>();
        for (PyType it : members) {
            if (getTypeTruthiness(it, location, context) != toDrop) {
                kept.add(it);
            }
        }
        if (kept.isEmpty()) {
            return null;
        }
        else if (kept.size() == members.size()) {
            return Ref.create(type);
        }
        else {
            return Ref.create(PyUnionType.union(kept));
        }
    }

    /**
     * Returns the return type of the dunder {@code name} of {@code type}, or {@code null} when {@code type} has no such member.
     * <p>
     * {@link PyUtil#getReturnTypeOfMember} gives an unknown type for a missing member, so it cannot tell a missing {@code __bool__}
     * from a {@code __bool__} with an unknown return type.
     */
    private static @Nullable Ref<@Nullable PyType> getDunderReturnType(PyClassType type,
                                                                       String name,
                                                                       @Nullable PyExpression anchor,
                                                                       TypeEvalContext context) {
        List<? extends RatedResolveResult> members = type.resolveMember(name, anchor, AccessDirection.READ, PyResolveContext.defaultContext(context));
        if (members == null || members.isEmpty()) {
            return null;
        }
        return Ref.create(PyUtil.getReturnTypeOfMember(type, name, anchor, context));
    }

    private static ThreeState combineMemberTruthiness(PyUnionType union, Function<@Nullable PyType, ThreeState> memberTruthiness) {
        ThreeState result = null;
        for (PyType member : union.getMembers()) {
            ThreeState truthiness = memberTruthiness.apply(member);
            if (truthiness == ThreeState.UNSURE) {
                return ThreeState.UNSURE;
            }
            if (result == null) {
                result = truthiness;
            }
            else if (result != truthiness) {
                return ThreeState.UNSURE;
            }
        }
        return result != null ? result : ThreeState.UNSURE;
    }

    private static ThreeState literalReturnTruthiness(@Nullable PyType returnType, Function<PyLiteralType, @Nullable Boolean> literalTruthiness) {
        if (returnType instanceof PyUnionType unionType) {
            return combineMemberTruthiness(unionType, it -> literalReturnTruthiness(it, literalTruthiness));
        }
        Boolean value = returnType instanceof PyLiteralType literalType ? literalTruthiness.apply(literalType) : null;
        if (value == null) {
            return ThreeState.UNSURE;
        }
        return ThreeState.fromBoolean(value);
    }
}
