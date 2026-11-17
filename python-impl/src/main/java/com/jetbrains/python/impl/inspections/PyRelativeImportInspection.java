// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.namespacePackages.PyNamespacePackagesService;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredWriteAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.Document;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.editor.rawHighlight.HighlightDisplayLevel;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.python.impl.localize.PySourceRootsLocalize;
import consulo.undoRedo.BasicUndoableAction;
import consulo.undoRedo.ProjectUndoManager;
import consulo.undoRedo.UnexpectedUndoException;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class PyRelativeImportInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameRelativeImport();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public HighlightDisplayLevel getDefaultLevel() {
        return HighlightDisplayLevel.WEAK_WARNING;
    }

    // TODO consulo: JB registers suppressId="PyPackages"

    @Override
    public PsiElementVisitor buildVisitor(
        ProblemsHolder holder,
        boolean isOnTheFly,
        LocalInspectionToolSession session,
        Object state
    ) {
        if (!PyNamespacePackagesService.isEnabled() ||
            LanguageLevel.forElement(holder.getFile()).isOlderThan(LanguageLevel.PYTHON34)) {
            return PsiElementVisitor.EMPTY_VISITOR;
        }
        return new Visitor(holder, PyInspectionVisitor.getContext(session));
    }

    private static class Visitor extends PyInspectionVisitor {
        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context) {
            super(holder, context);
        }

        @Override
        public void visitPyFromImportStatement(PyFromImportStatement node) {
            PsiFile containingFile = node.getContainingFile();
            PsiDirectory directory = containingFile != null ? containingFile.getContainingDirectory() : null;
            if (directory == null) {
                return;
            }
            if (node.getRelativeLevel() > 0 && !PyUtil.isExplicitPackage(directory) && !isInsideOrdinaryPackage(directory)) {
                handleRelativeImportNotInsidePackage(node, directory);
            }
        }

        private static boolean isInsideOrdinaryPackage(PsiDirectory directory) {
            PsiDirectory curDir = directory;
            while (curDir != null) {
                if (PyUtil.isOrdinaryPackage(curDir)) {
                    return true;
                }
                curDir = curDir.getParentDirectory();
            }
            return false;
        }

        private void handleRelativeImportNotInsidePackage(PyFromImportStatement node, PsiDirectory directory) {
            List<LocalQuickFix> fixes = new ArrayList<>();
            PyMarkAsNamespacePackageQuickFix markFix = getMarkAsNamespacePackageQuickFix(directory);
            if (markFix != null) {
                fixes.add(markFix);
            }
            if (node.getRelativeLevel() == 1) {
                fixes.add(new PyChangeToSameDirectoryImportQuickFix());
            }
            LocalizeValue message = PyLocalize.inspRelativeImportRelativeImportOutsidePackage();
            registerProblem(node, message.get(), fixes.toArray(LocalQuickFix.EMPTY_ARRAY));
        }

        private static @Nullable PyMarkAsNamespacePackageQuickFix getMarkAsNamespacePackageQuickFix(PsiDirectory directory) {
            Module module = ModuleUtilCore.findModuleForPsiElement(directory);
            if (module == null) {
                return null;
            }

            PsiDirectory curDir = directory;
            while (curDir != null) {
                VirtualFile virtualFile = curDir.getVirtualFile();
                if (PyUtil.isRoot(curDir)) {
                    return null;
                }

                PsiDirectory parentDir = curDir.getParentDirectory();
                if (parentDir != null && (PyUtil.isRoot(parentDir) || PyUtil.isOrdinaryPackage(parentDir))) {
                    return new PyMarkAsNamespacePackageQuickFix(module, virtualFile);
                }

                curDir = parentDir;
            }

            return null;
        }
    }

    private static class PyMarkAsNamespacePackageQuickFix implements LocalQuickFix {
        private final Module myModule;
        private final VirtualFile myDirectory;

        PyMarkAsNamespacePackageQuickFix(Module module, VirtualFile directory) {
            myModule = module;
            myDirectory = directory;
        }

        @Override
        public LocalizeValue getName() {
            return PySourceRootsLocalize.qfixMarkAsNamespacePackage(myDirectory.getName());
        }

        @Override
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            Document document = PsiDocumentManager.getInstance(project).getDocument(descriptor.getPsiElement().getContainingFile());
            BasicUndoableAction undoableAction = new BasicUndoableAction(document == null ? new Document[0] : new Document[]{document}) {
                @Override
                public void undo() {
                    PyNamespacePackagesService.getInstance(myModule).toggleMarkingAsNamespacePackage(myDirectory);
                }

                @Override
                public void redo() {
                    PyNamespacePackagesService.getInstance(myModule).toggleMarkingAsNamespacePackage(myDirectory);
                }
            };
            try {
                undoableAction.redo();
            }
            catch (UnexpectedUndoException e) {
                throw new RuntimeException(e);
            }
            ProjectUndoManager.getInstance(project).undoableActionPerformed(undoableAction);
        }

        // TODO consulo: IntentionPreviewInfo missing - JB generatePreview returns IntentionPreviewInfo.EMPTY:
        //  the quick fix updates a directory's properties in the project structure, nothing changes in the current file
    }

    // JB: PsiUpdateModCommandQuickFix (ModCommand API is not available in Consulo)
    private static class PyChangeToSameDirectoryImportQuickFix implements LocalQuickFix {
        @Override
        public LocalizeValue getName() {
            return PySourceRootsLocalize.qfixChangeToSameDirectoryImport();
        }

        @Override
        @RequiredWriteAction
        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if (!(descriptor.getPsiElement() instanceof PyFromImportStatement oldImport)) {
                return;
            }
            assert oldImport.getRelativeLevel() == 1;
            PyReferenceExpression qualifier = oldImport.getImportSource();
            if (qualifier != null) {
                PsiElement possibleDot = PsiTreeUtil.prevVisibleLeaf(qualifier);
                assert possibleDot != null && possibleDot.getNode().getElementType() == PyTokenTypes.DOT;
                if (possibleDot != null) {
                    possibleDot.delete();
                }
            }
            else {
                replaceByImportStatements(oldImport);
            }
        }

        @RequiredWriteAction
        private static void replaceByImportStatements(PyFromImportStatement oldImport) {
            Project project = oldImport.getProject();
            PyElementGenerator generator = PyElementGenerator.getInstance(project);
            List<String> names = new ArrayList<>();
            for (PyImportElement importElement : oldImport.getImportElements()) {
                names.add(importElement.getText());
            }
            if (names.isEmpty()) {
                return;
            }
            LanguageLevel langLevel = LanguageLevel.forElement(oldImport);
            for (int i = names.size() - 1; i >= 0; i--) {
                PyImportStatement newImport = generator.createImportStatement(langLevel, names.get(i), null);
                oldImport.getParent().addAfter(newImport, oldImport);
            }
            oldImport.delete();
        }
    }
}
