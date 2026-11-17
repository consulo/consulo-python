// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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
package com.jetbrains.python.impl.actions;

import com.jetbrains.python.impl.namespacePackages.PyNamespacePackagesService;
import com.jetbrains.python.impl.refactoring.move.PyMoveRefactoringUtil;
import consulo.application.util.registry.Registry;
import consulo.ide.action.CreateFileFromTemplateDialog;
import consulo.ide.impl.actions.CreateDirectoryOrPackageType;
import consulo.ide.impl.idea.ide.actions.CreateDirectoryOrPackageHandler;
import consulo.ide.util.DirectoryChooserUtil;
import consulo.language.editor.util.IdeView;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.python.impl.localize.PySourceRootsLocalize;
import consulo.python.module.extension.PyModuleExtension;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.ui.ex.awt.Messages;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;

public final class CreatePackageAction extends DumbAwareAction implements AnActionWithSyncUpdate {
    private static final String NAMESPACE_PACKAGE_TYPE = "Namespace Package";
    private static final String ORDINARY_PACKAGE_TYPE = "Package";

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        IdeView view = e.getData(IdeView.KEY);
        if (view == null) {
            return;
        }

        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }

        PsiDirectory directory = DirectoryChooserUtil.getOrChooseDirectory(view);
        if (directory == null) {
            return;
        }

        Module module = e.getData(Module.KEY);
        if (module == null) {
            return;
        }

        CreateFileFromTemplateDialog.Builder builder = createDialogBuilder(project);

        SmartPointerManager pointerManager = SmartPointerManager.getInstance(project);
        SmartPsiElementPointer<PsiDirectory> directoryPointer = pointerManager.createSmartPsiElementPointer(directory);
        CreateDirectoryOrPackageHandler newOrdinaryPackageHandler = getNewOrdinaryPackageHandler(project, directory, directoryPointer);
        CreateDirectoryOrPackageHandler newNamespacePackageHandler =
            getNewNamespacePackageHandler(project, directory, directoryPointer, module);

        builder.show(
            PySourceRootsLocalize.dialogTitleCanTCreatePackage(),
            ORDINARY_PACKAGE_TYPE,
            new CreateFileFromTemplateDialog.FileCreator<PsiDirectory>() {
                @Override
                @RequiredUIAccess
                public PsiDirectory createFile(String name, String templateName) {
                    if (templateName.equals(ORDINARY_PACKAGE_TYPE)) {
                        createNewPackage(name, newOrdinaryPackageHandler, item -> {
                            if (item != null) {
                                view.selectElement(item);
                            }
                        });
                    }
                    else if (templateName.equals(NAMESPACE_PACKAGE_TYPE)) {
                        createNewPackage(name, newNamespacePackageHandler, item -> {
                            if (item != null) {
                                view.selectElement(item);
                            }
                        });
                        // TODO consulo: FUS statistics missing - PyNamespacePackagesStatisticsCollector.logNamespacePackageCreatedByUser()
                    }
                    return directory;
                }

                // TODO consulo: CreateFileFromTemplateDialog.FileCreator#startInWriteAction missing - JB returns false here

                @Override
                public LocalizeValue getActionName(String name, String templateName) {
                    return PySourceRootsLocalize.commandNameCreateNewPackage(name);
                }
            },
            it -> {
            }
        );
    }

    private static CreateDirectoryOrPackageHandler getNewOrdinaryPackageHandler(
        Project project,
        PsiDirectory directory,
        SmartPsiElementPointer<PsiDirectory> directoryPointer
    ) {
        return new CreateDirectoryOrPackageHandler(project, directory, CreateDirectoryOrPackageType.Package, ".") {
            @Override
            @RequiredUIAccess
            protected void createDirectories(String subDirName) {
                super.createDirectories(subDirName);
                PsiFileSystemItem element = getCreatedElement();
                PsiDirectory restoredDirectory = directoryPointer.getElement();
                if (element instanceof PsiDirectory elementDirectory && restoredDirectory != null) {
                    PyMoveRefactoringUtil.createInitPyInHierarchy(elementDirectory, restoredDirectory);
                }
            }
        };
    }

    private static CreateDirectoryOrPackageHandler getNewNamespacePackageHandler(
        Project project,
        PsiDirectory directory,
        SmartPsiElementPointer<PsiDirectory> directoryPointer,
        Module module
    ) {
        return new CreateDirectoryOrPackageHandler(project, directory, CreateDirectoryOrPackageType.Package, ".") {
            @Override
            @RequiredUIAccess
            protected void createDirectories(String subDirName) {
                super.createDirectories(subDirName);

                PsiDirectory restoredDirectory = directoryPointer.getElement();
                if (restoredDirectory == null) {
                    return;
                }
                PsiFileSystemItem element = getCreatedElement();
                PsiFileSystemItem lastElement = element;
                while (element != null && !element.equals(restoredDirectory)) {
                    lastElement = element;
                    element = element.getParent();
                }
                if (element == null) {
                    return;
                }

                VirtualFile topmostCreatedDirectory = lastElement.getVirtualFile();
                if (topmostCreatedDirectory == null) {
                    return;
                }
                PyNamespacePackagesService.getInstance(module).toggleMarkingAsNamespacePackage(topmostCreatedDirectory);
            }
        };
    }

    @RequiredUIAccess
    private static void createNewPackage(
        String name,
        CreateDirectoryOrPackageHandler createHandler,
        Consumer<? super PsiFileSystemItem> consumer
    ) {
        if (createHandler.checkInput(name) && createHandler.canClose(name)) {
            consumer.accept(createHandler.getCreatedElement());
        }
        else {
            String errorMessage = createHandler.getErrorText(name);
            Messages.showErrorDialog(errorMessage, PySourceRootsLocalize.dialogTitleCanTCreatePackage().get());
        }
    }

    private static CreateFileFromTemplateDialog.Builder createDialogBuilder(Project project) {
        CreateFileFromTemplateDialog.Builder builder = CreateFileFromTemplateDialog.createDialog(project);
        builder
            .setTitle(PySourceRootsLocalize.dialogTitleNewPythonPackage())
            .addKind(PySourceRootsLocalize.newPackageListItemOrdinaryPackage(), PlatformIconGroup.nodesPackage(), ORDINARY_PACKAGE_TYPE);

        // JB registry key "python.create.namespace.package.action" (defaultValue="false", intellij.python.sourceRoots.xml)
        if (PyNamespacePackagesService.isEnabled() && Registry.is("python.create.namespace.package.action", false)) {
            builder.addKind(
                PySourceRootsLocalize.newPackageListItemNamespacePackage(),
                PlatformIconGroup.nodesPackage(),
                NAMESPACE_PACKAGE_TYPE
            );
        }

        return builder;
    }

    /**
     * Consulo: kept for existing callers, JB moved this helper to {@link PyMoveRefactoringUtil#createInitPyInHierarchy}.
     */
    @Deprecated
    @RequiredUIAccess
    public static void createInitPyInHierarchy(PsiDirectory created, PsiDirectory ancestor) {
        PyMoveRefactoringUtil.createInitPyInHierarchy(created, ancestor);
    }

    @Override
    public void update(AnActionEvent e) {
        boolean enabled = isEnabled(e);
        e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    protected @Nullable Image getTemplateIcon() {
        return PlatformIconGroup.nodesPackage();
    }

    private static boolean isEnabled(AnActionEvent e) {
        Project project = e.getData(Project.KEY);
        IdeView ideView = e.getData(IdeView.KEY);
        if (project == null || ideView == null) {
            return false;
        }
        PsiDirectory[] directories = ideView.getDirectories();
        if (directories.length == 0) {
            return false;
        }
        // Consulo: the action is available only for modules with the Python module extension
        Module module = e.getData(Module.KEY);
        return module != null && module.getExtension(PyModuleExtension.class) != null;
    }
}
