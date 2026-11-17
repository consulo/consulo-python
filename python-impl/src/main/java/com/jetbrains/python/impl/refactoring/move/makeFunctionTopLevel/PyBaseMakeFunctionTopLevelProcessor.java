// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.move.makeFunctionTopLevel;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.controlflow.ReadWriteInstruction;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.impl.refactoring.move.PyMoveRefactoringUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNonlocalStatement;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.Application;
import consulo.language.controlFlow.ControlFlow;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.event.RefactoringEventData;
import consulo.language.editor.refactoring.ui.UsageViewDescriptorAdapter;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.impl.psi.PyUtil.inSameFile;
import static com.jetbrains.python.impl.psi.PyUtil.multiResolveTopPriority;
import static com.jetbrains.python.impl.psi.PyUtil.turnConstructorIntoClass;
import static com.jetbrains.python.impl.psi.resolve.PyNamespacePackageUtil.isInNamespacePackage;

/**
 * @author Mikhail Golubev
 */
public abstract class PyBaseMakeFunctionTopLevelProcessor extends BaseRefactoringProcessor {
    protected final PyFunction myFunction;
    protected final PsiFile mySourceFile;
    protected final PyResolveContext myResolveContext;
    protected final PyElementGenerator myGenerator;
    protected final String myDestinationPath;
    protected final List<PsiElement> myExternalReads = new ArrayList<>();

    public PyBaseMakeFunctionTopLevelProcessor(PyFunction targetFunction, String destinationPath) {
        super(targetFunction.getProject());
        myFunction = targetFunction;
        myDestinationPath = destinationPath;
        TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(myProject, targetFunction.getContainingFile());
        myResolveContext = PyResolveContext.defaultContext(typeEvalContext);
        myGenerator = PyElementGenerator.getInstance(myProject);
        mySourceFile = myFunction.getContainingFile();
    }

    @Override
    protected final UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
        return new UsageViewDescriptorAdapter() {
            @Override
            public PsiElement[] getElements() {
                return new PsiElement[]{myFunction};
            }

            @Override
            public String getProcessedElementsHeader() {
                return getRefactoringName().get();
            }
        };
    }

    @Override
    protected final UsageInfo[] findUsages() {
        return PyPsiIndexUtil.findUsages(myFunction, false).toArray(UsageInfo.EMPTY_ARRAY);
    }

    @Override
    protected final LocalizeValue getCommandName() {
        return getRefactoringName();
    }

    @Override
    protected final void performRefactoring(UsageInfo[] usages) {
        List<String> newParameters = collectNewParameterNames();

        assert Application.get().isWriteAccessAllowed();

        boolean isNamespace = isInNamespacePackage(myFunction);
        PyFile targetFile = PyClassRefactoringUtil.getOrCreateFile(myDestinationPath, myProject, isNamespace);
        if (targetFile.findTopLevelFunction(myFunction.getName()) != null) {
            throw new IncorrectOperationException(PyLocalize.refactoringMoveErrorDestinationFileContainsFunction(myFunction.getName()));
        }
        if (importsRequired(usages, targetFile)) {
            PyMoveRefactoringUtil.checkValidImportableFile(targetFile, targetFile.getVirtualFile());
        }

        PsiElement position = PyMoveRefactoringUtil.findLowestPossibleTopLevelInsertionPosition(Arrays.asList(usages), targetFile);
        // We should update usages before we generate and insert new function, because we have to update its usages inside
        // (e.g. recursive calls) it first
        updateUsages(newParameters, usages);
        PyFunction newFunction = insertFunction(createNewFunction(newParameters), targetFile, position);

        myFunction.delete();

        updateImports(newFunction, usages);
    }

    private boolean importsRequired(UsageInfo[] usages, PyFile targetFile) {
        return ContainerUtil.exists(usages, info -> {
            PsiElement element = info.getElement();
            if (element == null) {
                return false;
            }
            return !belongsToFunction(element) && info.getFile() != targetFile;
        });
    }

    private boolean belongsToFunction(PsiElement element) {
        return PsiTreeUtil.isAncestor(myFunction, element, false);
    }

    private void updateImports(PyFunction newFunction, UsageInfo[] usages) {
        Set<PsiFile> usageFiles = new HashSet<>();
        for (UsageInfo usage : usages) {
            usageFiles.add(usage.getFile());
        }
        for (PsiFile file : usageFiles) {
            if (file != newFunction.getContainingFile()) {
                PyPsiRefactoringUtil.insertImport(file, newFunction, null, true);
            }
        }
        // References inside the body of function
        if (newFunction.getContainingFile() != mySourceFile) {
            for (PsiElement read : myExternalReads) {
                if (read instanceof PsiNamedElement namedElement && read.isValid()) {
                    PyPsiRefactoringUtil.insertImport(newFunction, namedElement, null, true);
                }
            }
            PyClassRefactoringUtil.optimizeImports(mySourceFile);
        }
    }

    protected abstract LocalizeValue getRefactoringName();

    protected abstract List<String> collectNewParameterNames();

    protected abstract void updateUsages(Collection<String> newParamNames, UsageInfo[] usages);

    protected abstract PyFunction createNewFunction(Collection<String> newParamNames);

    protected final PyParameterList addParameters(PyParameterList paramList, Collection<String> newParameters) {
        if (!newParameters.isEmpty()) {
            String commaSeparatedNames = StringUtil.join(newParameters, ", ");
            StringBuilder paramListText = new StringBuilder(paramList.getText());
            paramListText.insert(1, commaSeparatedNames + (paramList.getParameters().length > 0 ? ", " : ""));
            PyParameterList newElement = myGenerator.createParameterList(LanguageLevel.forElement(myFunction), paramListText.toString());
            return (PyParameterList) paramList.replace(newElement);
        }
        return paramList;
    }

    protected PyArgumentList addArguments(PyArgumentList argList, Collection<String> newArguments) {
        if (!newArguments.isEmpty()) {
            String commaSeparatedNames = StringUtil.join(newArguments, ", ");
            StringBuilder argListText = new StringBuilder(argList.getText());
            argListText.insert(1, commaSeparatedNames + (argList.getArguments().length > 0 ? ", " : ""));
            PyArgumentList newElement = myGenerator.createArgumentList(LanguageLevel.forElement(argList), argListText.toString());
            return (PyArgumentList) argList.replace(newElement);
        }
        return argList;
    }

    protected PyFunction insertFunction(PyFunction newFunction, PyFile newFile, @Nullable PsiElement anchor) {
        if (mySourceFile == newFile) {
            // In the same file try inserting generated function at the top level but preferably right after the original scope owner
            PsiElement surroundingStatement = PyPsiUtils.getParentRightBefore(myFunction, mySourceFile);
            if (anchor == null || surroundingStatement.getTextRange().getEndOffset() < anchor.getTextRange().getStartOffset()) {
                return (PyFunction) mySourceFile.addAfter(newFunction, surroundingStatement);
            }
        }
        // Insert at the end or before first top-level usage in the file
        return (PyFunction) newFile.addBefore(newFunction, anchor);
    }

    protected AnalysisResult analyseScope(ScopeOwner owner) {
        ControlFlow controlFlow = ControlFlowCache.getControlFlow(owner);
        AnalysisResult result = new AnalysisResult();
        for (Instruction instruction : controlFlow.getInstructions()) {
            if (instruction instanceof ReadWriteInstruction readWriteInstruction) {
                PsiElement element = readWriteInstruction.getElement();
                if (element == null) {
                    continue;
                }
                if (readWriteInstruction.getAccess().isReadAccess()) {
                    for (PsiElement resolvedElement : multiResolveTopPriority(element, myResolveContext)) {
                        PsiElement resolved =
                            ObjectUtil.chooseNotNull(turnConstructorIntoClass(as(resolvedElement, PyFunction.class)), resolvedElement);
                        if (resolved != null) {
                            if (isFromEnclosingScope(resolved)) {
                                result.readsFromEnclosingScope.add(element);
                            }
                            else if (!belongsToFunction(resolved)) {
                                myExternalReads.add(resolved);
                            }
                            if (resolved instanceof PyParameter parameter && parameter.isSelf()) {
                                if (PsiTreeUtil.getParentOfType(resolved, PyFunction.class) == myFunction) {
                                    result.readsOfSelfParameter.add(element);
                                }
                                else if (!PsiTreeUtil.isAncestor(myFunction, resolved, true)) {
                                    result.readsOfSelfParametersFromEnclosingScope.add(element);
                                }
                            }
                        }
                    }
                }
                if (readWriteInstruction.getAccess().isWriteAccess() && element instanceof PyTargetExpression targetExpression) {
                    for (PsiElement resolved : multiResolveTopPriority(element, myResolveContext)) {
                        if (resolved != null) {
                            if (element.getParent() instanceof PyNonlocalStatement && isFromEnclosingScope(resolved)) {
                                result.nonlocalWritesToEnclosingScope.add(targetExpression);
                            }
                            if (resolved instanceof PyParameter parameter && parameter.isSelf() &&
                                PsiTreeUtil.getParentOfType(resolved, PyFunction.class) == myFunction) {
                                result.writesToSelfParameter.add(targetExpression);
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private boolean isFromEnclosingScope(PsiElement element) {
        return inSameFile(element, myFunction) &&
            !belongsToFunction(element) &&
            !(ScopeUtil.getScopeOwner(element) instanceof PsiFile);
    }

    @Override
    protected @Nullable RefactoringEventData getBeforeData() {
        RefactoringEventData data = new RefactoringEventData();
        data.addElements(List.of(myFunction));
        return data;
    }

    @Override
    protected @Nullable RefactoringEventData getAfterData(UsageInfo[] usages) {
        RefactoringEventData data = new RefactoringEventData();
        data.addElements(List.of(myFunction));
        return data;
    }

    protected static class AnalysisResult {
        final List<PsiElement> readsFromEnclosingScope = new ArrayList<>();
        final List<PyTargetExpression> nonlocalWritesToEnclosingScope = new ArrayList<>();
        final List<PsiElement> readsOfSelfParametersFromEnclosingScope = new ArrayList<>();
        final List<PsiElement> readsOfSelfParameter = new ArrayList<>();
        // No one writes to "self", but handle this case too just to be sure
        final List<PyTargetExpression> writesToSelfParameter = new ArrayList<>();
    }
}
