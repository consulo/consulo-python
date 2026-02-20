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
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.impl.codeInsight.PyTypingTypeProvider;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.references.PyQualifiedReference;
import com.jetbrains.python.impl.psi.impl.references.PyTargetReference;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.impl.PyTypeProvider;
import com.jetbrains.python.psi.impl.stubs.CustomTargetExpressionStub;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyClassStub;
import com.jetbrains.python.psi.stubs.PyFunctionStub;
import com.jetbrains.python.psi.stubs.PyTargetExpressionStub;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.content.scope.SearchScope;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.scope.LocalSearchScope;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.navigation.ItemPresentation;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.ex.awtUnsafe.TargetAWT;
import consulo.util.lang.Pair;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;

/**
 * @author yole
 */
public class PyTargetExpressionImpl extends PyBaseElementImpl<PyTargetExpressionStub> implements PyTargetExpression {
    QualifiedName myQualifiedName;

    public PyTargetExpressionImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyTargetExpressionImpl(PyTargetExpressionStub stub) {
        super(stub, PyElementTypes.TARGET_EXPRESSION);
    }

    public PyTargetExpressionImpl(PyTargetExpressionStub stub, IStubElementType nodeType) {
        super(stub, nodeType);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyTargetExpression(this);
    }

    @Nullable
    @Override
    @RequiredReadAction
    public String getName() {
        PyTargetExpressionStub stub = getStub();
        if (stub != null) {
            return stub.getName();
        }
        ASTNode node = getNameElement();
        return node != null ? node.getText() : null;
    }

    @Override
    @RequiredReadAction
    public int getTextOffset() {
        ASTNode nameElement = getNameElement();
        return nameElement != null ? nameElement.getStartOffset() : getTextRange().getStartOffset();
    }

    @Override
    @Nullable
    @RequiredReadAction
    public ASTNode getNameElement() {
        return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
    }

    @Override
    @RequiredReadAction
    public PsiElement getNameIdentifier() {
        ASTNode nameElement = getNameElement();
        return nameElement == null ? null : nameElement.getPsi();
    }

    @Override
    @RequiredReadAction
    public String getReferencedName() {
        return getName();
    }

    @Override
    @RequiredWriteAction
    public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
        ASTNode oldNameElement = getNameElement();
        if (oldNameElement != null) {
            ASTNode nameElement = PyUtil.createNewName(this, name);
            getNode().replaceChild(oldNameElement, nameElement);
        }
        return this;
    }

    @Override
    @RequiredReadAction
    public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key) {
        if (!TypeEvalStack.mayEvaluate(this)) {
            return null;
        }
        try {
            if (PyNames.ALL.equals(getName())) {
                // no type for __all__, to avoid unresolved reference errors for expressions where a qualifier is a name
                // imported via __all__
                return null;
            }
            PyType pyType = PyReferenceExpressionImpl.getReferenceTypeFromProviders(this, context, null);
            if (pyType != null) {
                return pyType;
            }
            PyType type = getTypeFromDocString();
            if (type != null) {
                return type;
            }
            if (!context.maySwitchToAST(this)) {
                PsiElement value = resolveAssignedValue(PyResolveContext.noImplicits().withTypeEvalContext(context));
                if (value instanceof PyTypedElement typedElem) {
                    type = context.getType(typedElem);
                    if (type instanceof PyNoneType) {
                        return null;
                    }
                    return type;
                }
                return null;
            }
            type = getTypeFromComment(this);
            if (type != null) {
                return type;
            }
            PsiElement parent = getParent();
            if (parent instanceof PyAssignmentStatement assignment) {
                PyExpression assignedValue = assignment.getAssignedValue();
                if (assignedValue instanceof PyParenthesizedExpression parenthesized) {
                    assignedValue = parenthesized.getContainedExpression();
                }
                if (assignedValue != null) {
                    if (assignedValue instanceof PyYieldExpression assignedYield) {
                        return assignedYield.isDelegating() ? context.getType(assignedValue) : null;
                    }
                    return context.getType(assignedValue);
                }
            }
            if (parent instanceof PyTupleExpression) {
                PsiElement nextParent = parent.getParent();
                while (nextParent instanceof PyParenthesizedExpression || nextParent instanceof PyTupleExpression) {
                    nextParent = nextParent.getParent();
                }
                if (nextParent instanceof PyAssignmentStatement assignment) {
                    PyExpression value = assignment.getAssignedValue();
                    PyExpression lhs = assignment.getLeftHandSideExpression();
                    PyTupleExpression targetTuple = PsiTreeUtil.findChildOfType(lhs, PyTupleExpression.class, false);
                    if (value != null && targetTuple != null) {
                        PyType assignedType = PyTypeChecker.toNonWeakType(context.getType(value), context);
                        if (assignedType instanceof PyTupleType tupleType) {
                            PyType t = PyTypeChecker.getTargetTypeFromTupleAssignment(this, targetTuple, tupleType);
                            if (t != null) {
                                return t;
                            }
                        }
                    }
                }
            }
            if (parent instanceof PyWithItem withItem) {
                return getWithItemVariableType(context, withItem);
            }
            PyType iterType = getTypeFromIteration(context);
            if (iterType != null) {
                return iterType;
            }
            PyType excType = getTypeFromExcept();
            if (excType != null) {
                return excType;
            }
            return null;
        }
        finally {
            TypeEvalStack.evaluated(this);
        }
    }

    @Nullable
    @Override
    public PyAnnotation getAnnotation() {
        PsiElement topTarget = this;
        while (topTarget.getParent() instanceof PyParenthesizedExpression) {
            topTarget = topTarget.getParent();
        }
        PsiElement parent = topTarget.getParent();
        if (parent != null) {
            PyAssignmentStatement assignment = as(parent, PyAssignmentStatement.class);
            if (assignment != null) {
                PyExpression[] targets = assignment.getRawTargets();
                if (targets.length == 1 && targets[0] == topTarget) {
                    return assignment.getAnnotation();
                }
            }
            else if (parent instanceof PyTypeDeclarationStatement) {
                return ((PyTypeDeclarationStatement) parent).getAnnotation();
            }
        }
        return null;
    }

    @Nullable
    private static PyType getWithItemVariableType(TypeEvalContext context, PyWithItem item) {
        PyExpression expression = item.getExpression();
        if (expression != null && context.getType(expression) instanceof PyClassType exprClassType) {
            PyClass cls = exprClassType.getPyClass();
            PyFunction enter = cls.findMethodByName(PyNames.ENTER, true, null);
            if (enter != null) {
                PyType enterType = enter.getCallType(expression, Collections.<PyExpression, PyNamedParameter>emptyMap(), context);
                if (enterType != null) {
                    return enterType;
                }
                PyType typeFromProvider = item.getApplication().getExtensionPoint(PyTypeProvider.class)
                    .computeSafeIfAny(provider -> provider.getContextManagerVariableType(cls, expression, context));
                if (typeFromProvider == null) {
                    // Guess the return type of __enter__
                    return PyUnionType.createWeakType(exprClassType);
                }
                return typeFromProvider;
            }
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public PyType getTypeFromDocString() {
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
            return PyTypeParser.getTypeByName(this, typeName);
        }
        return null;
    }

    @Nullable
    @RequiredReadAction
    public static PyType getTypeFromComment(PyTargetExpressionImpl targetExpression) {
        String docComment = DocStringUtil.getAttributeDocComment(targetExpression);
        if (docComment != null) {
            StructuredDocString structuredDocString = DocStringUtil.parse(docComment, targetExpression);
            String typeName = structuredDocString.getParamType(null);
            if (typeName == null) {
                typeName = structuredDocString.getParamType(targetExpression.getName());
            }
            if (typeName != null) {
                return PyTypeParser.getTypeByName(targetExpression, typeName);
            }
        }
        return null;
    }

    @Nullable
    private PyType getTypeFromIteration(@Nonnull TypeEvalContext context) {
        PyExpression target = null;
        PyExpression source = null;
        PyForPart forPart = PsiTreeUtil.getParentOfType(this, PyForPart.class);
        if (forPart != null) {
            PyExpression expr = forPart.getTarget();
            if (PsiTreeUtil.isAncestor(expr, this, false)) {
                target = expr;
                source = forPart.getSource();
            }
        }
        PyComprehensionElement comprehension = PsiTreeUtil.getParentOfType(this, PyComprehensionElement.class);
        if (comprehension != null) {
            for (PyComprehensionForComponent c : comprehension.getForComponents()) {
                PyExpression expr = c.getIteratorVariable();
                if (PsiTreeUtil.isAncestor(expr, this, false)) {
                    target = expr;
                    source = c.getIteratedList();
                }
            }
        }
        if (source != null) {
            PyType sourceType = context.getType(source);
            PyType type = getIterationType(sourceType, source, this, context);
            if (type instanceof PyTupleType tupleType && target instanceof PyTupleExpression tupleExpr) {
                return PyTypeChecker.getTargetTypeFromTupleAssignment(this, tupleExpr, tupleType);
            }
            if (target == this && type != null) {
                return type;
            }
        }
        return null;
    }

    @Nullable
    public static PyType getIterationType(
        @Nullable PyType iterableType,
        @Nullable PyExpression source,
        @Nonnull PsiElement anchor,
        @Nonnull TypeEvalContext context
    ) {
        if (iterableType instanceof PyTupleType tupleType) {
            return tupleType.getIteratedItemType();
        }
        else if (iterableType instanceof PyUnionType unionType) {
            Collection<PyType> members = unionType.getMembers();
            List<PyType> iterationTypes = new ArrayList<>();
            for (PyType member : members) {
                iterationTypes.add(getIterationType(member, source, anchor, context));
            }
            return PyUnionType.union(iterationTypes);
        }
        else if (iterableType != null && PyABCUtil.isSubtype(iterableType, PyNames.ITERABLE, context)) {
            PyFunction iterateMethod = findMethodByName(iterableType, PyNames.ITER, context);
            if (iterateMethod != null) {
                PyType iterateReturnType = getContextSensitiveType(iterateMethod, context, source);
                return getCollectionElementType(iterateReturnType);
            }
            String nextMethodName = LanguageLevel.forElement(anchor).isAtLeast(LanguageLevel.PYTHON30) ? PyNames.DUNDER_NEXT : PyNames.NEXT;
            PyFunction next = findMethodByName(iterableType, nextMethodName, context);
            if (next != null) {
                return getContextSensitiveType(next, context, source);
            }
            PyFunction getItem = findMethodByName(iterableType, PyNames.GETITEM, context);
            if (getItem != null) {
                return getContextSensitiveType(getItem, context, source);
            }
        }
        else if (iterableType != null && PyABCUtil.isSubtype(iterableType, PyNames.ASYNC_ITERABLE, context)) {
            PyFunction iterateMethod = findMethodByName(iterableType, PyNames.AITER, context);
            if (iterateMethod != null) {
                PyType iterateReturnType = getContextSensitiveType(iterateMethod, context, source);
                return getCollectionElementType(iterateReturnType);
            }
        }
        return null;
    }

    @Nullable
    private static PyType getCollectionElementType(@Nullable PyType type) {
        if (type instanceof PyCollectionType collectionType) {
            return collectionType.getIteratedItemType();
        }
        return null;
    }

    @Nullable
    private static PyFunction findMethodByName(@Nonnull PyType type, @Nonnull String name, @Nonnull TypeEvalContext context) {
        PyResolveContext resolveContext = PyResolveContext.defaultContext().withTypeEvalContext(context);
        List<? extends RatedResolveResult> results = type.resolveMember(name, null, AccessDirection.READ, resolveContext);
        if (results != null && !results.isEmpty()) {
            RatedResolveResult result = results.get(0);
            PsiElement element = result.getElement();
            if (element instanceof PyFunction) {
                return (PyFunction) element;
            }
        }
        return null;
    }

    @Nullable
    public static PyType getContextSensitiveType(
        @Nonnull PyFunction function,
        @Nonnull TypeEvalContext context,
        @Nullable PyExpression source
    ) {
        return function.getCallType(source, Collections.<PyExpression, PyNamedParameter>emptyMap(), context);
    }

    @Nullable
    @RequiredReadAction
    private PyType getTypeFromExcept() {
        PyExceptPart exceptPart = PsiTreeUtil.getParentOfType(this, PyExceptPart.class);
        if (exceptPart == null || exceptPart.getTarget() != this) {
            return null;
        }
        if (exceptPart.getExceptClass() instanceof PyReferenceExpression refExpr
            && refExpr.getReference().resolve() instanceof PyClass pyClass) {
            return new PyClassTypeImpl(pyClass, false);
        }
        return null;
    }

    @Override
    @RequiredReadAction
    public PyExpression getQualifier() {
        ASTNode qualifier = getNode().findChildByType(PythonDialectsTokenSetProvider.INSTANCE.getExpressionTokens());
        return qualifier != null ? (PyExpression) qualifier.getPsi() : null;
    }

    @Nullable
    @Override
    public QualifiedName asQualifiedName() {
        if (myQualifiedName == null) {
            myQualifiedName = PyPsiUtils.asQualifiedName(this);
        }
        return myQualifiedName;
    }

    @Override
    @RequiredReadAction
    public String toString() {
        return super.toString() + ": " + getName();
    }

    @RequiredReadAction
    public Icon getIcon(int flags) {
        if (isQualified() || PsiTreeUtil.getStubOrPsiParentOfType(this, PyDocStringOwner.class) instanceof PyClass) {
            return TargetAWT.to(PlatformIconGroup.nodesField());
        }
        return TargetAWT.to(PlatformIconGroup.nodesVariable());
    }

    @Override
    @RequiredReadAction
    public boolean isQualified() {
        PyTargetExpressionStub stub = getStub();
        if (stub != null) {
            return stub.isQualified();
        }
        return getQualifier() != null;
    }

    @Nullable
    @Override
    @RequiredReadAction
    public PsiElement resolveAssignedValue(@Nonnull PyResolveContext resolveContext) {
        TypeEvalContext context = resolveContext.getTypeEvalContext();
        if (context.maySwitchToAST(this)) {
            PyExpression value = findAssignedValue();
            if (value != null) {
                List<PsiElement> results = PyUtil.multiResolveTopPriority(value, resolveContext);
                return !results.isEmpty() ? results.get(0) : null;
            }
            return null;
        }
        else {
            QualifiedName qName = getAssignedQName();
            if (qName != null) {
                ScopeOwner owner = ScopeUtil.getScopeOwner(this);
                if (owner instanceof PyTypedElement) {
                    List<String> components = qName.getComponents();
                    if (!components.isEmpty()) {
                        PsiElement resolved = owner;
                        for (String component : components) {
                            if (!(resolved instanceof PyTypedElement typedElem)) {
                                return null;
                            }
                            PyType qualifierType = context.getType(typedElem);
                            if (qualifierType == null) {
                                return null;
                            }
                            List<? extends RatedResolveResult> results =
                                qualifierType.resolveMember(component, null, AccessDirection.READ, resolveContext);
                            if (results == null || results.isEmpty()) {
                                return null;
                            }
                            resolved = results.get(0).getElement();
                        }
                        return resolved;
                    }
                }
            }
            return null;
        }
    }

    @Nullable
    @Override
    @RequiredReadAction
    public PyExpression findAssignedValue() {
        PyPsiUtils.assertValid(this);
        PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(this, PyAssignmentStatement.class);
        if (assignment != null) {
            List<Pair<PyExpression, PyExpression>> mapping = assignment.getTargetsToValuesMapping();
            for (Pair<PyExpression, PyExpression> pair : mapping) {
                PyExpression assigned_to = pair.getFirst();
                if (assigned_to == this) {
                    return pair.getSecond();
                }
            }
        }
        return null;
    }

    @Nullable
    @Override
    @RequiredReadAction
    public QualifiedName getAssignedQName() {
        PyTargetExpressionStub stub = getStub();
        if (stub != null) {
            if (stub.getInitializerType() == PyTargetExpressionStub.InitializerType.ReferenceExpression) {
                return stub.getInitializer();
            }
            return null;
        }
        return PyPsiUtils.toQualifiedName(findAssignedValue());
    }

    @Override
    @RequiredReadAction
    public QualifiedName getCalleeName() {
        PyTargetExpressionStub stub = getStub();
        if (stub != null) {
            PyTargetExpressionStub.InitializerType initializerType = stub.getInitializerType();
            if (initializerType == PyTargetExpressionStub.InitializerType.CallExpression) {
                return stub.getInitializer();
            }
            else if (initializerType == PyTargetExpressionStub.InitializerType.Custom) {
                CustomTargetExpressionStub customStub = stub.getCustomStub(CustomTargetExpressionStub.class);
                if (customStub != null) {
                    return customStub.getCalleeName();
                }
            }
            return null;
        }
        PyExpression value = findAssignedValue();
        if (value instanceof PyCallExpression callExpr) {
            PyExpression callee = callExpr.getCallee();
            return PyPsiUtils.toQualifiedName(callee);
        }
        return null;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiReference getReference() {
        return getReference(PyResolveContext.defaultContext());
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public PsiPolyVariantReference getReference(PyResolveContext resolveContext) {
        if (isQualified()) {
            return new PyQualifiedReference(this, resolveContext);
        }
        return new PyTargetReference(this, resolveContext);
    }

    @Nonnull
    @Override
    @RequiredReadAction
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
                return ((PyClassStub) parentStub).getPsi();
            }
            if (parentStub instanceof PyFunctionStub) {
                StubElement functionParent = parentStub.getParentStub();
                if (functionParent instanceof PyClassStub) {
                    return ((PyClassStub) functionParent).getPsi();
                }
            }

            return null;
        }

        PsiElement parent = PsiTreeUtil.getParentOfType(this, PyFunction.class, PyClass.class);
        if (parent instanceof PyClass pyClass) {
            return pyClass;
        }
        if (parent instanceof PyFunction function) {
            return function.getContainingClass();
        }
        return null;
    }

    @Override
    public ItemPresentation getPresentation() {
        return new PyElementPresentation(this) {
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
    @Override
    public String getDocStringValue() {
        PyTargetExpressionStub stub = getStub();
        if (stub != null) {
            return stub.getDocString();
        }
        return DocStringUtil.getDocStringValue(this);
    }

    @Nullable
    @Override
    public StructuredDocString getStructuredDocString() {
        return DocStringUtil.getStructuredDocString(this);
    }

    @Nullable
    @Override
    @RequiredReadAction
    public PyStringLiteralExpression getDocStringExpression() {
        if (getParent() instanceof PyAssignmentStatement assignment
            && PyPsiUtils.getNextNonCommentSibling(assignment, true) instanceof PyExpressionStatement exprStmt
            && exprStmt.getExpression() instanceof PyStringLiteralExpression stringLiteral) {
            return stringLiteral;
        }
        return null;
    }

    @Override
    public void subtreeChanged() {
        super.subtreeChanged();
        myQualifiedName = null;
    }

    @Nullable
    @Override
    public String getQualifiedName() {
        return QualifiedNameFinder.getQualifiedName(this);
    }

    @Nullable
    @Override
    @RequiredReadAction
    public PsiComment getTypeComment() {
        PsiComment comment = null;
        PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(this, PyAssignmentStatement.class);
        if (assignment != null) {
            PyExpression assignedValue = assignment.getAssignedValue();
            if (assignedValue != null) {
                comment = as(PyPsiUtils.getNextNonWhitespaceSiblingOnSameLine(assignedValue), PsiComment.class);
            }
        }
        else {
            PyStatementListContainer forOrWith = PsiTreeUtil.getParentOfType(this, PyForPart.class, PyWithStatement.class);
            if (forOrWith != null) {
                comment = PyUtil.getCommentOnHeaderLine(forOrWith);
            }
        }
        return comment != null && PyTypingTypeProvider.getTypeCommentValue(comment.getText()) != null ? comment : null;
    }

    @Nullable
    @Override
    @RequiredReadAction
    public String getTypeCommentAnnotation() {
        PyTargetExpressionStub stub = getStub();
        if (stub != null) {
            return stub.getTypeComment();
        }

        PsiComment comment = getTypeComment();
        if (comment != null) {
            return PyTypingTypeProvider.getTypeCommentValue(comment.getText());
        }
        return null;
    }
}
