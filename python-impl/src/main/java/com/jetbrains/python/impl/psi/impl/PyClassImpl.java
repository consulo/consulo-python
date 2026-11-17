// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.util.collection.Lists;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.language.editor.completion.CompletionUtilCore;
import com.jetbrains.python.impl.codeInsight.InitVarInfo;
import consulo.language.ast.ASTNode;
import consulo.navigation.ItemPresentation;
import consulo.util.lang.Comparing;
import consulo.application.util.NotNullLazyValue;
import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.content.scope.SearchScope;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.ast.TokenSet;
import consulo.application.util.CachedValueProvider.Result;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.ParameterizedCachedValue;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import consulo.localize.LocalizeValue;
import consulo.util.collection.ArrayFactory;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.application.util.function.Processor;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import com.jetbrains.python.impl.PyCustomType;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.PyLanguageFacadeKt;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.PyAstFunction.Modifier;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.impl.codeInsight.PyDataclassParameters;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyKnownDecorators;
import com.jetbrains.python.psi.PyNoneLiteralExpression;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyPossibleClassMember;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PySubscriptionExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypeDeclarationStatement;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.impl.psi.impl.stubs.PyClassElementType;
import com.jetbrains.python.impl.psi.impl.stubs.PyVersionSpecificStubBaseKt;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.impl.psi.stubs.PropertyStubStorage;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.toolbox.Maybe;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Predicate;

import static consulo.util.lang.StringUtil.join;
import static consulo.util.lang.StringUtil.notNullize;
import static com.jetbrains.python.impl.codeInsight.PyDataclassesKt.getDataclassInitVars;
import static com.jetbrains.python.impl.codeInsight.PyDataclassesKt.parseDataclassParameters;
import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.impl.psi.impl.PyDeprecationUtilKt.extractDeprecationMessageFromDecorator;
import static java.util.Collections.emptySet;

public class PyClassImpl extends PyBaseElementImpl<PyClassStub> implements PyClass {
    public static class MROException extends Exception {
        public MROException(String s) {
            super(s);
        }
    }

    public static final PyClass[] EMPTY_ARRAY = new PyClassImpl[0];

    private volatile @Nullable List<PyTargetExpression> myInstanceAttributes;
    private volatile @Nullable List<PyTargetExpression> myFallbackInstanceAttributes;
    // Class attributes initialized in @classmethod-decorated methods
    private volatile @Nullable List<PyTargetExpression> myClassAttributesFromClassMethods;

    private volatile Map<String, Property> myLocalPropertyCache;

    @Override
    public PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        if (PyTypingTypeProvider.ANY.equals(getQualifiedName())) {
            return PyAnyType.getAny();
        }
        return new PyClassTypeImpl(this, true);
    }

    public PyClassImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyClassImpl(PyClassStub stub) {
        this(stub, PyStubElementTypes.CLASS_DECLARATION);
    }

    public PyClassImpl(PyClassStub stub, IStubElementType nodeType) {
        super(stub, nodeType);
    }

    @Override
    public PsiElement setName(String name) throws IncorrectOperationException {
        ASTNode nameElement = PyUtil.createNewName(this, name);
        ASTNode node = getNameNode();
        if (node != null) {
            getNode().replaceChild(node, nameElement);
        }
        return this;
    }

    @Override
    public @Nullable String getName() {
        PyClassStub stub = getStub();
        if (stub != null) {
            return stub.getName();
        }
        else {
            return PyClass.super.getName();
        }
    }

    public Image getIcon(int flags) {
        return PlatformIconGroup.nodesClass();
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyClass(this);
    }

    @Override
    public PyExpression[] getSuperClassExpressions() {
        PyArgumentList argList = getSuperClassExpressionList();
        if (argList != null) {
            return argList.getArguments();
        }
        return PyExpression.EMPTY_ARRAY;
    }

    public static List<PyExpression> getUnfoldedSuperClassExpressions(PyClass pyClass) {
        return StreamEx
            .of(pyClass.getSuperClassExpressions())
            .filter(expression -> !(expression instanceof PyKeywordArgument))
            .flatCollection(PyClassImpl::unfoldSuperClassExpression)
            .toList();
    }

    private static List<PyExpression> unfoldSuperClassExpression(PyExpression expression) {
        if (isSixWithMetaclassCall(expression)) {
            PyExpression[] arguments = ((PyCallExpression)expression).getArguments();
            if (arguments.length > 1) {
                // TODO consulo: ContainerUtil.subArrayAsList missing
                return Arrays.asList(arguments).subList(1, arguments.length);
            }
            else {
                return Collections.emptyList();
            }
        }
        // Heuristic: unfold Foo[Bar] to Foo for subscription expressions for superclasses
        else if (expression instanceof PySubscriptionExpression subscriptionExpr) {
            return Collections.singletonList(subscriptionExpr.getOperand());
        }

        return Collections.singletonList(expression);
    }

    public static boolean isSixWithMetaclassCall(PyExpression expression) {
        if (expression instanceof PyCallExpression) {
            PyExpression callee = ((PyCallExpression)expression).getCallee();

            if (callee instanceof PyReferenceExpression) {
                QualifiedName sixWithMetaclass = QualifiedName.fromComponents("six", "with_metaclass");
                QualifiedName djangoWithMetaclass = QualifiedName.fromDottedString("django.utils.six.with_metaclass");

                return ContainerUtil.exists(PyResolveUtil.resolveImportedElementQNameLocally((PyReferenceExpression)callee),
                        name -> name.equals(sixWithMetaclass) || name.equals(djangoWithMetaclass));
            }
        }

        return false;
    }

    @Override
    public final List<PyClass> getAncestorClasses(@Nullable TypeEvalContext context) {
        List<PyClass> results = new ArrayList<>();
        for (PyClassLikeType type : getAncestorTypes(notNullizeContext(context))) {
            if (type instanceof PyClassType) {
                results.add(((PyClassType)type).getPyClass());
            }
        }
        return results;
    }

    @Override
    public boolean isSubclass(PyClass parent, @Nullable TypeEvalContext context) {
        if (this == parent) {
            return true;
        }
        for (PyClass superclass : getAncestorClasses(context)) {
            if (parent == superclass) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isSubclass(String superClassQName, @Nullable TypeEvalContext context) {
        if (superClassQName.equals(getQualifiedName())) {
            return true;
        }
        for (PyClassLikeType type : getAncestorTypes(notNullizeContext(context))) {
            if (type != null && superClassQName.equals(type.getClassQName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public @Nullable String getQualifiedName() {
        return QualifiedNameFinder.getQualifiedName(this);
    }

    @Override
    public @Nullable List<String> getSlots(@Nullable TypeEvalContext context) {
        Set<String> result = new LinkedHashSet<>();

        PyClassType currentType = new PyClassTypeImpl(this, true);
        TypeEvalContext contextToUse = notNullizeContext(context);

        List<PyClassLikeType> meAndAncestors = ContainerUtil.concat(Collections.singletonList(currentType), getAncestorTypes(contextToUse));
        for (PyClassLikeType type : ContainerUtil.reverse(meAndAncestors)) {
            if (!(type instanceof PyClassType)) {
                return null;
            }

            PyClass cls = ((PyClassType)type).getPyClass();
            if (PyUtil.isObjectClass(cls)) {
                continue;
            }

            if (!cls.isNewStyleClass(contextToUse)) {
                return null;
            }

            result.removeAll(getNamesOfOwnAttributesThatHaveAssignedValues()); // remove slots that are shadowed by class attributes

            List<String> ownSlots = cls.getOwnSlots();
            if (ownSlots != null && ownSlots.contains(PyNames.DUNDER_DICT)) {
                return null; // not "viably slotted" due to __dict__ in __slots__
            }
            if (ownSlots == null) {
                // check @dataclass(slots=True)
                ownSlots = getOwnSlotsSynthesized(cls, contextToUse);
            }
            if (ownSlots == null) {
                return null; // not "viably slotted"
            }

            result.addAll(ownSlots);
        }

        return new ArrayList<>(result);
    }

    @Override
    public @Nullable List<String> getOwnSlots() {
        PyClassStub stub = getStub();
        if (stub != null) {
            return stub.getSlots();
        }

        PyTargetExpression slotsTargetExpr = ContainerUtil.find(getClassAttributes(), target -> PyNames.SLOTS.equals(target.getName()));
        if (slotsTargetExpr != null) {
            PyExpression slotsValue = slotsTargetExpr.findAssignedValue();
            LinkedHashSet<String> slots = new LinkedHashSet<>();
            if (slotsValue instanceof PyStringLiteralExpression) {
                slots.add(((PyStringLiteralExpression)slotsValue).getStringValue());
            }
            else {
                List<String> slotsValues = PyUtilCore.strListValue(slotsValue);
                if (slotsValues != null) {
                    slots.addAll(slotsValues);
                }
            }

            slots.removeAll(getNamesOfOwnAttributesThatHaveAssignedValues()); // remove slots that are shadowed by class attributes

            return new ArrayList<>(slots);
        }

        return null;
    }

    private Set<String> getNamesOfOwnAttributesThatHaveAssignedValues() {
        return getClassAttributes().stream()
            .filter(target -> target.hasAssignedValue() && target.getName() != null)
            .map(PyTargetExpression::getName).collect(Collectors.toSet());
    }

    /**
     * Returns null if this class is not made "viably slotted" via <code>@dataclass(slots=True)</code>.
     * Returns a list of all valid slotted attribute names otherwise.
     */
    public static @Nullable List<String> getOwnSlotsSynthesized(PyClass cls, TypeEvalContext context) {
        PyDataclassParameters dcParams = parseDataclassParameters(cls, context);
        if (dcParams != null && dcParams.getSlots()) {
            List<String> result = new ArrayList<>();
            List<InitVarInfo> initVars = getDataclassInitVars(cls, dcParams, context);
            Set<PyTargetExpression> initVarTargets = initVars == null ? emptySet() : ContainerUtil.map2Set(initVars, iv -> iv.getTargetExpression());
            List<PyTargetExpression> attributes = cls.getClassAttributes();

            for (PyTargetExpression target : attributes) {
                String name = target.getName();
                if (name == null) {
                    continue;
                }

                // Include only dataclass instance fields:
                // - must be an annotated field (PEP 526), not an unannotated class attribute
                // - must not be typing.ClassVar
                // - must not be dataclasses.InitVar
                boolean isAnnotatedField = target.getAnnotation() != null || target.getParent() instanceof PyTypeDeclarationStatement;
                if (!isAnnotatedField) {
                    continue;
                }
                if (PyTypingTypeProvider.isClassVar(target, context)) {
                    continue;
                }
                if (initVarTargets.contains(target)) {
                    continue;
                }

                result.add(name);
            }
            return result;
        }
        return null;
    }

    @Override
    public @Nullable List<String> getOwnMatchArgs() {
        PyClassStub stub = getStub();
        if (stub != null) {
            return stub.getMatchArgs();
        }

        PyTargetExpression matchArgs = ContainerUtil.find(getClassAttributes(), target -> PyNames.MATCH_ARGS.equals(target.getName()));
        if (matchArgs != null) {
            PyExpression value = matchArgs.findAssignedValue();
            return PyUtilCore.strListValue(value);
        }

        return null;
    }

    @Override
    public PyClass[] getSuperClasses(@Nullable TypeEvalContext context) {
        List<PyClassLikeType> superTypes = getSuperClassTypes(notNullizeContext(context));
        if (superTypes.isEmpty()) {
            return EMPTY_ARRAY;
        }
        List<PyClass> result = new ArrayList<>();
        for (PyClassLikeType type : superTypes) {
            if (type instanceof PyClassType) {
                result.add(((PyClassType)type).getPyClass());
            }
        }
        return result.toArray(PyClass.EMPTY_ARRAY);
    }

    @Override
    public ItemPresentation getPresentation() {
        return new PyElementPresentation(this) {
            @Override
            public LocalizeValue getPresentableText() {
                PyPsiUtils.assertValid(PyClassImpl.this);
                StringBuilder result = new StringBuilder(notNullize(getName(), PyNames.UNNAMED_ELEMENT));
                List<String> superClassesText = getSuperClassesText();
                if (!superClassesText.isEmpty()) {
                    result.append("(");
                    result.append(join(superClassesText, expr -> notNullize(expr, PyNames.UNNAMED_ELEMENT), ", "));
                    result.append(")");
                }
                return LocalizeValue.of(result.toString());
            }
        };
    }

    private List<String> getSuperClassesText() {
        PyClassStub stub = getGreenStub();
        if (stub == null) {
            return ContainerUtil.map(getSuperClassExpressions(), PsiElement::getText);
        }
        else {
            return stub.getSuperClassesText();
        }
    }

    private static List<PyClassLikeType> mroMerge(List<List<PyClassLikeType>> sequences) throws MROException {
        List<PyClassLikeType> result = new LinkedList<>(); // need to insert to 0th position on linearize
        while (true) {
            // filter blank sequences
            List<List<PyClassLikeType>> nonBlankSequences = new ArrayList<>(sequences.size());
            for (List<PyClassLikeType> item : sequences) {
                if (!item.isEmpty()) {
                    nonBlankSequences.add(item);
                }
            }
            if (nonBlankSequences.isEmpty()) {
                return result;
            }
            // find a clean head
            boolean found = false;
            PyClassLikeType head = null; // to keep compiler happy; really head is assigned in the loop at least once.
            for (List<PyClassLikeType> seq : nonBlankSequences) {
                head = seq.getFirst();
                if (head == null) {
                    seq.removeFirst();
                    found = true;
                    break;
                }
                boolean headInTails = false;
                for (List<PyClassLikeType> tailSeq : nonBlankSequences) {
                    if (tailSeq.indexOf(head) > 0) { // -1 is not found, 0 is head, >0 is tail.
                        headInTails = true;
                        break;
                    }
                }
                if (!headInTails) {
                    found = true;
                    break;
                }
                else {
                    head = null; // as a signal
                }
            }
            if (!found) {
                // Inconsistent hierarchy results in TypeError
                throw new MROException("Inconsistent class hierarchy");
            }
            // our head is clean;
            result.add(head);
            // remove it from heads of other sequences
            if (head != null) {
                for (List<PyClassLikeType> seq : nonBlankSequences) {
                    if (Comparing.equal(seq.getFirst(), head)) {
                        seq.removeFirst();
                    }
                }
            }
        } // we either return inside the loop or die by assertion
    }

    private static List<PyClassLikeType> mroLinearize(PyClassLikeType type,
            boolean addThisType,
            TypeEvalContext context,
            Map<PyClassLikeType, Ref<List<PyClassLikeType>>> cache)
        throws MROException {
        Ref<List<PyClassLikeType>> computed = cache.get(type);
        if (computed != null) {
            if (computed.isNull()) {
                throw new MROException("Circular class inheritance");
            }
            return computed.get();
        }
        cache.put(type, Ref.create());
        List<PyClassLikeType> result = null;
        try {
            List<PyClassLikeType> bases = removeNotNullDuplicates(type.getSuperClassTypes(context));
            List<List<PyClassLikeType>> baseClassMROs = new ArrayList<>(bases.size());
            for (PyClassLikeType base : bases) {
                if (base == null) {
                    baseClassMROs.add(Collections.emptyList());
                }
                else if (base.isDefinition()) {
                    baseClassMROs.add(mroLinearize(base, true, context, cache));
                }
                else {
                    // Don't include ancestors of a metaclass instance
                    List<PyClassLikeType> metaclassInstanceMro = new ArrayList<>();
                    metaclassInstanceMro.add(base);
                    if (base instanceof PyClassType) {
                        PyClassImpl pyClass = as(((PyClassType)base).getPyClass(), PyClassImpl.class);
                        if (pyClass != null) {
                            ContainerUtil.addIfNotNull(metaclassInstanceMro, pyClass.getImplicitSuper());
                        }
                    }
                    baseClassMROs.add(metaclassInstanceMro);
                }
            }
            int skippedGenericPos = findTypingGenericSkippedInMRO(bases, baseClassMROs);
            if (skippedGenericPos >= 0) {
                bases.remove(skippedGenericPos);
                baseClassMROs.remove(skippedGenericPos);
            }
            List<List<PyClassLikeType>> lines = new ArrayList<>();
            for (List<PyClassLikeType> baseClassMRO : baseClassMROs) {
                if (!baseClassMRO.isEmpty()) {
                    // mroMerge() updates passed MRO lists internally
                    lines.add(new LinkedList<>(baseClassMRO));
                }
            }
            if (!bases.isEmpty()) {
                lines.add(bases);
            }
            result = mroMerge(lines);
            if (addThisType) {
                result.addFirst(type);
            }
            result = Collections.unmodifiableList(result);
        }
        finally {
            cache.put(type, Ref.create(result));
        }
        return result;
    }

    /**
     * Returns the position of {@code typing.Generic} in {@code bases} when the MRO of a later base also contains it, or -1.
     * At runtime, {@code __mro_entries__} of {@code typing.Generic[...]} removes this entry
     * when a later base is a parameterized generic class.
     * Otherwise, {@code typing.Generic} keeps its position.
     */
    private static int findTypingGenericSkippedInMRO(List<PyClassLikeType> bases,
            List<List<PyClassLikeType>> baseClassMROs) {
        int genericPos = ContainerUtil.indexOf(bases, PyClassImpl::isTypingGeneric);
        if (genericPos < 0) {
            return -1;
        }
        for (int i = genericPos + 1; i < bases.size(); i++) {
            if (ContainerUtil.exists(baseClassMROs.get(i), PyClassImpl::isTypingGeneric)) {
                return genericPos;
            }
        }
        return -1;
    }

    private static boolean isTypingGeneric(@Nullable PyClassLikeType type) {
        return type instanceof PyCustomType customType && PyTypingTypeProvider.GENERIC.equals(customType.getClassQName());
    }

    private static <T> List<T> removeNotNullDuplicates(List<T> list) {
        Set<T> distinct = new HashSet<>();
        List<T> result = new ArrayList<>();
        for (T elem : list) {
            if (elem != null) {
                boolean isUnique = distinct.add(elem);
                if (!isUnique) {
                    continue;
                }
            }
            result.add(elem);
        }
        return result;
    }

    @Override
    public PyFunction[] getMethodsInherited(@Nullable TypeEvalContext context) {
        List<PyFunction> collectedMethods = new ArrayList<>(Arrays.asList(getMethods()));

        for (PyClass superClass : getAncestorClasses(context)) {
            collectedMethods.addAll(Arrays.asList(superClass.getMethods()));
        }
        return collectedMethods.toArray(PyFunction.EMPTY_ARRAY);
    }

    @Override
    public PyFunction[] getMethods() {
        TokenSet functionDeclarationTokens = PythonDialectsTokenSetProvider.getInstance().getFunctionDeclarationTokens();
        return getClassChildren(functionDeclarationTokens, PyFunction.class, PyFunction.ARRAY_FACTORY);
    }

    @Override
    public Map<String, Property> getPropertiesInherited(@Nullable TypeEvalContext context) {
        initLocalProperties();
        Map<String, Property> propertiesHashMap = new HashMap<>(myLocalPropertyCache);

        for (PyClass superClass : getAncestorClasses(context)) {
            Map<String, Property> superClassProperties = superClass.getPropertiesInherited(context);
            for (Map.Entry<String, Property> entry : superClassProperties.entrySet()) {
                // Do not replace existing property in case superclass have it, keep only subclass properties
                propertiesHashMap.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }
        return propertiesHashMap;
    }

    @Override
    public Map<String, Property> getProperties() {
        initLocalProperties();
        return new HashMap<>(myLocalPropertyCache);
    }

    @Override
    public PyClass[] getNestedClasses() {
        return getClassChildren(TokenSet.create(PyElementTypes.CLASS_DECLARATION), PyClass.class, PyClass.ARRAY_FACTORY);
    }

    private <T extends StubBasedPsiElement<? extends StubElement<T>>> T[] getClassChildren(TokenSet elementTypes,
            Class<T> childrenClass,
            ArrayFactory<? extends T> factory) {
        List<T> result = new ArrayList<>();
        processClassLevelDeclarations(new PsiScopeProcessor() {
            @Override
            public boolean execute(PsiElement element, ResolveState state) {
                if (childrenClass.isInstance(element) && elementTypes.contains(((StubBasedPsiElement<?>)element).getElementType())) {
                    result.add(childrenClass.cast(element));
                }
                return true;
            }
        });
        return ContainerUtil.toArray(result, factory);
    }

    public static class NameFinder<T extends PyElement> implements Processor<T> {
        private final TypeEvalContext myContext;
        private T myResult;
        private final String[] myNames;
        private int myLastResultIndex = -1;
        private PyClass myLastVisitedClass = null;

        public NameFinder(TypeEvalContext context, String... names) {
            myContext = context;
            myNames = names;
            myResult = null;
        }

        public T getResult() {
            return myResult;
        }

        protected @Nullable PyClass getContainingClass(T element) {
            return null;
        }

        @Override
        public boolean process(T target) {
            PyClass currentClass = getContainingClass(target);
            // Stop when the current class changes and there was a result
            if (myLastVisitedClass != null && myLastVisitedClass != currentClass && myResult != null) {
                return false;
            }

            myLastVisitedClass = currentClass;

            int index = ArrayUtil.indexOf(myNames, target.getName());
            // Do not depend on the order in which elements appear, always try to find the first one
            if (index >= 0) {
                if (myLastResultIndex == -1 ||
                        index < myLastResultIndex ||
                        index == myLastResultIndex && PyiUtil.isOverload(myResult, myContext) && !PyiUtil.isOverload(target, myContext)) {
                    myLastResultIndex = index;
                    myResult = target;

                    if (index == 0 && !PyiUtil.isOverload(myResult, myContext)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    private static class MultiNameFinder<T extends PyElement> implements Processor<T> {

        private final List<T> myResult;

        private final String[] myNames;

        private @Nullable PyClass myLastVisitedClass;

        MultiNameFinder(String ... names) {
            myResult = new ArrayList<>();
            myNames = names;
            myLastVisitedClass = null;
        }

        @Override
        public boolean process(T t) {
            PyClass currentClass = t instanceof PyPossibleClassMember ? ((PyPossibleClassMember)t).getContainingClass() : null;
            // Stop when the current class changes and there was a result
            if (myLastVisitedClass != null && currentClass != myLastVisitedClass && !myResult.isEmpty()) {
                return false;
            }

            myLastVisitedClass = currentClass;

            if (ArrayUtil.contains(t.getName(), myNames)) {
                myResult.add(t);
            }

            return true;
        }
    }

    @Override
    public PyFunction findMethodByName(@Nullable String name, boolean inherited, @Nullable TypeEvalContext context) {
        if (name == null) {
            return null;
        }
        NameFinder<PyFunction> proc = new NameFinder<>(notNullizeContext(context), name);
        visitMethods(proc, inherited, context);
        return proc.getResult();
    }

    @Override
    public PyFunction findMethodInImplementations(String name, @Nullable TypeEvalContext context) {
        NameFinder<PyFunction> proc = new NameFinder<PyFunction>(notNullizeContext(context), name);
        visitImplementationMethods(this, proc, context);
        return proc.getResult();
    }

    @Override
    public List<PyFunction> multiFindMethodByName(String name, boolean inherited, @Nullable TypeEvalContext context) {
        MultiNameFinder<PyFunction> processor = new MultiNameFinder<>(name);
        visitMethods(processor, inherited, context);
        return processor.myResult;
    }

    @Override
    public @Nullable PyClass findNestedClass(String name, boolean inherited) {
        if (name == null) {
            return null;
        }
        NameFinder<PyClass> proc = new NameFinder<>(TypeEvalContext.codeInsightFallback(getProject()), name);
        visitNestedClasses(proc, inherited);
        return proc.getResult();
    }

    @Override
    public @Nullable PyFunction findInitOrNew(boolean inherited, @Nullable TypeEvalContext context) {
        NameFinder<PyFunction> proc;
        if (isNewStyleClass(context)) {
            proc = new NameFinder<>(notNullizeContext(context), PyNames.INIT, PyNames.NEW) {
                @Override
                protected @Nullable PyClass getContainingClass(PyFunction element) {
                    return element.getContainingClass();
                }
            };
        }
        else {
            proc = new NameFinder<>(notNullizeContext(context), PyNames.INIT);
        }
        visitMethods(proc, inherited, context);
        return proc.getResult();
    }

    @Override
    public List<PyFunction> multiFindInitOrNew(boolean inherited, @Nullable TypeEvalContext context) {
        MultiNameFinder<PyFunction> processor = isNewStyleClass(context)
                ? new MultiNameFinder<>(PyNames.INIT, PyNames.NEW)
                : new MultiNameFinder<>(PyNames.INIT);

        visitMethods(processor, inherited, context);
        return processor.myResult;
    }

    private static final Maybe<PyCallable> UNKNOWN_CALL = new Maybe<>(); // denotes _not_ a PyFunction, actually
    private static final Maybe<PyCallable> NONE = new Maybe<>(null); // denotes an explicit None

    /**
     * @param filter returns true if the property is acceptable
     * @return the first property that filter accepted.
     */
    private @Nullable Property processPropertiesInClass(@Nullable Predicate<? super Property> filter) {
        Property decoratedProperty = processDecoratedProperties(filter);
        if (decoratedProperty != null) {
            return decoratedProperty;
        }

        if (getStub() != null) {
            return processStubProperties(filter);
        }
        else {
            // name = property(...) assignments from PSI
            for (PyTargetExpression target : getClassAttributes()) {
                Property property = PropertyImpl.fromTarget(target);
                if (property != null && (filter == null || filter.test(property))) {
                    return property;
                }
            }
        }

        return null;
    }

    private @Nullable Property processDecoratedProperties(@Nullable Predicate<? super Property> filter) {
        // look at @property decorators
        MultiMap<String, PyFunction> grouped = new MultiMap<>();
        // group suitable same-named methods, each group defines a property
        for (PyFunction method : getMethods()) {
            grouped.putValue(method.getName(), method);
        }
        for (Map.Entry<String, Collection<PyFunction>> entry : grouped.entrySet()) {
            Maybe<PyCallable> getter = NONE;
            Maybe<PyCallable> setter = NONE;
            Maybe<PyCallable> deleter = NONE;
            String decoratorName = entry.getKey();
            for (PyFunction method : entry.getValue()) {
                PyDecoratorList decoratorList = method.getDecoratorList();
                if (decoratorList != null) {
                    for (PyDecorator deco : decoratorList.getDecorators()) {
                        TypeEvalContext context = TypeEvalContext.codeInsightFallback(getProject());
                        PyKnownDecorators knownDecorators = PyKnownDecoratorUtil.knownDecoratorsOf(deco, context);
                        if (knownDecorators.hasTrait(PyKnownDecorator::isMutableProperty)) {
                            getter = new Maybe<>(method);
                            setter = new Maybe<>(method);
                            deleter = new Maybe<>(method);
                        }
                        else if (knownDecorators.hasTrait(PyKnownDecorator::isProperty)) {
                            getter = new Maybe<>(method);
                        }

                        QualifiedName qname = deco.getQualifiedName();
                        if (qname != null) {
                            if (qname.matches(decoratorName, PyNames.GETTER)) {
                                getter = new Maybe<>(method);
                            }
                            else if (qname.matches(decoratorName, PyNames.SETTER)) {
                                setter = new Maybe<>(method);
                            }
                            else if (qname.matches(decoratorName, PyNames.DELETER)) {
                                deleter = new Maybe<>(method);
                            }
                        }
                    }
                }
                if (getter != NONE && setter != NONE && deleter != NONE) break; // can't improve
            }
            if (getter != NONE || setter != NONE || deleter != NONE) {
                PropertyImpl prop = new PropertyImpl(decoratorName, getter, setter, deleter, null, null);
                if (filter == null || filter.test(prop)) {
                    return prop;
                }
            }
        }
        return null;
    }

    private Maybe<PyCallable> fromPacked(Maybe<String> maybeName) {
        if (maybeName.isDefined()) {
            String value = maybeName.value();
            if (value == null || PyNames.NONE.equals(value)) {
                return NONE;
            }
            PyFunction method = findMethodByName(value, true, null);
            if (method != null) {
                return new Maybe<>(method);
            }
        }
        return UNKNOWN_CALL;
    }

    private @Nullable Property processStubProperties(@Nullable Predicate<? super Property> filter) {
        PyClassStub stub = getStub();
        if (stub != null) {
            for (StubElement<?> subStub : getVersionSpecificChildrenStubs(stub)) {
                if (subStub.getStubType() == PyElementTypes.TARGET_EXPRESSION) {
                    PyTargetExpressionStub targetStub = (PyTargetExpressionStub)subStub;
                    PropertyStubStorage prop = targetStub.getCustomStub(PropertyStubStorage.class);
                    if (prop != null) {
                        Maybe<PyCallable> getter = fromPacked(prop.getGetter());
                        Maybe<PyCallable> setter = fromPacked(prop.getSetter());
                        Maybe<PyCallable> deleter = fromPacked(prop.getDeleter());
                        String doc = prop.getDoc();
                        if (getter != NONE || setter != NONE || deleter != NONE) {
                            PropertyImpl property = new PropertyImpl(targetStub.getName(), getter, setter, deleter, doc, targetStub.getPsi());
                            if (filter == null || filter.test(property)) {
                                return property;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    @Override
    public @Nullable Property findProperty(String name, boolean inherited, @Nullable TypeEvalContext context) {
        initLocalProperties();

        Property property = myLocalPropertyCache.get(name);
        if (property != null) {
            return property;
        }

        if (findMethodByName(name, false, context) != null ||
                findClassAttribute(name, false, context) != null ||
                Lists.notNullize(getOwnSlots()).contains(name)) {
            return null;
        }

        if (inherited) {
            for (PyClass cls : getAncestorClasses(context)) {
                Property ancestorProperty = cls.findProperty(name, false, context);
                if (ancestorProperty != null) {
                    return ancestorProperty;
                }
            }
        }

        return null;
    }

    @Override
    public @Nullable Property findPropertyByCallable(PyCallable callable) {
        initLocalProperties();

        for (Property property : myLocalPropertyCache.values()) {
            if (property.getGetter().valueOrNull() == callable ||
                    property.getSetter().valueOrNull() == callable ||
                    property.getDeleter().valueOrNull() == callable) {
                return property;
            }
        }

        return null;
    }

    private synchronized void initLocalProperties() {
        if (myLocalPropertyCache == null) {
            Map<String, Property> result = new HashMap<>();

            processProperties(
                property -> {
                    result.put(property.getName(), property);
                    return false;
                },
                false
            );

            myLocalPropertyCache = result;
        }
    }

    @Override
    public @Nullable Property scanProperties(Predicate<? super Property> filter, boolean inherited) {
        return processProperties(filter, inherited);
    }

    private @Nullable Property processProperties(@Nullable Predicate<? super Property> filter, boolean inherited) {
        PyPsiUtils.assertValid(this);

        Property local = processPropertiesInClass(filter);
        if (local != null) {
            return local;
        }

        if (inherited) {
            for (PyClass cls : getAncestorClasses(null)) {
                Property property = ((PyClassImpl)cls).processPropertiesInClass(filter);
                if (property != null) {
                    return property;
                }
            }
        }

        return null;
    }

    private static final class PropertyImpl extends PropertyBunch<PyCallable> implements Property {
        private final String myName;

        private PropertyImpl(String name,
                Maybe<PyCallable> getter,
                Maybe<PyCallable> setter,
                Maybe<PyCallable> deleter,
                String doc,
                PyTargetExpression site) {
            myName = name;
            myDeleter = deleter;
            myGetter = getter;
            mySetter = setter;
            myDoc = doc;
            mySite = site;
        }

        @Override
        public Maybe<PyCallable> getGetter() {
            return filterNonStubExpression(myGetter);
        }

        @Override
        public Maybe<PyCallable> getSetter() {
            return filterNonStubExpression(mySetter);
        }

        @Override
        public Maybe<PyCallable> getDeleter() {
            return filterNonStubExpression(myDeleter);
        }

        @Override
        public String getName() {
            return myName;
        }

        @Override
        public @Nullable PyTargetExpression getDefinitionSite() {
            return mySite;
        }

        @Override
        public Maybe<PyCallable> getByDirection(AccessDirection direction) {
            return switch (direction) {
                case READ -> getGetter();
                case WRITE -> getSetter();
                case DELETE -> getDeleter();
            };
        }

        @Override
        public @Nullable PyType getType(PyClassType receiverType, TypeEvalContext context) {
            if (mySite instanceof PyTargetExpressionImpl) {
                PyType targetDocStringType = ((PyTargetExpressionImpl)mySite).getTypeFromDocString();
                if (targetDocStringType != null) {
                    return targetDocStringType;
                }
            }
            if (!myGetter.isDefined()) {
                return PyAnyType.getAny();
            }
            PyCallable callable = myGetter.value();
            if (callable != null) {
                // Ignore return types of non stub-based elements if we are not allowed to use AST
                if (!(callable instanceof StubBasedPsiElement) && !context.maySwitchToAST(callable)) {
                    return PyAnyType.getAny();
                }
                RatedResolveResult resolvedCallable = new RatedResolveResult(RatedResolveResult.RATE_NORMAL, callable);
                PyType getterType = PyTypeUtil.getTypeOfBoundMember(receiverType, List.of(resolvedCallable), context);
                return PyCallExpressionHelper.getCallType(getterType, List.of(), context);
            }
            return PyAnyType.getUnknown();
        }

        @Override
        protected Maybe<PyCallable> translate(@Nullable PyExpression expr) {
            if (expr == null || expr instanceof PyNoneLiteralExpression) {
                return NONE;
            }
            if (expr instanceof PyCallable) {
                return new Maybe<>((PyCallable)expr);
            }
            PsiReference ref = expr.getReference();
            if (ref != null) {
                PsiElement something = ref.resolve();
                if (something instanceof PyCallable) {
                    return new Maybe<>((PyCallable)something);
                }
            }
            return NONE;
        }

        private static Maybe<PyCallable> filterNonStubExpression(Maybe<PyCallable> maybeCallable) {
            PyCallable callable = maybeCallable.valueOrNull();
            if (callable != null) {
                if (!(callable instanceof StubBasedPsiElement)) {
                    return UNKNOWN_CALL;
                }
            }
            return maybeCallable;
        }

        @Override
        public String toString() {
            return "property(" + myGetter + ", " + mySetter + ", " + myDeleter + ", " + myDoc + ")";
        }

        public static @Nullable PropertyImpl fromTarget(PyTargetExpression target) {
            PyExpression expr = target.findAssignedValue();
            PropertyImpl prop = new PropertyImpl(target.getName(), null, null, null, null, target);
            boolean success = fillFromCall(expr, prop);
            return success ? prop : null;
        }
    }

    @Override
    public boolean visitMethods(Predicate<? super PyFunction> processor, boolean inherited, @Nullable TypeEvalContext context) {
        if (!ContainerUtil.process(getMethods(), processor)) {
            return false;
        }
        if (inherited) {
            for (PyClass ancestor : getAncestorClasses(context)) {
                if (!ancestor.visitMethods(processor, false, context)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean visitImplementationMethods(PyClass self, Predicate<? super PyFunction> proc, @Nullable TypeEvalContext context) {
        if (!ContainerUtil.process(self.getMethods(), proc)) {
            return false;
        }
        for (PyClass ancestor : self.getAncestorClasses(context)) {
            PyClass implementation = PyiUtil.getOriginalElementOrLeaveAsIs(ancestor, PyClass.class);
            if (!visitImplementationMethods(implementation, proc, context)) {
                return false;
            }
        }
        return true;
    }

    public boolean visitNestedClasses(Predicate<? super PyClass> processor, boolean inherited) {
        PyClass[] nestedClasses = getNestedClasses();
        if (!ContainerUtil.process(nestedClasses, processor)) {
            return false;
        }
        if (inherited) {
            for (PyClass ancestor : getAncestorClasses(null)) {
                if (!((PyClassImpl)ancestor).visitNestedClasses(processor, false)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean visitClassAttributes(Predicate<? super PyTargetExpression> processor,
            boolean inherited,
            @Nullable TypeEvalContext context) {
        List<PyTargetExpression> methods = getClassAttributes();
        if (!ContainerUtil.process(methods, processor)) {
            return false;
        }
        if (inherited) {
            for (PyClass ancestor : getAncestorClasses(context)) {
                if (!ancestor.visitClassAttributes(processor, false, context)) {
                    return false;
                }
            }
        }
        return true;
        // NOTE: sorry, not enough metaprogramming to generalize visitMethods and visitClassAttributes
    }

    @Override
    public final List<PyTargetExpression> getClassAttributesInherited(TypeEvalContext context) {
        MyAttributesCollector attributesCollector = new MyAttributesCollector();
        visitClassAttributes(attributesCollector, true, context);
        return attributesCollector.getAttributes();
    }

    @Override
    public List<PyTargetExpression> getClassAttributes() {
        List<PyTargetExpression> result = new ArrayList<>();
        PyClassStub stub = getStub();
        if (stub != null) {
            for (StubElement<?> element : getVersionSpecificChildrenStubs(stub)) {
                if (element.getStubType() == PyElementTypes.TARGET_EXPRESSION) {
                    result.add((PyTargetExpression)element.getPsi());
                }
            }
        }
        else {
            getStatementList().acceptChildren(
                new PyTypeCheckedTopLevelElementVisitor(PyLanguageFacadeKt.getEffectiveLanguageLevel(getContainingFile())) {
                    @Override
                    protected void checkAddElement(PsiElement psiElement) {
                        if (psiElement instanceof PyAssignmentStatement assignmentStatement) {
                            PyExpression[] targets = assignmentStatement.getTargets();
                            for (PyExpression target : targets) {
                                if (target instanceof PyTargetExpression) {
                                    result.add((PyTargetExpression)target);
                                }
                            }
                        }
                        else if (psiElement instanceof PyTypeDeclarationStatement) {
                            PyExpression target = ((PyTypeDeclarationStatement)psiElement).getTarget();
                            if (target instanceof PyTargetExpression) {
                                result.add((PyTargetExpression)target);
                            }
                        }
                    }
                });
        }
        return result;
    }

    @Override
    public PyTargetExpression findClassAttribute(String name, boolean inherited, TypeEvalContext context) {
        NameFinder<PyTargetExpression> processor = new NameFinder<>(notNullizeContext(context), name);
        visitClassAttributes(processor, inherited, context);
        return processor.getResult();
    }

    @Override
    public List<PyTargetExpression> getInstanceAttributes() {
        List<PyTargetExpression> attributes = myInstanceAttributes;
        if (attributes != null) {
            return attributes;
        }
        attributes = collectInstanceAttributes(Collections.emptyMap());
        myInstanceAttributes = attributes;
        return attributes;
    }

    @Override
    public @Nullable PyTargetExpression findInstanceAttribute(String name, boolean inherited) {
        List<PyTargetExpression> instanceAttributes = getInstanceAttributes();
        for (PyTargetExpression instanceAttribute : instanceAttributes) {
            if (name.equals(instanceAttribute.getReferencedName())) {
                return instanceAttribute;
            }
        }
        if (inherited) {
            for (PyClass ancestor : getAncestorClasses(null)) {
                PyTargetExpression attribute = ancestor.findInstanceAttribute(name, false);
                if (attribute != null) {
                    return attribute;
                }
            }
        }
        return null;
    }

    private List<PyTargetExpression> getFallbackInstanceAttributes() {
        List<PyTargetExpression> attributes = myFallbackInstanceAttributes;
        if (attributes != null) {
            return attributes;
        }
        Map<String, ScopeOwner> scopesToSkip = StreamEx.of(getInstanceAttributes())
            .filter(e -> e.getName() != null)
            .mapToEntry(e -> e.getName(), e -> ScopeUtil.getScopeOwner(e))
            .distinctKeys()
            .toMap();
        attributes = collectInstanceAttributes(scopesToSkip);
        myFallbackInstanceAttributes = attributes;
        return attributes;
    }

    private List<PyTargetExpression> collectInstanceAttributes(Map<String, ScopeOwner> scopesToSkip) {
        MultiMap<String, PyTargetExpression> result = new MultiMap<>();
        collectAttributesInConstructors(result, scopesToSkip);
        PyFunction[] methods = getMethods();
        for (PyFunction method : methods) {
            collectInstanceAttributes(method, result, result.keySet(), scopesToSkip);
        }
        return new ArrayList<>(result.values());
    }

    private void collectAttributesInConstructors(MultiMap<String, PyTargetExpression> result,
            Map<String, ScopeOwner> scopesToSkip) {
        PyFunction newMethod = findMethodByName(PyNames.NEW, false, null);
        if (newMethod != null) {
            for (PyTargetExpression target : getTargetExpressions(newMethod)) {
                String name = target.getName();
                if (scopesToSkip.get(name) != newMethod) {
                    result.putValue(name, target);
                }
            }
        }
        PyFunction initMethod = findMethodByName(PyNames.INIT, false, null);
        if (initMethod != null) {
            collectInstanceAttributes(initMethod, result, emptySet(), scopesToSkip);
        }
    }

    public static void collectInstanceAttributes(PyFunction method, MultiMap<String, PyTargetExpression> result) {
        collectInstanceAttributes(method, result, emptySet(), Collections.emptyMap());
    }

    /**
     * @deprecated Use collectInstanceAttributes(com.jetbrains.python.psi.PyFunction, consulo.util.collection.MultiMap)
     */
    @Deprecated
    public static void collectInstanceAttributes(PyFunction method, Map<String, PyTargetExpression> result) {
        MultiMap<String, PyTargetExpression> localResult = new MultiMap<>();
        collectInstanceAttributes(method, localResult, emptySet(), Collections.emptyMap());
        for (Map.Entry<String, Collection<PyTargetExpression>> entry : localResult.entrySet()) {
            result.put(entry.getKey(), ContainerUtil.getFirstItem(entry.getValue()));
        }
    }

    private static void collectInstanceAttributes(PyFunction method,
            MultiMap<String, PyTargetExpression> result,
            Set<String> namesToSkip,
            Map<String, ScopeOwner> scopesToSkip) {
        PyParameter[] params = method.getParameterList().getParameters();
        if (params.length == 0) {
            return;
        }
        for (PyTargetExpression target : getTargetExpressions(method)) {
            String name = target.getName();
            if (!namesToSkip.contains(name) &&
                    scopesToSkip.get(name) != method &&
                    PyUtil.isInstanceAttribute(target)) {
                result.putValue(name, target);
            }
        }
    }

    private static List<PyTargetExpression> getTargetExpressions(PyFunction function) {
        PyFunctionStub stub = function.getStub();
        if (stub != null) {
            return Arrays.asList(stub.getChildrenByType(PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.EMPTY_ARRAY));
        }
        else {
            PyStatementList statementList = function.getStatementList();
            List<PyTargetExpression> result = new ArrayList<>();
            statementList.accept(new PyRecursiveElementVisitor() {
                @Override
                public void visitPyClass(PyClass node) {
                }

                @Override
                public void visitPyAssignmentStatement(PyAssignmentStatement node) {
                    for (PyExpression expression : node.getTargets()) {
                        if (expression instanceof PyTargetExpression) {
                            result.add((PyTargetExpression)expression);
                        }
                    }
                }

                @Override
                public void visitPyTypeDeclarationStatement(PyTypeDeclarationStatement node) {
                    PyExpression target = node.getTarget();
                    if (target instanceof PyTargetExpression) {
                        result.add((PyTargetExpression)target);
                    }
                }

                @Override
                public void visitPyWithStatement(PyWithStatement node) {
                    StreamEx
                        .of(node.getWithItems())
                        .map(PyWithItem::getTarget)
                        .select(PyTargetExpression.class)
                        .forEach(result::add);

                    super.visitPyWithStatement(node);
                }
            });
            return result;
        }
    }

    @Override
    public boolean isNewStyleClass(@Nullable TypeEvalContext context) {
        return NotNullLazyValue.<ParameterizedCachedValue<Boolean, TypeEvalContext>>createValue(() -> {
            return CachedValuesManager.getManager(getProject())
                .createParameterizedCachedValue(param -> new Result<>(calculateNewStyleClass(param), PsiModificationTracker.MODIFICATION_COUNT),
                    false);
        }).getValue().getValue(context);
    }

    private boolean calculateNewStyleClass(@Nullable TypeEvalContext context) {
        PsiFile containingFile = getContainingFile();
        if (containingFile instanceof PyFile && ((PyFile)containingFile).getLanguageLevel().isPy3K()) {
            return true;
        }
        PyClass objClass = PyBuiltinCache.getInstance(this).getClass(PyNames.OBJECT);
        if (this == objClass) return true; // a rare but possible case
        if (hasNewStyleMetaClass(this)) {
            return true;
        }
        for (PyClassLikeType type : getOldStyleAncestorTypes(notNullizeContext(context))) {
            if (type == null) {
                // unknown, assume new-style class
                return true;
            }
            if (type instanceof PyClassType) {
                PyClass pyClass = ((PyClassType)type).getPyClass();
                if (pyClass == objClass || hasNewStyleMetaClass(pyClass)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasNewStyleMetaClass(PyClass pyClass) {
        PsiFile containingFile = pyClass.getContainingFile();
        if (containingFile instanceof PyFile) {
            PsiElement element = ((PyFile)containingFile).getElementNamed(PyNames.DUNDER_METACLASS);
            if (element instanceof PyTargetExpression) {
                QualifiedName qName = ((PyTargetExpression)element).getAssignedQName();
                if (qName != null && qName.matches("type")) {
                    return true;
                }
            }
        }
        if (pyClass.findClassAttribute(PyNames.DUNDER_METACLASS, false, null) != null) {
            return true;
        }
        return false;
    }

    @Override
    public boolean processClassLevelDeclarations(PsiScopeProcessor processor) {
        PyClassStub stub = getStub();
        if (stub != null) {
            for (StubElement<?> child : getVersionSpecificChildrenStubs(stub)) {
                if (!processor.execute(child.getPsi(), ResolveState.initial())) {
                    return false;
                }
            }
        }
        else {
            PyResolveUtil.scopeCrawlUp(processor, this, null, this);
        }
        return true;
    }

    private Iterable<StubElement<?>> getVersionSpecificChildrenStubs(PyClassStub stub) {
        return PyVersionSpecificStubBaseKt.getChildrenStubs(stub, PyLanguageFacadeKt.getEffectiveLanguageLevel(getContainingFile()));
    }

    @Override
    public boolean processClassObjectAttributes(PsiScopeProcessor processor, @Nullable PsiElement location) {
        if (!processClassLevelDeclarations(processor)) {
            return false;
        }
        PyFunction containingMethod = PsiTreeUtil.getStubOrPsiParentOfType(location, PyFunction.class);
        PyClass containingClass = containingMethod != null ? containingMethod.getContainingClass() : null;
        boolean isClassMethod = containingMethod != null && containingMethod.getModifier() == Modifier.CLASSMETHOD;
        List<PyTargetExpression> allClassAttributes = getClassAttributesDefinedInClassMethods();
        List<PyTargetExpression> prioritizedClassAttrs;
        if (isClassMethod && containingClass != null && CompletionUtilCore.getOriginalElement(containingClass) == this) {
            List<PyTargetExpression> sameMethodClassAttrs = ContainerUtil.filter(allClassAttributes,
                    attr -> ScopeUtil.getScopeOwner(attr) == containingMethod);
            List<PyTargetExpression> otherMethodClassAttrs = ContainerUtil.filter(allClassAttributes,
                    attr -> ScopeUtil.getScopeOwner(attr) != containingMethod);
            prioritizedClassAttrs = ContainerUtil.concat(sameMethodClassAttrs, otherMethodClassAttrs);
        }
        else {
            prioritizedClassAttrs = allClassAttributes;
        }
        for (PyTargetExpression target : prioritizedClassAttrs) {
            if (!processor.execute(target, ResolveState.initial())) {
                return false;
            }
        }
        return true;
    }

    private List<PyTargetExpression> getClassAttributesDefinedInClassMethods() {
        List<PyTargetExpression> classAttrs = myClassAttributesFromClassMethods;
        if (classAttrs == null) {
            classAttrs = new ArrayList<>();
            for (PyFunction method : getMethods()) {
                if (method.getModifier() == Modifier.CLASSMETHOD) {
                    for (PyTargetExpression target : getTargetExpressions(method)) {
                        if (PyUtil.isInstanceAttribute(target)) {
                            classAttrs.add(target);
                        }
                    }
                }
            }
            myClassAttributesFromClassMethods = classAttrs;
        }
        return classAttrs;
    }

    @Override
    public boolean processInstanceLevelDeclarations(PsiScopeProcessor processor, @Nullable PsiElement location) {
        for (PyTargetExpression expr : getInstanceAttributes()) {
            if (!processor.execute(expr, ResolveState.initial())) {
                return false;
            }
        }
        for (PyTargetExpression expr : getFallbackInstanceAttributes()) {
            if (!processor.execute(expr, ResolveState.initial())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getTextOffset() {
        ASTNode name = getNameNode();
        return name != null ? name.getStartOffset() : super.getTextOffset();
    }

    @Override
    public String getDocStringValue() {
        PyClassStub stub = getStub();
        if (stub != null) {
            return stub.getDocString();
        }
        return PyClass.super.getDocStringValue();
    }

    @Override
    public @Nullable String getDeprecationMessage() {
        PyClassStub stub = getStub();
        if (stub != null) {
            return stub.getDeprecationMessage();
        }
        return extractDeprecationMessageFromDecorator(this);
    }

    @Override
    public @Nullable StructuredDocString getStructuredDocString() {
        return DocStringUtil.getStructuredDocString(this);
    }

    @Override
    public String toString() {
        return "PyClass: " + getName();
    }

    @Override
    public void subtreeChanged() {
        super.subtreeChanged();
        ControlFlowCache.clear(this);
        myInstanceAttributes = null;
        myFallbackInstanceAttributes = null;
        myClassAttributesFromClassMethods = null;
        myLocalPropertyCache = null;
    }

    @Override
    public SearchScope getUseScope() {
        ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(this);
        if (scopeOwner instanceof PyFunction) {
            return new LocalSearchScope(scopeOwner);
        }
        return super.getUseScope();
    }

    @Override
    public List<PyClassLikeType> getSuperClassTypes(TypeEvalContext context) {
        return PyUtil.getParameterizedCachedValue(this, context, this::doGetSuperClassTypes);
    }

    private List<@Nullable PyClassLikeType> doGetSuperClassTypes(TypeEvalContext context) {
        List<PyClassLikeType> result = new ArrayList<>();

        // In some cases stub may not provide all information, so we use stubs only if AST access is disabled
        if (context.maySwitchToAST(this)) {
            fillSuperClassesSwitchingToAst(context, result);
        }
        else {
            fillSuperClassesNoSwitchToAst(context, getStub(), result);
        }

        PyPsiUtils.assertValid(this);
        if (result.isEmpty()) {
            return Optional
                .ofNullable(getImplicitSuper())
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
        }

        return result;
    }

    private void fillSuperClassesSwitchingToAst(TypeEvalContext context, List<@Nullable PyClassLikeType> result) {
        for (PyExpression expression : getUnfoldedSuperClassExpressions(this)) {
            PyType type = context.getType(expression);
            PyClassLikeType classLikeType = null;
            if (type instanceof PyClassLikeType) {
                classLikeType = (PyClassLikeType)type;
            }
            else {
                PyReferenceExpression referenceExpr = as(expression, PyReferenceExpression.class);
                PsiElement resolved;
                if (referenceExpr != null) {
                    resolved = referenceExpr.followAssignmentsChain(PyResolveContext.defaultContext(context)).getElement();
                }
                else {
                    PsiReference ref = expression.getReference();
                    resolved = ref != null ? ref.resolve() : null;
                }
                if (resolved instanceof PyClass) {
                    PyType resolvedType = context.getType((PyClass)resolved);
                    if (resolvedType instanceof PyClassLikeType) {
                        classLikeType = (PyClassLikeType)resolvedType;
                    }
                }
            }
            result.add(classLikeType);
        }
    }

    private void fillSuperClassesNoSwitchToAst(TypeEvalContext context,
            @Nullable PyClassStub stub,
            List<? super PyClassLikeType> result) {
        Map<QualifiedName, QualifiedName> superClasses = stub != null
                ? stub.getSuperClasses()
                : PyClassElementType.getSuperClassQNames(this);

        PsiFile file = getContainingFile();
        if (file instanceof PyFile) {
            for (QualifiedName name : superClasses.keySet()) {
                result.add(name != null ? classTypeFromQName(name, (PyFile)file, context) : null);
            }
        }
    }

    private @Nullable PyClassLikeType getImplicitSuper() {
        PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(this);
        PyClassType objectType = builtinCache.getObjectType();

        if (objectType != null && this == objectType.getPyClass()) {
            return null;
        }

        if (LanguageLevel.forElement(this).isPython2() && getMetaClassQName() == null && !hasNewStyleMetaClass(this)) {
            return null;
        }

        return objectType == null ? null : objectType.toClass();
    }

    @Override
    public List<PyClassLikeType> getAncestorTypes(TypeEvalContext context) {
        return ContainerUtil.filter(getAncestorTypesWithMetaClassInstances(context), type ->
            type == null || type.isDefinition()
        );
    }

    private List<PyClassLikeType> getAncestorTypesWithMetaClassInstances(TypeEvalContext context) {
        return PyUtil.getParameterizedCachedValue(this, context, contextArgument -> {
            List<PyClassLikeType> ancestorTypes;
            if (isNewStyleClass(contextArgument)) {
                try {
                    ancestorTypes = getMROAncestorTypes(contextArgument);
                }
                catch (MROException ignored) {
                    ancestorTypes = getOldStyleAncestorTypes(contextArgument);
                    boolean hasUnresolvedAncestorTypes = false;
                    for (PyClassLikeType type : ancestorTypes) {
                        if (type == null) {
                            hasUnresolvedAncestorTypes = true;
                            break;
                        }
                    }
                    if (!hasUnresolvedAncestorTypes) {
                        ancestorTypes = Collections.singletonList(null);
                    }
                }
            }
            else {
                ancestorTypes = getOldStyleAncestorTypes(contextArgument);
            }
            return ancestorTypes;
        });
    }

    @Override
    public @Nullable PyType getMetaClassType(TypeEvalContext context) {
        PyPsiUtils.assertValid(this);
        if (context.maySwitchToAST(this)) {
            PyExpression expression = getMetaClassExpression();
            if (expression != null) {
                PyType type = context.getType(expression);
                if (type != null) {
                    return type;
                }
            }
        }
        else {
            QualifiedName name = getMetaClassQName();
            PsiFile file = getContainingFile();
            if (file instanceof PyFile pyFile) {
                if (name != null) {
                    return classTypeFromQName(name, pyFile, context);
                }
            }
        }
        LanguageLevel level = LanguageLevel.forElement(this);
        if (level.isPython2()) {
            PsiFile file = getContainingFile();
            if (file instanceof PyFile pyFile) {
                PsiElement element = pyFile.getElementNamed(PyNames.DUNDER_METACLASS);
                if (element instanceof PyTypedElement) {
                    return context.getType((PyTypedElement)element);
                }
            }
        }
        return null;
    }

    @Override
    public @Nullable PyClassLikeType getMetaClassType(boolean inherited, TypeEvalContext context) {
        if (!inherited) {
            return as(getMetaClassType(context), PyClassLikeType.class);
        }
        List<PyClassLikeType> metaClassTypes = getAllPossibleMetaClassTypes(context);
        PyClassLikeType mostDerivedMeta = getMostDerivedClassType(metaClassTypes, context);
        return mostDerivedMeta != null ? mostDerivedMeta : PyBuiltinCache.getInstance(this).getObjectType("type");
    }

    private static @Nullable PyClassLikeType getMostDerivedClassType(List<PyClassLikeType> classTypes,
            TypeEvalContext context) {
        if (classTypes.isEmpty()) {
            return null;
        }
        try {
            return classTypes
                .stream()
                .max(
                    (t1, t2) -> {
                        if (Objects.equals(t1, t2)) {
                            return 0;
                        }
                        else if (t1.getAncestorTypes(context).contains(t2)) {
                            return 1;
                        }
                        else if (t2.getAncestorTypes(context).contains(t1)) {
                            return -1;
                        }
                        else {
                            throw new NotDerivedClassTypeException();
                        }
                    }
                )
                .orElse(null);
        }
        catch (NotDerivedClassTypeException ignored) {
            return null;
        }
    }

    private static final class NotDerivedClassTypeException extends RuntimeException {
    }

    private List<PyClassLikeType> getAllPossibleMetaClassTypes(TypeEvalContext context) {
        List<PyClassLikeType> results = new ArrayList<>();
        PyClassLikeType ownMeta = getMetaClassType(false, context);
        if (ownMeta != null) {
            results.add(ownMeta);
        }
        for (PyClassLikeType ancestor : getAncestorTypesWithMetaClassInstances(context)) {
            if (ancestor != null) {
                if (!ancestor.isDefinition()) {
                    results.add(ancestor.toClass());
                }
                else {
                    PyClassLikeType ancestorMeta = ancestor.getMetaClassType(context, false);
                    if (ancestorMeta != null) {
                        results.add(ancestorMeta);
                    }
                }
            }
        }
        return results;
    }

    private @Nullable QualifiedName getMetaClassQName() {
        PyClassStub stub = getStub();
        return stub != null ? stub.getMetaClass() : PyPsiUtils.asQualifiedName(getMetaClassExpression());
    }

    @Override
    public @Nullable PyExpression getMetaClassExpression() {
        LanguageLevel level = LanguageLevel.forElement(this);
        if (!level.isPython2()) {
            // Requires AST access
            for (PyExpression expression : getSuperClassExpressions()) {
                if (expression instanceof PyKeywordArgument argument) {
                    if (PyNames.METACLASS.equals(argument.getKeyword())) {
                        return argument.getValueExpression();
                    }
                }
            }
        }
        else {
            PyTargetExpression attribute = findClassAttribute(PyNames.DUNDER_METACLASS, false, null);
            if (attribute != null) {
                return attribute.findAssignedValue();
            }
        }

        for (PyExpression expression : getSuperClassExpressions()) {
            if (isSixWithMetaclassCall(expression)) {
                PyExpression[] arguments = ((PyCallExpression)expression).getArguments();
                if (arguments.length != 0) {
                    return arguments[0];
                }
            }
        }

        PyDecoratorList decoratorList = getDecoratorList();
        if (decoratorList != null) {
            for (PyDecorator decorator : decoratorList.getDecorators()) {
                if (isSixAddMetaclass(decorator)) {
                    PyExpression[] arguments = decorator.getArguments();
                    if (arguments.length != 0) {
                        return arguments[0];
                    }
                }
            }
        }

        return null;
    }

    private static boolean isSixAddMetaclass(PyDecorator decorator) {
        PyExpression callee = decorator.getCallee();

        if (callee instanceof PyReferenceExpression) {
            QualifiedName sixAddMetaclass = QualifiedName.fromComponents("six", "add_metaclass");
            QualifiedName djangoAddMetaclass = QualifiedName.fromDottedString("django.utils.six.add_metaclass");

            return ContainerUtil.exists(PyResolveUtil.resolveImportedElementQNameLocally((PyReferenceExpression)callee),
                    name -> name.equals(sixAddMetaclass) || name.equals(djangoAddMetaclass));
        }

        return false;
    }

    private List<PyClassLikeType> getMROAncestorTypes(TypeEvalContext context) throws MROException {
        PyPsiUtils.assertValid(this);
        PyType thisType = context.getType(this);
        if (thisType instanceof PyClassLikeType) {
            List<PyClassLikeType> ancestorTypes = mroLinearize((PyClassLikeType)thisType, false, context, new HashMap<>());
            if (isOverriddenMRO(ancestorTypes, context)) {
                ArrayList<PyClassLikeType> withNull = new ArrayList<>(ancestorTypes);
                withNull.add(null);
                return withNull;
            }
            return ancestorTypes;
        }
        else {
            return Collections.emptyList();
        }
    }

    private boolean isOverriddenMRO(List<PyClassLikeType> ancestorTypes, TypeEvalContext context) {
        List<PyClass> classes = new ArrayList<>();
        classes.add(this);
        for (PyClassLikeType ancestorType : ancestorTypes) {
            if (ancestorType instanceof PyClassType classType) {
                classes.add(classType.getPyClass());
            }
        }

        PyClass typeClass = PyBuiltinCache.getInstance(this).getClass("type");

        for (PyClass cls : classes) {
            PyType metaClassType = cls.getMetaClassType(context);
            if (metaClassType instanceof PyClassType) {
                PyClass metaClass = ((PyClassType)metaClassType).getPyClass();
                if (cls == metaClass) {
                    return false;
                }
                PyFunction mroMethod = metaClass.findMethodByName(PyNames.MRO, true, null);
                if (mroMethod != null) {
                    PyClass mroClass = mroMethod.getContainingClass();
                    if (mroClass != null && mroClass != typeClass) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private List<PyClassLikeType> getOldStyleAncestorTypes(TypeEvalContext context) {
        List<PyClassLikeType> results = new ArrayList<>();
        Deque<PyClassLikeType> toProcess = new LinkedList<>();
        Set<PyClassLikeType> seen = new HashSet<>();
        Set<PyClassLikeType> visited = new HashSet<>();
        PyType thisType = context.getType(this);
        if (thisType instanceof PyClassLikeType) {
            toProcess.add((PyClassLikeType)thisType);
        }
        while (!toProcess.isEmpty()) {
            PyClassLikeType currentType = toProcess.pollFirst();
            if (!visited.add(currentType)) {
                continue;
            }
            for (PyClassLikeType superType : currentType.getSuperClassTypes(context)) {
                if (superType == null || !seen.contains(superType)) {
                    results.add(superType);
                    seen.add(superType);
                }
                if (superType != null && !visited.contains(superType)) {
                    toProcess.addLast(superType);
                }
            }
        }
        return results;
    }

    private static @Nullable PyClassLikeType classTypeFromQName(QualifiedName qualifiedName, PyFile containingFile,
            TypeEvalContext context) {
        PsiElement element =
            ContainerUtil.getFirstItem(PyResolveUtil.resolveQualifiedNameInScope(qualifiedName, containingFile, context));
        if (element instanceof PyTypedElement) {
            PyType type = context.getType((PyTypedElement)element);
            if (type instanceof PyClassLikeType) {
                return (PyClassLikeType)type;
            }
        }
        return null;
    }

    @Override
    public @Nullable PyClassLikeType getType(TypeEvalContext context) {
        return as(context.getType(this), PyClassLikeType.class);
    }

    @Override
    public @Nullable PyTypeParameterList getTypeParameterList() {
        return getStubOrPsiChild(PyStubElementTypes.TYPE_PARAMETER_LIST);
    }

    @Override
    public @Nullable PyDecoratorList getDecoratorList() {
        return getStubOrPsiChild(PyStubElementTypes.DECORATOR_LIST);
    }

    public static boolean canHaveAbstractMethods(PyClass pyClass, TypeEvalContext context) {
        PyClassLikeType metaClassType = pyClass.getMetaClassType(true, context);
        if (metaClassType != null && PyNames.ABC_META.equals(metaClassType.getClassQName())) {
            return true;
        }
        return pyClass.getAncestorTypes(context).stream()
            .filter(Objects::nonNull)
            .map(PyClassLikeType::getClassQName)
            .anyMatch(qName -> PyTypingTypeProvider.PROTOCOL.equals(qName) || PyTypingTypeProvider.PROTOCOL_EXT.equals(qName));
    }

    private TypeEvalContext notNullizeContext(@Nullable TypeEvalContext context) {
        return context == null ? TypeEvalContext.codeInsightFallback(getProject()) : context;
    }

    private static final class MyAttributesCollector implements Processor<PyTargetExpression> {
        private final List<PyTargetExpression> myAttributes = new ArrayList<>();

        @Override
        public boolean process(PyTargetExpression expression) {
            myAttributes.add(expression);
            return true;
        }

        List<PyTargetExpression> getAttributes() {
            return Collections.unmodifiableList(myAttributes);
        }
    }
}
