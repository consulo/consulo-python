// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.move.moduleMembers;

import com.google.common.collect.Sets;
import com.jetbrains.python.impl.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.impl.psi.resolve.PyNamespacePackageUtil;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.impl.refactoring.move.PyMoveRefactoringUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.language.editor.refactoring.BaseRefactoringProcessor;
import consulo.language.editor.refactoring.event.RefactoringEventData;
import consulo.language.editor.refactoring.ui.UsageViewDescriptorAdapter;
import consulo.language.editor.refactoring.util.CommonRefactoringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.undoRedo.CommandProcessor;
import consulo.usage.UsageInfo;
import consulo.usage.UsageViewDescriptor;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.MultiMap;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Group found usages by moved elements and move each of these elements using {@link PyMoveSymbolProcessor}.
 *
 * @author Mikhail Golubev
 * @see PyMoveSymbolProcessor
 */
public class PyMoveModuleMembersProcessor extends BaseRefactoringProcessor {
    private final List<SmartPsiElementPointer<PsiNamedElement>> myElements;
    private final Set<PsiFile> mySourceFiles;
    private final String myDestination;

    public PyMoveModuleMembersProcessor(PsiNamedElement[] elements, String destination) {
        super(elements[0].getProject());
        SmartPointerManager manager = SmartPointerManager.getInstance(myProject);
        myElements = ContainerUtil.map(elements, manager::createSmartPsiElementPointer);
        mySourceFiles = new LinkedHashSet<>(ContainerUtil.map(elements, PsiElement::getContainingFile));
        myDestination = destination;
    }

    @Override
    protected UsageViewDescriptor createUsageViewDescriptor(UsageInfo[] usages) {
        return new UsageViewDescriptorAdapter() {
            @Override
            public PsiElement[] getElements() {
                return ContainerUtil.mapNotNull(myElements, SmartPsiElementPointer::getElement).toArray(PsiElement.EMPTY_ARRAY);
            }

            @Override
            public String getProcessedElementsHeader() {
                return getRefactoringName().get();
            }
        };
    }

    @Override
    protected UsageInfo[] findUsages() {
        return StreamEx.of(myElements)
            .map(SmartPsiElementPointer::getElement)
            .nonNull()
            .flatMap(e -> StreamEx.of(PyPsiIndexUtil.findUsages(e, false))
                .map(info -> new MyUsageInfo(info, e)))
            .toArray(UsageInfo[]::new);
    }

    @Override
    protected void performRefactoring(UsageInfo[] usages) {
        MultiMap<PsiElement, UsageInfo> usagesByElement = MultiMap.create();
        for (UsageInfo usage : usages) {
            usagesByElement.putValue(((MyUsageInfo) usage).myMovedElement, usage);
        }
        boolean isNamespace = ContainerUtil.all(mySourceFiles, PyNamespacePackageUtil::isInNamespacePackage);
        CommandProcessor.getInstance().newCommand()
            .project(myProject)
            .name(getRefactoringName())
            .inWriteAction()
            .run(() -> {
                PyFile destination = PyClassRefactoringUtil.getOrCreateFile(myDestination, myProject, isNamespace);
                CommonRefactoringUtil.checkReadOnlyStatus(myProject, destination);
                Set<PsiFile> optimizeImportsTargets = Sets.newLinkedHashSet(mySourceFiles);
                for (SmartPsiElementPointer<PsiNamedElement> pointer : myElements) {
                    // TODO: Check for resulting circular imports
                    PsiNamedElement e = pointer.getElement();
                    if (e == null) {
                        continue;
                    }
                    CommonRefactoringUtil.checkReadOnlyStatus(myProject, e);
                    assert e instanceof PyClass || e instanceof PyFunction || e instanceof PyTargetExpression;
                    String name = e.getName();
                    if (name == null) {
                        continue;
                    }
                    if (e instanceof PyClass && destination.findTopLevelClass(name) != null) {
                        throw new IncorrectOperationException(PyLocalize.refactoringMoveErrorDestinationFileContainsClass(name));
                    }
                    if (e instanceof PyFunction && destination.findTopLevelFunction(name) != null) {
                        throw new IncorrectOperationException(PyLocalize.refactoringMoveErrorDestinationFileContainsFunction(name));
                    }
                    if (e instanceof PyTargetExpression && destination.findTopLevelAttribute(name) != null) {
                        throw new IncorrectOperationException(PyLocalize.refactoringMoveErrorDestinationFileContainsGlobalVariable(name));
                    }
                    Collection<UsageInfo> usageInfos = usagesByElement.get(e);
                    boolean usedFromOutside = ContainerUtil.exists(usageInfos, usageInfo -> {
                        PsiElement element = usageInfo.getElement();
                        return element != null && !PsiTreeUtil.isAncestor(e, element, false);
                    });
                    if (usedFromOutside) {
                        PyMoveRefactoringUtil.checkValidImportableFile(e, destination.getVirtualFile());
                    }
                    PyMoveSymbolResult results = new PyMoveSymbolProcessor(e, destination, usageInfos, myElements).moveElement();
                    optimizeImportsTargets.addAll(results.getOptimizeImportsTargets());
                }

                for (PsiFile file : optimizeImportsTargets) {
                    PyClassRefactoringUtil.optimizeImports(file);
                }
            });
    }

    @Override
    protected LocalizeValue getCommandName() {
        return getRefactoringName();
    }

    @Override
    protected @Nullable String getRefactoringId() {
        return "refactoring.python.move.module.members";
    }

    @Override
    protected @Nullable RefactoringEventData getBeforeData() {
        RefactoringEventData data = new RefactoringEventData();
        data.addElements(ContainerUtil.<SmartPsiElementPointer<PsiNamedElement>, PsiElement>mapNotNull(
            myElements,
            SmartPsiElementPointer::getElement
        ));
        return data;
    }

    @Override
    protected @Nullable RefactoringEventData getAfterData(UsageInfo[] usages) {
        RefactoringEventData data = new RefactoringEventData();
        data.addElements(ContainerUtil.<SmartPsiElementPointer<PsiNamedElement>, PsiElement>mapNotNull(
            myElements,
            SmartPsiElementPointer::getElement
        ));
        return data;
    }

    private static class MyUsageInfo extends UsageInfo {
        private final PsiElement myMovedElement;

        MyUsageInfo(UsageInfo usageInfo, PsiElement element) {
            // Consulo: UsageInfo has no reference class
            super(usageInfo.getSmartPointer(), usageInfo.getPsiFileRange(), usageInfo.isDynamicUsage(), usageInfo.isNonCodeUsage);
            myMovedElement = element;
        }
    }

    public static LocalizeValue getRefactoringName() {
        return PyLocalize.refactoringMoveModuleMembers();
    }
}
