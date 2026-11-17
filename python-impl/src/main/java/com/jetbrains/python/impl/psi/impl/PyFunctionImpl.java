// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.language.controlFlow.ControlFlowUtil;
import consulo.language.controlFlow.Instruction;
import consulo.language.ast.ASTNode;
import consulo.navigation.ItemPresentation;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.util.lang.ref.Ref;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.content.scope.SearchScope;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.python.impl.icon.PythonImplIconGroup;
import consulo.ui.image.Image;
import consulo.localize.LocalizeValue;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.util.collection.JBIterable;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.impl.codeInsight.controlflow.CallInstruction;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.FlowContext;
import com.jetbrains.python.impl.codeInsight.controlflow.PyDataFlow;
import com.jetbrains.python.impl.codeInsight.controlflow.PyFinallyFailExitInstruction;
import com.jetbrains.python.impl.codeInsight.controlflow.PyRaiseInstruction;
import com.jetbrains.python.impl.codeInsight.controlflow.PyWithContextExitInstruction;
import com.jetbrains.python.impl.codeInsight.controlflow.Reachability;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.impl.psi.PsiQuery;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.impl.psi.PyElementKnownDecorators;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypeParameterList;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyWithStatement;
import com.jetbrains.python.psi.PyYieldExpression;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.stubs.PyAnnotationOwnerStub;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.impl.psi.types.PyCallableParameterImpl;
import com.jetbrains.python.psi.types.PyCallableType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyFunctionTypeImpl;
import com.jetbrains.python.psi.types.PyNeverType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypedDictType;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.impl.psi.types.PyUnpackedTypedDictTypeImpl;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static consulo.util.lang.StringUtil.notNullize;
import static consulo.util.collection.ContainerUtil.map;
import static com.jetbrains.python.ast.PyAstFunction.Modifier.CLASSMETHOD;
import static com.jetbrains.python.ast.PyAstFunction.Modifier.STATICMETHOD;
import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper.interpretAsModifierWrappingCall;
import static com.jetbrains.python.impl.psi.impl.PyDeprecationUtilKt.extractDeprecationMessageFromDecorator;

public class PyFunctionImpl extends PyBaseElementImpl<PyFunctionStub> implements PyFunction {

    private static final Key<CachedValue<List<PyAssignmentStatement>>>
        ATTRIBUTES_KEY = Key.create("attributes");

    public PyFunctionImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyFunctionImpl(PyFunctionStub stub) {
        this(stub, PyStubElementTypes.FUNCTION_DECLARATION);
    }

    public PyFunctionImpl(PyFunctionStub stub, IStubElementType nodeType) {
        super(stub, nodeType);
    }

    private class CachedStructuredDocStringProvider implements CachedValueProvider<StructuredDocString> {
        @Override
        public @Nullable Result<StructuredDocString> compute() {
            PyFunctionImpl f = PyFunctionImpl.this;
            return Result.create(DocStringUtil.getStructuredDocString(f), f);
        }
    }

    private final CachedStructuredDocStringProvider myCachedStructuredDocStringProvider = new CachedStructuredDocStringProvider();
    private volatile @Nullable Boolean myIsGenerator;

    @Override
    public @Nullable String getName() {
        PyFunctionStub stub = getStub();
        if (stub != null) {
            return stub.getName();
        }

        return PyFunction.super.getName();
    }

    @Override
    public PsiElement setName(String name) throws IncorrectOperationException {
        ASTNode nameElement = PyUtil.createNewName(this, name);
        ASTNode nameNode = getNameNode();
        if (nameNode != null) {
            getNode().replaceChild(nameNode, nameElement);
        }
        return this;
    }

    public Image getIcon(int flags) {
        PyPsiUtils.assertValid(this);
        Property property = getProperty();
        if (property != null) {
            if (property.getGetter().valueOrNull() == this) {
                return PythonImplIconGroup.pythonPropertygetter();
            }
            if (property.getSetter().valueOrNull() == this) {
                return PythonImplIconGroup.pythonPropertysetter();
            }
            if (property.getDeleter().valueOrNull() == this) {
                return PythonImplIconGroup.pythonPropertydeleter();
            }
            return PlatformIconGroup.nodesProperty();
        }
        if (getContainingClass() != null) {
            return PlatformIconGroup.nodesMethod();
        }
        return PlatformIconGroup.nodesFunction();
    }

    @Override
    public List<PyCallableParameter> getParameters(TypeEvalContext context) {
        return Optional
            .ofNullable(context.getType(this))
            .filter(PyCallableType.class::isInstance)
            .map(PyCallableType.class::cast)
            .map(callableType -> callableType.getParameters(context))
            .orElseGet(() -> map(getParameterList().getParameters(), PyCallableParameterImpl::psi));
    }

    @Override
    public @Nullable PyClass getContainingClass() {
        PyFunctionStub stub = getStub();
        if (stub != null) {
            StubElement<?> parentStub = stub.getParentStub();
            if (parentStub instanceof PyClassStub) {
                return ((PyClassStub)parentStub).getPsi();
            }

            return null;
        }

        return PyFunction.super.getContainingClass();
    }

    @Override
    public @Nullable PyType getReturnType(TypeEvalContext context, TypeEvalContext.Key key) {
        for (PyTypeProvider typeProvider : PyTypeProvider.EP_NAME.getExtensionList()) {
            Ref<PyType> returnTypeRef = typeProvider.getReturnType(this, context);
            if (returnTypeRef != null) {
                return PyTypingTypeProvider.removeNarrowedTypeIfNeeded(derefType(returnTypeRef, typeProvider));
            }
        }

        return getInferredReturnType(context);
    }

    @Override
    public @Nullable PyType getInferredReturnType(TypeEvalContext context) {
        PyType inferredType = PyAnyType.getUnknown();
        if (context.allowReturnTypes(this)) {
            PyType returnType = getReturnStatementType(context);
            Pair<PyType, PyType> yieldSendTypePair = getYieldExpressionType(context);
            if (yieldSendTypePair != null) {
                inferredType = PyTypingTypeProvider.wrapInGeneratorType(yieldSendTypePair.first, yieldSendTypePair.second, returnType, this);
            }
            else {
                inferredType = returnType;
            }
        }
        inferredType = PyNeverType.toNoReturnIfNeeded(inferredType);
        return PyTypingTypeProvider.removeNarrowedTypeIfNeeded(PyTypingTypeProvider.toAsyncIfNeeded(this, inferredType));
    }

    /** The type of `None`, or an unknown type when the project has no builtins. */
    private @Nullable PyType getNoneTypeOrUnknown() {
        PyClassType noneType = PyBuiltinCache.getInstance(this).getNoneType();
        return noneType != null ? noneType : PyAnyType.getUnknown();
    }

    private static @Nullable PyType derefType(Ref<PyType> typeRef, PyTypeProvider typeProvider) {
        PyType type = typeRef.get();
        if (type != null) {
            type.assertValid(typeProvider.toString());
        }
        return type;
    }

    @Override
    public ItemPresentation getPresentation() {
        return new PyElementPresentation(this) {
            @Override
            public LocalizeValue getPresentableText() {
                return LocalizeValue.of(notNullize(getName(), PyNames.UNNAMED_ELEMENT) + getParameterList().getPresentableText(true));
            }
        };
    }

    public static class YieldCollector extends PyRecursiveElementVisitor {
        public List<PyYieldExpression> getYieldExpressions() {
            return myYieldExpressions;
        }

        private final List<PyYieldExpression> myYieldExpressions = new ArrayList<>();

        @Override
        public void visitPyYieldExpression(PyYieldExpression node) {
            myYieldExpressions.add(node);
        }

        @Override
        public void visitPyFunction(PyFunction node) {
            // Ignore nested functions
        }

        @Override
        public void visitPyLambdaExpression(PyLambdaExpression node) {
            // Ignore nested lambdas
        }

        @Override
        public void visitPyClass(PyClass node) {
            // Ignore nested classes
        }
    }

    /**
     * @return pair of YieldType and SendType. Null when there are no yields
     */
    private @Nullable Pair<PyType, PyType> getYieldExpressionType(TypeEvalContext context) {
        PyStatementList statements = getStatementList();
        YieldCollector visitor = new YieldCollector();
        statements.accept(visitor);
        List<PyType> yieldTypes = map(visitor.getYieldExpressions(), it -> it.getYieldType(context));
        List<PyType> sendTypes = map(visitor.getYieldExpressions(), it -> it.getSendType(context));
        if (!yieldTypes.isEmpty()) {
            return Pair.create(PyUnionType.unionOrUnknown(yieldTypes), PyUnionType.unionOrUnknown(sendTypes));
        }
        return null;
    }

    @Override
    public @Nullable PyType getReturnStatementType(TypeEvalContext context) {
        return PyUtil.getNullableParameterizedCachedValue(this, context, (it) -> getReturnStatementTypeNoCache(it));
    }

    private @Nullable PyType getReturnStatementTypeNoCache(TypeEvalContext context) {
        List<PyStatement> returnPoints = getReturnPoints(context);
        List<PyType> types = new ArrayList<>();
        boolean hasReturn = false;

        for (PyStatement point : returnPoints) {
            if (point instanceof PyReturnStatement returnStatement) {
                hasReturn = true;
                PyExpression expr = returnStatement.getExpression();
                types.add(expr != null ? context.getType(expr) : getNoneTypeOrUnknown());
            }
            else {
                types.add(getNoneTypeOrUnknown());
            }
        }

        if ((isGeneratedStub() || PyKnownDecoratorUtil.hasAbstractDecorator(this, context)) && !hasReturn) {
            if (PyUtil.isInitMethod(this)) {
                return getNoneTypeOrUnknown();
            }
            return PyAnyType.getUnknown();
        }
        return PyUnionType.unionOrNever(types);
    }

    @Override
    public List<PyStatement> getReturnPoints(TypeEvalContext context) {
        return collectReturnPoints(this, context, true);
    }

    /**
     * Implements {@link PyFunction#getReturnPoints(TypeEvalContext)}.
     *
     * @param inspectCalleeBodies if false, a call stops the flow only when its callee has an annotated {@code NoReturn} return type.
     *                            Use false to analyze the body of a callee, so that the analysis does not go into the callees of the callee.
     * @see CallInstruction#isNoReturnCall(TypeEvalContext, boolean)
     */
    public static List<PyStatement> collectReturnPoints(PyFunction function,
            TypeEvalContext context,
            boolean inspectCalleeBodies) {
        PyDataFlow dataFlow = ControlFlowCache.getDataFlow(function, new FlowContext(context, false));
        Instruction[] flow = dataFlow.getInstructions();

        class ReturnPointCollector {
            List<PyStatement> returnPoints = new ArrayList<>();
            boolean collectImplicitReturn = true;

            ControlFlowUtil.Operation checkInstruction(Instruction instruction) {
                if (dataFlow.getReachability(instruction) != Reachability.REACHABLE) {
                    return ControlFlowUtil.Operation.CONTINUE;
                }
                if (instruction instanceof PyFinallyFailExitInstruction exitInstruction) {
                    // Most nodes in try-part are connected to finally-fail-part,
                    // but we will only be interested in explicit return statements.
                    boolean oldCollectImplicitReturn = collectImplicitReturn;
                    collectImplicitReturn = false;
                    walkCFG(exitInstruction.getBegin().num());
                    collectImplicitReturn = oldCollectImplicitReturn;
                    return ControlFlowUtil.Operation.CONTINUE;
                }
                if (instruction instanceof CallInstruction ci && ci.isNoReturnCall(context, inspectCalleeBodies)) {
                    return ControlFlowUtil.Operation.CONTINUE;
                }
                if (instruction instanceof PyRaiseInstruction) {
                    return ControlFlowUtil.Operation.CONTINUE;
                }
                if (instruction instanceof PyWithContextExitInstruction withExit) {
                    if (collectImplicitReturn && withExit.isSuppressingExceptions(context)) {
                        returnPoints.add(PsiTreeUtil.getParentOfType(withExit.getElement(), PyWithStatement.class));
                    }
                    return ControlFlowUtil.Operation.CONTINUE;
                }
                PsiElement element = instruction.getElement();
                if (!(element instanceof PyStatement statement)) {
                    return ControlFlowUtil.Operation.NEXT;
                }
                if (collectImplicitReturn || statement instanceof PyReturnStatement) {
                    returnPoints.add(statement);
                }
                return ControlFlowUtil.Operation.CONTINUE;
            }

            void walkCFG(int startInstruction) {
                ControlFlowUtil.iteratePrev(startInstruction, flow, this::checkInstruction);
            }
        }

        ReturnPointCollector collector = new ReturnPointCollector();
        collector.walkCFG(flow.length - 1);
        return collector.returnPoints;
    }

    @Override
    public @Nullable String getDeprecationMessage() {
        PyFunctionStub stub = getStub();
        if (stub != null) {
            return stub.getDeprecationMessage();
        }
        return extractDeprecationMessage();
    }

    public @Nullable String extractDeprecationMessage() {
        String deprecationMessageFromDecorator = extractDeprecationMessageFromDecorator(this);
        if (deprecationMessageFromDecorator != null) {
            return deprecationMessageFromDecorator;
        }
        PyStatementList statementList = getStatementList();
        return PyFunction.extractDeprecationMessage(Arrays.asList(statementList.getStatements()));
    }

    @Override
    public PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        for (PyTypeProvider provider : PyTypeProvider.EP_NAME.getExtensionList()) {
            PyType type = provider.getCallableType(this, context);
            if (type != null) {
                return type;
            }
        }
        List<PyCallableParameter> parameters = new ArrayList<>();
        for (PyParameter parameter : getParameterList().getParameters()) {
            if (parameter instanceof PyNamedParameter namedParameter &&
                    namedParameter.isKeywordContainer() &&
                    context.getType(namedParameter) instanceof PyTypedDictType typedDictType) {
                PyUnpackedTypedDictTypeImpl keywordContainerType = new PyUnpackedTypedDictTypeImpl(typedDictType);
                parameters.add(PyCallableParameterImpl.keywordContainerNonPsi(parameter.getName(), keywordContainerType));
            }
            else {
                parameters.add(PyCallableParameterImpl.psi(parameter));
            }
        }
        return new PyFunctionTypeImpl(this, parameters);
    }

    @Override
    public String getDocStringValue() {
        PyFunctionStub stub = getStub();
        if (stub != null) {
            return stub.getDocString();
        }
        return PyFunction.super.getDocStringValue();
    }

    @Override
    public @Nullable StructuredDocString getStructuredDocString() {
        return LanguageCachedValueUtil.getCachedValue(this, myCachedStructuredDocStringProvider);
    }

    private boolean isGeneratedStub() {
        VirtualFile vFile = getContainingFile().getVirtualFile();
        if (vFile != null) {
            vFile = vFile.getParent();
            if (vFile != null) {
                vFile = vFile.getParent();
                if (vFile != null && vFile.getName().equals(PythonSdkType.SKELETON_DIR_NAME)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyFunction(this);
    }

    @Override
    public int getTextOffset() {
        return PyFunction.super.getTextOffset();
    }

    @Override
    public String toString() {
        return super.toString() + "('" + getName() + "')";
    }

    @Override
    public void subtreeChanged() {
        super.subtreeChanged();
        ControlFlowCache.clear(this);
        myIsGenerator = null;
    }

    @Override
    public @Nullable Property getProperty() {
        PyClass containingClass = getContainingClass();
        if (containingClass != null) {
            return containingClass.findPropertyByCallable(this);
        }
        return null;
    }

    @Override
    public @Nullable String getAnnotationValue() {
        PyAnnotationOwnerStub stub = getStub();
        if (stub != null) {
            return stub.getAnnotation();
        }
        return PyFunction.super.getAnnotationValue();
    }

    @Override
    public @Nullable String getTypeCommentAnnotation() {
        return getTypeCommentAnnotationFromStubOrPsi(this);
    }

    @Override
    public SearchScope getUseScope() {
        ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(this);
        if (scopeOwner instanceof PyFunction) {
            return new LocalSearchScope(scopeOwner);
        }
        return super.getUseScope();
    }

    /**
     * Looks for two standard decorators to a function, or a wrapping assignment that closely follows it.
     *
     * @return a flag describing what was detected.
     */
    @Override
    public @Nullable Modifier getModifier() {
        PyKnownDecorator decorator = getClassOrStaticMethodDecorator();
        if (decorator != null) {
            if (decorator.isClassMethod()) {
                return CLASSMETHOD;
            }
            else if (decorator.isStaticMethod()) {
                return STATICMETHOD;
            }
        }

        String funcName = getName();

        PyClass cls = getContainingClass();
        if (cls != null) {
            // implicit staticmethod __new__
            if (PyNames.NEW.equals(funcName) && cls.isNewStyleClass(null)) {
                return STATICMETHOD;
            }

            LanguageLevel level = LanguageLevel.forElement(this);

            // implicit classmethod __init_subclass__
            if (PyNames.INIT_SUBCLASS.equals(funcName) && level.isAtLeast(LanguageLevel.PYTHON36)) {
                return CLASSMETHOD;
            }

            // implicit classmethod __class_getitem__
            if (PyNames.CLASS_GETITEM.equals(funcName) && level.isAtLeast(LanguageLevel.PYTHON37)) {
                return CLASSMETHOD;
            }
        }

        PyFunctionStub stub = getStub();
        if (stub != null) {
            return getModifierFromStub(stub);
        }

        if (funcName != null) {
            PyAssignmentStatement currentAssignment = PsiTreeUtil.getNextSiblingOfType(this, PyAssignmentStatement.class);
            while (currentAssignment != null) {
                String modifier = currentAssignment
                    .getTargetsToValuesMapping()
                    .stream()
                    .filter(pair -> pair.getFirst() instanceof PyTargetExpression && funcName.equals(pair.getFirst().getName()))
                    .filter(pair -> pair.getSecond() instanceof PyCallExpression)
                    .map(pair -> interpretAsModifierWrappingCall((PyCallExpression)pair.getSecond()))
                    .filter(interpreted -> interpreted != null && interpreted.getSecond() == this)
                    .map(interpreted -> interpreted.getFirst())
                    .filter(wrapperName -> PyNames.CLASSMETHOD.equals(wrapperName) || PyNames.STATICMETHOD.equals(wrapperName))
                    .findAny()
                    .orElse(null);

                if (PyNames.CLASSMETHOD.equals(modifier)) {
                    return CLASSMETHOD;
                }
                else if (PyNames.STATICMETHOD.equals(modifier)) {
                    return STATICMETHOD;
                }

                currentAssignment = PsiTreeUtil.getNextSiblingOfType(currentAssignment, PyAssignmentStatement.class);
            }
        }

        return null;
    }

    @Override
    public boolean isGenerator() {
        PyFunctionStub stub = getStub();
        if (stub != null) {
            return stub.isGenerator();
        }

        Boolean result = myIsGenerator;
        if (result == null) {
            Ref<Boolean> containsYield = Ref.create(false);
            getStatementList().accept(new PyRecursiveElementVisitor() {
                @Override
                public void visitPyYieldExpression(PyYieldExpression node) {
                    containsYield.set(true);
                }

                @Override
                public void visitPyFunction(PyFunction node) {
                    // Ignore nested functions
                }

                @Override
                public void visitPyLambdaExpression(PyLambdaExpression node) {
                    // Ignore lambdas
                }

                @Override
                public void visitPyClass(PyClass node) {
                    // Ignore nested classes
                }

                @Override
                public void visitElement(PsiElement element) {
                    if (!containsYield.get()) {
                        super.visitElement(element);
                    }
                }
            });
            myIsGenerator = result = containsYield.get();
        }
        return result;
    }

    @Override
    public boolean isAsync() {
        PyFunctionStub stub = getStub();
        if (stub != null) {
            return stub.isAsync();
        }
        return PyFunction.super.isAsync();
    }

    @Override
    public boolean onlyRaisesNotImplementedError() {
        PyFunctionStub stub = getStub();
        if (stub != null) {
            return stub.onlyRaisesNotImplementedError();
        }

        return PyFunction.super.onlyRaisesNotImplementedError();
    }

    private static @Nullable Modifier getModifierFromStub(PyFunctionStub stub) {
        return JBIterable
            .<@Nullable StubElement<?>>of(stub.getParentStub())
            .<StubElement<?>>flatMap(element -> (List<StubElement<?>>) (List<?>) element.getChildrenStubs())
            .skipWhile(siblingStub -> !stub.equals(siblingStub))
            .transform(nextSiblingStub -> as(nextSiblingStub, PyTargetExpressionStub.class))
            .filter(Objects::nonNull)
            .filter(nextSiblingStub -> nextSiblingStub.getInitializerType() == PyTargetExpressionStub.InitializerType.CallExpression &&
                Objects.equals(stub.getName(), nextSiblingStub.getName()))
            .transform(PyTargetExpressionStub::getInitializer)
            .transform(
                initializerName -> {
                    if (initializerName == null) {
                        return null;
                    }
                    else if (initializerName.matches(PyNames.CLASSMETHOD)) {
                        return CLASSMETHOD;
                    }
                    else if (initializerName.matches(PyNames.STATICMETHOD)) {
                        return STATICMETHOD;
                    }
                    else {
                        return null;
                    }
                }
            )
            .find(Objects::nonNull);
    }

    /**
     * When a function is decorated many decorators, finds the deepest builtin decorator:
     * <pre>
     * &#x40;foo
     * &#x40;classmethod <b># &lt;-- that's it</b>
     * &#x40;bar
     * def moo(cls):
     * &nbsp;&nbsp;pass
     * </pre>
     *
     * @return name of the built-in decorator, or null (even if there are non-built-in decorators).
     */
    private @Nullable PyKnownDecorator getClassOrStaticMethodDecorator() {
        TypeEvalContext context = TypeEvalContext.codeInsightFallback(getProject());
        PyElementKnownDecorators knownDecorators = PyKnownDecoratorUtil.knownDecoratorsOf(this, context);
        return knownDecorators.findLastWithTrait(decorator -> decorator.isClassMethod() || decorator.isStaticMethod());
    }

    @Override
    public @Nullable String getQualifiedName() {
        return QualifiedNameFinder.getQualifiedName(this);
    }

    @Override
    public List<PyAssignmentStatement> findAttributes() {
        /*
         * TODO: This method if insanely heavy since it unstubs foreign files.
         * Need to save stubs and use them somehow.
         *
         */
        return CachedValuesManager.getManager(getProject()).getCachedValue(this, ATTRIBUTES_KEY, () -> {
            List<PyAssignmentStatement> result = findAttributesStatic(this);
            return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
        }, false);
    }

    @Override
    public PyParameterList getParameterList() {
        return getRequiredStubOrPsiChild(PyStubElementTypes.PARAMETER_LIST);
    }

    @Override
    public @Nullable PyTypeParameterList getTypeParameterList() {
        return getStubOrPsiChild(PyStubElementTypes.TYPE_PARAMETER_LIST);
    }

    @Override
    public @Nullable PyDecoratorList getDecoratorList() {
        return getStubOrPsiChild(PyStubElementTypes.DECORATOR_LIST);
    }

    @Override
    public @Nullable PyAnnotation getAnnotation() {
        return getStubOrPsiChild(PyStubElementTypes.ANNOTATION);
    }

    /**
     * @param self should be this
     */
    private static List<PyAssignmentStatement> findAttributesStatic(PsiElement self) {
        List<PyAssignmentStatement> result = new ArrayList<>();
        for (PyAssignmentStatement statement : new PsiQuery<>(self).siblings(PyAssignmentStatement.class)
            .getElements()) {
            for (PyQualifiedExpression targetExpression : new PsiQuery<PsiElement>(statement.getTargets())
                .filter(new PsiQuery.PsiFilter<>(PyQualifiedExpression.class))
                .getElements()) {
                PyExpression qualifier = targetExpression.getQualifier();
                if (qualifier == null) {
                    continue;
                }
                PsiReference qualifierReference = qualifier.getReference();
                if (qualifierReference == null) {
                    continue;
                }
                if (qualifierReference.isReferenceTo(self)) {
                    result.add(statement);
                }
            }
        }
        return result;
    }
}
