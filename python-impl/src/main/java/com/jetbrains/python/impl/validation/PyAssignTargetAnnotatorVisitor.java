// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.validation;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentExpression;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyBoolLiteralExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyDelStatement;
import com.jetbrains.python.psi.PyDictCompExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyForStatement;
import com.jetbrains.python.psi.PyGeneratorExpression;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.PyListLiteralExpression;
import com.jetbrains.python.psi.PyNoneLiteralExpression;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyParenthesizedExpression;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySequenceExpression;
import com.jetbrains.python.psi.PySetCompExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PyStarExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyWithItem;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.validation.PyAnnotationHolder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.python.impl.localize.PyLocalize;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;


public class PyAssignTargetAnnotatorVisitor extends PyElementVisitor {
    private final PyAnnotationHolder myHolder;

    public PyAssignTargetAnnotatorVisitor(PyAnnotationHolder holder) {
        myHolder = holder;
    }

    private PyAnnotationHolder getHolder() {
        return myHolder;
    }

    private enum Operation {
        Assign, AugAssign, Delete, Except, For, With
    }

    @Override
    public void visitPyAssignmentStatement(PyAssignmentStatement node) {
        for (PyExpression expression : node.getRawTargets()) {
            expression.accept(new ExprVisitor(Operation.Assign));
        }

        PyExpression expression = node.getAssignedValue();
        if (expression instanceof PyAssignmentExpression) {
            getHolder()
                .newAnnotation(HighlightSeverity.ERROR, PyLocalize.annUnparenthesizedAssignmentExpressionValue().get())
                .range(expression)
                .create();
        }
    }

    @Override
    public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
        node.getTarget().accept(new ExprVisitor(Operation.AugAssign));
    }

    @Override
    public void visitPyDelStatement(PyDelStatement node) {
        ExprVisitor visitor = new ExprVisitor(Operation.Delete);
        for (PyExpression expr : node.getTargets()) {
            expr.accept(visitor);
        }
    }

    @Override
    public void visitPyExceptBlock(PyExceptPart node) {
        PyExpression target = node.getTarget();
        if (target != null) {
            target.accept(new ExprVisitor(Operation.Except));
        }
    }

    @Override
    public void visitPyForStatement(PyForStatement node) {
        PyExpression target = node.getForPart().getTarget();
        if (target != null) {
            target.accept(new ExprVisitor(Operation.For));
            checkNotAssignmentExpression(target, PyLocalize.annAssignmentExpressionAsATarget().get());
        }
    }

    @Override
    public void visitPyWithItem(PyWithItem node) {
        PyExpression target = node.getTarget();
        if (target != null) {
            target.accept(new ExprVisitor(Operation.With));
        }
    }

    @Override
    public void visitPyExpressionStatement(PyExpressionStatement node) {
        PyExpression expression = node.getExpression();
        if (expression instanceof PyAssignmentExpression) {
            getHolder()
                .newAnnotation(HighlightSeverity.ERROR, PyLocalize.annUnparenthesizedAssignmentExpressionStatement().get())
                .range(expression)
                .create();
        }
    }

    @Override
    public void visitPyAssignmentExpression(PyAssignmentExpression node) {
        PyComprehensionElement comprehensionElement =
            PsiTreeUtil.getParentOfType(node, PyComprehensionElement.class, true, ScopeOwner.class);
        if (ScopeUtil.getScopeOwner(comprehensionElement) instanceof PyClass) {
            getHolder().newAnnotation(HighlightSeverity.ERROR,
                PyLocalize.annAssignmentExpressionsWithinAComprehensionCannotBeUsedInAClassBody().get()).create();
        }
    }

    @Override
    public void visitPyComprehensionElement(PyComprehensionElement node) {
        String targetMessage = PyLocalize.annAssignmentExpressionAsATarget().get();
        String iterableMessage = PyLocalize.annAssignmentExpressionInAnIterable().get();

        node.getForComponents().forEach(
            it -> {
                PyExpression iteratorVariable = it.getIteratorVariable();
                iteratorVariable.accept(new ExprVisitor(Operation.For));
                checkNotAssignmentExpression(iteratorVariable, targetMessage);
                checkNoAssignmentExpressionAsChild(it.getIteratedList(), iterableMessage);
            }
        );
    }

    private void checkNoAssignmentExpressionAsChild(@Nullable PyExpression expression, String message) {
        PsiTreeUtil
            .findChildrenOfType(expression, PyAssignmentExpression.class)
            .forEach(it -> checkNotAssignmentExpression(it, message));
    }

    private void checkNotAssignmentExpression(@Nullable PyExpression expression, String message) {
        if (PyPsiUtils.flattenParens(expression) instanceof PyAssignmentExpression) {
            getHolder()
                .newAnnotation(HighlightSeverity.ERROR, message)
                .range(expression)
                .create();
        }
    }

    private class ExprVisitor extends PyElementVisitor {
        private final Operation myOp;

        ExprVisitor(Operation op) {
            myOp = op;
        }

        @Override
        public void visitPyReferenceExpression(PyReferenceExpression node) {
            String referencedName = node.getReferencedName();
            if (PyNames.NONE.equals(referencedName)) {
                //noinspection DialogTitleCapitalization
                getHolder().newAnnotation(HighlightSeverity.ERROR, (myOp == Operation.Delete) ?
                    PyLocalize.annDeletingNone().get() :
                    PyLocalize.annAssignToNone().get()).range(node).create();
            }
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            String targetName = node.getName();
            if (PyNames.NONE.equals(targetName)) {
                VirtualFile vfile = node.getContainingFile().getVirtualFile();
                if (vfile != null && !vfile.getUrl().contains("/" + PythonSdkType.SKELETON_DIR_NAME + "/")) {
                    //noinspection DialogTitleCapitalization
                    getHolder().newAnnotation(HighlightSeverity.ERROR,
                            (myOp == Operation.Delete) ?
                                PyLocalize.annDeletingNone().get() :
                                PyLocalize.annAssignToNone().get()).range(node)
                        .create();
                }
            }
            if (PyNames.DEBUG.equals(targetName)) {
                if (LanguageLevel.forElement(node).isPy3K()) {
                    getHolder().newAnnotation(HighlightSeverity.ERROR, PyLocalize.annAssignmentToKeyword().get()).range(node).create();
                }
                else {
                    getHolder().newAnnotation(HighlightSeverity.ERROR, PyLocalize.annCannotAssignToDebug().get()).range(node).create();
                }
            }
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            getHolder().newAnnotation(HighlightSeverity.ERROR, (myOp == Operation.Delete) ?
                    PyLocalize.annCantDeleteCall().get() :
                    PyLocalize.annCantAssignToCall().get()).range(node)
                .create();
        }

        @Override
        public void visitPyGeneratorExpression(PyGeneratorExpression node) {
            getHolder().newAnnotation(HighlightSeverity.ERROR,
                (myOp == Operation.AugAssign ? PyLocalize.annCantAugAssignToGenerator() : PyLocalize.annCantAssignToGenerator()).get())
                .range(node).create();
        }

        @Override
        public void visitPyBinaryExpression(PyBinaryExpression node) {
            getHolder().newAnnotation(HighlightSeverity.ERROR, PyLocalize.annCantAssignToOperator().get()).range(node).create();
        }

        @Override
        public void visitPyTupleExpression(PyTupleExpression node) {
            if (node.isEmpty() && LanguageLevel.forElement(node).isPython2()) {
                getHolder().newAnnotation(HighlightSeverity.ERROR, PyLocalize.annCantAssignToParens().get()).range(node).create();
            }
            else if (myOp == Operation.AugAssign) {
                getHolder().newAnnotation(HighlightSeverity.ERROR, PyLocalize.annCantAugAssignToTupleOrGenerator().get()).range(node).create();
            }
            else {
                node.acceptChildren(this);
            }
        }

        @Override
        public void visitPyParenthesizedExpression(PyParenthesizedExpression node) {
            if (myOp == Operation.AugAssign) {
                getHolder().newAnnotation(HighlightSeverity.ERROR, PyLocalize.annCantAugAssignToTupleOrGenerator().get()).range(node).create();
            }
            else {
                node.acceptChildren(this);
            }
        }

        @Override
        public void visitPyListLiteralExpression(PyListLiteralExpression node) {
            if (myOp == Operation.AugAssign) {
                getHolder().newAnnotation(HighlightSeverity.ERROR, PyLocalize.annCantAugAssignToListOrComprh().get()).range(node).create();
            }
            else {
                node.acceptChildren(this);
            }
        }

        @Override
        public void visitPyListCompExpression(PyListCompExpression node) {
            getHolder().markError(node,
                (myOp == Operation.AugAssign ? PyLocalize.annCantAugAssignToComprh() : PyLocalize.annCantAssignToComprh()).get());
        }

        @Override
        public void visitPyDictCompExpression(PyDictCompExpression node) {
            getHolder().markError(node,
                (myOp == Operation.AugAssign ? PyLocalize.annCantAugAssignToDictComprh() : PyLocalize.annCantAssignToDictComprh()).get());
        }

        @Override
        public void visitPySetCompExpression(PySetCompExpression node) {
            getHolder().markError(node,
                (myOp == Operation.AugAssign ? PyLocalize.annCantAugAssignToSetComprh() : PyLocalize.annCantAssignToSetComprh()).get());
        }

        @Override
        public void visitPyStarExpression(PyStarExpression node) {
            super.visitPyStarExpression(node);
            if (!(node.getParent() instanceof PySequenceExpression)) {
                getHolder().markError(node, PyLocalize.annCantAugAssignStarredAssignmentTargetMustBeInListOrTuple().get());
            }
        }

        @Override
        public void visitPyDictLiteralExpression(PyDictLiteralExpression node) {
            checkLiteral(node);
        }

        @Override
        public void visitPySetLiteralExpression(PySetLiteralExpression node) {
            checkLiteral(node);
        }

        @Override
        public void visitPyNumericLiteralExpression(PyNumericLiteralExpression node) {
            checkLiteral(node);
        }

        @Override
        public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
            checkLiteral(node);
        }

        private void checkLiteral(PsiElement node) {
            getHolder().newAnnotation(HighlightSeverity.ERROR,
                    (myOp == Operation.Delete ? PyLocalize.annCantDeleteLiteral() : PyLocalize.annCantAssignToLiteral()).get()).range(node)
                .create();
        }

        @Override
        public void visitPyLambdaExpression(PyLambdaExpression node) {
            getHolder().newAnnotation(HighlightSeverity.ERROR, PyLocalize.annCantAssignToLambda().get()).range(node).create();
        }

        @Override
        public void visitPyNoneLiteralExpression(PyNoneLiteralExpression node) {
            getHolder().newAnnotation(HighlightSeverity.ERROR, PyLocalize.annAssignmentToKeyword().get()).range(node).create();
        }

        @Override
        public void visitPyBoolLiteralExpression(PyBoolLiteralExpression node) {
            getHolder().newAnnotation(HighlightSeverity.ERROR, PyLocalize.annAssignmentToKeyword().get()).range(node).create();
        }

        @Override
        public void visitPyPrefixExpression(PyPrefixExpression node) {
            if (node.getOperator() == PyTokenTypes.AWAIT_KEYWORD) {
                getHolder().newAnnotation(HighlightSeverity.ERROR, PyLocalize.annCantAssignToAwaitExpr().get()).range(node).create();
            }
        }
    }
}
