/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.icon.IconDescriptor;
import consulo.language.icon.IconDescriptorUpdater;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.module.content.ProjectRootManager;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.python.impl.icon.PythonImplIconGroup;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;

import java.util.Objects;

/**
 * @author yole
 */
@ExtensionImpl
public class PyIconDescriptorUpdater implements IconDescriptorUpdater {
    @Override
    @RequiredReadAction
    public void updateIcon(@Nonnull IconDescriptor iconDescriptor, @Nonnull PsiElement element, int i) {
        if (element instanceof PsiDirectory directory) {
            if (directory.findFile(PyNames.INIT_DOT_PY) != null) {
                VirtualFile vFile = directory.getVirtualFile();
                VirtualFile root = ProjectRootManager.getInstance(directory.getProject()).getFileIndex().getSourceRootForFile(vFile);
                if (!Objects.equals(root, vFile)) {
                    iconDescriptor.setMainIcon(PlatformIconGroup.nodesPackage());
                }
            }
        }
        else if (element instanceof PyClass) {
            iconDescriptor.setMainIcon(PlatformIconGroup.nodesClass());
        }
        else if (element instanceof PyFunction function) {
            Image icon = null;
            Property property = function.getProperty();
            if (property != null) {
                if (property.getGetter().valueOrNull() == this) {
                    icon = PythonImplIconGroup.pythonPropertygetter();
                }
                else if (property.getSetter().valueOrNull() == this) {
                    icon = PythonImplIconGroup.pythonPropertysetter();
                }
                else if (property.getDeleter().valueOrNull() == this) {
                    icon = PythonImplIconGroup.pythonPropertydeleter();
                }
                else {
                    icon = PlatformIconGroup.nodesProperty();
                }
            }
            if (icon != null) {
                iconDescriptor.setMainIcon(icon);
            }
            else {
                iconDescriptor.setMainIcon(PlatformIconGroup.nodesMethod());
            }
        }
    }
}
