// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.inspection.LocalQuickFixOnPsiElement;
import consulo.language.editor.intention.HighPriorityAction;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.module.content.ProjectFileIndex;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ContainerUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * The object contains a list of import candidates and serves only to show the initial hint;
 * the actual work is done in ImportFromExistingAction..
 */
public class AutoImportQuickFix extends LocalQuickFixOnPsiElement implements HighPriorityAction {

    private final List<ImportCandidateHolder> myImports; // from where and what to import
    private final String myInitialName;
    private final boolean myUseQualifiedImport;
    private final Class<? extends PsiReference> myReferenceType;
    private boolean myExpended = false;

    /**
     * Creates a new, empty fix object.
     *
     * @param node    to which the fix applies.
     * @param name    name to import
     * @param qualify if true, add an "import ..." statement and qualify the name; else use "from ... import name"
     */
    public AutoImportQuickFix(PsiElement node,
                              Class<? extends PsiReference> referenceType,
                              String name,
                              boolean qualify) {
        this(node, referenceType, name, qualify, Collections.emptyList());
    }

    private AutoImportQuickFix(PsiElement node,
                               Class<? extends PsiReference> referenceType,
                               String name,
                               boolean qualify,
                               Collection<ImportCandidateHolder> candidates) {
        super(node);
        myReferenceType = referenceType;
        myInitialName = name;
        myUseQualifiedImport = qualify;
        myImports = new ArrayList<>(candidates);
    }

    /**
     * Adds another import source.
     *
     * @param importable    an element that could be imported either from import element or from file.
     * @param file          the file which is the source of the importable
     * @param importElement an existing import element that can be a source for the importable.
     */
    public void addImport(PsiNamedElement importable, PsiFile file, @Nullable PyImportElement importElement) {
        myImports.add(new ImportCandidateHolder(importable, file, importElement, null, null, null));
    }

    /**
     * Adds another import source.
     *
     * @param importable an element that could be imported either from import element or from file.
     * @param file       the file which is the source of the importable
     * @param path       import path for the file, as a qualified name (a.b.c)
     */
    public void addImport(PsiNamedElement importable, PsiFileSystemItem file, @Nullable QualifiedName path) {
        myImports.add(new ImportCandidateHolder(importable, file, null, path, null, null));
    }

    public void addImport(PsiNamedElement importable,
                          PsiFileSystemItem file,
                          @Nullable QualifiedName path,
                          @Nullable String asName) {
        addImport(importable, file, null, path, asName);
    }

    public void addImport(PsiNamedElement importable,
                          PsiFileSystemItem file,
                          @Nullable PyImportElement importElement,
                          @Nullable QualifiedName path,
                          @Nullable String asName) {
        myImports.add(new ImportCandidateHolder(importable, file, importElement, path, asName, null));
    }

    public void addImport(PsiNamedElement importable,
                          PsiFileSystemItem file,
                          @Nullable QualifiedName path,
                          @Nullable String asName,
                          @Nullable String qualifiedReferenceText) {
        myImports.add(new ImportCandidateHolder(importable, file, null, path, asName, qualifiedReferenceText));
    }

    public void addImport(PsiNamedElement importable,
                          PsiFileSystemItem file,
                          @Nullable PyImportElement importElement,
                          @Nullable QualifiedName path,
                          @Nullable String asName,
                          @Nullable String qualifiedReferenceText) {
        myImports.add(new ImportCandidateHolder(importable, file, importElement, path, asName, qualifiedReferenceText));
    }

    @Override
    public LocalizeValue getText() {
        if (myUseQualifiedImport) {
            return PyLocalize.actQualifyWithModule();
        }
        else if (myImports.size() == 1) {
            return PyLocalize.qfixAutoImportImportName(myImports.get(0).getPresentableText());
        }
        else {
            return PyLocalize.qfixAutoImportImportThisName();
        }
    }

    public LocalizeValue getFamilyName() {
        return PyLocalize.qfixNameAutoImport();
    }

    public ImportFromExistingAction createAction(PsiElement element) {
        final ImportFromExistingAction action =
            new ImportFromExistingAction(element, myImports, myInitialName, null, myUseQualifiedImport, false);
        action.onDone(() -> myExpended = true);
        return action;
    }

    @Override
    @RequiredReadAction
    public boolean isAvailable() {
        final PsiElement element = getStartElement();
        if (element == null) {
            return false;
        }
        PyPsiUtils.assertValid(element);
        return !myExpended && element.isValid() && !myImports.isEmpty();
    }

    @Override
    public void invoke(Project project, PsiFile psiFile, PsiElement startElement, PsiElement endElement) {
        invoke();
    }

    public void invoke() throws IncorrectOperationException {
        // make sure file is committed, writable, etc
        final PsiElement startElement = getStartElement();
        if (startElement == null) {
            return;
        }
        final PsiReference reference = findOriginalReference(startElement);
        if (reference == null || isResolved(reference)) {
            return;
        }
        // act
        ImportFromExistingAction action = createAction();
        if (action != null) {
            action.execute(); // assume that action runs in WriteAction on its own behalf
        }
        myExpended = true;
    }

    protected @Nullable ImportFromExistingAction createAction() {
        return new ImportFromExistingAction(getStartElement(), myImports, myInitialName, null, myUseQualifiedImport, false);
    }

    public void sortCandidates() {
        Collections.sort(myImports);
    }

    public List<ImportCandidateHolder> getCandidates() {
        return Collections.unmodifiableList(myImports);
    }

    public boolean hasOnlyFunctions() {
        for (ImportCandidateHolder holder : myImports) {
            if (!(holder.getImportable() instanceof PyFunction)) {
                return false;
            }
        }
        return true;
    }

    public boolean hasProjectImports() {
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(getStartElement().getProject());
        for (ImportCandidateHolder anImport : myImports) {
            PsiFileSystemItem importFile = anImport.getFile();
            VirtualFile file = importFile != null ? importFile.getVirtualFile() : null;
            if (file != null && fileIndex.isInContent(file)) {
                return true;
            }
        }
        return false;
    }

    public AutoImportQuickFix forLocalImport() {
        return new AutoImportLocallyQuickFix(getStartElement(), myReferenceType, this.myInitialName, myUseQualifiedImport, myImports);
    }

    public String getNameToImport() {
        return myInitialName;
    }

    public Class<? extends PsiReference> getReferenceType() {
        return myReferenceType;
    }

    public boolean isUseQualifiedImport() {
        return myUseQualifiedImport;
    }

    public static boolean isResolved(PsiReference reference) {
        if (reference instanceof PsiPolyVariantReference) {
            return ((PsiPolyVariantReference) reference).multiResolve(false).length > 0;
        }
        return reference.resolve() != null;
    }

    public @Nullable PsiReference findOriginalReference(PsiElement element) {
        return ContainerUtil.findInstance(element.getReferences(), myReferenceType);
    }

    @Override
    public @Nullable AutoImportQuickFix getFileModifierForPreview(PsiFile target) {
        PsiElement unresolvedRef = getStartElement();
        if (unresolvedRef == null) {
            return null;
        }
        PsiElement unresolvedRefCopy = PsiTreeUtil.findSameElementInCopy(unresolvedRef, target);
        List<ImportCandidateHolder> candidates = new ArrayList<>();
        for (ImportCandidateHolder candidate : myImports) {
            ImportCandidateHolder importCandidateForPreview = updateExistingImportElementForPreview(candidate, target);
            if (importCandidateForPreview == null) {
                return null;
            }
            candidates.add(importCandidateForPreview);
        }
        return new AutoImportQuickFix(unresolvedRefCopy, myReferenceType, myInitialName, myUseQualifiedImport, candidates);
    }

    private static @Nullable ImportCandidateHolder updateExistingImportElementForPreview(ImportCandidateHolder candidate,
                                                                                         PsiFile target) {
        PyImportElement importElement = candidate.getImportElement();
        if (importElement == null) {
            return candidate;
        }
        if (candidate.getImportable() == null || candidate.getFile() == null) {
            return null;
        }
        return new ImportCandidateHolder(candidate.getImportable(),
            candidate.getFile(),
            PsiTreeUtil.findSameElementInCopy(importElement, target),
            candidate.getPath(),
            candidate.getAsName(),
            candidate.getQualifiedReferenceText());
    }

    private static class AutoImportLocallyQuickFix extends AutoImportQuickFix {
        private AutoImportLocallyQuickFix(PsiElement element,
                                          Class<? extends PsiReference> type,
                                          String name,
                                          boolean qualify,
                                          List<ImportCandidateHolder> imports) {
            super(element, type, name, qualify, imports);
        }

        @Override
        public LocalizeValue getFamilyName() {
            return PyLocalize.qfixNameLocalAutoImport();
        }

        @Override
        public LocalizeValue getText() {
            return PyLocalize.qfixLocalAutoImportImportLocally(super.getText());
        }

        @Override
        protected ImportFromExistingAction createAction() {
            return new ImportFromExistingAction(getStartElement(), getCandidates(), getNameToImport(), null, isUseQualifiedImport(), true);
        }
    }
}
