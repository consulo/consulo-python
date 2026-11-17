// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
package com.jetbrains.python.impl;

import com.jetbrains.python.impl.psi.PyUtil;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.content.ProjectRootsUtil;
import consulo.language.icon.IconDescriptor;
import consulo.language.icon.IconDescriptorUpdater;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.module.content.ProjectFileIndex;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

/**
 * JB: {@code IconProvider} registered with id "pyDirectoryDefault"; Consulo uses {@link IconDescriptorUpdater}.
 */
@ExtensionImpl(id = "pyDirectoryDefault")
public class PyDirectoryIconProvider implements IconDescriptorUpdater {
    @Override
    @RequiredReadAction
    public void updateIcon(IconDescriptor iconDescriptor, PsiElement element, int flags) {
        if (!(element instanceof PsiDirectory directory) || !directory.isValid()) {
            return;
        }
        VirtualFile vFile = directory.getVirtualFile();
        Project project = directory.getProject();
        ProjectFileIndex fileIndex = ProjectFileIndex.getInstance(project);

        if (ProjectRootsUtil.isModuleContentRoot(vFile, project)) {
            // JB: returns PythonPyprojectIcons.Model.PyProjectModule for pyproject.toml based modules, otherwise AllIcons.Nodes.Module
            // TODO consulo: pyproject.toml module model (isPyProjectTomlBased) missing;
            //  Consulo keeps the module content root icon provided by the platform
            return;
        }

        // Preserve original icons for excluded directories and source roots
        if (fileIndex.isExcluded(vFile) || isSpecialDirectory(directory)) {
            return;
        }

        if (PyUtil.isExplicitPackage(directory)) {
            iconDescriptor.setMainIcon(PlatformIconGroup.nodesPackage());
        }
    }

    private static boolean isSpecialDirectory(PsiDirectory directory) {
        // On large projects, using the ProjectFileIndex here is *noticeably* faster than
        // asking for all source- and content-roots and checking .contains(vFile)
        return ProjectRootsUtil.isSourceRoot(directory) || ProjectRootsUtil.isModuleContentRoot(directory);
    }
}
