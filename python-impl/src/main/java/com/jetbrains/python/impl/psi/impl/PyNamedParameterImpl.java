// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.ui.image.Image;
import consulo.platform.base.icon.PlatformIconGroup;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.impl.PyPsiUtils;

import consulo.language.ast.ASTNode;
import consulo.navigation.ItemPresentation;
import consulo.util.lang.ref.Ref;
import consulo.language.psi.ContributedReferenceHost;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceService;
import consulo.language.psi.ReferenceProvidersRegistry;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.content.scope.SearchScope;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.application.util.function.Processor;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyConditionalExpression;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyIfPart;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyMatchStatement;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyAnnotationOwnerStub;
import com.jetbrains.python.psi.stubs.PyNamedParameterStub;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.impl.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyFunctionType;
import com.jetbrains.python.impl.psi.types.PySelfType;
import com.jetbrains.python.impl.psi.types.PyStructuralType;
import com.jetbrains.python.impl.psi.types.PyTupleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;
import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;


public class PyNamedParameterImpl extends PyBaseElementImpl<PyNamedParameterStub> implements PyNamedParameter, ContributedReferenceHost {
    public PyNamedParameterImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyNamedParameterImpl(PyNamedParameterStub stub) {
        this(stub, PyStubElementTypes.NAMED_PARAMETER);
    }

    public PyNamedParameterImpl(PyNamedParameterStub stub, IStubElementType nodeType) {
        super(stub, nodeType);
    }

    @Override
    public final PsiReference[] getReferences() {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this, PsiReferenceService.Hints.NO_HINTS);
    }

    @Override
    public @Nullable String getName() {
        PyNamedParameterStub stub = getStub();
        if (stub != null) {
            return stub.getName();
        }
        else {
            return PyNamedParameter.super.getName();
        }
    }

    @Override
    public int getTextOffset() {
        return PyNamedParameter.super.getTextOffset();
    }

    @Override
    public PsiElement setName(String name) throws IncorrectOperationException {
        ASTNode oldNameIdentifier = getNameIdentifierNode();
        if (oldNameIdentifier != null) {
            ASTNode nameElement = PyUtil.createNewName(this, name);
            getNode().replaceChild(oldNameIdentifier, nameElement);
        }
        return this;
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyNamedParameter(this);
    }

    @Override
    public boolean isPositionalContainer() {
        PyNamedParameterStub stub = getStub();
        if (stub != null) {
            return stub.isPositionalContainer();
        }
        else {
            return PyNamedParameter.super.isPositionalContainer();
        }
    }

    @Override
    public boolean isKeywordContainer() {
        PyNamedParameterStub stub = getStub();
        if (stub != null) {
            return stub.isKeywordContainer();
        }
        else {
            return PyNamedParameter.super.isKeywordContainer();
        }
    }

    @Override
    public @Nullable PyExpression getDefaultValue() {
        PyNamedParameterStub stub = getStub();
        if (stub != null && stub.getDefaultValueText() == null) {
            return null;
        }
        return PyNamedParameter.super.getDefaultValue();
    }

    @Override
    public boolean hasDefaultValue() {
        PyNamedParameterStub stub = getStub();
        if (stub != null) {
            return stub.getDefaultValueText() != null;
        }
        return PyNamedParameter.super.hasDefaultValue();
    }

    @Override
    public @Nullable String getDefaultValueText() {
        PyNamedParameterStub stub = getStub();
        if (stub != null) {
            return stub.getDefaultValueText();
        }
        return PyNamedParameter.super.getDefaultValueText();
    }

    @Override
    public @Nullable PyType getArgumentType(TypeEvalContext context) {
        return PyCallableParameterImpl.psi(this).getArgumentType(context);
    }

    @Override
    public @Nullable PyAnnotation getAnnotation() {
        return getStubOrPsiChild(PyStubElementTypes.ANNOTATION);
    }

    @Override
    public @Nullable String getAnnotationValue() {
        PyAnnotationOwnerStub stub = getStub();
        if (stub != null) {
            return stub.getAnnotation();
        }
        return PyNamedParameter.super.getAnnotationValue();
    }

    // TODO consulo: Consulo resolves PSI icons through PyIconDescriptorUpdater, this method is kept for parity with JB
    public Image getIcon(int flags) {
        return PlatformIconGroup.nodesParameter();
    }

    protected @Nullable PyCallable getContainingCallable(PyParameterList parameterList) {
        return parameterList.getContainingCallable();
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        PsiElement parent = getParentByStub();
        if (parent instanceof PyParameterList) {
            PyCallable callable = getContainingCallable((PyParameterList)parent);
            if (callable != null) {
                if (callable instanceof PyFunction func) {
                    Ref<PyType> resultRef = getTypeFromProviders(this, func, context);
                    if (resultRef != null) {
                        return resultRef.get();
                    }
                    if (isSelf()) {
                        // must be 'self' or 'cls'
                        PyClass containingClass = func.getContainingClass();
                        if (containingClass != null) {
                            boolean isDefinition = PyUtil.isNewMethod(func) || func.getModifier() == PyAstFunction.Modifier.CLASSMETHOD;
                            return new PySelfType(new PyClassTypeImpl(containingClass, isDefinition));
                        }
                    }
                }
                if (callable instanceof PyLambdaExpression lambda) {
                    PyType lambdaType = context.getType(lambda);
                    List<PyCallableParameter> parameters = lambdaType instanceof PyFunctionType funcType ? funcType.getParameters(context) : null;
                    if (parameters != null) {
                        PyCallableParameter parameter = ContainerUtil.find(parameters, param -> Objects.equals(param.getName(), getName()));
                        if (parameter != null) {
                            PyType result = parameter.getType(context);
                            if (result != null) {
                                return result;
                            }
                        }
                    }
                }
                if (isKeywordContainer()) {
                    PyType keywordContainerType = PyTypeUtil.toKeywordContainerType(this, PyAnyType.getUnknown());
                    return keywordContainerType != null ? keywordContainerType : PyAnyType.getUnknown();
                }
                if (isPositionalContainer()) {
                    PyType positionalContainerType = PyTypeUtil.toPositionalContainerType(this, PyAnyType.getUnknown());
                    return positionalContainerType != null ? positionalContainerType : PyAnyType.getUnknown();
                }
                if (context.maySwitchToAST(this)) {
                    PyExpression defaultValue = getDefaultValue();
                    if (defaultValue != null) {
                        PyType type = PyTypeUtil.widenLiteralAndNumeric(context.getType(defaultValue));
                        if (type != null && !isNoneType(type)) {
                            if (type instanceof PyTupleType) {
                                return PyUnionType.createWeakType(type);
                            }
                            return type;
                        }
                    }
                }
                // Guess the type from file-local calls under an assumed type to prevent recursion.
                if (context.allowCallContext(this)) {
                    PyType assumedResult = context.assumeType(this, PyAnyType.getUnknown(), ctx -> {
                        List<PyType> types = new ArrayList<>();
                        PyResolveContext resolveContext = PyResolveContext.defaultContext(ctx);
                        PyCallableParameter parameter = PyCallableParameterImpl.psi(this);

                        processLocalCalls(
                            callable, call -> {
                                call.multiMapArguments(resolveContext).stream()
                                    .flatMap(mapping -> mapping.getMappedParameters().entrySet().stream())
                                    .filter(entry -> parameter.equals(entry.getValue()))
                                    .map(Map.Entry::getKey)
                                    .filter(Objects::nonNull)
                                    .map(ctx::getType)
                                    .filter(it -> !isUnknown(it))
                                    .forEach(types::add);
                                return true;
                            }
                        );

                        return types.isEmpty() ? null : PyUnionType.createWeakType(PyUnionType.unionOrUnknown(types));
                    });
                    if (assumedResult != null) {
                        return assumedResult;
                    }
                }
                PyType typeFromUsages = getTypeFromUsages(context, new HashSet<>());
                if (typeFromUsages != null) {
                    return typeFromUsages;
                }
            }
        }
        return PyAnyType.getUnknown();
    }

    private static boolean isGuessedParameter(PyNamedParameter parameter) {
        return parameter.getAnnotationValue() == null &&
               parameter.getTypeCommentAnnotation() == null &&
               !parameter.hasDefaultValue() &&
               !parameter.isSelf() &&
               !parameter.isPositionalContainer() &&
               !parameter.isKeywordContainer();
    }

    private static @Nullable Ref<PyType> getTypeFromProviders(PyNamedParameter parameter,
                                                              PyFunction function,
                                                              TypeEvalContext context) {
        for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
            Ref<PyType> resultRef = provider.getParameterType(parameter, function, context);
            if (resultRef != null) {
                return resultRef;
            }
        }
        return null;
    }

    @Override
    public ItemPresentation getPresentation() {
        return new PyElementPresentation(this);
    }

    private static @Nullable PyType getForwardedParameterType(PyCallableParameter callableParameter,
                                                              TypeEvalContext context,
                                                              Set<PyNamedParameter> visited) {
        if (!(callableParameter.getParameter() instanceof PyNamedParameterImpl parameter) || !isGuessedParameter(parameter)) {
            return callableParameter.getType(context);
        }

        PsiElement parent = parameter.getParentByStub();
        if (parent instanceof PyParameterList parameterList && parameterList.getContainingCallable() instanceof PyFunction function) {
            Ref<PyType> resultRef = getTypeFromProviders(parameter, function, context);
            if (resultRef != null) {
                return resultRef.get();
            }
        }
        return parameter.getTypeFromUsages(context, visited);
    }

    private @Nullable PyType getTypeFromUsages(TypeEvalContext context, Set<PyNamedParameter> visited) {
        if (!context.maySwitchToAST(this) || !visited.add(this)) {
            return null;
        }

        Set<String> usedAttributes = new LinkedHashSet<>();
        ScopeOwner owner = ScopeUtil.getScopeOwner(this);
        String name = getName();

        Ref<Boolean> noneComparison = Ref.create(false);
        PyResolveContext resolveContext = PyResolveContext.defaultContext(context);

        if (owner != null && name != null) {
            // TODO revise this whole approach to constructing structural types.
            //  Instead of selectively skipping conditional branches in a visitor, use CFG and consider
            //  all attribute access reachable on the original untyped parameter, not intercepted by
            //  reassignments or type narrowing.
            owner.accept(new PyRecursiveElementVisitor() {
                @Override
                public void visitPyElement(PyElement node) {
                    if (node instanceof ScopeOwner && node != owner) {
                        return;
                    }
                    if (node instanceof PyQualifiedExpression expr) {
                        PyExpression qualifier = expr.getQualifier();
                        if (qualifier != null) {
                            String attributeName = expr.getReferencedName();
                            PyExpression referencedExpr = node instanceof PyBinaryExpression && PyNames.isRightOperatorName(attributeName) ?
                                                                ((PyBinaryExpression)node).getRightExpression() : qualifier;
                            if (attributeName != null && isReferenceToParameter(referencedExpr)) {
                                usedAttributes.add(attributeName);
                            }
                        }
                        else if (isReferenceToParameter(expr)) {
                            getParametersByCallArgument(expr, context).stream()
                                .filter(Objects::nonNull)
                                .map(parameter -> getForwardedParameterType(parameter, context, visited))
                                .filter(PyStructuralType.class::isInstance)
                                .map(PyStructuralType.class::cast)
                                .forEach(type -> usedAttributes.addAll(type.getAttributeNames()));
                        }
                    }
                    super.visitPyElement(node);
                }

                @Override
                public void visitPyIfStatement(PyIfStatement node) {
                    PyExpression ifCondition = node.getIfPart().getCondition();
                    if (ifCondition != null) {
                        ifCondition.accept(this);
                    }
                    for (PyIfPart part : node.getElifParts()) {
                        PyExpression elseIfCondition = part.getCondition();
                        if (elseIfCondition != null) {
                            elseIfCondition.accept(this);
                        }
                    }
                }

                @Override
                public void visitPyCallExpression(PyCallExpression node) {
                    Optional
                        .ofNullable(node.getCallee())
                        .filter(callee -> "len".equals(callee.getName()) && isReferenceToParameter(node.getArgument(0, PyReferenceExpression.class)))
                        .map(PyExpression::getReference)
                        .map(PsiReference::resolve)
                        .filter(element -> PyBuiltinCache.getInstance(element).isBuiltin(element))
                        .ifPresent(callable -> usedAttributes.add(PyNames.LEN));

                    super.visitPyCallExpression(node);
                }

                @Override
                public void visitPyForStatement(PyForStatement node) {
                    if (isReferenceToParameter(node.getForPart().getSource())) {
                        usedAttributes.add(node.isAsync() ? PyNames.AITER : PyNames.ITER);
                    }

                    super.visitPyForStatement(node);
                }

                @Override
                public void visitPyMatchStatement(PyMatchStatement node) {
                    PyExpression subject = node.getSubject();
                    if (subject != null) {
                        subject.accept(this);
                    }
                }

                @Override
                public void visitPyConditionalExpression(PyConditionalExpression node) {
                    PyExpression condition = node.getCondition();
                    if (condition != null) {
                        condition.accept(this);
                    }
                }

                @Override
                public void visitPyBinaryExpression(PyBinaryExpression node) {
                    super.visitPyBinaryExpression(node);

                    if (noneComparison.get() || !node.isOperator(PyNames.IS) && !node.isOperator("isnot")) {
                        return;
                    }

                    PyExpression lhs = node.getLeftExpression();
                    PyExpression rhs = node.getRightExpression();

                    boolean lhsIsParam = isReferenceToParameter(lhs);
                    boolean rhsIsParam = isReferenceToParameter(rhs);
                    if (lhsIsParam ^ rhsIsParam) {
                        PyExpression other = lhsIsParam ? rhs : lhs;
                        if (other != null && isNoneType(context.getType(other))) {
                            noneComparison.set(true);
                        }
                    }
                }

                private boolean isReferenceToParameter(@Nullable PsiElement element) {
                    if (element == null) {
                        return false;
                    }
                    if (!(element instanceof PyReferenceExpression || element instanceof PyTargetExpression)) {
                        return false;
                    }
                    if (((PyQualifiedExpression)element).isQualified()) {
                        return false;
                    }
                    List<@Nullable PsiElement> definitions = PyUtil.multiResolveTopPriority(element, resolveContext);
                    return ContainerUtil.all(definitions, e -> e == PyNamedParameterImpl.this);
                }
            });
        }

        if (!usedAttributes.isEmpty()) {
            PyStructuralType structuralType = new PyStructuralType(usedAttributes, true);
            return noneComparison.get() ? PyUnionType.unionOrUnknown(structuralType, PyBuiltinCache.getInstance(this).getNoneType()) : structuralType;
        }

        return null;
    }

    private List<PyCallableParameter> getParametersByCallArgument(PsiElement element, TypeEvalContext context) {
        PyArgumentList argumentList = PsiTreeUtil.getParentOfType(element, PyArgumentList.class);
        if (argumentList != null) {
            boolean elementIsArgument = false;
            for (PyExpression argument : argumentList.getArgumentExpressions()) {
                if (PyPsiUtils.flattenParens(argument) == element) {
                    elementIsArgument = true;
                    break;
                }
            }
            PyCallExpression callExpression = argumentList.getCallExpression();
            if (elementIsArgument && callExpression != null) {
                PyExpression callee = callExpression.getCallee();
                if (callee instanceof PyReferenceExpression calleeReferenceExpr) {
                    PyExpression firstQualifier = PyPsiUtils.getFirstQualifier(calleeReferenceExpr);
                    PsiReference ref = firstQualifier.getReference();
                    if (ref != null && ref.isReferenceTo(this)) {
                        return Collections.emptyList();
                    }
                }
                PyResolveContext resolveContext = PyResolveContext.defaultContext(context);
                return callExpression.multiMapArguments(resolveContext)
                    .stream()
                    .flatMap(mapping -> mapping.getMappedParameters().entrySet().stream())
                    .filter(entry -> entry.getKey() == element)
                    .map(Map.Entry::getValue)
                    .collect(Collectors.toList());
            }
        }
        return Collections.emptyList();
    }

    private static void processLocalCalls(PyCallable function, Processor<? super PyCallExpression> processor) {
        PsiFile file = function.getContainingFile();
        String name = function.getName();
        if (file != null && name != null) {
            // Text search is faster than ReferencesSearch in LocalSearchScope
            String text = file.getText();
            for (int pos = text.indexOf(name); pos != -1; pos = text.indexOf(name, pos + 1)) {
                PsiReference ref = file.findReferenceAt(pos);
                if (ref != null && ref.isReferenceTo(function)) {
                    PyCallExpression expr = PsiTreeUtil.getParentOfType(file.findElementAt(pos), PyCallExpression.class);
                    if (expr != null && !processor.process(expr)) {
                        return;
                    }
                }
            }
        }
    }

    @Override
    public String toString() {
        return super.toString() + "('" + getName() + "')";
    }

    @Override
    public SearchScope getUseScope() {
        ScopeOwner owner = ScopeUtil.getScopeOwner(this);
        if (owner instanceof PyFunction) {
            return owner.getUseScope();
        }
        return new LocalSearchScope(getContainingFile());
    }

    @Override
    public @Nullable String getTypeCommentAnnotation() {
        return getTypeCommentAnnotationFromStubOrPsi(this);
    }
}
