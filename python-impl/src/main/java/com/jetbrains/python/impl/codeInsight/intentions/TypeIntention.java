// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.impl.documentation.doctest.PyDoctestFile;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyLambdaExpression;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.codeEditor.Editor;
import consulo.language.ast.ASTNode;
import consulo.language.editor.TargetElementUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * User: ktisha
 * Common part for type specifying intentions
 */
public abstract class TypeIntention extends PyBaseIntentionAction {

    @Override
    public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
        if (!(psiFile instanceof PyFile) || psiFile instanceof PyDoctestFile) {
            return false;
        }
        updateText(false);

        if (findOnlySuitableParameter(editor, psiFile) != null) {
            return true;
        }
        if (findOnlySuitableFunction(editor, psiFile) != null) {
            updateText(true);
            return true;
        }
        return false;
    }

    protected final @Nullable PyFunction findOnlySuitableFunction(Editor editor, PsiFile file) {
        return findOnlySuitableFunction(editor, file, input -> !isReturnTypeDefined(input));
    }

    public static @Nullable PyFunction findOnlySuitableFunction(Editor editor,
            PsiFile file,
            Predicate<PyFunction> condition) {
        PsiElement elementAt = getElementUnderCaret(editor, file);
        return elementAt != null ? ContainerUtil.getOnlyItem(findSuitableFunctions(elementAt, condition)) : null;
    }

    protected final @Nullable PyNamedParameter findOnlySuitableParameter(Editor editor, PsiFile file) {
        PsiElement elementAt = getElementUnderCaret(editor, file);
        StreamEx<PyNamedParameter> parameters;
        PyNamedParameter immediateParam = PsiTreeUtil.getParentOfType(elementAt, PyNamedParameter.class);
        if (immediateParam != null) {
            parameters = StreamEx.of(immediateParam);
        }
        else {
            PyReferenceExpression referenceExpr = PsiTreeUtil.getParentOfType(elementAt, PyReferenceExpression.class);
            if (referenceExpr != null) {
                parameters = StreamEx.of(PyUtil.multiResolveTopPriority(referenceExpr, getResolveContext(elementAt)))
                    .select(PyNamedParameter.class);
            }
            else {
                parameters = StreamEx.empty();
            }
        }
        ProjectFileIndex index = ProjectFileIndex.getInstance(file.getProject());
        return parameters
            .filter(param -> !param.isSelf())
            .filter(param -> PsiTreeUtil.getParentOfType(param, PyLambdaExpression.class) == null)
            .filter(param -> {
                VirtualFile dir = param.getContainingFile().getOriginalFile().getVirtualFile();
                return dir != null && !index.isInLibraryClasses(dir);
            })
            .filter(param -> !isParamTypeDefined(param))
            .findFirst().orElse(null);
    }

    private static @Nullable PsiElement getElementUnderCaret(Editor editor, PsiFile file) {
        int offset = TargetElementUtil.adjustOffset(file, editor.getDocument(), editor.getCaretModel().getOffset());
        return PyUtil.findNonWhitespaceAtOffset(file, offset);
    }

    protected abstract void updateText(boolean isReturn);

    protected abstract boolean isParamTypeDefined(PyNamedParameter parameter);

    protected abstract boolean isReturnTypeDefined(PyFunction function);

    private static List<PyFunction> findSuitableFunctions(PsiElement elementAt,
            Predicate<PyFunction> extraCondition) {
        StreamEx<PyFunction> definitions;
        PyFunction immediateDefinition = findFunctionDefinitionUnderCaret(elementAt);
        if (immediateDefinition != null) {
            definitions = StreamEx.of(immediateDefinition);
        }
        else {
            definitions = StreamEx.of(getCallExpressions(elementAt))
                .flatMap(call -> StreamEx.of(call.multiResolveCallee(getResolveContext(elementAt))))
                .map(result -> result.getCallable())
                .select(PyFunction.class);
        }
        ProjectFileIndex index = ProjectFileIndex.getInstance(elementAt.getProject());
        return definitions
            .filter(elem -> {
                VirtualFile dir = elem.getContainingFile().getOriginalFile().getVirtualFile();
                return dir != null && !index.isInLibraryClasses(dir);
            })
            .filter(extraCondition)
            .toList();
    }

    private static @Nullable PyFunction findFunctionDefinitionUnderCaret(PsiElement elementAt) {
        PyFunction parentFunction = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
        if (parentFunction != null) {
            ASTNode nameNode = parentFunction.getNameNode();
            if (nameNode != null) {
                PsiElement prev = elementAt.getContainingFile().findElementAt(elementAt.getTextOffset() - 1);
                if (nameNode.getPsi() == elementAt || nameNode.getPsi() == prev) {
                    return parentFunction;
                }
            }
        }
        return null;
    }

    private static List<PyCallExpression> getCallExpressions(PsiElement elementAt) {
        PyResolveContext context = getResolveContext(elementAt);
        PyReferenceExpression referenceExpr = PsiTreeUtil.getParentOfType(elementAt, PyReferenceExpression.class);
        if (referenceExpr != null) {
            List<PyCallExpression> calls = StreamEx.of(PyUtil.multiResolveTopPriority(referenceExpr, context))
                .select(PyTargetExpression.class)
                .filter(target -> context.getTypeEvalContext().maySwitchToAST(target))
                .map(target -> target.findAssignedValue())
                .select(PyCallExpression.class)
                .toList();
            if (!calls.isEmpty()) {
                return calls;
            }
        }

        PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(elementAt, PyAssignmentStatement.class);
        if (assignment != null) {
            PyExpression assignedValue = assignment.getAssignedValue();
            if (assignedValue instanceof PyCallExpression) {
                return Collections.singletonList((PyCallExpression)assignedValue);
            }
        }
        PyCallExpression immediateCall = PsiTreeUtil.getParentOfType(elementAt, PyCallExpression.class, false);
        if (immediateCall != null) {
            return Collections.singletonList(immediateCall);
        }
        return Collections.emptyList();
    }

    private static PyResolveContext getResolveContext(PsiElement origin) {
        TypeEvalContext typeEvalContext = TypeEvalContext.codeAnalysis(origin.getProject(), origin.getContainingFile());
        return PyResolveContext.defaultContext(typeEvalContext);
    }
}

