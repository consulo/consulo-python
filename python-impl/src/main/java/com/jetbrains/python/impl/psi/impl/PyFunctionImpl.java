/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.codeInsight.PyTypingTypeProvider;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.psi.PsiQuery;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.Application;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.component.extension.ExtensionPoint;
import consulo.content.scope.SearchScope;
import consulo.language.ast.ASTNode;
import consulo.language.psi.*;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.util.LanguageCachedValueUtil;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.ItemPresentation;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.python.impl.icon.PythonImplIconGroup;
import consulo.ui.image.Image;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.JBIterable;
import consulo.util.dataholder.Key;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper.interpretAsModifierWrappingCall;
import static com.jetbrains.python.psi.PyFunction.Modifier.CLASSMETHOD;
import static com.jetbrains.python.psi.PyFunction.Modifier.STATICMETHOD;
import static consulo.util.lang.StringUtil.notNullize;

/**
 * Implements PyFunction.
 */
public class PyFunctionImpl extends PyBaseElementImpl<PyFunctionStub> implements PyFunction {

    private static final Key<CachedValue<List<PyAssignmentStatement>>> ATTRIBUTES_KEY = Key.create("attributes");

    public PyFunctionImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyFunctionImpl(PyFunctionStub stub) {
        this(stub, PyElementTypes.FUNCTION_DECLARATION);
    }

    public PyFunctionImpl(PyFunctionStub stub, IStubElementType nodeType) {
        super(stub, nodeType);
    }

    private class CachedStructuredDocStringProvider implements CachedValueProvider<StructuredDocString> {
        @Nullable
        @Override
        public Result<StructuredDocString> compute() {
            PyFunctionImpl f = PyFunctionImpl.this;
            return Result.create(DocStringUtil.getStructuredDocString(f), f);
        }
    }

    private CachedStructuredDocStringProvider myCachedStructuredDocStringProvider = new CachedStructuredDocStringProvider();

    @Nullable
    @Override
    @RequiredReadAction
    public String getName() {
        PyFunctionStub stub = getStub();
        if (stub != null) {
            return stub.getName();
        }

        ASTNode node = getNameNode();
        return node != null ? node.getText() : null;
    }

    @Override
    @RequiredReadAction
    public PsiElement getNameIdentifier() {
        ASTNode nameNode = getNameNode();
        return nameNode != null ? nameNode.getPsi() : null;
    }

    @Override
    @RequiredWriteAction
    public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
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

    @Nullable
    @Override
    @RequiredReadAction
    public ASTNode getNameNode() {
        return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PyParameterList getParameterList() {
        return getRequiredStubOrPsiChild(PyElementTypes.PARAMETER_LIST);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PyStatementList getStatementList() {
        PyStatementList statementList = childToPsi(PyElementTypes.STATEMENT_LIST);
        assert statementList != null : "Statement list missing for function " + getText();
        return statementList;
    }

    @Override
    public PyClass getContainingClass() {
        PyFunctionStub stub = getStub();
        if (stub != null) {
            return stub.getParentStub() instanceof PyClassStub classStub ? classStub.getPsi() : null;
        }

        if (PsiTreeUtil.getParentOfType(this, StubBasedPsiElement.class) instanceof PyClass pyClass) {
            return pyClass;
        }
        return null;
    }

    @Nullable
    @Override
    @RequiredReadAction
    public PyDecoratorList getDecoratorList() {
        return getStubOrPsiChild(PyElementTypes.DECORATOR_LIST); // PsiTreeUtil.getChildOfType(this, PyDecoratorList.class);
    }

    @Nullable
    @Override
    @RequiredReadAction
    public PyType getReturnType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key) {
        PyType type = getReturnType(context);
        return isAsync() && isAsyncAllowed() ? createCoroutineType(type) : type;
    }

    @Nullable
    @RequiredReadAction
    private PyType getReturnType(@Nonnull TypeEvalContext context) {
        for (PyTypeProvider typeProvider : Application.get().getExtensionList(PyTypeProvider.class)) {
            SimpleReference<PyType> returnTypeRef = typeProvider.getReturnType(this, context);
            if (returnTypeRef != null) {
                return derefType(returnTypeRef, typeProvider);
            }
        }

        if (context.allowReturnTypes(this)) {
            SimpleReference<? extends PyType> yieldTypeRef = getYieldStatementType(context);
            if (yieldTypeRef != null) {
                return yieldTypeRef.get();
            }
            return getReturnStatementType(context);
        }

        return null;
    }

    @Nullable
    @Override
    public PyType getCallType(@Nonnull TypeEvalContext context, @Nonnull PyCallSiteExpression callSite) {
        for (PyTypeProvider typeProvider : callSite.getApplication().getExtensionList(PyTypeProvider.class)) {
            SimpleReference<PyType> typeRef = typeProvider.getCallType(this, callSite, context);
            if (typeRef != null) {
                return derefType(typeRef, typeProvider);
            }
        }

        PyExpression receiver = PyTypeChecker.getReceiver(callSite, this);
        Map<PyExpression, PyNamedParameter> mapping = PyCallExpressionHelper.mapArguments(callSite, this, context);
        return getCallType(receiver, mapping, context);
    }

    @Nullable
    private static PyType derefType(@Nonnull SimpleReference<PyType> typeRef, @Nonnull PyTypeProvider typeProvider) {
        PyType type = typeRef.get();
        if (type != null) {
            type.assertValid(typeProvider.toString());
        }
        return type;
    }

    @Nullable
    @Override
    public PyType getCallType(
        @Nullable PyExpression receiver,
        @Nonnull Map<PyExpression, PyNamedParameter> parameters,
        @Nonnull TypeEvalContext context
    ) {
        return analyzeCallType(context.getReturnType(this), receiver, parameters, context);
    }

    @Nullable
    private PyType analyzeCallType(
        @Nullable PyType type,
        @Nullable PyExpression receiver,
        @Nonnull Map<PyExpression, PyNamedParameter> parameters,
        @Nonnull TypeEvalContext context
    ) {
        if (PyTypeChecker.hasGenerics(type, context)) {
            Map<PyGenericType, PyType> substitutions = PyTypeChecker.unifyGenericCall(receiver, parameters, context);
            if (substitutions != null) {
                type = PyTypeChecker.substitute(type, substitutions, context);
            }
            else {
                type = null;
            }
        }
        if (receiver != null) {
            type = replaceSelf(type, receiver, context);
        }
        if (type != null && isDynamicallyEvaluated(parameters.values(), context)) {
            type = PyUnionType.createWeakType(type);
        }
        return type;
    }

    @Override
    public ItemPresentation getPresentation() {
        return new PyElementPresentation(this) {
            @Nullable
            @Override
            @RequiredReadAction
            public String getPresentableText() {
                return notNullize(getName(), PyNames.UNNAMED_ELEMENT) + getParameterList().getPresentableText(true);
            }

            @Nullable
            @Override
            @RequiredReadAction
            public String getLocationString() {
                PyClass containingClass = getContainingClass();
                if (containingClass != null) {
                    return "(" + containingClass.getName() + " in " + getPackageForFile(getContainingFile()) + ")";
                }
                return super.getLocationString();
            }
        };
    }

    @Nullable
    private PyType replaceSelf(@Nullable PyType returnType, @Nullable PyExpression receiver, @Nonnull TypeEvalContext context) {
        // TODO: Currently we substitute only simple subclass types, but we could handle union and collection types as well
        if (receiver != null
            && returnType instanceof PyClassType returnClassType
            && returnClassType.getPyClass() == getContainingClass()
            && context.getType(receiver) instanceof PyClassType receiverClassType
            && PyTypeChecker.match(returnClassType, receiverClassType, context)) {
            return returnClassType.isDefinition() ? receiverClassType : receiverClassType.toInstance();
        }
        return returnType;
    }

    private static boolean isDynamicallyEvaluated(@Nonnull Collection<PyNamedParameter> parameters, @Nonnull TypeEvalContext context) {
        for (PyNamedParameter parameter : parameters) {
            if (context.getType(parameter) instanceof PyDynamicallyEvaluatedType) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    @RequiredReadAction
    private SimpleReference<? extends PyType> getYieldStatementType(@Nonnull final TypeEvalContext context) {
        SimpleReference<PyType> elementType = null;
        PyBuiltinCache cache = PyBuiltinCache.getInstance(this);
        PyStatementList statements = getStatementList();
        final Set<PyType> types = new LinkedHashSet<>();
        statements.accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyYieldExpression(PyYieldExpression node) {
                PyExpression expr = node.getExpression();
                PyType type = expr != null ? context.getType(expr) : null;
                if (node.isDelegating() && type instanceof PyCollectionType collectionType) {
                    // TODO: Select the parameter types that matches T in Iterable[T]
                    List<PyType> elementTypes = collectionType.getElementTypes(context);
                    types.add(elementTypes.isEmpty() ? null : elementTypes.get(0));
                }
                else {
                    types.add(type);
                }
            }

            @Override
            public void visitPyFunction(PyFunction node) {
                // Ignore nested functions
            }
        });
        int n = types.size();
        if (n == 1) {
            elementType = SimpleReference.create(types.iterator().next());
        }
        else if (n > 0) {
            elementType = SimpleReference.create(PyUnionType.union(types));
        }
        if (elementType != null) {
            PyClass generator = cache.getClass(PyNames.FAKE_GENERATOR);
            if (generator != null) {
                List<PyType> parameters = Arrays.asList(elementType.get(), null, getReturnStatementType(context));
                return SimpleReference.create(new PyCollectionTypeImpl(generator, false, parameters));
            }
        }
        if (!types.isEmpty()) {
            return SimpleReference.create(null);
        }
        return null;
    }

    @Nullable
    @Override
    @RequiredReadAction
    public PyType getReturnStatementType(TypeEvalContext typeEvalContext) {
        ReturnVisitor visitor = new ReturnVisitor(this, typeEvalContext);
        PyStatementList statements = getStatementList();
        statements.accept(visitor);
        if (isGeneratedStub() && !visitor.myHasReturns) {
            if (PyNames.INIT.equals(getName())) {
                return PyNoneType.INSTANCE;
            }
            return null;
        }
        return visitor.result();
    }

    @Nullable
    private PyType createCoroutineType(@Nullable PyType returnType) {
        PyBuiltinCache cache = PyBuiltinCache.getInstance(this);
        if (returnType instanceof PyClassLikeType classLikeType && PyNames.FAKE_COROUTINE.equals(classLikeType.getClassQName())) {
            return classLikeType;
        }
        PyClass generator = cache.getClass(PyNames.FAKE_COROUTINE);
        return generator != null ? new PyCollectionTypeImpl(generator, false, Collections.singletonList(returnType)) : null;
    }

    @Override
    public PyFunction asMethod() {
        if (getContainingClass() != null) {
            return this;
        }
        else {
            return null;
        }
    }

    @Nullable
    @Override
    public PyType getReturnTypeFromDocString() {
        String typeName = extractReturnType();
        return typeName != null ? PyTypeParser.getTypeByName(this, typeName) : null;
    }

    @Nullable
    @Override
    @RequiredReadAction
    public String getDeprecationMessage() {
        PyFunctionStub stub = getStub();
        if (stub != null) {
            return stub.getDeprecationMessage();
        }
        return extractDeprecationMessage();
    }

    @Nullable
    @RequiredReadAction
    public String extractDeprecationMessage() {
        PyStatementList statementList = getStatementList();
        return extractDeprecationMessage(Arrays.asList(statementList.getStatements()));
    }

    @Override
    public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key) {
        PyType callableType = Application.get().getExtensionPoint(PyTypeProvider.class)
            .computeSafeIfAny(provider -> provider.getCallableType(this, context));
        if (callableType != null) {
            return callableType;
        }
        PyFunctionTypeImpl type = new PyFunctionTypeImpl(this);
        if (PyKnownDecoratorUtil.hasUnknownDecorator(this, context) && getProperty() == null) {
            return PyUnionType.createWeakType(type);
        }
        return type;
    }

    private static final Set<String> DEPRECATION_WARNINGS = Set.of(PyNames.DEPRECATION_WARNING, PyNames.PENDING_DEPRECATION_WARNING);
    @Nullable
    public static String extractDeprecationMessage(List<PyStatement> statements) {
        for (PyStatement statement : statements) {
            if (statement instanceof PyExpressionStatement exprStmt
                && exprStmt.getExpression() instanceof PyCallExpression callExpr
                && callExpr.isCalleeText(PyNames.WARN)) {
                PyReferenceExpression warningClass = callExpr.getArgument(1, PyReferenceExpression.class);
                if (warningClass != null && DEPRECATION_WARNINGS.contains(warningClass.getReferencedName())) {
                    return PyPsiUtils.strValue(callExpr.getArguments()[0]);
                }
            }
        }
        return null;
    }

    @Override
    public String getDocStringValue() {
        PyFunctionStub stub = getStub();
        if (stub != null) {
            return stub.getDocString();
        }
        return DocStringUtil.getDocStringValue(this);
    }

    @Nullable
    @Override
    public StructuredDocString getStructuredDocString() {
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

    @Nullable
    private String extractReturnType() {
        String ARROW = "->";
        StructuredDocString structuredDocString = getStructuredDocString();
        if (structuredDocString != null) {
            return structuredDocString.getReturnType();
        }
        String docString = getDocStringValue();
        if (docString != null && docString.contains(ARROW)) {
            List<String> lines = StringUtil.split(docString, "\n");
            while (lines.size() > 0 && lines.get(0).trim().length() == 0) {
                lines.remove(0);
            }
            if (lines.size() > 1 && lines.get(1).trim().length() == 0) {
                String firstLine = lines.get(0);
                int pos = firstLine.lastIndexOf(ARROW);
                if (pos >= 0) {
                    return firstLine.substring(pos + 2).trim();
                }
            }
        }
        return null;
    }

    private static class ReturnVisitor extends PyRecursiveElementVisitor {
        private final PyFunction myFunction;
        private final TypeEvalContext myContext;
        private PyType myResult = null;
        private boolean myHasReturns = false;
        private boolean myHasRaises = false;

        public ReturnVisitor(PyFunction function, TypeEvalContext context) {
            myFunction = function;
            myContext = context;
        }

        @Override
        public void visitPyReturnStatement(PyReturnStatement node) {
            if (ScopeUtil.getScopeOwner(node) == myFunction) {
                PyExpression expr = node.getExpression();
                PyType returnType;
                returnType = expr == null ? PyNoneType.INSTANCE : myContext.getType(expr);
                if (!myHasReturns) {
                    myResult = returnType;
                    myHasReturns = true;
                }
                else {
                    myResult = PyUnionType.union(myResult, returnType);
                }
            }
        }

        @Override
        public void visitPyRaiseStatement(PyRaiseStatement node) {
            myHasRaises = true;
        }

        @Nullable
        PyType result() {
            return myHasReturns || myHasRaises ? myResult : PyNoneType.INSTANCE;
        }
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyFunction(this);
    }

    @Override
    @RequiredReadAction
    public int getTextOffset() {
        ASTNode name = getNameNode();
        return name != null ? name.getStartOffset() : super.getTextOffset();
    }

    @Override
    @RequiredReadAction
    public PyStringLiteralExpression getDocStringExpression() {
        PyStatementList stmtList = getStatementList();
        return DocStringUtil.findDocStringExpression(stmtList);
    }

    @Override
    @RequiredReadAction
    public String toString() {
        return super.toString() + "('" + getName() + "')";
    }

    @Override
    public void subtreeChanged() {
        super.subtreeChanged();
        ControlFlowCache.clear(this);
    }

    @Override
    public Property getProperty() {
        PyClass containingClass = getContainingClass();
        if (containingClass != null) {
            return containingClass.findPropertyByCallable(this);
        }
        return null;
    }

    @Override
    @RequiredReadAction
    public PyAnnotation getAnnotation() {
        return getStubOrPsiChild(PyElementTypes.ANNOTATION);
    }

    @Nullable
    @Override
    @RequiredReadAction
    public PsiComment getTypeComment() {
        PsiComment inlineComment = PyUtil.getCommentOnHeaderLine(this);
        if (inlineComment != null && PyTypingTypeProvider.getTypeCommentValue(inlineComment.getText()) != null) {
            return inlineComment;
        }

        PyStatementList statements = getStatementList();
        if (statements.getStatements().length != 0) {
            PsiComment comment = as(statements.getFirstChild(), PsiComment.class);
            if (comment != null && PyTypingTypeProvider.getTypeCommentValue(comment.getText()) != null) {
                return comment;
            }
        }
        return null;
    }

    @Nullable
    @Override
    @RequiredReadAction
    public String getTypeCommentAnnotation() {
        PyFunctionStub stub = getStub();
        if (stub != null) {
            return stub.getTypeComment();
        }
        PsiComment comment = getTypeComment();
        if (comment != null) {
            return PyTypingTypeProvider.getTypeCommentValue(comment.getText());
        }
        return null;
    }

    @Nonnull
    @Override
    public SearchScope getUseScope() {
        if (ScopeUtil.getScopeOwner(this) instanceof PyFunction function) {
            return new LocalSearchScope(function);
        }
        return super.getUseScope();
    }

    /**
     * Looks for two standard decorators to a function, or a wrapping assignment that closely follows it.
     *
     * @return a flag describing what was detected.
     */
    @Nullable
    @Override
    @RequiredReadAction
    public Modifier getModifier() {
        String decoName = getClassOrStaticMethodDecorator();
        if (PyNames.CLASSMETHOD.equals(decoName)) {
            return CLASSMETHOD;
        }
        else if (PyNames.STATICMETHOD.equals(decoName)) {
            return STATICMETHOD;
        }

        // implicit staticmethod __new__
        PyClass cls = getContainingClass();
        if (cls != null && PyNames.NEW.equals(getName()) && cls.isNewStyleClass(null)) {
            return STATICMETHOD;
        }

        PyFunctionStub stub = getStub();
        if (stub != null) {
            return getModifierFromStub(stub);
        }

        String funcName = getName();
        if (funcName != null) {
            PyAssignmentStatement currentAssignment = PsiTreeUtil.getNextSiblingOfType(this, PyAssignmentStatement.class);
            while (currentAssignment != null) {
                String modifier = currentAssignment.getTargetsToValuesMapping()
                    .stream()
                    .filter(
                        pair -> pair.getFirst() instanceof PyTargetExpression targetExpr
                            && funcName.equals(targetExpr.getName())
                    )
                    .filter(pair -> pair.getSecond() instanceof PyCallExpression)
                    .map(pair -> interpretAsModifierWrappingCall(
                        (PyCallExpression) pair.getSecond(),
                        this
                    ))
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
    @RequiredReadAction
    public boolean isAsync() {
        PyFunctionStub stub = getStub();
        if (stub != null) {
            return stub.isAsync();
        }
        return getNode().findChildByType(PyTokenTypes.ASYNC_KEYWORD) != null;
    }

    @Override
    @RequiredReadAction
    public boolean isAsyncAllowed() {
        LanguageLevel languageLevel = LanguageLevel.forElement(this);
        String functionName = getName();

        return languageLevel.isAtLeast(LanguageLevel.PYTHON35)
            && (functionName == null
            || ArrayUtil.contains(functionName, PyNames.AITER, PyNames.ANEXT, PyNames.AENTER, PyNames.AEXIT)
            || !PyNames.getBuiltinMethods(languageLevel).containsKey(functionName));
    }

    @Nullable
    private static Modifier getModifierFromStub(@Nonnull PyFunctionStub stub) {
        Optional<List<StubElement>> siblingsStubsOptional = Optional.of(stub)
            .map(StubElement::getParentStub)
            .map(StubElement::getChildrenStubs);

        if (siblingsStubsOptional.isPresent()) {
            return JBIterable.from(siblingsStubsOptional.get())
                .skipWhile(siblingStub -> !stub.equals(siblingStub))
                .transform(nextSiblingStub -> as(
                    nextSiblingStub,
                    PyTargetExpressionStub.class
                ))
                .filter(Objects::nonNull)
                .filter(nextSiblingStub -> nextSiblingStub.getInitializerType() == PyTargetExpressionStub.InitializerType.CallExpression)
                .transform(PyTargetExpressionStub::getInitializer)
                .transform(initializerName -> {
                    if (initializerName.matches(PyNames.CLASSMETHOD)) {
                        return CLASSMETHOD;
                    }
                    else if (initializerName.matches(PyNames.STATICMETHOD)) {
                        return STATICMETHOD;
                    }
                    else {
                        return null;
                    }
                })
                .find(Objects::nonNull);
        }

        return null;
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
    @Nullable
    @RequiredReadAction
    private String getClassOrStaticMethodDecorator() {
        PyDecoratorList decoList = getDecoratorList();
        if (decoList != null) {
            PyDecorator[] decos = decoList.getDecorators();
            if (decos.length > 0) {
                ExtensionPoint<PyKnownDecoratorProvider> knownDecoratorProviders =
                    decoList.getApplication().getExtensionPoint(PyKnownDecoratorProvider.class);
                for (int i = decos.length - 1; i >= 0; i -= 1) {
                    PyDecorator deco = decos[i];
                    String decoName = deco.getName();
                    if (PyNames.CLASSMETHOD.equals(decoName) || PyNames.STATICMETHOD.equals(decoName)) {
                        return decoName;
                    }
                    String name = knownDecoratorProviders.computeSafeIfAny(provider -> provider.toKnownDecorator(decoName));
                    if (name != null) {
                        return name;
                    }
                }
            }
        }
        return null;
    }

    @Nullable
    @Override
    public String getQualifiedName() {
        return QualifiedNameFinder.getQualifiedName(this);
    }

    @Nonnull
    @Override
    public List<PyAssignmentStatement> findAttributes() {
        /**
         * TODO: This method if insanely heavy since it unstubs foreign files.
         * Need to save stubs and use them somehow.
         *
         */
        return CachedValuesManager.getManager(getProject()).getCachedValue(
            this,
            ATTRIBUTES_KEY,
            () -> {
                List<PyAssignmentStatement> result = findAttributesStatic(this);
                return CachedValueProvider.Result.create(result, PsiModificationTracker.MODIFICATION_COUNT);
            },
            false
        );
    }

    /**
     * @param self should be this
     */
    @Nonnull
    @RequiredReadAction
    private static List<PyAssignmentStatement> findAttributesStatic(@Nonnull PsiElement self) {
        List<PyAssignmentStatement> result = new ArrayList<>();
        for (PyAssignmentStatement statement : new PsiQuery(self).siblings(PyAssignmentStatement.class).getElements()) {
            List<PyQualifiedExpression> elements = new PsiQuery(statement.getTargets()).filter(PyQualifiedExpression.class).getElements();
            for (PyQualifiedExpression targetExpression : elements) {
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

    @Nonnull
    @Override
    @RequiredReadAction
    public ProtectionLevel getProtectionLevel() {
        int underscoreLevels = PyUtil.getInitialUnderscores(getName());
        for (ProtectionLevel level : ProtectionLevel.values()) {
            if (level.getUnderscoreLevel() == underscoreLevels) {
                return level;
            }
        }
        return ProtectionLevel.PRIVATE;
    }
}
