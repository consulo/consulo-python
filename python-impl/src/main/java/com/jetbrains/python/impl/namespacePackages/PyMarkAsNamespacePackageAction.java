// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.namespacePackages;

import com.jetbrains.python.PyLanguageFacade;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.annotation.component.ActionImpl;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.python.impl.localize.PySourceRootsLocalize;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.ui.ex.action.AnActionWithSyncUpdate;
import consulo.ui.ex.action.Presentation;
import consulo.virtualFileSystem.VirtualFile;

import java.util.Arrays;

// TODO consulo: JB adds this action to "MarkRootGroup" after "MarkSourceRoot"; Consulo's MarkRootGroup builds its children dynamically
@ActionImpl(id = "MarkNamespacePackageDirectory")
public class PyMarkAsNamespacePackageAction extends AnAction implements AnActionWithSyncUpdate {
    public PyMarkAsNamespacePackageAction() {
        super(PySourceRootsLocalize.actionMarknamespacepackagedirectoryText());
    }

    @Override
    public void update(AnActionEvent e) {
        Presentation presentation = e.getPresentation();
        presentation.setEnabled(false);
        presentation.setVisible(false);

        VirtualFile[] virtualFiles = e.getData(VirtualFile.KEY_OF_ARRAY);
        if (virtualFiles == null) {
            return;
        }
        if (virtualFiles.length == 0) {
            return;
        }
        Project project = e.getData(Project.KEY);
        if (project == null) {
            return;
        }
        if (Arrays.stream(virtualFiles)
            .anyMatch(it -> PyLanguageFacade.getInstance().getEffectiveLanguageLevel(project, it).isOlderThan(LanguageLevel.PYTHON34))) {
            return;
        }

        Module module = e.getData(Module.KEY);
        if (module == null) {
            return;
        }
        PyNamespacePackagesService service = PyNamespacePackagesService.getInstance(module);
        if (!PyNamespacePackagesService.isEnabled()) {
            return;
        }
        presentation.setVisible(true);

        presentation.setIcon(PlatformIconGroup.nodesPackage());
        if (Arrays.stream(virtualFiles).allMatch(service::canBeMarked)) {
            presentation.setEnabled(true);
            presentation.setText(getMarkText());
        }
        else if (Arrays.stream(virtualFiles).allMatch(service::isMarked)) {
            presentation.setEnabled(true);
            presentation.setText(getUnmarkText());
        }
        else {
            presentation.setEnabled(false);
            presentation.setText(getMarkText());
        }
    }

    /**
     * JB: {@code if (PlatformUtils.isPyCharm()) "python.namespace.package.folder" else "python.python.namespace.package.folder"}.
     * Consulo is never PyCharm, so the non-PyCharm text is used.
     */
    private static LocalizeValue getMarkText() {
        return PySourceRootsLocalize.pythonPythonNamespacePackageFolder();
    }

    /**
     * JB: {@code if (PlatformUtils.isPyCharm()) "python.unmark.as.namespace.package" else "python.unmark.as.python.namespace.package"}.
     * Consulo is never PyCharm, so the non-PyCharm text is used.
     */
    private static LocalizeValue getUnmarkText() {
        return PySourceRootsLocalize.pythonUnmarkAsPythonNamespacePackage();
    }

    @Override
    @RequiredUIAccess
    public void actionPerformed(AnActionEvent e) {
        Module module = e.getData(Module.KEY);
        if (module == null) {
            return;
        }
        VirtualFile[] virtualFiles = e.getData(VirtualFile.KEY_OF_ARRAY);
        if (virtualFiles == null) {
            return;
        }
        PyNamespacePackagesService service = PyNamespacePackagesService.getInstance(module);
        for (VirtualFile it : virtualFiles) {
            service.toggleMarkingAsNamespacePackage(it);
        }
    }
}
