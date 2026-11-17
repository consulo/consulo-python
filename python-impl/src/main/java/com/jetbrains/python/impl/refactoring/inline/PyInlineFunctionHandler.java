// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.inline;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.references.PyImportReference;
import com.jetbrains.python.impl.psi.resolve.MainPythonSdkKt;
import com.jetbrains.python.impl.psi.search.PyOverridingMethodsSearch;
import com.jetbrains.python.impl.psi.search.PySuperMethodsSearch;
import com.jetbrains.python.impl.pyi.PyiFile;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.impl.refactoring.PyRefactoringUiService;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyIfPart;
import com.jetbrains.python.psi.PyIfStatement;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReturnStatement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.content.bundle.Sdk;
import consulo.language.Language;
import consulo.language.editor.TargetElementUtil;
import consulo.language.editor.refactoring.inline.InlineActionHandler;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.SyntaxTraverser;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Aleksei.Kniazev
 */
@ExtensionImpl
public class PyInlineFunctionHandler extends InlineActionHandler {
    @Override
    public boolean isEnabledForLanguage(@Nullable Language l) {
        return l instanceof PythonLanguage;
    }

    @Override
    public boolean canInlineElement(@Nullable PsiElement element) {
        if (element instanceof PyFunction function) {
            PsiFile containingFile;
            if (function.getContainingFile() instanceof PyiFile) {
                PsiElement original = PyiUtil.getOriginalElement(function);
                containingFile = original != null ? original.getContainingFile() : null;
            }
            else {
                containingFile = function.getContainingFile();
            }
            return containingFile instanceof PyFile;
        }
        return false;
    }

    @Override
    public void inlineElement(@Nullable Project project, @Nullable Editor editor, @Nullable PsiElement element) {
        invoke(project, editor, element);
    }

    public void invoke(@Nullable Project project, @Nullable Editor editor, @Nullable PsiElement element) {
        invoke(project, editor, element, true);
    }

    public void invoke(@Nullable Project project, @Nullable Editor editor, @Nullable PsiElement element, boolean showDialog) {
        invoke(project, editor, element, showDialog, false);
    }

    public void invoke(
        @Nullable Project project, @Nullable Editor editor, @Nullable PsiElement element, boolean showDialog,
        boolean inlineThisOnly
    ) {
        if (project == null || editor == null || !(element instanceof PyFunction function)) {
            return;
        }
        Scope functionScope = ControlFlowCache.getScope(function);
        LocalizeValue error;
        if (function.isAsync()) {
            error = PyLocalize.refactoringInlineFunctionAsync();
        }
        else if (function.isGenerator()) {
            error = PyLocalize.refactoringInlineFunctionGenerator();
        }
        else if (PyUtil.isConstructorLikeMethod(function)) {
            error = PyLocalize.refactoringInlineFunctionConstructor();
        }
        else if (PyBuiltinCache.getInstance(function).isBuiltin(function)) {
            error = PyLocalize.refactoringInlineFunctionBuiltin();
        }
        else if (isSpecialMethod(function)) {
            error = PyLocalize.refactoringInlineFunctionSpecialMethod();
        }
        else if (isUnderSkeletonDir(function)) {
            error = PyLocalize.refactoringInlineFunctionSkeletonOnly();
        }
        else if (hasDecorators(function)) {
            error = PyLocalize.refactoringInlineFunctionDecorator();
        }
        else if (hasReferencesToSelf(function)) {
            error = PyLocalize.refactoringInlineFunctionSelfReferrent();
        }
        else if (hasStarArgs(function)) {
            error = PyLocalize.refactoringInlineFunctionStar();
        }
        else if (overridesMethod(function, project)) {
            error = PyLocalize.refactoringInlineFunctionOverridesMethod();
        }
        else if (isOverridden(function)) {
            error = PyLocalize.refactoringInlineFunctionIsOverridden();
        }
        else if (functionScope.hasGlobals()) {
            error = PyLocalize.refactoringInlineFunctionGlobal();
        }
        else if (functionScope.hasNonLocals()) {
            error = PyLocalize.refactoringInlineFunctionNonlocal();
        }
        else if (hasNestedFunction(function)) {
            error = PyLocalize.refactoringInlineFunctionNested();
        }
        else if (hasNonExhaustiveIfs(function)) {
            error = PyLocalize.refactoringInlineFunctionInterruptsFlow();
        }
        else {
            error = null;
        }
        if (error != null) {
            CommonRefactoringUtil.showErrorHint(project, editor, error, PyLocalize.refactoringInlineFunctionTitle(),
                Helper.REFACTORING_ID);
            return;
        }
        if (showDialog && !ApplicationManager.getApplication().isUnitTestMode()) {
            PyRefactoringUiService.getInstance().showPyInlineFunctionDialog(project, editor, function, Helper.findReference(editor));
        }
        else {
            PyInlineFunctionProcessor processor =
                new PyInlineFunctionProcessor(project, editor, function, Helper.findReference(editor), inlineThisOnly, true);
            processor.setPreviewUsages(false);
            processor.run();
        }
    }

    private boolean isSpecialMethod(PyFunction function) {
        return function.getContainingClass() != null && function.getName() != null &&
            PyNames.getBuiltinMethods(LanguageLevel.forElement(function)).containsKey(function.getName());
    }

    private boolean hasNestedFunction(PyFunction function) {
        for (PsiElement element : SyntaxTraverser.psiTraverser(function.getStatementList()).traverse()) {
            if (element instanceof PyFunction) {
                return true;
            }
        }
        return false;
    }

    private boolean hasNonExhaustiveIfs(PyFunction function) {
        List<PyReturnStatement> returns = new ArrayList<>();

        function.accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyReturnStatement(PyReturnStatement node) {
                returns.add(node);
            }
        });

        if (returns.isEmpty()) {
            return false;
        }
        Set<PyIfStatement> cache = new HashSet<>();
        Set<PyIfStatement> distinct = new LinkedHashSet<>();
        for (PyReturnStatement statement : returns) {
            PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(statement, PyIfStatement.class);
            if (ifStatement != null) {
                distinct.add(ifStatement);
            }
        }
        for (PyIfStatement ifStatement : distinct) {
            if (checkInterruptsControlFlow(ifStatement, cache)) {
                return true;
            }
        }
        return false;
    }

    private boolean checkInterruptsControlFlow(PyIfStatement ifStatement, Set<PyIfStatement> cache) {
        if (cache.contains(ifStatement)) {
            return false;
        }
        cache.add(ifStatement);
        var elsePart = ifStatement.getElsePart();
        if (elsePart == null) {
            return true;
        }

        if (checkLastStatement(ifStatement.getIfPart().getStatementList(), cache)) {
            return true;
        }
        if (checkLastStatement(elsePart.getStatementList(), cache)) {
            return true;
        }
        for (PyIfPart elifPart : ifStatement.getElifParts()) {
            if (checkLastStatement(elifPart.getStatementList(), cache)) {
                return true;
            }
        }

        PyIfStatement parentIfStatement = PsiTreeUtil.getParentOfType(ifStatement, PyIfStatement.class);
        return parentIfStatement != null && checkInterruptsControlFlow(parentIfStatement, cache);
    }

    private boolean checkLastStatement(PyStatementList statementList, Set<PyIfStatement> cache) {
        PyStatement[] statements = statementList.getStatements();
        if (statements.length == 0) {
            return true;
        }
        PyStatement last = statements[statements.length - 1];
        if (last instanceof PyIfStatement lastIf) {
            if (checkInterruptsControlFlow(lastIf, cache)) {
                return true;
            }
        }
        else if (!(last instanceof PyReturnStatement)) {
            return true;
        }
        return false;
    }

    private boolean hasDecorators(PyFunction function) {
        PyDecoratorList decoratorList = function.getDecoratorList();
        return decoratorList != null && decoratorList.getDecorators().length != 0;
    }

    private boolean overridesMethod(PyFunction function, Project project) {
        return function.getContainingClass() != null
            && PySuperMethodsSearch.search(function, TypeEvalContext.codeAnalysis(project, function.getContainingFile()))
            .iterator().hasNext();
    }

    private boolean isOverridden(PyFunction function) {
        return function.getContainingClass() != null && PyOverridingMethodsSearch.search(function, true).iterator().hasNext();
    }

    private boolean hasStarArgs(PyFunction function) {
        for (PyParameter parameter : function.getParameterList().getParameters()) {
            if (parameter instanceof PyNamedParameter named && (named.isPositionalContainer() || named.isKeywordContainer())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasReferencesToSelf(PyFunction function) {
        for (PsiElement element : SyntaxTraverser.psiTraverser(function.getStatementList())) {
            if (element instanceof PyReferenceExpression referenceExpression && referenceExpression.getReference().isReferenceTo(function)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUnderSkeletonDir(PyFunction function) {
        PsiFile containingFile = PyiUtil.getOriginalElementOrLeaveAsIs(function, PyElement.class).getContainingFile();
        Sdk sdk = MainPythonSdkKt.pythonSdkIfReady(containingFile);
        if (sdk == null) {
            return false;
        }
        // Consulo: the legacy PythonSdkUtil.findSkeletonsDir lives in PySdkUtil
        VirtualFile skeletonsDir = PySdkUtil.findSkeletonsDir(sdk);
        if (skeletonsDir == null) {
            return false;
        }
        return VirtualFileUtil.isAncestor(skeletonsDir, containingFile.getVirtualFile(), true);
    }

    public static final class Helper {
        private Helper() {
        }

        public static PyInlineFunctionHandler getInstance() {
            return EP_NAME.findExtensionOrFail(PyInlineFunctionHandler.class);
        }

        public static final String REFACTORING_ID = "refactoring.inlineMethod";

        public static @Nullable PsiReference findReference(Editor editor) {
            // TODO consulo: TargetElementUtilBase.findReferenceWithoutExpectedCaret missing, TargetElementUtil.findReference used
            PsiReference reference = TargetElementUtil.findReference(editor);
            return !(reference instanceof PyImportReference) ? reference : null;
        }
    }
}
