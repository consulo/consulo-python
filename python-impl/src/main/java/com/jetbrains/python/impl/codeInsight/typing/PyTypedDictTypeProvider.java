// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typing;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionNavigator;
import com.jetbrains.python.impl.psi.impl.PyEvaluator;
import com.jetbrains.python.impl.psi.impl.stubs.PyTypedDictStubImpl;
import com.jetbrains.python.impl.psi.stubs.PyTypedDictFieldStub;
import com.jetbrains.python.impl.psi.stubs.PyTypedDictStub;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.StubAwareComputation;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.*;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.psi.FileContextUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;

import static com.jetbrains.python.impl.psi.types.PyTypedDictType.TYPED_DICT_CLOSED_PARAMETER;
import static com.jetbrains.python.impl.psi.types.PyTypedDictType.TYPED_DICT_EXTRA_ITEMS_PARAMETER;
import static com.jetbrains.python.impl.psi.types.PyTypedDictType.TYPED_DICT_TOTAL_PARAMETER;

@ExtensionImpl(id = "pyTypedDictTypeProvider", order = "after pyiTypeProvider")
public class PyTypedDictTypeProvider extends PyTypeProviderBase {
    /**
     * typealias TDFields = LinkedHashMap<String, PyTypedDictType.FieldTypeAndTotality>
     */
    private static final class TDFields extends LinkedHashMap<String, PyTypedDictType.FieldTypeAndTotality> {
        TDFields() {
        }

        TDFields(Map<String, PyTypedDictType.FieldTypeAndTotality> map) {
            super(map);
        }
    }

    @Override
    public @Nullable PyType getReferenceExpressionType(PyReferenceExpression referenceExpression, TypeEvalContext context) {
        PyType type = getTypedDictTypeForCallee(referenceExpression, context);
        return type != null ? type : getTypedDictMemberType(referenceExpression, context);
    }

    @Override
    public @Nullable Ref<PyType> getReferenceType(PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
        PyType type;
        if (referenceTarget instanceof PyClass pyClass) {
            type = getTypedDictTypeForClass(pyClass, true, context);
        }
        else if (referenceTarget instanceof PyTargetExpression targetExpression) {
            type = getTypedDictTypeForTarget(targetExpression, context);
        }
        else {
            type = null;
        }
        return PyTypeUtil.notNullToRef(type);
    }

    @Override
    public @Nullable Ref<@Nullable PyType> prepareCalleeTypeForCall(@Nullable PyType type, PyExpression callee, TypeEvalContext context) {
        return type instanceof PyTypedDictType ? Ref.create(type) : null;
    }

    public static final class Helper {
        private Helper() {
        }

        public static boolean nameIsTypedDict(@Nullable String name) {
            return PyTypingTypeProvider.TYPED_DICT.equals(name) || PyTypingTypeProvider.TYPED_DICT_EXT.equals(name);
        }

        public static boolean isGetMethodToOverride(PyCallExpression call, TypeEvalContext context) {
            PyExpression callee = call.getCallee();
            return callee != null && PyTypingTypeProvider.resolveToQualifiedNames(callee, context)
                .stream()
                .anyMatch(it -> "dict.get".equals(PyNames.FQN.unqualifyBuiltinName(it)) /* py3 */ ||
                                PyTypingTypeProvider.MAPPING_GET.equals(it) /* py2 */);
        }

        public static boolean isTypedDict(PyExpression expression, TypeEvalContext context) {
            return PyTypingTypeProvider.resolveToQualifiedNames(expression, context).stream().anyMatch(Helper::nameIsTypedDict);
        }

        public static boolean isTypingTypedDictInheritor(PyClass cls, TypeEvalContext context) {
            if (checkIfClassIsDirectTypedDictInheritor(cls, context)) {
                return true;
            }
            List<PyClassLikeType> ancestors = cls.getAncestorTypes(context);

            for (PyClassLikeType type : ancestors) {
                if (type instanceof PyTypedDictType || nameIsTypedDict(type != null ? type.getClassQName() : null)) {
                    return true;
                }
            }
            return false;
        }

        public static List<TypedDictFieldQualifier> getTypedDictFieldQualifiers(PySubscriptionExpression expression,
                                                                                TypeEvalContext context) {
            List<TypedDictFieldQualifier> result = new ArrayList<>();
            expression.accept(new PyRecursiveElementVisitor() {
                @Override
                public void visitPySubscriptionExpression(PySubscriptionExpression node) {
                    Collection<String> resolvedNames = PyTypingTypeProvider.resolveToQualifiedNames(node.getOperand(), context);
                    if (resolvedNames.stream()
                        .anyMatch(name -> PyTypingTypeProvider.REQUIRED.equals(name) || PyTypingTypeProvider.REQUIRED_EXT.equals(name))) {
                        result.add(TypedDictFieldQualifier.REQUIRED);
                    }
                    else if (resolvedNames.stream()
                        .anyMatch(name -> PyTypingTypeProvider.NOT_REQUIRED.equals(name) || PyTypingTypeProvider.NOT_REQUIRED_EXT.equals(name))) {
                        result.add(TypedDictFieldQualifier.NOT_REQUIRED);
                    }
                    else if (resolvedNames.stream()
                        .anyMatch(name -> PyTypingTypeProvider.READONLY.equals(name) || PyTypingTypeProvider.READONLY_EXT.equals(name))) {
                        result.add(TypedDictFieldQualifier.READ_ONLY);
                    }
                    super.visitPySubscriptionExpression(node);
                }
            });
            return result;
        }

        public enum TypedDictFieldQualifier {
            REQUIRED,
            NOT_REQUIRED,
            READ_ONLY
        }

        public static @Nullable PyTypedDictType getTypedDictTypeForResolvedElement(PsiElement resolved, TypeEvalContext context) {
            // TODO consulo: Ref.deref missing
            Ref<PyTypedDictType> ref = PyUtil.getParameterizedCachedValue(resolved, context, typeEvalContext -> {
                PyTypedDictType type = resolved instanceof PyClass pyClass ? getTypedDictTypeForClass(pyClass, false, typeEvalContext) : null;
                return Ref.create(type);
            });
            return ref != null ? ref.get() : null;
        }
    }

    private static @Nullable PyTypedDictType getTypedDictTypeForTarget(PyTargetExpression target, TypeEvalContext context) {
        return StubAwareComputation.on(target)
            .withCustomStub(it -> it.getCustomStub(PyTypedDictStub.class))
            .overStub(it -> getTypedDictTypeFromStub(target, it))
            .withStubBuilder(it -> PyTypedDictStubImpl.create(it))
            .compute(context);
    }

    private enum TypedDictMethod {
        KEYS("dict.keys"),
        VALUES("dict.values"),
        ITEMS("dict.items"),
        POPITEM("typing.MutableMapping.popitem");

        private final String qualifiedName;

        TypedDictMethod(String qualifiedName) {
            this.qualifiedName = qualifiedName;
        }

        public String getQualifiedName() {
            return qualifiedName;
        }

        private static final Map<String, TypedDictMethod> BY_QUALIFIED_NAME = new HashMap<>();

        static {
            for (TypedDictMethod it : values()) {
                BY_QUALIFIED_NAME.put(it.qualifiedName, it);
            }
        }

        static @Nullable TypedDictMethod fromQualifiedName(String name) {
            return BY_QUALIFIED_NAME.get(name);
        }

        static @Nullable TypedDictMethod fromResolvedNames(Collection<String> names) {
            for (String it : names) {
                TypedDictMethod method = fromQualifiedName(it);
                if (method != null) {
                    return method;
                }
            }
            return null;
        }
    }

    private static @Nullable PyCallableType getTypedDictMemberType(PsiElement referenceTarget, TypeEvalContext context) {
        PyCallExpression callExpression =
            context.maySwitchToAST(referenceTarget) ? PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceTarget) : null;
        if (!(callExpression != null && callExpression.getCallee() instanceof PyQualifiedExpression callee)) {
            return null;
        }
        PyExpression receiver = callee.getQualifier();
        if (receiver == null) {
            return null;
        }
        PyType receiverType = context.getType(receiver);
        if (!(receiverType instanceof PyTypedDictType receiverTypedDictType)) {
            return null;
        }

        if (Helper.isGetMethodToOverride(callExpression, context)) {
            return buildGetMethodType(referenceTarget, callExpression, receiverTypedDictType, context);
        }

        Collection<String> resolvedNames = PyTypingTypeProvider.resolveToQualifiedNames(callee, context);
        TypedDictMethod method = TypedDictMethod.fromResolvedNames(resolvedNames);
        if (method == null) {
            return null;
        }

        PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(referenceTarget);
        PyClassType strType = builtinCache.getStrType();
        if (strType == null) {
            return null;
        }
        PyType valueType = collectTypedDictValueType(receiverTypedDictType, builtinCache, context);
        if (valueType == null) {
            return null;
        }

        PyType returnType;
        switch (method) {
            case KEYS: {
                PyClassType listType = builtinCache.getListType();
                if (listType == null) {
                    return null;
                }
                returnType = new PyCollectionTypeImpl(listType.getPyClass(), false, List.of(strType));
                break;
            }
            case VALUES: {
                PyClassType listType = builtinCache.getListType();
                if (listType == null) {
                    return null;
                }
                returnType = new PyCollectionTypeImpl(listType.getPyClass(), false, List.of(valueType));
                break;
            }
            case ITEMS: {
                PyClassType listType = builtinCache.getListType();
                if (listType == null) {
                    return null;
                }
                PyTupleType tupleType = PyTupleType.create(referenceTarget, List.of(strType, valueType));
                if (tupleType == null) {
                    return null;
                }
                returnType = new PyCollectionTypeImpl(listType.getPyClass(), false, List.of(tupleType));
                break;
            }
            case POPITEM: {
                PyTupleType tupleType = PyTupleType.create(referenceTarget, List.of(strType, valueType));
                if (tupleType == null) {
                    return null;
                }
                returnType = tupleType;
                break;
            }
            default:
                return null;
        }

        return new PyCallableTypeImpl(Collections.<PyCallableParameter>emptyList(), returnType);
    }

    private static PyCallableType buildGetMethodType(
        PsiElement referenceTarget,
        PyCallExpression callExpression,
        PyTypedDictType typedDictType,
        TypeEvalContext context
    ) {
        List<PyCallableParameter> parameters = new ArrayList<>();
        PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(referenceTarget);
        parameters.add(PyCallableParameterImpl.nonPsi("key", builtinCache.getStrType()));
        parameters.add(PyCallableParameterImpl.nonPsi("default", PyAnyType.getAny(), PyNames.NONE));
        String key = PyEvaluator.evaluate(callExpression.getArgument(0, "key", PyExpression.class), String.class);
        PyExpression defaultArgument = callExpression.getArgument(1, "default", PyExpression.class);
        PyType defaultType = defaultArgument != null ? context.getType(defaultArgument) : builtinCache.getNoneType();
        PyTypedDictType.FieldTypeAndTotality valueTypeAndTotality = typedDictType.fields(context).get(key);
        PyType returnType;
        if (valueTypeAndTotality == null) {
            returnType = defaultType;
        }
        else if (Boolean.TRUE.equals(valueTypeAndTotality.getQualifiers().isRequired())) {
            returnType = valueTypeAndTotality.getType();
        }
        else {
            returnType = PyUnionType.unionOrUnknown(valueTypeAndTotality.getType(), defaultType);
        }
        return new PyCallableTypeImpl(parameters, returnType);
    }

    private static @Nullable PyType collectTypedDictValueType(
        PyTypedDictType typedDictType,
        PyBuiltinCache builtinCache,
        TypeEvalContext context
    ) {
        List<@Nullable PyType> valueTypes = new ArrayList<>();

        for (PyTypedDictType.FieldTypeAndTotality field : typedDictType.fields(context).values()) {
            valueTypes.add(field.getType());
        }

        if (!typedDictType.isClosed() && typedDictType.extraItemsType(context) != null) {
            valueTypes.add(typedDictType.extraItemsType(context));
        }

        List<@Nullable PyType> nonNullTypes = new ArrayList<>();
        for (PyType it : valueTypes) {
            if (it != null) {
                nonNullTypes.add(it);
            }
        }
        if (nonNullTypes.isEmpty()) {
            return builtinCache.getObjectType();
        }

        return PyUnionType.unionOrUnknown(nonNullTypes);
    }

    private static @Nullable PyType getTypedDictTypeForCallee(PyReferenceExpression referenceExpression, TypeEvalContext context) {
        if (PyCallExpressionNavigator.getPyCallExpressionByCallee(referenceExpression) == null) {
            return null;
        }

        if (Helper.isTypedDict(referenceExpression, context)) {
            PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(referenceExpression);
            LanguageLevel languageLevel = LanguageLevel.forElement(referenceExpression);

            PyClassType dictType = builtinCache.getDictType();
            PyType strToTypeDictType;
            if (dictType != null) {
                strToTypeDictType =
                    new PyCollectionTypeImpl(dictType.getPyClass(), false, Arrays.asList(builtinCache.getStrType(), builtinCache.getTypeType()));
            }
            else {
                strToTypeDictType = null;
            }

            List<PyCallableParameter> parameters = List.of(
                PyCallableParameterImpl.nonPsi("typename", builtinCache.getStringType(languageLevel)),
                PyCallableParameterImpl.nonPsi("fields", strToTypeDictType),
                PyCallableParameterImpl.keywordOnlySeparatorNonPsi(),
                PyCallableParameterImpl.positionalOnlySeparatorNonPsi(),
                PyCallableParameterImpl.nonPsi(TYPED_DICT_TOTAL_PARAMETER, builtinCache.getBoolType(), PyNames.TRUE),
                PyCallableParameterImpl.nonPsi(TYPED_DICT_CLOSED_PARAMETER, builtinCache.getBoolType(), PyNames.FALSE),
                PyCallableParameterImpl.nonPsi(
                    TYPED_DICT_EXTRA_ITEMS_PARAMETER,
                    builtinCache.getTypeType(),
                    PyNames.NONE
                )
            );

            return new PyCallableTypeImpl(parameters, PyAnyType.getUnknown());
        }

        return null;
    }

    private static @Nullable PyTypedDictType getTypedDictTypeForClass(
        PyClass cls,
        boolean isDefinition,
        TypeEvalContext context
    ) {
        if (!Helper.isTypingTypedDictInheritor(cls, context)) {
            return null;
        }

        // TODO consulo: Ref.deref missing
        Ref<Pair<PyTypedDictType, PyTypedDictType>> formsRef = PyUtil.getParameterizedCachedValue(cls, context, evalContext -> {
            PyTypedDictType definition = createTypedDictTypeForClass(cls, evalContext);
            return Ref.create(definition != null ? Pair.create(definition, definition.toInstance()) : null);
        });
        Pair<PyTypedDictType, PyTypedDictType> forms = formsRef != null ? formsRef.get() : null;
        if (forms == null) {
            return null;
        }
        return isDefinition ? forms.getFirst() : forms.getSecond();
    }

    private static @Nullable PyTypedDictType createTypedDictTypeForClass(PyClass cls, TypeEvalContext context) {

        List<PyTypedDictType> typedDictAncestors = new ArrayList<>();
        for (PyClassLikeType it : typedDictAncestors(cls, context)) {
            if (it instanceof PyTypedDictType typedDictType) {
                typedDictAncestors.add(typedDictType);
            }
        }

        String extraItemsText = getSuperClassKeywordArgumentText(cls, TYPED_DICT_EXTRA_ITEMS_PARAMETER);
        String closedText = getSuperClassKeywordArgumentText(cls, TYPED_DICT_CLOSED_PARAMETER);

        Function<TypeEvalContext, PyTypedDictType.FieldTypeAndTotality> extraItemsProvider = evalContext -> {
            PyType type;
            if (extraItemsText != null) {
                type = PyTypeUtil.derefOrUnknown(PyTypingTypeProvider.getStringBasedType(extraItemsText, cls, evalContext));
            }
            else {
                type = null;
                for (PyTypedDictType it : typedDictAncestors) {
                    type = it.extraItemsType(evalContext);
                    if (type != null) {
                        break;
                    }
                }
                if (type == null) {
                    type = PyAnyType.getUnknown();
                }
            }
            return extraItems(type, extraItemsText, cls, evalContext);
        };

        boolean closed;
        if (PyNames.TRUE.equals(closedText)) {
            closed = true;
        }
        else if (PyNames.FALSE.equals(closedText)) {
            closed = false;
        }
        else {
            closed = !typedDictAncestors.isEmpty() && typedDictAncestors.get(0).isClosed();
        }

        String name = cls.getName();
        if (name == null) {
            return null;
        }
        PyClassType dictType = PyBuiltinCache.getInstance(cls).getDictType();
        if (dictType == null) {
            return null;
        }
        PyClass dictClass = dictType.getPyClass();
        return new PyTypedDictType(
            name,
            evalContext -> collectFields(cls, evalContext),
            dictClass,
            true,
            cls,
            closed,
            extraItemsProvider,
            ownTypeParameters(cls, context)
        );
    }

    /**
     * The ancestors up to `TypedDict` itself, past which come `dict` and its own hierarchy.
     * <p>
     * Without AST the ancestors are resolved from the superclass names in the stub, so `TypedDict` is the resolved class rather than
     * a synthetic {@link com.jetbrains.python.impl.PyCustomType} and a TypedDict ancestor is a plain {@link PyClassType}. The boundary is
     * found by name so that both modes see the same slice, and the arguments dropped along with the expressions are put back by
     * {@link #specializeTypedDictAncestors}.
     */
    private static List<@Nullable PyClassLikeType> typedDictAncestors(PyClass cls, TypeEvalContext context) {
        return PyUtil.getParameterizedCachedValue(cls, context, evalContext -> {
            List<PyClassLikeType> ancestors = cls.getAncestorTypes(evalContext);
            int boundary = -1;
            for (int i = 0; i < ancestors.size(); i++) {
                PyClassLikeType it = ancestors.get(i);
                if (Helper.nameIsTypedDict(it != null ? it.getClassQName() : null)) {
                    boundary = i;
                    break;
                }
            }
            List<@Nullable PyClassLikeType> inheritedAsTypedDict = boundary >= 0 ? ancestors.subList(0, boundary) : ancestors;
            return specializeTypedDictAncestors(cls, inheritedAsTypedDict, evalContext);
        });
    }

    /**
     * {@link PyClass#getAncestorTypes} reports a bare `Base` for `class Child(Base[int])`. This function reads each subscripted
     * superclass expression as a type hint, and puts the parameterized ancestor back.
     * <p>
     * A TypedDict takes its items from the ancestor itself, so it specializes an inherited item as soon as the ancestors are
     * known. A generic class instead waits until somebody asks for the attribute.
     * <p>
     * This runs on the finished ancestor list. Evaluation of `Base[T]` anchors `T` in the scope that declares it. That asks
     * for the type parameters of {@code cls}, and through them for the ancestors that are still being computed.
     */
    private static List<@Nullable PyClassLikeType> specializeTypedDictAncestors(
        PyClass cls,
        List<@Nullable PyClassLikeType> ancestors,
        TypeEvalContext context
    ) {
        Map<PyClass, PyTypedDictType> specializedBases = new HashMap<>();
        for (PyExpression expression : PyTypingTypeProvider.getSuperClassExpressions(cls)) {
            if (!(expression instanceof PySubscriptionExpression)) {
                continue;
            }
            Ref<PyType> baseTypeRef = PyTypingTypeProvider.getType(expression, context);
            // TODO consulo: Ref.deref missing
            if (!((baseTypeRef != null ? baseTypeRef.get() : null) instanceof PyTypedDictType baseType)) {
                continue;
            }
            if (!baseType.isParameterized()) {
                continue;
            }
            if (!(baseType.getDeclarationElement() instanceof PyClass baseClass)) {
                continue;
            }
            specializedBases.put(baseClass, baseType);
        }
        if (specializedBases.isEmpty()) {
            return ancestors;
        }

        List<@Nullable PyClassLikeType> result = new ArrayList<>();
        for (PyClassLikeType it : ancestors) {
            PyClass declaringClass = it != null ? declaringClass(it) : null;
            PyTypedDictType specialized = declaringClass != null ? specializedBases.get(declaringClass) : null;
            result.add(specialized != null ? specialized : it);
        }
        return result;
    }

    /**
     * A TypedDict type is built on the `dict` class, so it is identified by its own declaration rather than by {@link PyClassType#getPyClass}.
     */
    private static @Nullable PyClass declaringClass(PyClassLikeType type) {
        if (type instanceof PyTypedDictType typedDictType) {
            return typedDictType.getDeclarationElement() instanceof PyClass pyClass ? pyClass : null;
        }
        else if (type instanceof PyClassType classType) {
            return classType.getPyClass();
        }
        else {
            return null;
        }
    }

    /**
     * Read from the declaration, not from the items, which a recursive TypedDict cannot evaluate while it is being built.
     */
    private static List<@Nullable PyType> ownTypeParameters(PyClass cls, TypeEvalContext context) {
        PyClassType genericDefinitionType = PyTypeChecker.findGenericDefinitionType(cls, context);
        return genericDefinitionType != null ? genericDefinitionType.getTypeArguments() : Collections.emptyList();
    }

    /**
     * `extra_items` is a keyword argument rather than an assignment, so the item it stands for has no value expression.
     */
    private static PyTypedDictType.FieldTypeAndTotality extraItems(
        @Nullable PyType type,
        @Nullable String extraItemsText,
        PsiElement anchor,
        TypeEvalContext context
    ) {
        PyTypedDictType.TypedDictFieldQualifiers qualifiers =
            extraItemsText != null ? getStringBasedTypedDictQualifiers(extraItemsText, anchor, context) : null;
        if (qualifiers == null) {
            qualifiers = new PyTypedDictType.TypedDictFieldQualifiers();
        }
        return new PyTypedDictType.FieldTypeAndTotality(null, type, qualifiers);
    }

    private static @Nullable String getSuperClassKeywordArgumentText(PyClass cls, String name) {
        // This method is stub-friendly
        PyClassStub stub = cls.getStub();
        if (stub != null) {
            for (String entry : stub.getSuperClassesText()) {
                int eqIndex = entry.indexOf('=');
                if (eqIndex < 0) {
                    continue;
                }
                if (entry.substring(0, eqIndex).trim().equals(name)) {
                    return entry.substring(eqIndex + 1).trim();
                }
            }
            return null;
        }
        PyArgumentList superClassExpressionList = cls.getSuperClassExpressionList();
        PyKeywordArgument keywordArgument = superClassExpressionList != null ? superClassExpressionList.getKeywordArgument(name) : null;
        PyExpression valueExpression = keywordArgument != null ? keywordArgument.getValueExpression() : null;
        return valueExpression != null ? valueExpression.getText() : null;
    }


    private static TDFields collectFields(PyClass cls, TypeEvalContext context) {
        Map<String, PyTypedDictType.FieldTypeAndTotality> fields = new LinkedHashMap<>();
        for (PyClassLikeType ancestorType : typedDictAncestors(cls, context)) {
            if (ancestorType instanceof PyTypedDictType typedDictType) {
                fields.putAll(typedDictType.fields(context));
            }
            // Without AST a TypedDict ancestor is a plain PyClassType, see typedDictAncestors.
            else if (ancestorType instanceof PyClassType classType) {
                if (Helper.isTypingTypedDictInheritor(classType.getPyClass(), context)) {
                    fields.putAll(collectTypingTDInheritorFields(classType.getPyClass(), context));
                }
            }
        }
        fields.putAll(collectDeclaredFields(cls, context));
        return new TDFields(fields);
    }

    private static TDFields collectTypingTDInheritorFields(PyClass cls, TypeEvalContext context) {
        PyType type = cls.getType(context);
        if (type instanceof PyTypedDictType typedDictType) {
            return new TDFields(typedDictType.fields(context));
        }
        return collectDeclaredFields(cls, context);
    }

    /**
     * Scans the class's own declarations instead of going through {@link PyClass#getType}, which for {@code cls} would re-enter the very
     * TypedDict type whose items are being computed — the recursion that used to leave a cut `Unknown` in the cache.
     */
    private static TDFields collectDeclaredFields(PyClass cls, TypeEvalContext context) {
        List<Pair<PyTargetExpression, PyTypedDictType.TypedDictFieldQualifiers>> fields = new ArrayList<>();
        boolean totality = getTotality(cls);
        cls.processClassLevelDeclarations((element, state) -> {
            if (element instanceof PyTargetExpression targetExpression) {
                PyTargetExpressionStub stub = targetExpression.getStub();
                if (context.maySwitchToAST(cls) || stub == null) {
                    PyAnnotation annotation = targetExpression.getAnnotation();
                    if (annotation != null) {
                        fields.add(Pair.create(targetExpression, checkTypeSpecification(annotation.getValue(), context, totality)));
                    }
                }
                else {
                    String stubAnnotation = stub.getAnnotation();
                    if (stubAnnotation != null) {
                        PyExpression annotation = PyUtil.createExpressionFromFragment(stubAnnotation, cls);
                        fields.add(Pair.create(stub.getPsi(), checkTypeSpecification(annotation, context, totality)));
                    }
                }
            }
            return true;
        });


        TDFields result = new TDFields();
        for (Pair<PyTargetExpression, PyTypedDictType.TypedDictFieldQualifiers> field : fields) {
            result.put(field.getFirst().getName(),
                       new PyTypedDictType.FieldTypeAndTotality(field.getFirst(), context.getType(field.getFirst()), field.getSecond()));
        }
        return result;
    }

    private static PyTypedDictType.TypedDictFieldQualifiers checkTypeSpecification(
        @Nullable PyExpression annotation,
        TypeEvalContext context,
        boolean totality
    ) {
        if (annotation instanceof PySubscriptionExpression subscriptionExpression) {
            return parseTypedDictFieldQualifiers(subscriptionExpression, context, totality);
        }
        return new PyTypedDictType.TypedDictFieldQualifiers(totality);
    }

    private static PyTypedDictType.TypedDictFieldQualifiers parseTypedDictFieldQualifiers(PySubscriptionExpression expression,
                                                                                         TypeEvalContext context) {
        return parseTypedDictFieldQualifiers(expression, context, null);
    }

    private static PyTypedDictType.TypedDictFieldQualifiers parseTypedDictFieldQualifiers(PySubscriptionExpression expression,
                                                                                         TypeEvalContext context,
                                                                                         @Nullable Boolean totality) {
        Boolean isRequired = totality;
        boolean isReadOnly = false;
        boolean hasExplicitRequiredQualifier = false;

        for (Helper.TypedDictFieldQualifier qualifier : Helper.getTypedDictFieldQualifiers(expression, context)) {
            switch (qualifier) {
                case REQUIRED: {
                    isRequired = true;
                    hasExplicitRequiredQualifier = true;
                    break;
                }
                case NOT_REQUIRED: {
                    isRequired = false;
                    hasExplicitRequiredQualifier = true;
                    break;
                }
                case READ_ONLY:
                    isReadOnly = true;
                    break;
            }
        }
        return new PyTypedDictType.TypedDictFieldQualifiers(isRequired, isReadOnly, hasExplicitRequiredQualifier);
    }

    /**
     * This method helps to avoid the situation when two processes try to get an element's type simultaneously
     * and one of them ends up with null.
     */
    private static boolean checkIfClassIsDirectTypedDictInheritor(PyClass cls, TypeEvalContext context) {
        PyClassStub stub = cls.getStub();
        if (context.maySwitchToAST(cls) || stub == null) {
            for (PyExpression it : cls.getSuperClassExpressions()) {
                if (Helper.isTypedDict(it, context)) {
                    return true;
                }
            }
            return false;
        }
        else {
            for (String it : stub.getSuperClassesText()) {
                PyExpression superClassExpression = PyUtil.createExpressionFromFragment(it, cls);
                if (superClassExpression == null) {
                    return false;
                }
                if (Helper.isTypedDict(superClassExpression, context)) {
                    return true;
                }
            }
            return false;
        }
    }


    private static boolean getTotality(PyClass cls) {
        PyClassStub stub = cls.getStub();
        if (stub != null) {
            return !stub.getSuperClassesText().contains("total=False");
        }
        else {
            PyArgumentList superClassExpressionList = cls.getSuperClassExpressionList();
            PyKeywordArgument total = superClassExpressionList != null ? superClassExpressionList.getKeywordArgument("total") : null;
            return total != null && total.getValueExpression() instanceof PyBoolLiteralExpression boolLiteralExpression
                ? boolLiteralExpression.getValue()
                : true;
        }
    }

    private static @Nullable PyTypedDictType getTypedDictTypeFromStub(
        PyTargetExpression target,
        @Nullable PyTypedDictStub stub
    ) {
        if (stub == null) {
            return null;
        }

        PyClassType dictType = PyBuiltinCache.getInstance(target).getDictType();
        if (dictType == null) {
            return null;
        }
        PyClass dictClass = dictType.getPyClass();

        String extraItemsText = stub.getExtraItemsType();
        Function<TypeEvalContext, PyTypedDictType.FieldTypeAndTotality> extraItemsProvider = evalContext -> {
            PyType type = PyTypeUtil.derefOrUnknown(
                extraItemsText != null ? PyTypingTypeProvider.getStringBasedType(extraItemsText, target, evalContext) : null);
            return extraItems(type, extraItemsText, target, evalContext);
        };

        return new PyTypedDictType(
            stub.getName(),
            evalContext -> parseTypedDictFields(target, stub.getFields(), evalContext, stub.isRequired()),
            dictClass,
            true,
            target,
            stub.isClosed(),
            extraItemsProvider,
            Collections.emptyList()
        );
    }

    private static TDFields parseTypedDictFields(
        PsiElement anchor,
        List<PyTypedDictFieldStub> fields,
        TypeEvalContext context,
        boolean total
    ) {
        TDFields result = new TDFields();
        for (PyTypedDictFieldStub field : fields) {
            result.put(field.name(), parseTypedDictField(anchor, field.type(), context, total));
        }
        return result;
    }

    private static PyTypedDictType.FieldTypeAndTotality parseTypedDictField(
        PsiElement anchor,
        @Nullable String type,
        TypeEvalContext context,
        boolean total
    ) {
        if (type == null) {
            return new PyTypedDictType.FieldTypeAndTotality(null, PyAnyType.getUnknown());
        }

        Pair<@Nullable Ref<PyType>, PyTypedDictType.@Nullable TypedDictFieldQualifiers> valueTypeWithQualifiers =
            getStringBasedTypeForTypedDict(type, anchor, context);
        if (valueTypeWithQualifiers == null) {
            return new PyTypedDictType.FieldTypeAndTotality(null, PyAnyType.getUnknown());
        }

        PyType pyType = PyTypeUtil.derefOrUnknown(valueTypeWithQualifiers.getFirst());
        PyTypedDictType.TypedDictFieldQualifiers requiredField = valueTypeWithQualifiers.getSecond();

        Boolean requiredFieldIsRequired = requiredField != null ? requiredField.isRequired() : null;
        boolean isRequired = requiredFieldIsRequired != null ? requiredFieldIsRequired : total;
        PyTypedDictType.TypedDictFieldQualifiers qualifiers =
            new PyTypedDictType.TypedDictFieldQualifiers(isRequired, requiredField != null && requiredField.isReadOnly());
        return new PyTypedDictType.FieldTypeAndTotality(null, pyType, qualifiers);
    }

    private static @Nullable Pair<@Nullable Ref<PyType>, PyTypedDictType.@Nullable TypedDictFieldQualifiers> getStringBasedTypeForTypedDict(
        String contents,
        PsiElement anchor,
        TypeEvalContext context
    ) {
        PyExpression expr = createStringBasedTypedDictExpression(contents, anchor);
        if (expr == null) {
            return null;
        }
        PyTypedDictType.TypedDictFieldQualifiers qualifiers = null;
        if (expr instanceof PySubscriptionExpression subscriptionExpression) {
            qualifiers = parseTypedDictFieldQualifiers(subscriptionExpression, context);
        }
        return Pair.create(PyTypingTypeProvider.getType(expr, context), qualifiers);
    }

    private static PyTypedDictType.@Nullable TypedDictFieldQualifiers getStringBasedTypedDictQualifiers(
        String contents,
        PsiElement anchor,
        TypeEvalContext context
    ) {
        return createStringBasedTypedDictExpression(contents, anchor) instanceof PySubscriptionExpression subscriptionExpression
            ? parseTypedDictFieldQualifiers(subscriptionExpression, context)
            : null;
    }

    private static @Nullable PyExpression createStringBasedTypedDictExpression(String contents, PsiElement anchor) {
        PsiFile file = FileContextUtil.getContextFile(anchor);
        if (file == null) {
            return null;
        }
        return PyUtil.createExpressionFromFragment(contents, file);
    }
}
