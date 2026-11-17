// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.util.lang.ref.SoftReference;
import consulo.ui.image.Image;
import consulo.platform.base.icon.PlatformIconGroup;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.impl.PyPsiUtils;

import consulo.language.ast.ASTNode;
import consulo.navigation.ItemPresentation;
import consulo.util.lang.ref.Ref;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.content.scope.SearchScope;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentExpression;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyBoolLiteralExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyForPart;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyNonlocalStatement;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyPattern;
import com.jetbrains.python.psi.PyPlainStringElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.impl.psi.impl.references.PyQualifiedReference;
import com.jetbrains.python.impl.psi.impl.references.PyTargetReference;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyAnnotationOwnerStub;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyLiteralKind;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.impl.psi.types.PyABCUtil;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyCollectionTypeImpl;
import com.jetbrains.python.impl.psi.types.PyLegacyDocstringTypeParser;
import com.jetbrains.python.impl.psi.types.PyLiteralType;
import com.jetbrains.python.impl.psi.types.PyNamedTupleType;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.PyTypeMember;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;


public class PyTargetExpressionImpl extends PyBaseElementImpl<PyTargetExpressionStub> implements PyTargetExpression {
    private volatile @Nullable QualifiedName myQualifiedName;

    public PyTargetExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyTargetExpressionImpl(PyTargetExpressionStub stub) {
        super(stub, PyStubElementTypes.TARGET_EXPRESSION);
    }

    public PyTargetExpressionImpl(PyTargetExpressionStub stub, IStubElementType nodeType) {
        super(stub, nodeType);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyTargetExpression(this);
    }

    @Override
    public int getTextOffset() {
        return PyTargetExpression.super.getTextOffset();
    }

    @Override
    public @Nullable String getName() {
        PyTargetExpressionStub stub = getStub();
        if (stub != null) {
            return stub.getName();
        }
        return PyTargetExpression.super.getName();
    }

    @Override
    public PsiElement setName(String name) throws IncorrectOperationException {
        ASTNode oldNameElement = getNameElement();
        if (oldNameElement != null) {
            ASTNode nameElement = PyUtil.createNewName(this, name);
            getNode().replaceChild(oldNameElement, nameElement);
        }
        return this;
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        if (PyNames.ALL.equals(getName())) {
            // no type for __all__, to avoid unresolved reference errors for expressions where a qualifier is a name
            // imported via __all__
            return PyAnyType.getUnknown();
        }
        Ref<PyType> pyType = PyReferenceExpressionImpl.getReferenceTypeFromProviders(this, context, null);
        if (pyType != null) {
            return pyType.get();
        }
        PyType type = getTypeFromDocString();
        if (type != null) {
            return type;
        }
        if (!context.maySwitchToAST(this)) {
            PyLiteralKind literalKind = getAssignedLiteralKind(this);
            if (literalKind != null) {
                if (PyLiteralType.inferLiteralTypeForLiteralExpressions()) {
                    String literalValue = getAssignedLiteralValue(this);
                    if (literalValue != null) {
                        PyType literalType = PyLiteralType.fromLiteralKind(this, literalKind, literalValue);
                        if (literalType != null) {
                            return literalType;
                        }
                    }
                }
                return PyUtil.convertToType(literalKind, PyBuiltinCache.getInstance(this));
            }

            List<PyType> types = new ArrayList<>();
            QualifiedName qName = getAssignedQName();
            if (qName != null && qName.getComponentCount() != 0) {
                ScopeOwner owner = ScopeUtil.getScopeOwner(this);
                if (owner != null) {
                    List<RatedResolveResult> results = PyResolveUtil.resolveQualifiedNameInScopeNew(qName, owner, context);
                    for (RatedResolveResult result : results) {
                        if (result instanceof PyTypeMember typeMember) {
                            types.add(typeMember.getType());
                        }
                        else if (result.getElement() instanceof PyTypedElement element) {
                            types.add(context.getType(element));
                        }
                    }
                }
            }
            return PyUnionType.unionOrUnknown(types);
        }
        type = getTypeFromComment(this);
        if (type != null) {
            return type;
        }
        PsiElement parent = PsiTreeUtil.skipParentsOfType(this, PyParenthesizedExpression.class);
        if (parent instanceof PyAssignmentStatement assignmentStatement) {
            PyExpression assignedValue = assignmentStatement.getAssignedValue();
            if (assignedValue != null) {
                if (PyTypingTypeProvider.isFinal(this, context)) {
                    PyType literalType = PyLiteralType.getLiteralType(assignedValue, context);
                    if (literalType != null) {
                        return literalType;
                    }
                }
                PyType inheritedDeclaredType = getInheritedDeclaredAttributeType(context);
                if (inheritedDeclaredType != null) {
                    return inheritedDeclaredType;
                }
                PyType resultType = assignedValue.getType(context);
                if (ScopeUtil.getScopeOwner(this) instanceof PyClass) {
                    return PyLiteralType.upcastLiteralToClass(resultType);
                }
                return resultType;
            }
            return PyAnyType.getUnknown();
        }
        PsiElement containingSeq = parent instanceof PyStarExpression
                                   ? PsiTreeUtil.skipParentsOfType(parent, PyParenthesizedExpression.class)
                                   : parent;
        if (containingSeq instanceof PyTupleExpression || containingSeq instanceof PyListLiteralExpression) {
            PsiElement nextParent =
                PsiTreeUtil.skipParentsOfType(containingSeq, PyParenthesizedExpression.class, PyTupleExpression.class, PyListLiteralExpression.class);
            if (nextParent instanceof PyAssignmentStatement assignment) {
                PyExpression value = assignment.getAssignedValue();
                PyExpression lhs = assignment.getLeftHandSideExpression();
                PySequenceExpression targetTupleOrList = PsiTreeUtil.findChildOfType(lhs, PySequenceExpression.class, false);
                if (value != null && (targetTupleOrList instanceof PyTupleExpression || targetTupleOrList instanceof PyListLiteralExpression)) {
                    PyType assignedType = PyUnionType.toNonWeakType(context.getType(value));
                    if (assignedType != null) {
                        PyType positionalItemType = getTargetTypeFromIterableUnpacking(this, targetTupleOrList, value, assignedType, context);
                        if (positionalItemType != null) {
                            return positionalItemType;
                        }
                    }
                }
            }
        }
        if (parent instanceof PyWithItem withItem) {
            return getWithItemVariableType(withItem, context);
        }
        if (parent instanceof PyPattern pattern) {
            return context.getType(pattern);
        }
        if (parent instanceof PyAssignmentExpression) {
            PyExpression assignedValue = ((PyAssignmentExpression)parent).getAssignedValue();
            return assignedValue == null ? PyAnyType.getUnknown() : context.getType(assignedValue);
        }
        if (parent instanceof PyImportElement importElement) {
            PyReferenceExpression importedRef = importElement.getImportReferenceExpression();
            return importedRef == null ? null : context.getType(importedRef);
        }
        if (parent instanceof PyGlobalStatement || parent instanceof PyNonlocalStatement) {
            PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
            List<PyType> collect = Arrays.stream(getReference(resolveContext).multiResolve(false))
                .map(ResolveResult::getElement)
                .filter(e -> e != this)
                .filter(PyTypedElement.class::isInstance)
                .map(PyTypedElement.class::cast)
                .map(context::getType)
                .toList();

            return PyUnionType.unionOrUnknown(collect);
        }
        if (parent instanceof PyExceptPart && ((PyExceptPart)parent).isStar() &&
            LanguageLevel.forElement(this).isAtLeast(LanguageLevel.PYTHON311)) {
            return PyClassTypeImpl.createTypeByQName(this, "ExceptionGroup", false);
        }
        PyType iterType = getTypeFromIteration(context);
        if (iterType != null) {
            return iterType;
        }
        PyType excType = getTypeFromExcept(context);
        if (excType != null) {
            return excType;
        }
        return PyAnyType.getUnknown();
    }

    /**
     * Returns the declared type of an annotated attribute with the same name in an ancestor class.
     * An unannotated class or instance attribute that overrides such an attribute keeps this type.
     * Returns {@code null} for an annotated target, for a target that is not an attribute, and when no such ancestor attribute exists.
     */
    public @Nullable PyType getInheritedDeclaredAttributeType(TypeEvalContext context) {
        if (getAnnotationValue() != null || getTypeCommentAnnotation() != null) {
            return null;
        }
        if (!PyUtil.isAttribute(this)) {
            return null;
        }
        String name = getName();
        if (name == null) {
            return null;
        }
        PyClass containingClass = getContainingClass();
        if (containingClass == null) {
            return null;
        }
        for (PyClass ancestor : containingClass.getAncestorClasses(context)) {
            PyTargetExpression inherited = ancestor.findClassAttribute(name, false, context);
            if (inherited == null || inherited == this) {
                continue;
            }
            if (inherited.getAnnotationValue() != null || inherited.getTypeCommentAnnotation() != null) {
                return context.getType(inherited);
            }
        }
        return null;
    }

    /**
     * Computes the type of {@code target} when the sequence {@code topmostContainingTupleOrList} is unpacked from an
     * iterable of type {@code assignedType} (e.g. {@code [a, b] = value}, {@code for a, b in value}).
     * <p>
     * Mutually recursive with {@link PyTypeChecker#getTargetTypeFromTupleAssignment}: fixed-length (heterogeneous) tuple
     * items are distributed positionally by that method, which delegates each nested target back here so that a nested
     * homogeneous iterable (e.g. a {@code list} inside a {@code tuple}) is unpacked correctly.
     */
    public static @Nullable PyType getTargetTypeFromIterableUnpacking(PyExpression target,
                                                                      PySequenceExpression topmostContainingTupleOrList,
                                                                      @Nullable PyExpression assignedIterable, PyType assignedType,
                                                                      TypeEvalContext context) {
        if (assignedType instanceof PyTupleType tupleType) {
            return PyTypeChecker.getTargetTypeFromTupleAssignment(target, topmostContainingTupleOrList, tupleType, context);
        }
        else if (assignedType instanceof PyClassLikeType classLikeType) {
            PyNamedTupleType namedTupleType = ContainerUtil.findInstance(classLikeType.getAncestorTypes(context), PyNamedTupleType.class);
            if (namedTupleType != null) {
                return PyTypeChecker.getTargetTypeFromTupleAssignment(target, topmostContainingTupleOrList, namedTupleType, context);
            }
            else {
                PyType iterationType = assignedIterable != null
                                             ? getIterationType(assignedType, assignedIterable, false, context)
                                             : getIterationType(assignedType, context);
                if (iterationType == null) {
                    return null;
                }
                PyExpression[] elements = topmostContainingTupleOrList.getElements();
                if (ArrayUtil.contains(target, elements)) {
                    return iterationType;
                }

                for (PyExpression element : elements) {
                    // A starred target collects a homogeneous list of the element type regardless of its
                    // position (e.g. `head, *tail = xs` or `first, *middle, last = xs`).
                    if (element instanceof PyStarExpression starExpression && PyPsiUtils.flattenParens(starExpression.getExpression()) == target) {
                        return createHomogeneousListType(target, iterationType);
                    }
                    if (PyPsiUtils.flattenParens(element) instanceof PySequenceExpression sequenceExpression
                        && sequenceExpression.getTextRange().contains(target.getTextRange())) {
                        PyType foundType = getTargetTypeFromIterableUnpacking(target, sequenceExpression, null, iterationType, context);
                        if (foundType != null) {
                            return foundType;
                        }
                    }
                }
            }
        }
        return PyAnyType.getUnknown();
    }

    private static @Nullable PyType createHomogeneousListType(PsiElement anchor, PyType elementType) {
        PyClass listClass = PyBuiltinCache.getInstance(anchor).getClass("list");
        if (listClass == null) {
            return PyAnyType.getUnknown();
        }
        return new PyCollectionTypeImpl(listClass, false, Collections.singletonList(PyLiteralType.upcastLiteralToClass(elementType)));
    }

    @Override
    public @Nullable String getAnnotationValue() {
        PyAnnotationOwnerStub stub = getStub();
        if (stub != null) {
            return stub.getAnnotation();
        }
        return PyTargetExpression.super.getAnnotationValue();
    }

    private static @Nullable PyType getWithItemVariableType(PyWithItem item, TypeEvalContext context) {
        PyExpression withExpression = item.getExpression();
        PyType withType = context.getType(withExpression);
        PyWithStatement withStatement = PsiTreeUtil.getParentOfType(item, PyWithStatement.class);
        boolean isAsync = withStatement != null && withStatement.isAsync();

        return PyTypeUtil
            .toStream(withType)
            .select(PyClassType.class)
            .map(t -> getEnterTypeFromPyClass(withExpression, t, isAsync, context))
            .collect(PyTypeUtil.toUnion(withType));
    }

    private static @Nullable PyType getEnterTypeFromPyClass(PyExpression withExpression,
                                                            PyClassType withType,
                                                            boolean isAsync,
                                                            TypeEvalContext context) {
        String enterName = isAsync ? PyNames.AENTER : PyNames.ENTER;
        Ref<PyType> enterCallType = PyCallExpressionHelper.getSpecialMethodCallType(withType, enterName, List.of(), context);
        if (enterCallType != null) {
            PyType enterType = enterCallType.get();
            if (!isUnknown(enterType)) {
                return isAsync ? SoftReference.deref(PyTypingTypeProvider.coroutineOrGeneratorElementType(enterType)) : enterType;
            }
            for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
                PyType typeFromProvider = provider.getContextManagerVariableType(withType.getPyClass(), withExpression, context);
                if (typeFromProvider != null) {
                    return typeFromProvider;
                }
            }
            // Guess the return type of __enter__
            return PyUnionType.createWeakType(withType);
        }
        return PyAnyType.getUnknown();
    }

    public @Nullable PyType getTypeFromDocString() {
        String typeName = null;
        String name = getName();
        StructuredDocString targetDocString = getStructuredDocString();
        if (targetDocString != null) {
            typeName = targetDocString.getParamType(null);
            if (typeName == null) {
                typeName = targetDocString.getParamType(name);
            }
        }
        if (typeName == null && PyUtil.isAttribute(this)) {
            PyClass cls = getContainingClass();
            if (cls != null) {
                StructuredDocString classDocString = cls.getStructuredDocString();
                if (classDocString != null) {
                    typeName = classDocString.getParamType(name);
                }
            }
        }
        if (typeName != null) {
            return PyLegacyDocstringTypeParser.getTypeByName(this, typeName);
        }
        return null;
    }

    public static @Nullable PyType getTypeFromComment(PyTargetExpressionImpl targetExpression) {
        String docComment = DocStringUtil.getAttributeDocComment(targetExpression);
        if (docComment != null) {
            StructuredDocString structuredDocString = DocStringUtil.parse(docComment, targetExpression);
            String typeName = structuredDocString.getParamType(null);
            if (typeName == null) {
                typeName = structuredDocString.getParamType(targetExpression.getName());
            }
            if (typeName != null) {
                return PyLegacyDocstringTypeParser.getTypeByName(targetExpression, typeName);
            }
        }
        return null;
    }

    private @Nullable PyType getTypeFromIteration(TypeEvalContext context) {
        PyExpression target = null;
        PyExpression source = null;
        boolean isAsync = false;
        PyForStatement forStatement = PsiTreeUtil.getParentOfType(this, PyForStatement.class);
        if (forStatement != null) {
            PyForPart forPart = forStatement.getForPart();
            PyExpression expr = forPart.getTarget();
            if (PsiTreeUtil.isAncestor(expr, this, false)) {
                target = expr;
                source = forPart.getSource();
                isAsync = forStatement.isAsync();
            }
        }
        PyComprehensionElement comprh = PsiTreeUtil.getParentOfType(this, PyComprehensionElement.class);
        if (comprh != null) {
            for (PyComprehensionForComponent c : comprh.getForComponents()) {
                PyExpression expr = c.getIteratorVariable();
                if (PsiTreeUtil.isAncestor(expr, this, false)) {
                    target = expr;
                    source = c.getIteratedList();
                    isAsync = c.isAsync();
                }
            }
        }
        if (source != null) {
            PyType sourceType = context.getType(source);
            PyType type = getIterationType(sourceType, this, isAsync, context);
            target = PyPsiUtils.flattenParens(target);
            if (type != null && (target instanceof PyTupleExpression || target instanceof PyListLiteralExpression)) {
                return getTargetTypeFromIterableUnpacking(this, (PySequenceExpression)target, null, type, context);
            }
            if (target == this && type != null) {
                return type;
            }
        }
        return null;
    }

    private static @Nullable PyType getIterationType(@Nullable PyType iterableType, TypeEvalContext context) {
        if (iterableType instanceof PyTupleType tupleType) {
            return tupleType.getIteratedItemType();
        }
        if (iterableType instanceof PyClassType classType) {
            PyType element = PyTypeChecker.getIteratedItemType(classType, context);
            if (element != null) {
                return element;
            }
        }
        return PyAnyType.getUnknown();
    }

    // TODO migrate this to matching against typing.Iterable protocol with PyTypeUtil.convertToType
    public static @Nullable PyType getIterationType(@Nullable PyType iterableType, PsiElement anchor,
                                                    boolean isAsync, TypeEvalContext context) {
        if (iterableType instanceof PyTupleType tupleType) {
            return tupleType.getIteratedItemType();
        }
        if (iterableType instanceof PyUnionType) {
            return ((PyUnionType)iterableType).map(member -> getIterationType(member, anchor, isAsync, context));
        }
        if (!isAsync) {
            if (!isUnknown(iterableType) && PyABCUtil.isSubtype(iterableType, PyNames.ITERABLE, context)) {
                Ref<PyType> iterateCallType = PyCallExpressionHelper.getSpecialMethodCallType(iterableType, PyNames.ITER, List.of(), context);
                if (iterateCallType != null) {
                    return getIteratedItemType(iterateCallType.get(), anchor, context, false);
                }
                Ref<PyType> nextMethodCallType = getNextMethodCallType(iterableType, anchor, context, false);
                if (nextMethodCallType != null) {
                    return nextMethodCallType.get();
                }
                Ref<PyType> getItemCallType = PyCallExpressionHelper.getSpecialMethodCallType(iterableType, PyNames.GETITEM,
                                                                                                    List.of(), context);
                if (getItemCallType != null) {
                    return getItemCallType.get();
                }
            }
        }
        else {
            if (iterableType != null && PyABCUtil.isSubtype(iterableType, PyNames.ASYNC_ITERABLE, context)) {
                Ref<PyType> iterateCallType = PyCallExpressionHelper.getSpecialMethodCallType(iterableType, PyNames.AITER,
                                                                                                    List.of(), context);
                if (iterateCallType != null) {
                    return getIteratedItemType(iterateCallType.get(), anchor, context, true);
                }
            }
        }
        return PyAnyType.getUnknown();
    }

    private static @Nullable PyType getIteratedItemType(@Nullable PyType type,
                                                        PsiElement anchor,
                                                        TypeEvalContext context,
                                                        boolean async) {
        Ref<PyType> nextMethodCallType = getNextMethodCallType(type, anchor, context, async);
        if (nextMethodCallType != null && !isUnknown(nextMethodCallType.get())) {
            if (async) {
                return PyTypeUtil.derefOrUnknown(PyTypingTypeProvider.unwrapCoroutineReturnType(nextMethodCallType.get()));
            }
            return nextMethodCallType.get();
        }
        if (type instanceof PyClassType pyClassType && pyClassType.isParameterized()) {
            return pyClassType.getIteratedItemType();
        }
        return PyAnyType.getUnknown();
    }

    private static @Nullable Ref<PyType> getNextMethodCallType(@Nullable PyType type,
                                                               PsiElement anchor,
                                                               TypeEvalContext context,
                                                               boolean async) {
        String nextMethodName = async
                                      ? PyNames.ANEXT
                                      : !LanguageLevel.forElement(anchor).isPython2()
                                        ? PyNames.DUNDER_NEXT
                                        : PyNames.NEXT;
        return PyCallExpressionHelper.getSpecialMethodCallType(type, nextMethodName, List.of(), context);
    }

    private @Nullable PyType getTypeFromExcept(TypeEvalContext context) {
        PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(this, PyExceptPart.class);
        if (exceptPart == null || exceptPart.getTarget() != this) {
            return PyAnyType.getUnknown();
        }
        PyExpression exceptClass = PyPsiUtils.flattenParens(exceptPart.getExceptClass());
        if (exceptClass instanceof PyTupleExpression tuple) {
            List<PyType> members = ContainerUtil.map(tuple.getElements(), element -> getExceptionInstanceType(element, context));
            return PyUnionType.unionOrUnknown(members);
        }
        return getExceptionInstanceType(exceptClass, context);
    }

    private static @Nullable PyType getExceptionInstanceType(@Nullable PyExpression exceptClass, TypeEvalContext context) {
        if (exceptClass == null) {
            return PyAnyType.getUnknown();
        }
        if (context.getType(exceptClass) instanceof PyClassType classType && classType.isDefinition()) {
            return classType.toInstance();
        }
        return PyAnyType.getUnknown();
    }

    @Override
    public @Nullable QualifiedName asQualifiedName() {
        if (myQualifiedName == null) {
            myQualifiedName = PyPsiUtils.asQualifiedName(this);
        }
        return myQualifiedName;
    }

    @Override
    public String toString() {
        return super.toString() + ": " + getName();
    }

    // TODO consulo: Consulo resolves PSI icons through PyIconDescriptorUpdater, this method is kept for parity with JB
    public Image getIcon(int flags) {
        if (isQualified() || PsiTreeUtil.getStubOrPsiParentOfType(this, PyDocStringOwner.class) instanceof PyClass) {
            return PlatformIconGroup.nodesField();
        }
        return PlatformIconGroup.nodesVariable();
    }

    @Override
    public boolean isQualified() {
        PyTargetExpressionStub stub = getStub();
        if (stub != null) {
            return stub.isQualified();
        }
        return getQualifier() != null;
    }

    @Override
    public List<PsiElement> multiResolveAssignedValue(PyResolveContext resolveContext) {
        TypeEvalContext context = resolveContext.getTypeEvalContext();

        if (context.maySwitchToAST(this)) {
            PyExpression value = findAssignedValue();
            return value != null
                   ? ContainerUtil.filter(PyUtil.multiResolveTopPriority(value, resolveContext), Objects::nonNull)
                   : Collections.emptyList();
        }
        else {
            QualifiedName qName = getAssignedQName();

            if (qName != null && qName.getComponentCount() != 0) {
                ScopeOwner owner = ScopeUtil.getScopeOwner(this);
                if (owner != null) {
                    return PyResolveUtil.resolveQualifiedNameInScope(qName, owner, context);
                }
            }

            return Collections.emptyList();
        }
    }

    @Override
    public @Nullable QualifiedName getAssignedQName() {
        return SoftReference.deref(getAssignedReferenceQualifiedName(this));
    }

    @Override
    public QualifiedName getCalleeName() {
        return SoftReference.deref(getAssignedCallCalleeQualifiedName(this));
    }

    @Override
    public PsiReference getReference() {
        return getReference(PyResolveContext.defaultContext(TypeEvalContext.codeInsightFallback(getProject())));
    }

    @Override
    public PsiPolyVariantReference getReference(PyResolveContext resolveContext) {
        if (isQualified()) {
            return new PyQualifiedReference(this, resolveContext);
        }
        return new PyTargetReference(this, resolveContext);
    }

    @Override
    public SearchScope getUseScope() {
        if (isQualified()) {
            return super.getUseScope();
        }
        ScopeOwner owner = ScopeUtil.getScopeOwner(this);
        if (owner != null) {
            Scope scope = ControlFlowCache.getScope(owner);
            if (scope.isGlobal(getName())) {
                return GlobalSearchScope.projectScope(getProject());
            }
            if (scope.isNonlocal(getName())) {
                return new LocalSearchScope(getContainingFile());
            }
        }

        // find highest level function containing our var
        PyElement container = this;
        while (true) {
            PyElement parentContainer = PsiTreeUtil.getParentOfType(container, PyFunction.class, PyClass.class);
            if (parentContainer instanceof PyClass) {
                if (isQualified()) {
                    return super.getUseScope();
                }
                break;
            }
            if (parentContainer == null) {
                break;
            }
            container = parentContainer;
        }
        if (container instanceof PyFunction) {
            return new LocalSearchScope(container);
        }
        return super.getUseScope();
    }

    @Override
    public PyClass getContainingClass() {
        PyTargetExpressionStub stub = getStub();
        if (stub != null) {
            StubElement parentStub = stub.getParentStub();
            if (parentStub instanceof PyClassStub) {
                return ((PyClassStub)parentStub).getPsi();
            }
            if (parentStub instanceof PyFunctionStub) {
                StubElement functionParent = parentStub.getParentStub();
                if (functionParent instanceof PyClassStub) {
                    return ((PyClassStub)functionParent).getPsi();
                }
            }

            return null;
        }

        return PyTargetExpression.super.getContainingClass();
    }

    @Override
    public ItemPresentation getPresentation() {
        return new PyElementPresentation(this);
    }

    @Override
    public @Nullable String getDocStringValue() {
        PyTargetExpressionStub stub = getStub();
        if (stub != null) {
            return stub.getDocString();
        }
        return PyTargetExpression.super.getDocStringValue();
    }

    @Override
    public @Nullable StructuredDocString getStructuredDocString() {
        return DocStringUtil.getStructuredDocString(this);
    }

    @Override
    public void subtreeChanged() {
        super.subtreeChanged();
        myQualifiedName = null;
    }

    @Override
    public @Nullable String getQualifiedName() {
        return QualifiedNameFinder.getQualifiedName(this);
    }

    @Override
    public @Nullable String getTypeCommentAnnotation() {
        return getTypeCommentAnnotationFromStubOrPsi(this);
    }

    @Override
    public boolean hasAssignedValue() {
        PyTargetExpressionStub stub = getStub();
        if (stub != null) {
            return stub.hasAssignedValue();
        }
        return findAssignedValue() != null;
    }

    public static @Nullable Ref<QualifiedName> getAssignedReferenceQualifiedName(PyTargetExpression psi) {
        PyTargetExpressionStub stub = psi.getStub();
        if (stub != null) {
            if (stub.getInitializerType() == PyTargetExpressionStub.InitializerType.ReferenceExpression) {
                return Ref.create(stub.getInitializer());
            }
            return null;
        }
        PyExpression value = psi.findAssignedValue();
        return value instanceof PyReferenceExpression ? Ref.create(((PyReferenceExpression)value).asQualifiedName()) : null;
    }

    public static @Nullable Ref<QualifiedName> getAssignedCallCalleeQualifiedName(PyTargetExpression psi) {
        PyTargetExpressionStub stub = psi.getStub();
        if (stub != null) {
            PyTargetExpressionStub.InitializerType initializerType = stub.getInitializerType();
            if (initializerType == PyTargetExpressionStub.InitializerType.CallExpression) {
                return Ref.create(stub.getInitializer());
            }
            else if (initializerType == PyTargetExpressionStub.InitializerType.Custom) {
                CustomTargetExpressionStub customStub = stub.getCustomStub(CustomTargetExpressionStub.class);
                if (customStub != null) {
                    QualifiedName calleeName = customStub.getCalleeName();
                    return calleeName != null ? Ref.create(calleeName) : null;
                }
            }
            return null;
        }
        PyExpression value = psi.findAssignedValue();
        return value instanceof PyCallExpression ? Ref.create(PyPsiUtils.asQualifiedName(((PyCallExpression)value).getCallee())) : null;
    }

    private static @Nullable PyLiteralKind getAssignedLiteralKind(PyTargetExpression psi) {
        PyTargetExpressionStub stub = psi.getStub();
        if (stub != null) {
            if (stub.getInitializerType() == PyTargetExpressionStub.InitializerType.Other) {
                return stub.getAssignedLiteralKind();
            }
            return null;
        }
        PyExpression value = psi.findAssignedValue();
        return PyLiteralKind.fromExpression(value);
    }

    private static @Nullable String getAssignedLiteralValue(PyTargetExpression psi) {
        PyTargetExpressionStub stub = psi.getStub();
        if (stub != null) {
            if (stub.getInitializerType() == PyTargetExpressionStub.InitializerType.Other) {
                return stub.getAssignedLiteralValue();
            }
            return null;
        }
        return getAssignedLiteralValueText(psi.findAssignedValue());
    }

    /**
     * The textual value of a literal assignment used to rebuild a {@link PyLiteralType} without AST, paired with
     * {@link PyLiteralKind#fromExpression}. Returns {@code null} for kinds that don't carry a literal type
     * ({@code FLOAT}/{@code NONE}) and for non-plain strings (bytes, f-strings, Python 2), so the stub-based and
     * AST-based type inference stay consistent.
     */
    public static @Nullable String getAssignedLiteralValueText(@Nullable PyExpression value) {
        if (value instanceof PyNumericLiteralExpression numeric && numeric.isIntegerLiteral()) {
            BigInteger bigInt = numeric.getBigIntegerValue();
            return bigInt.toString();
        }
        if (value instanceof PyBoolLiteralExpression bool) {
            return String.valueOf(bool.getValue());
        }
        if (value instanceof PyStringLiteralExpression string
            && !string.isInterpolated()
            && !LanguageLevel.forElement(string).isPython2()
            && !string.getStringElements().isEmpty()
            && ContainerUtil.and(string.getStringElements(), element -> element instanceof PyPlainStringElement && !element.isBytes())) {
            return string.getStringValue();
        }
        return null;
    }
}
