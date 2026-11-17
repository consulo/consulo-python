// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.ast;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonDialectsTokenSetProvider;
import com.jetbrains.python.ast.docstring.DocStringUtilCore;
import com.jetbrains.python.ast.impl.PyPsiUtilsCore;
import com.jetbrains.python.ast.impl.PyUtilCore;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.ParameterizedCachedValue;
import consulo.application.util.ParameterizedCachedValueProvider;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.util.dataholder.Key;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.Pair;
import org.jspecify.annotations.Nullable;

import java.util.List;

public interface PyAstTargetExpression extends PyAstQualifiedExpression, PsiNameIdentifierOwner, PyAstDocStringOwner,
                                               PyAstQualifiedNameOwner, PyAstReferenceOwner,
                                               PyAstPossibleClassMember, PyAstTypeCommentOwner, PyAstAnnotationOwner {
    PyAstTargetExpression[] EMPTY_ARRAY = new PyAstTargetExpression[0];

    @Override
    @RequiredReadAction
    default @Nullable String getName() {
        ASTNode node = getNameElement();
        return node != null ? node.getText() : null;
    }

    @Override
    @RequiredReadAction
    default int getTextOffset() {
        ASTNode nameElement = getNameElement();
        return nameElement != null ? nameElement.getStartOffset() : getTextRange().getStartOffset();
    }

    @Override
    @RequiredReadAction
    default @Nullable ASTNode getNameElement() {
        return getNode().findChildByType(PyTokenTypes.IDENTIFIER);
    }

    @Override
    @RequiredReadAction
    default PsiElement getNameIdentifier() {
        ASTNode nameElement = getNameElement();
        return nameElement == null ? null : nameElement.getPsi();
    }

    @Override
    @RequiredReadAction
    default String getReferencedName() {
        return getName();
    }

    @Override
    @RequiredReadAction
    default PsiElement setName(String name) throws IncorrectOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    @RequiredReadAction
    default @Nullable PyAstAnnotation getAnnotation() {
        PsiElement topTarget = this;
        while (topTarget.getParent() instanceof PyAstParenthesizedExpression) {
            topTarget = topTarget.getParent();
        }
        PsiElement parent = topTarget.getParent();
        if (parent != null) {
            PyAstAssignmentStatement assignment = ObjectUtil.tryCast(parent, PyAstAssignmentStatement.class);
            if (assignment != null) {
                PyAstExpression[] targets = assignment.getRawTargets();
                if (targets.length == 1 && targets[0] == topTarget) {
                    return assignment.getAnnotation();
                }
            }
            else if (parent instanceof PyAstTypeDeclarationStatement) {
                return ((PyAstTypeDeclarationStatement)parent).getAnnotation();
            }
        }
        return null;
    }

    @Override
    @RequiredReadAction
    default @Nullable PyAstExpression getQualifier() {
        ASTNode qualifier = getNode().findChildByType(PythonDialectsTokenSetProvider.getInstance().getExpressionTokens());
        return qualifier != null ? (PyAstExpression)qualifier.getPsi() : null;
    }

    @Override
    @RequiredReadAction
    default boolean isQualified() {
        return getQualifier() != null;
    }

    @Override
    @RequiredReadAction
    default @Nullable PyAstClass getContainingClass() {
        PsiElement parent = PsiTreeUtil.getParentOfType(this, PyAstFunction.class, PyAstClass.class);
        if (parent instanceof PyAstClass) {
            return (PyAstClass)parent;
        }
        if (parent instanceof PyAstFunction) {
            return ((PyAstFunction)parent).getContainingClass();
        }
        return null;
    }

    @Override
    @RequiredReadAction
    default @Nullable PyAstStringLiteralExpression getDocStringExpression() {
        PsiElement parent = getParent();
        if (parent instanceof PyAstAssignmentStatement || parent instanceof PyAstTypeDeclarationStatement) {
            PsiElement nextSibling = PyPsiUtilsCore.getNextNonCommentSibling(parent, true);
            if (nextSibling instanceof PyAstExpressionStatement) {
                PyAstExpression expression = ((PyAstExpressionStatement)nextSibling).getExpression();
                if (expression instanceof PyAstStringLiteralExpression) {
                    return (PyAstStringLiteralExpression)expression;
                }
            }
        }
        return null;
    }

    /**
     * Find the value that maps to this target expression in an enclosing assignment expression.
     * Does not work with other expressions (e.g. if the target is in a 'for' loop).
     *
     * Operates at the AST level.
     *
     * @return the expression assigned to target via an enclosing assignment expression, or null.
     */
    @RequiredReadAction
    default @Nullable PyAstExpression findAssignedValue() {
        PyPsiUtilsCore.assertValid(this);
        // TODO consulo: StubBuildCachedValuesManager.getCachedValueStubBuildOptimized missing
        return CachedValuesManager.getManager(getProject())
            .getParameterizedCachedValue(this, FIND_ASSIGNED_VALUE_KEY, FIND_ASSIGNED_VALUE_PROVIDER, false, this);
    }

    Key<ParameterizedCachedValue<PyAstExpression, PyAstTargetExpression>> FIND_ASSIGNED_VALUE_KEY = Key.create("python.findAssignedValue");

    ParameterizedCachedValueProvider<PyAstExpression, PyAstTargetExpression>
        FIND_ASSIGNED_VALUE_PROVIDER = expression -> CachedValueProvider.Result
            .create(expression.findAssignedValueInternal(), PsiModificationTracker.MODIFICATION_COUNT);

    @RequiredReadAction
    private @Nullable PyAstExpression findAssignedValueInternal() {
        PyAstAssignmentStatement assignment = PsiTreeUtil.getParentOfType(this, PyAstAssignmentStatement.class);
        if (assignment != null) {
            List<? extends Pair<? extends PyAstExpression, ? extends PyAstExpression>> mapping = assignment.getTargetsToValuesMapping();
            for (Pair<? extends PyAstExpression, ? extends PyAstExpression> pair : mapping) {
                PyAstExpression assigned_to = pair.getFirst();
                if (assigned_to == this) {
                    return pair.getSecond();
                }
            }
        }
        PyAstImportElement importElement = PsiTreeUtil.getParentOfType(this, PyAstImportElement.class);
        if (importElement != null) {
            return importElement.getImportReferenceExpression();
        }
        PyAstAssignmentExpression assignmentExpression = ObjectUtil.tryCast(getParent(), PyAstAssignmentExpression.class);
        if (assignmentExpression != null) {
            return assignmentExpression.getAssignedValue();
        }
        return null;
    }

    /**
     * Returns the qualified name (if there is any) assigned to the expression.
     *
     * This method does not access AST if underlying PSI is stub based.
     */
    @RequiredReadAction
    default @Nullable QualifiedName getAssignedQName() {
        PyAstExpression value = findAssignedValue();
        return value instanceof PyAstReferenceExpression ? ((PyAstReferenceExpression)value).asQualifiedName() : null;
    }

    /**
     * If the value assigned to the target expression is a call, returns the (unqualified and unresolved) name of the
     * callee. Otherwise, returns null.
     *
     * @return the name of the callee or null if the assigned value is not a call.
     */
    @RequiredReadAction
    default @Nullable QualifiedName getCalleeName() {
        PyAstExpression value = findAssignedValue();
        return value instanceof PyAstCallExpression ? PyPsiUtilsCore.asQualifiedName(((PyAstCallExpression)value).getCallee()) : null;
    }

    /**
     * Checks if target has assigned value.
     *
     * This method does not access AST if underlying PSI is stub based.
     *
     * @return true if target has assigned expression, false otherwise (e.g. in type declaration statement).
     */
    default boolean hasAssignedValue() {
        return true;
    }

    @Override
    @RequiredReadAction
    default @Nullable String getDocStringValue() {
        return DocStringUtilCore.getDocStringValue(this);
    }

    @Override
    default void acceptPyVisitor(PyAstElementVisitor pyVisitor) {
        pyVisitor.visitPyTargetExpression(this);
    }

    @Override
    @RequiredReadAction
    default @Nullable PsiComment getTypeComment() {
        PsiComment comment = null;
        PyAstAssignmentStatement assignment = PsiTreeUtil.getParentOfType(this, PyAstAssignmentStatement.class);
        if (assignment != null) {
            PyAstExpression assignedValue = assignment.getAssignedValue();
            if (assignedValue != null && !PsiTreeUtil.isAncestor(assignedValue, this, false)) {
                comment = ObjectUtil.tryCast(PyPsiUtilsCore.getNextNonWhitespaceSiblingOnSameLine(assignedValue), PsiComment.class);
            }
        }
        else {
            PyAstStatementListContainer forOrWith = null;
            PyAstForPart forPart = PsiTreeUtil.getParentOfType(this, PyAstForPart.class);
            if (forPart != null && PsiTreeUtil.isAncestor(forPart.getTarget(), this, false)) {
                forOrWith = forPart;
            }
            PyAstWithItem withPart = PsiTreeUtil.getParentOfType(this, PyAstWithItem.class);
            if (withPart != null && PsiTreeUtil.isAncestor(withPart.getTarget(), this, false)) {
                forOrWith = ObjectUtil.tryCast(withPart.getParent(), PyAstWithStatement.class);
            }

            if (forOrWith != null) {
                comment = PyUtilCore.getCommentOnHeaderLine(forOrWith);
            }
        }
        return comment != null && PyUtilCore.getTypeCommentValue(comment.getText()) != null ? comment : null;
    }
}
