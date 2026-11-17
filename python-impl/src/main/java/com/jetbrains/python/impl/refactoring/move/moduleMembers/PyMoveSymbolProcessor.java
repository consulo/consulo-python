// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.move.moduleMembers;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.formatter.PyTrailingBlankLinesPostFormatProcessor;
import com.jetbrains.python.impl.codeInsight.PyDunderAllReference;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import com.jetbrains.python.impl.refactoring.move.PyMoveRefactoringUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.codeStyle.CodeStyle;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.usage.UsageInfo;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import one.util.streamex.StreamEx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.impl.psi.impl.PyImportStatementNavigator.getImportStatementByElement;

/**
 * @author Mikhail Golubev
 */
public class PyMoveSymbolProcessor {
    private final PsiNamedElement myMovedElement;
    private final PyFile myDestinationFile;
    private final List<UsageInfo> myUsages;
    private final List<? extends SmartPsiElementPointer<PsiNamedElement>> myAllMovedElements;
    private final List<PsiFile> myFilesWithStarUsages = new ArrayList<>();
    private final Set<ScopeOwner> myScopeOwnersWithGlobal = new HashSet<>();

    public PyMoveSymbolProcessor(
        PsiNamedElement element,
        PyFile destination,
        Collection<UsageInfo> usages,
        List<? extends SmartPsiElementPointer<PsiNamedElement>> otherElements
    ) {
        myMovedElement = element;
        myDestinationFile = destination;
        myAllMovedElements = otherElements;
        myUsages = ContainerUtil.sorted(usages, (u1, u2) -> PsiUtilCore.compareElementsByPosition(u1.getElement(), u2.getElement()));
    }

    @RequiredWriteAction
    public final PyMoveSymbolResult moveElement() {
        PsiElement oldElementBody = PyMoveModuleMembersHelper.expandNamedElementBody(myMovedElement);
        if (oldElementBody != null) {
            PyClassRefactoringUtil.rememberNamedReferences(oldElementBody);
            PsiElement newElementBody = addElementToFile(oldElementBody);
            PsiNamedElement newElement = PyMoveModuleMembersHelper.extractNamedElement(newElementBody);
            assert newElement != null;
            for (UsageInfo usage : myUsages) {
                PsiElement usageElement = usage.getElement();
                if (usageElement != null) {
                    updateSingleUsage(usageElement, newElement);
                }
            }
            PsiElement[] unwrappedElements =
                ContainerUtil.mapNotNull(myAllMovedElements, SmartPsiElementPointer::getElement).toArray(PsiElement.EMPTY_ARRAY);
            PyClassRefactoringUtil.restoreNamedReferences(newElementBody, myMovedElement, unwrappedElements);
            PyTrailingBlankLinesPostFormatProcessor postFormatProcessor = new PyTrailingBlankLinesPostFormatProcessor();
            if (PsiTreeUtil.nextVisibleLeaf(newElementBody) == null) {
                PyUtil.updateDocumentUnblockedAndCommitted(myDestinationFile, document -> {
                    PsiDocumentManager.getInstance(newElementBody.getProject()).commitDocument(document);
                    postFormatProcessor.processElement(newElementBody, CodeStyle.getSettings(myDestinationFile));
                });
            }
            deleteElement();
        }
        return new PyMoveSymbolResult(myFilesWithStarUsages);
    }

    @RequiredWriteAction
    private void deleteElement() {
        PsiElement elementBody = PyMoveModuleMembersHelper.expandNamedElementBody(myMovedElement);
        assert elementBody != null;
        elementBody.delete();
    }

    @RequiredWriteAction
    private PsiElement addElementToFile(PsiElement element) {
        PsiElement anchor = PyMoveRefactoringUtil.findLowestPossibleTopLevelInsertionPosition(myUsages, myDestinationFile);
        return myDestinationFile.addBefore(element, anchor);
    }

    @RequiredWriteAction
    private void updateSingleUsage(PsiElement usage, PsiNamedElement newElement) {
        PsiFile psiFile = usage.getContainingFile();
        PsiFile usageFile = psiFile;
        if (belongsToSomeMovedElement(usage)) {
            return;
        }
        if (usage instanceof PyQualifiedExpression qualifiedExpr) {
            if (myMovedElement instanceof PyClass && PyNames.INIT.equals(qualifiedExpr.getName())) {
                return;
            }
            else if (qualifiedExpr.isQualified()) {
                insertQualifiedImportAndReplaceReference(newElement, qualifiedExpr);
            }
            else if (usageFile == myMovedElement.getContainingFile()) {
                if (usage.getParent() instanceof PyGlobalStatement globalStatement) {
                    myScopeOwnersWithGlobal.add(ScopeUtil.getScopeOwner(usage));
                    if (globalStatement.getGlobals().length == 1) {
                        usage.getParent().delete();
                    }
                    else {
                        usage.delete();
                    }
                }
                else if (myScopeOwnersWithGlobal.contains(ScopeUtil.getScopeOwner(usage))) {
                    insertQualifiedImportAndReplaceReference(newElement, qualifiedExpr);
                }
                else {
                    insertImportFromAndReplaceReference(newElement, qualifiedExpr);
                }
            }
            else {
                PyImportStatementBase importStmt = getImportStatementByElement(usage);
                if (importStmt != null) {
                    PyClassRefactoringUtil.updateUnqualifiedImportOfElement(importStmt, newElement);
                }
                else if (resolvesToLocalStarImport(usage)) {
                    PyPsiRefactoringUtil.insertImport(usage, newElement);
                    myFilesWithStarUsages.add(usageFile);
                }
            }
        }
        else if (usage instanceof PyStringLiteralExpression) {
            for (PsiReference ref : usage.getReferences()) {
                if (ref instanceof PyDunderAllReference) {
                    PsiFile newFile = newElement.getContainingFile();
                    if (myMovedElement.getContainingFile().getName().equals(PyNames.INIT_DOT_PY)) {
                        // Update import statement in `__init__.py` if symbol is moved from there to another module
                        VirtualFile targetFile = newFile.getVirtualFile();
                        VirtualFile currentFile = psiFile.getVirtualFile();
                        if (targetFile != null && currentFile != null) {
                            VirtualFile currentDir = currentFile.getParent();
                            if (currentDir != null && !VirtualFileUtil.isAncestor(currentDir, targetFile, true)) {
                                usage.delete();
                            }
                            PyPsiRefactoringUtil.insertImport(usage, newElement);
                        }
                    }
                    else if (ref.getElement().getContainingFile().getName().equals(PyNames.INIT_DOT_PY)) {
                        // Remove old import statement in `__init__.py` if symbol is moved from some ancestor module to another one
                        VirtualFile targetFile = newFile.getVirtualFile();
                        VirtualFile currentFile = psiFile.getVirtualFile();
                        if (targetFile != null && currentFile != null) {
                            VirtualFile currentDir = currentFile.getParent();
                            if (currentDir != null && !VirtualFileUtil.isAncestor(currentDir, targetFile, true)) {
                                usage.delete();
                            }
                        }
                    }
                    else {
                        usage.delete();
                    }
                }
                else {
                    if (ref.isReferenceTo(myMovedElement)) {
                        ref.bindToElement(newElement);
                    }
                }
            }
        }
    }

    private boolean belongsToSomeMovedElement(PsiElement element) {
        return StreamEx.of(myAllMovedElements)
            .map(SmartPsiElementPointer::getElement)
            .nonNull()
            .map(PyMoveModuleMembersHelper::expandNamedElementBody)
            .anyMatch(moved -> PsiTreeUtil.isAncestor(moved, element, false));
    }

    /**
     * <pre>{@code
     *   print(foo.bar)
     * }</pre>
     * is transformed to
     * <pre>{@code
     *   from new import bar
     *   print(bar)
     * }</pre>
     */
    @RequiredWriteAction
    private static void insertImportFromAndReplaceReference(PsiNamedElement targetElement, PyQualifiedExpression expression) {
        PyPsiRefactoringUtil.insertImport(expression, targetElement, null, true);
        PyElementGenerator generator = PyElementGenerator.getInstance(expression.getProject());
        PyExpression generated = generator.createExpressionFromText(LanguageLevel.forElement(expression), expression.getReferencedName());
        expression.replace(generated);
    }

    /**
     * <pre>{@code
     *   print(foo.bar)
     * }</pre>
     * is transformed to
     * <pre>{@code
     *   import new
     *   print(new.bar)
     * }</pre>
     */
    @RequiredWriteAction
    private static void insertQualifiedImportAndReplaceReference(PsiNamedElement targetElement, PyQualifiedExpression expression) {
        PyElementGenerator generator = PyElementGenerator.getInstance(expression.getProject());
        PsiFile srcFile = targetElement.getContainingFile();
        LanguageLevel languageLevel = LanguageLevel.forElement(expression);
        if (srcFile != expression.getContainingFile()) {
            QualifiedName qualifier = QualifiedNameFinder.findCanonicalImportPath(srcFile, expression);
            if (PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT) {
                PyPsiRefactoringUtil.insertImport(expression, srcFile, null, true);
                String moduleName = qualifier.getLastComponent();
                String newQualifiedReference = moduleName + "." + expression.getReferencedName();
                expression.replace(generator.createExpressionFromText(languageLevel, newQualifiedReference));
            }
            else {
                PyPsiRefactoringUtil.insertImport(expression, srcFile, null, false);
                String newQualifiedReference = qualifier + "." + expression.getReferencedName();
                expression.replace(generator.createExpressionFromText(languageLevel, newQualifiedReference));
            }
        }
        else {
            expression.replace(generator.createExpressionFromText(languageLevel, expression.getReferencedName()));
        }
    }

    @RequiredReadAction
    private static boolean resolvesToLocalStarImport(PsiElement usage) {
        // Don't use PyUtil#multiResolveTopPriority here since it filters out low priority ImportedResolveResults
        List<PsiElement> resolvedElements = new ArrayList<>();
        if (usage instanceof PyReferenceOwner referenceOwner) {
            TypeEvalContext context = TypeEvalContext.codeInsightFallback(usage.getProject());
            PsiPolyVariantReference reference = referenceOwner.getReference(PyResolveContext.implicitContext(context));
            for (ResolveResult result : reference.multiResolve(false)) {
                resolvedElements.add(result.getElement());
            }
        }
        else {
            PsiReference ref = usage.getReference();
            if (ref != null) {
                resolvedElements.add(ref.resolve());
            }
        }
        PsiFile containingFile = usage.getContainingFile();
        if (containingFile != null) {
            for (PsiElement resolved : resolvedElements) {
                if (resolved instanceof PyStarImportElement && resolved.getContainingFile() == containingFile) {
                    return true;
                }
            }
        }
        return false;
    }
}
