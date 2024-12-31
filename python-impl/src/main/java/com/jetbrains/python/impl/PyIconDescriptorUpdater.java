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
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.language.icon.IconDescriptor;
import consulo.language.icon.IconDescriptorUpdater;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.module.content.ProjectRootManager;
import consulo.ui.image.Image;
import consulo.util.lang.Comparing;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class PyIconDescriptorUpdater implements IconDescriptorUpdater {
  @Override
  public void updateIcon(@Nonnull IconDescriptor iconDescriptor, @Nonnull PsiElement element, int i) {
    if (element instanceof PsiDirectory) {
      final PsiDirectory directory = (PsiDirectory)element;
      if (directory.findFile(PyNames.INIT_DOT_PY) != null) {
        final VirtualFile vFile = directory.getVirtualFile();
        final VirtualFile root = ProjectRootManager.getInstance(directory.getProject()).getFileIndex().getSourceRootForFile(vFile);
        if (!Comparing.equal(root, vFile)) {
          iconDescriptor.setMainIcon(AllIcons.Nodes.Package);
        }
      }
    }
    else if (element instanceof PyClass) {
      iconDescriptor.setMainIcon(AllIcons.Nodes.Class);
    }
    else if (element instanceof PyFunction) {
      Image icon = null;
      final Property property = ((PyFunction)element).getProperty();
      if (property != null) {
        if (property.getGetter().valueOrNull() == this) {
          icon = PythonIcons.Python.PropertyGetter;
        }
        else if (property.getSetter().valueOrNull() == this) {
          icon = PythonIcons.Python.PropertySetter;
        }
        else if (property.getDeleter().valueOrNull() == this) {
          icon = PythonIcons.Python.PropertyDeleter;
        }
        else {
          icon = AllIcons.Nodes.Property;
        }
      }
      if (icon != null) {
        iconDescriptor.setMainIcon(icon);
      }
      else {
        iconDescriptor.setMainIcon(AllIcons.Nodes.Method);
      }
    }
  }
}
