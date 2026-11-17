// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.util.lang.function.Condition;
import consulo.util.lang.EmptyRunnable;
import consulo.util.dataholder.Key;
import consulo.application.util.RecursionManager;
import consulo.util.lang.ref.Ref;
import consulo.util.dataholder.UserDataHolderBase;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ObjectUtil;
import consulo.language.util.ProcessingContext;
import java.util.function.Predicate;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.impl.PyCustomType;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.impl.codeInsight.PyCustomMemberUtils;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyAnnotationOwner;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElsePart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatementPart;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypeCommentOwner;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.psi.impl.PyResolveResultRater;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.impl.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.impl.psi.resolve.CompletionVariantsProcessor;
import com.jetbrains.python.impl.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.impl.psi.resolve.PyResolveProcessor;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.impl.refactoring.PyDefUseUtil;
import com.jetbrains.python.toolbox.Maybe;
import one.util.streamex.EntryStream;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyCallableParameterVariadicType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassMembersProvider;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyOverridingAncestorsClassMembersProvider;
import com.jetbrains.python.psi.types.PyOverridingClassMembersProvider;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeMember;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.util.collection.Lists;

import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;


public class PyClassTypeImpl extends UserDataHolderBase implements PyClassType {

    protected final PyClass myClass;
    protected final boolean myIsDefinition;
    protected final List<PyType> myTypeArguments;
    private final Ref<Integer> myHashCode = new Ref<>();

    /**
     * Describes a class-based type. Since everything in Python is an instance of some class, this type pretty much completes
     * the type system :)
     * Note that classes' and instances' member list can change during execution, so it is important to construct an instance of PyClassType
     * right in the place of reference, so that such changes could possibly be accounted for.
     *
     * @param source       PyClass which defines this type. For builtin or external classes, skeleton files contain the definitions.
     * @param isDefinition whether this type describes an instance or a definition of the class.
     */
    public PyClassTypeImpl(PyClass source, boolean isDefinition) {
        this(source, isDefinition, Collections.emptyList());
    }

    /**
     * @param source        PyClass which defines this type. For builtin or external classes, skeleton files contain the definitions.
     * @param isDefinition  whether this type describes an instance or a definition of the class.
     * @param typeArguments concrete type arguments of this class instance, e.g. {@code [int]} for {@code list[int]}.
     */
    public PyClassTypeImpl(PyClass source, boolean isDefinition, List<? extends PyType> typeArguments) {
        PyClass originalElement = CompletionUtilCore.getOriginalElement(source);
        myClass = originalElement != null ? originalElement : source;
        myIsDefinition = isDefinition;
        for (var argument : typeArguments) {
            PyAnyType.validate(argument);
        }
        myTypeArguments = new ArrayList<>(typeArguments);
    }

    /**
     * Creates a new instance of the same runtime type as this one. Subclasses (e.g. {@link PyCollectionTypeImpl}) override
     * this so that derived types produced by {@link #toInstance()}, {@link #toClass()} and call resolution preserve their
     * concrete class.
     */
    protected PyClassTypeImpl createInstance(PyClass source,
                                                      boolean isDefinition,
                                                      List<? extends PyType> typeArguments) {
        return new PyClassTypeImpl(source, isDefinition, typeArguments);
    }

    @Override
    public List<PyType> getTypeArguments() {
        return Collections.unmodifiableList(myTypeArguments);
    }

    @Override
    public @Nullable PyType getIteratedItemType() {
        return PyTypeChecker.getIteratedItemType(this, TypeEvalContext.codeInsightFallback(getPyClass().getProject()));
    }

    public <T> PyClassTypeImpl withUserData(Key<T> key, T value) {
        putUserData(key, value);
        return this;
    }

    /**
     * @return a PyClass which defined this type.
     */
    @Override
    public PyClass getPyClass() {
        return myClass;
    }


    @Override
    public PyQualifiedNameOwner getDeclarationElement() {
        return getPyClass();
    }

    /**
     * @return whether this type refers to an instance or a definition of the class.
     */
    @Override
    public boolean isDefinition() {
        return myIsDefinition;
    }

    @Override
    public PyClassType toInstance() {
        return myIsDefinition ? withUserDataCopy(createInstance(myClass, false, myTypeArguments)) : this;
    }

    @Override
    public PyClassType toClass() {
        return myIsDefinition ? this : createInstance(myClass, true, myTypeArguments);
    }

    /**
     * Wrap new instance to copy user data to it
     */
    <T extends PyClassTypeImpl> T withUserDataCopy(T newInstance) {
        newInstance.setUserMap(getUserMap());
        return newInstance;
    }

    @Override
    public @Nullable String getClassQName() {
        return myClass.getQualifiedName();
    }

    @Override
    public List<PyClassLikeType> getSuperClassTypes(TypeEvalContext context) {
        return myClass.getSuperClassTypes(context);
    }

    @Override
    public @Nullable List<? extends RatedResolveResult> resolveMember(String name,
                                                                      @Nullable PyExpression location,
                                                                      AccessDirection direction,
                                                                      PyResolveContext resolveContext) {
        return resolveMember(name, location, direction, resolveContext, true);
    }

    @Override
    public @Nullable List<? extends RatedResolveResult> resolveMember(String name,
                                                                      @Nullable PyExpression location,
                                                                      AccessDirection direction,
                                                                      PyResolveContext resolveContext,
                                                                      boolean inherited) {
        return RecursionManager.doPreventingRecursion(
            resolveContext.allowProperties()
            ? Arrays.asList(this, name, location, direction, resolveContext)
            : Arrays.asList(this, name, location, resolveContext),
            false,
            () -> doResolveMember(name, location, direction, resolveContext, inherited)
        );
    }

    private @Nullable List<? extends RatedResolveResult> doResolveMember(String name,
                                                                         @Nullable PyExpression location,
                                                                         AccessDirection direction,
                                                                         PyResolveContext resolveContext,
                                                                         boolean inherited) {
        TypeEvalContext context = resolveContext.getTypeEvalContext();
        PsiElement classMember =
            resolveByOverridingMembersProviders(this, name, location,
                                                resolveContext); //overriding members provers have priority to normal resolve
        if (classMember != null) {
            return ResolveResultList.to(classMember);
        }

        if (resolveContext.allowProperties()) {
            Ref<ResolveResultList> resultRef = findProperty(name, direction, false, resolveContext.getTypeEvalContext());
            if (resultRef != null) {
                return resultRef.get();
            }
        }

        List<? extends RatedResolveResult> classMembers = resolveInner(myClass, myIsDefinition, name, location, context);
        if (!classMembers.isEmpty()) {
            return classMembers;
        }

        classMember = resolveByOverridingAncestorsMembersProviders(this, name, location, resolveContext);
        if (classMember != null) {
            ResolveResultList list = new ResolveResultList();
            int rate = RatedResolveResult.RATE_NORMAL;
            for (PyResolveResultRater rater : PyResolveResultRater.EP_NAME.getExtensionList()) {
                rate += rater.getMemberRate(classMember, this, context);
            }
            list.poke(classMember, rate);
            return list;
        }


        if (inherited) {
            for (PyClassLikeType type : myClass.getAncestorTypes(context)) {
                // `typing.Generic` gives the members of `object`, so it must not hide the members of the later bases
                if (type instanceof PyCustomType customType && PyTypingTypeProvider.GENERIC.equals(customType.getClassQName())) {
                    continue;
                }
                if (type instanceof PyClassType) {
                    if (!myIsDefinition) {
                        type = type.toInstance();
                    }
                    List<? extends RatedResolveResult> superMembers =
                        resolveInner(((PyClassType)type).getPyClass(), myIsDefinition, name, location, context);
                    if (!superMembers.isEmpty()) {
                        return superMembers;
                    }
                }
                if (type != null) {
                    List<? extends RatedResolveResult> results = type.resolveMember(name, location, direction, resolveContext, false);
                    if (results != null && !results.isEmpty()) {
                        return results;
                    }
                }
            }
        }

        if (inherited && !PyNames.INIT.equals(name) && !PyNames.NEW.equals(name)) {
            List<? extends RatedResolveResult> typeMembers = resolveMetaClassMember(name, location, direction, resolveContext);
            if (typeMembers != null) {
                return typeMembers;
            }
        }

        if (inherited) {
            classMember =
                resolveByMembersProviders(this, name, location,
                                          resolveContext);  //ask providers after real class introspection as providers have less priority
        }

        if (classMember != null) {
            return ResolveResultList.to(classMember);
        }

        if (inherited) {
            for (PyClassLikeType type : myClass.getAncestorTypes(context)) {
                if (type instanceof PyClassType) {
                    PyClass pyClass = ((PyClassType)type).getPyClass();
                    PsiElement superMember =
                        resolveByMembersProviders(new PyClassTypeImpl(pyClass, isDefinition()), name, location, resolveContext);

                    if (superMember != null) {
                        return ResolveResultList.to(superMember);
                    }
                }
            }
        }

        return Collections.emptyList();
    }

    private @Nullable List<? extends RatedResolveResult> resolveMetaClassMember(String name,
                                                                                @Nullable PyExpression location,
                                                                                AccessDirection direction,
                                                                                PyResolveContext resolveContext) {
        TypeEvalContext context = resolveContext.getTypeEvalContext();
        if (!myClass.isNewStyleClass(context)) {
            return null;
        }

        PyClassLikeType typeType = getMetaClassType(context, true);
        if (typeType == null) {
            return null;
        }

        if (isDefinition()) {
            List<? extends RatedResolveResult> typeMembers = typeType.resolveMember(name, location, direction, resolveContext);
            if (!ContainerUtil.isEmpty(typeMembers)) {
                return typeMembers;
            }

            List<? extends RatedResolveResult> typeInstanceMembers =
                typeType.toInstance().resolveMember(name, location, direction, resolveContext);

            if (!ContainerUtil.isEmpty(typeInstanceMembers)) {
                return typeInstanceMembers;
            }
        }
        else if (typeType instanceof PyClassType) {
            List<PyTargetExpression> typeInstanceAttributes = ((PyClassType)typeType).getPyClass().getInstanceAttributes();

            if (!ContainerUtil.isEmpty(typeInstanceAttributes)) {
                List<RatedResolveResult> typeInstanceAttributesWithSpecifiedName = typeInstanceAttributes
                    .stream()
                    .filter(member -> name.equals(member.getName()))
                    .map(member -> new RatedResolveResult(PyReferenceImpl.getRate(member, context), member))
                    .collect(Collectors.toList());

                if (!typeInstanceAttributesWithSpecifiedName.isEmpty()) {
                    return typeInstanceAttributesWithSpecifiedName;
                }
            }
        }

        return null;
    }

    private Ref<ResolveResultList> findProperty(String name,
                                                AccessDirection direction,
                                                boolean inherited,
                                                @Nullable TypeEvalContext context) {
        Ref<ResolveResultList> resultRef = null;
        Property property = myClass.findProperty(name, inherited, context);
        if (property != null) {
            Maybe<PyCallable> accessor = property.getByDirection(direction);
            if (accessor.isDefined()) {
                PyCallable accessor_code = accessor.value();
                ResolveResultList ret = new ResolveResultList();
                if (accessor_code != null) {
                    ret.poke(accessor_code, RatedResolveResult.RATE_NORMAL);
                }
                PyTargetExpression site = property.getDefinitionSite();
                if (site != null) {
                    ret.poke(site, RatedResolveResult.RATE_LOW);
                }
                if (!ret.isEmpty()) {
                    resultRef = Ref.create(ret);
                }
                else {
                    resultRef = Ref.create();
                } // property is found, but the required accessor is explicitly absent
            }
        }
        return resultRef;
    }

    @Override
    public @Nullable PyClassLikeType getMetaClassType(TypeEvalContext context, boolean inherited) {
        return myClass.getMetaClassType(inherited, context);
    }

    @Override
    public boolean isCallable() {
        if (isDefinition()) {
            return true;
        }
        if (isMethodType(this)) {
            return true;
        }
        PyClass cls = getPyClass();
        if (PyABCUtil.isSubclass(cls, PyNames.CALLABLE, null)) {
            return true;
        }
        return false;
    }

    @Override
    public @Nullable List<PyCallableParameter> getParameters(TypeEvalContext context) {
        var resolveContext = PyResolveContext.defaultContext(context);
        PyType callableType = PyCallExpressionHelper.createCallableFromClass(this, resolveContext);
        return StreamEx.of(PyTypeUtil.getCallableItems(callableType))
            .findAny()
            .map(it -> it.getParameters(context))
            .orElse(null);
    }

    @Override
    public @Nullable PyCallableParameterVariadicType getParametersType(TypeEvalContext context) {
        List<PyCallableParameter> parameters = getParameters(context);
        return parameters != null ? new PyCallableParameterListTypeImpl(parameters) : null;
    }

    private static boolean isMethodType(PyClassType type) {
        PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(type.getPyClass());
        return type.equals(builtinCache.getClassMethodType()) ||
               type.equals(builtinCache.getStaticMethodType()) ||
               type.equals(builtinCache.getObjectType(PyNames.FUNCTION));
    }

    @Override
    public @Nullable PyType getReturnType(TypeEvalContext context) {
        return getPossibleCallType(context, null);
    }

    @Override
    public @Nullable PyType getCallType(TypeEvalContext context, PyElement callSite) {
        return getPossibleCallType(context, callSite);
    }

    private @Nullable PyType getPossibleCallType(TypeEvalContext context, @Nullable PyElement callSite) {
        if (!isDefinition()) {
            return PyUtil.getReturnTypeOfMember(this, PyNames.CALL, callSite instanceof PyCallSiteExpression callSiteExpression ? callSiteExpression : null , context);
        }
        else {
            return withUserDataCopy(createInstance(getPyClass(), false, myTypeArguments));
        }
    }

    @Override
    public List<@Nullable PyClassLikeType> getAncestorTypes(TypeEvalContext context) {
        return myClass.getAncestorTypes(context);
    }

    private static @Nullable PsiElement resolveByMembersProviders(PyClassType aClass,
                                                                  String name,
                                                                  @Nullable PsiElement location,
                                                                  PyResolveContext resolveContext) {
        for (PyClassMembersProvider provider : PyClassMembersProvider.EP_NAME.getExtensionList()) {
            PsiElement resolveResult = provider.resolveMember(aClass, name, location, resolveContext);
            if (resolveResult != null) {
                return resolveResult;
            }
        }

        return null;
    }

    private static @Nullable PsiElement resolveByOverridingMembersProviders(PyClassType aClass,
                                                                            String name,
                                                                            @Nullable PsiElement location,
                                                                            PyResolveContext resolveContext) {
        for (PyClassMembersProvider provider : PyClassMembersProvider.EP_NAME.getExtensionList()) {
            if (provider instanceof PyOverridingClassMembersProvider) {
                PsiElement resolveResult = provider.resolveMember(aClass, name, location, resolveContext);
                if (resolveResult != null) {
                    return resolveResult;
                }
            }
        }

        return null;
    }

    private static @Nullable PsiElement resolveByOverridingAncestorsMembersProviders(PyClassType type,
                                                                                     String name,
                                                                                     @Nullable PyExpression location,
                                                                                     PyResolveContext resolveContext) {
        for (PyClassMembersProvider provider : PyClassMembersProvider.EP_NAME.getExtensionList()) {
            if (provider instanceof PyOverridingAncestorsClassMembersProvider) {
                PsiElement resolveResult = provider.resolveMember(type, name, location, resolveContext);
                if (resolveResult != null) {
                    return resolveResult;
                }
            }
        }
        return null;
    }

    private static List<? extends RatedResolveResult> resolveInner(PyClass cls,
                                                                            boolean isDefinition,
                                                                            String name,
                                                                            @Nullable PyExpression location,
                                                                            TypeEvalContext context) {
        PyAttributesProcessor processor = new PyAttributesProcessor(name, location);
        Map<PsiElement, PyImportedNameDefiner> results;

        if (isDefinition || cls.processInstanceLevelDeclarations(processor, location)) {
            cls.processClassObjectAttributes(processor, location);
        }
        results = processor.getResults();

        return EntryStream
            .of(results)
            .mapKeyValue(
                (element, definer) -> {
                    int rate = PyReferenceImpl.getRate(element, context);
                    return definer != null ? new ImportedResolveResult(element, rate, definer) : new RatedResolveResult(rate, element);
                }
            )
            .toList();
    }

    private static final Key<Set<PyClassType>> CTX_VISITED = Key.create("PyClassType.Visited");
    public static final Key<Boolean> CTX_SUPPRESS_PARENTHESES = Key.create("PyFunction.SuppressParentheses");

    @Override
    public Object [] getCompletionVariants(String prefix, PsiElement location, ProcessingContext context) {
        if (isRecursive(context)) {
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }
        Set<String> visited = visitedNames(context);

        PsiFile origin = location != null ? CompletionUtilCore.getOriginalOrSelf(location).getContainingFile() : null;
        TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(myClass.getProject(), origin);

        boolean withinOurClass = withinClass(getPyClass(), location);
        Condition<String> nameFilter = name -> {
            if (!withinOurClass && PyUtil.isClassPrivateName(name)) {
                return false;
            }
            if (!withinOurClass && isClassProtected(name) && prefix == null) {
                return false;
            }
            return visited.add(name);
        };

        CompletionVariantsProcessor processor =
            new CompletionVariantsProcessor(location, null, nameFilter, false, context.get(CTX_SUPPRESS_PARENTHESES) != null);

        processMembers(processor, () -> processor.setAllowedNames(myClass.getSlots(typeEvalContext)));
        List<Object> result = new ArrayList<>(processor.getResultList());

        processOwnSlots(
            slot -> {
                if (visited.add(slot)) {
                    result.add(LookupElementBuilder.create(slot));
                }
                return true;
            },
            typeEvalContext
        );

        // provided
        processProvidedMembers(
            member -> {
                if (visited.add(member.getName())) {
                    result.add(PyCustomMemberUtils.toLookUpElement(member, getName()));
                }
                return true;
            },
            location,
            typeEvalContext
        );

        // inherited
        prepareTypesForProcessingMembers(getSuperClassTypes(typeEvalContext))
            .flatArray(type -> type.getCompletionVariants(prefix, location, context))
            .into(result);

        processMetaClassMembers(
            typeType -> ContainerUtil.addAll(result, typeType.getCompletionVariants(prefix, location, context)),
            typeInstanceAttribute -> {
                result.add(LookupElementBuilder.create(typeInstanceAttribute));
                return true;
            },
            typeEvalContext
        );

        return result.toArray();
    }

    private boolean isRecursive(ProcessingContext context) {
        Set<PyClassType> types = context.get(CTX_VISITED);
        if (types == null) {
            types = new HashSet<>();
            context.put(CTX_VISITED, types);
        }
        return !types.add(this);
    }

    private static Set<String> visitedNames(ProcessingContext context) {
        Set<String> names = context.get(CTX_NAMES);
        if (names == null) {
            names = new HashSet<>();
            context.put(CTX_NAMES, names);
        }
        return names;
    }

    private static boolean withinClass(PyClass cls, @Nullable PsiElement location) {
        PyClass containingClass = PsiTreeUtil.getParentOfType(location, PyClass.class);
        if (containingClass != null) {
            containingClass = CompletionUtilCore.getOriginalElement(containingClass);
        }
        return containingClass == PyiUtil.getOriginalElementOrLeaveAsIs(cls, PyClass.class) || isInSuperCall(location);
    }

    @Override
    public void visitMembers(Predicate<? super PsiElement> processor, boolean inherited, TypeEvalContext context) {
        processMembers(processor);

        if (inherited) {
            prepareTypesForProcessingMembers(getAncestorTypes(context)).forEach(type -> type.visitMembers(processor, false, context));
            processMetaClassMembers(typeType -> typeType.visitMembers(processor, true, context), processor, context);
        }
    }

    @Override
    public Set<String> getMemberNames(boolean inherited, TypeEvalContext context) {
        // PyNamedTupleType.getMemberNames provide names that we are not able to visit,
        // so this method could not be replaced with PyClassLikeType.visitMembers

        Set<String> result = new LinkedHashSet<>();

        processMembers(
            element -> {
                if (element instanceof PsiNamedElement) {
                    ContainerUtil.addIfNotNull(result, ((PsiNamedElement)element).getName());
                }
                return true;
            }
        );

        processOwnSlots(
            slot -> {
                result.add(slot);
                return true;
            },
            context
        );

        processProvidedMembers(
            member -> {
                result.add(member.getName());
                return true;
            },
            null,
            context
        );

        if (inherited) {
            prepareTypesForProcessingMembers(getAncestorTypes(context))
                .flatCollection(type -> type.getMemberNames(false, context))
                .into(result);

            processMetaClassMembers(
                typeType -> result.addAll(typeType.getMemberNames(true, context)),
                instanceTypeAttribute -> {
                    ContainerUtil.addIfNotNull(result, instanceTypeAttribute.getName());
                    return true;
                },
                context
            );
        }

        return result;
    }

    @Override
    public List<PyTypeMember> getAllMembers(PyResolveContext resolveContext) {
        List<PyTypeMember> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        TypeEvalContext context = resolveContext.getTypeEvalContext();
        for (Map.Entry<String, Property> entry : myClass.getProperties().entrySet()) {
            visited.add(entry.getKey());
            Property property = entry.getValue();
            PyType type = property.getType(this, context);
            result.add(new PyTypeMember(property, type));
        }

        visitMembers(element -> {
            if (element instanceof PsiNamedElement namedElement) {
                if (visited.add(namedElement.getName())) {
                    PyType type = null;
                    if (element instanceof PyTypedElement typedElement) {
                        type = context.getType(typedElement);
                    }

                    boolean isClassVar = false;
                    if (element instanceof PyAnnotationOwner && element instanceof PyTypeCommentOwner) {
                        isClassVar =
                            PyTypingTypeProvider.isClassVar((PyAnnotationOwner & PyTypeCommentOwner)element, context);
                    }
                    result.add(new PyTypeMember(element, type, isClassVar));
                }
            }
            return true;
        }, false, context);

        processProvidedMembers(
            member -> {
                PyTypeMember typeMember = convertCustomMemberToTypeMember(member, resolveContext);
                if (typeMember != null) {
                    result.add(typeMember);
                }
                return true;
            },
            null,
            context
        );

        return result;
    }

    @Override
    public List<PyTypeMember> findMember(String name, PyResolveContext resolveContext) {
        Property property = myClass.findProperty(name, true, resolveContext.getTypeEvalContext());
        if (property != null) {
            PyType type = property.getType(this, resolveContext.getTypeEvalContext());
            return List.of(new PyTypeMember(property, type));
        }
        List<PyTypeMember> customMembers = new ArrayList<>();
        processProvidedMembers(
            member -> {
                if (member.getName().equals(name)) {
                    PyTypeMember typeMember = convertCustomMemberToTypeMember(member, resolveContext);
                    if (typeMember != null) {
                        customMembers.add(typeMember);
                    }
                }
                return true;
            },
            null,
            resolveContext.getTypeEvalContext()
        );
        if (!customMembers.isEmpty()) {
            return customMembers;
        }
        List<PyTypeMember> types = getMemberTypes(name, resolveContext);
        if (types != null && !types.isEmpty()) {
            return types;
        }
        return List.of();
    }

    private @Nullable PyTypeMember convertCustomMemberToTypeMember(PyCustomMember customMember,
                                                                   PyResolveContext resolveContext) {
        PsiElement element = customMember.resolve(getPyClass(), resolveContext);
        if (element != null) {
            PyType type = null;
            if (element instanceof PyTypedElement typedElement) {
                type = resolveContext.getTypeEvalContext().getType(typedElement);
            }
            return new PyTypeMember(element, type, customMember.isClassVar());
        }
        return null;
    }

    @Nullable
    private List<PyTypeMember> getMemberTypes(String name,
                                                       PyResolveContext context) {
        for (PyTypeProvider typeProvider : PyTypeProvider.EP_NAME.getExtensionList()) {
            List<PyTypeMember> types = typeProvider.getMemberTypes(this, name, null, AccessDirection.READ, context);
            if (types != null) {
                return types;
            }
        }

        List<? extends RatedResolveResult> results = resolveMember(name, null, AccessDirection.READ, context);
        if (results == null) {
            return null;
        }

        return ContainerUtil.map(results, result -> {
            PsiElement element = result.getElement();
            boolean isClassVar = false;
            if (element instanceof PyAnnotationOwner && element instanceof PyTypeCommentOwner) {
                isClassVar = PyTypingTypeProvider.isClassVar((PyAnnotationOwner & PyTypeCommentOwner)element, context.getTypeEvalContext());
            }
            if (element instanceof PyTypedElement typedElement) {
                return new PyTypeMember(typedElement,
                                        context.getTypeEvalContext().getType(typedElement), isClassVar);
            }
            else {
                return new PyTypeMember(element, null, isClassVar);
            }
        });
    }

    private void processMembers(Predicate<? super PsiElement> processor) {
        PsiScopeProcessor scopeProcessor = new PsiScopeProcessor() {
            @Override
            public boolean execute(PsiElement element, ResolveState state) {
                return processor.test(element);
            }
        };

        processMembers(scopeProcessor, EmptyRunnable.getInstance());
    }

    private void processMembers(PsiScopeProcessor scopeProcessor, Runnable afterClassLevelBeforeInstanceLevel) {
        myClass.processClassObjectAttributes(scopeProcessor, null);
        if (!isDefinition()) {
            afterClassLevelBeforeInstanceLevel.run();
            myClass.processInstanceLevelDeclarations(scopeProcessor, null);
        }
    }

    private void processOwnSlots(Predicate<? super String> processor, TypeEvalContext context) {
        if (myClass.isNewStyleClass(context)) {
            for (String slot : Lists.notNullize(myClass.getOwnSlots())) {
                if (!processor.test(slot)) {
                    return;
                }
            }
        }
    }

    private void processProvidedMembers(Predicate<? super PyCustomMember> processor,
                                        @Nullable PsiElement location,
                                        TypeEvalContext context) {
        for (PyClassMembersProvider provider : PyClassMembersProvider.EP_NAME.getExtensionList()) {
            for (PyCustomMember member : provider.getMembers(this, location, context)) {
                if (!processor.test(member)) {
                    return;
                }
            }
        }
    }

    private StreamEx<PyClassLikeType> prepareTypesForProcessingMembers(List<@Nullable PyClassLikeType> types) {
        return StreamEx.of(types).nonNull().map(type -> isDefinition() ? type.toClass() : type.toInstance());
    }

    private void processMetaClassMembers(Consumer<? super PyClassLikeType> typeTypeConsumer,
                                         Predicate<? super PyTargetExpression> instanceTypeAttributesProcessor,
                                         TypeEvalContext context) {
        if (!myClass.isNewStyleClass(context)) {
            return;
        }

        PyClassLikeType typeType = getMetaClassType(context, true);
        if (typeType == null) {
            return;
        }

        if (isDefinition()) {
            typeTypeConsumer.accept(typeType.toInstance());
        }
        else if (typeType instanceof PyClassType) {
            for (PyTargetExpression attribute : ((PyClassType)typeType).getPyClass().getInstanceAttributes()) {
                if (!instanceTypeAttributesProcessor.test(attribute)) {
                    return;
                }
            }
        }
    }

    private static boolean isInSuperCall(PsiElement hook) {
        if (hook instanceof PyReferenceExpression) {
            PyExpression qualifier = ((PyReferenceExpression)hook).getQualifier();
            return qualifier instanceof PyCallExpression && ((PyCallExpression)qualifier).isCalleeText(PyNames.SUPER);
        }
        return false;
    }

    private static boolean isClassProtected(String lookupString) {
        return lookupString.startsWith("_") && !lookupString.startsWith("__");
    }

    @Override
    public @Nullable String getName() {
        if (isNoneType(this)) {
            return PyNames.NONE;
        }
        return getPyClass().getName();
    }

    @Override
    public boolean isBuiltin() {
        return PyBuiltinCache.getInstance(myClass).isBuiltin(myClass);
    }

    @Override
    public void assertValid(String message) {
        if (!myClass.isValid()) {
            throw new PsiInvalidElementAccessException(myClass, myClass.getClass().toString() + ": " + message);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PyClassTypeImpl classType = (PyClassTypeImpl)o;

        // Cheap fields first, then the memoized-hashCode fast path guarding the deep myTypeArguments walk.
        if (myIsDefinition != classType.myIsDefinition) {
            return false;
        }
        if (!myClass.equals(classType.myClass)) {
            return false;
        }
        if (hashCode() != classType.hashCode()) {
            return false;
        }
        if (!myTypeArguments.equals(classType.myTypeArguments)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        if (myHashCode.isNull()) {
            int result = myClass.hashCode();
            result = 31 * result + (myIsDefinition ? 1 : 0);
            if (!myTypeArguments.isEmpty()) {
                result = 31 * result + myTypeArguments.hashCode();
            }
            myHashCode.set(result);
        }
        return myHashCode.get();
    }

    @Override
    public String toString() {
        String name = getClassQName() == null ? getName() : getClassQName();
        String result = (isValid() ? "" : "[INVALID] ") + "PyClassType: " + name;
        if (!myTypeArguments.isEmpty()) {
            result += "[" + StringUtil.join(myTypeArguments, String::valueOf, ", ") + "]";
        }
        return result;
    }

    @Override
    public boolean isValid() {
        return myClass.isValid();
    }

    @Override
    public boolean isAttributeWritable(String name, TypeEvalContext context) {
        PyClass cls = getPyClass();

        if (isDefinition() || PyUtil.isObjectClass(cls)) {
            return true;
        }
        if (cls.findProperty(name, true, context) != null) {
            return true;
        }

        List<String> slots = cls.getSlots(context);
        return slots == null || slots.contains(name);
    }

    public static @Nullable PyClassTypeImpl createTypeByQName(PsiElement anchor,
                                                              String classQualifiedName,
                                                              boolean isDefinition) {
        PyClass pyClass = PyPsiFacade.getInstance(anchor.getProject()).createClassByQName(classQualifiedName, anchor);
        if (pyClass == null) {
            return null;
        }
        return new PyClassTypeImpl(pyClass, isDefinition);
    }

    /**
     * <p>Control flow aware Python attributes resolver.</p>
     *
     * <p>It respects control flow if a resolve candidate is defined in the same scope as the location we resolve the attribute from.</p>
     *
     * <p>Since an attribute doesn't have to be defined in the same method we use it, we have to assume that an attribute we cannot
     * resolve via the control flow graph is defined in some other method. If the attribute is not resolved via the graph, but is defined
     * in a sibling if-elif-else branch, we assume it will become available in our branch eventually in subsequent method calls.</p>
     */
    private static final class PyAttributesProcessor extends PyResolveProcessor {
        private final @Nullable PyExpression myLocation;

        PyAttributesProcessor(String name, @Nullable PyExpression location) {
            super(name);
            myLocation = location;
        }

        @Override
        protected boolean tryAddResult(@Nullable PsiElement element, @Nullable PyImportedNameDefiner definer) {
            PsiElement psiElement = definer != null ? definer : element;
            if (element instanceof PyTypeParameter) {
                return false;
            }
            if (inSameScope(psiElement, myLocation)) {
                if (PsiTreeUtil.isAncestor(psiElement, myLocation, false) ||
                    PyDefUseUtil.isDefinedBefore(psiElement, myLocation) ||
                    inDifferentBranchesOfSameIfStatement(psiElement, myLocation)) {
                    if (myOwner == null) {
                        myOwner = ScopeUtil.getScopeOwner(psiElement);
                    }
                    addResult(element, definer);
                }
                return true;
            }
            return super.tryAddResult(element, definer);
        }

        private static boolean inSameScope(@Nullable PsiElement e1, @Nullable PsiElement e2) {
            if (e1 == null || e2 == null) {
                return false;
            }
            ScopeOwner o1 = ScopeUtil.getScopeOwner(e1);
            ScopeOwner o2 = ScopeUtil.getScopeOwner(e2);
            return o1 != null && o1 == o2;
        }

        private static boolean inDifferentBranchesOfSameIfStatement(PsiElement e1, PsiElement e2) {
            PyIfStatement ifStatement = ObjectUtil.tryCast(PsiTreeUtil.findCommonParent(e1, e2), PyIfStatement.class);
            if (ifStatement == null) {
                return false;
            }
            List<PyStatementPart> parts = getIfStatementParts(ifStatement);
            PyStatementPart p1 = findIfStatementPartByElement(e1, parts);
            PyStatementPart p2 = findIfStatementPartByElement(e2, parts);
            return p1 != p2;
        }

        private static @Nullable PyStatementPart findIfStatementPartByElement(PsiElement element,
                                                                              List<PyStatementPart> parts) {
            return ContainerUtil.find(parts, part -> PsiTreeUtil.isAncestor(part, element, true));
        }

        private static List<PyStatementPart> getIfStatementParts(PyIfStatement statement) {
            List<PyStatementPart> parts = new ArrayList<>();
            parts.add(statement.getIfPart());
            parts.addAll(Arrays.asList(statement.getElifParts()));
            PyElsePart elsePart = statement.getElsePart();
            if (elsePart != null) {
                parts.add(elsePart);
            }
            return parts;
        }
    }
}
