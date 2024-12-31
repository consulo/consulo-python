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

package com.jetbrains.pyqt;

import com.jetbrains.python.psi.resolve.QualifiedNameResolver;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameResolverImpl;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.application.util.SystemInfo;
import consulo.component.ComponentManager;
import consulo.content.bundle.Sdk;
import consulo.language.psi.PsiDirectory;
import consulo.language.util.ModuleUtilCore;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.virtualFileSystem.fileType.INativeFileType;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.util.List;

/**
 * @author yole
 */
public abstract class QtFileType implements FileType, INativeFileType {
  private final String myName;
  private final LocalizeValue myDescription;
  private final String myDefaultExtension;
  private final Image myIcon;

  protected QtFileType(String name, LocalizeValue description, String defaultExtension, Image icon) {
    myName = name;
    myDescription = description;
    myDefaultExtension = defaultExtension;
    myIcon = icon;
  }

  @Nonnull
  @Override
  public String getId() {
    return myName;
  }

  @Nonnull
  @Override
  public LocalizeValue getDescription() {
    return myDescription;
  }

  @Nonnull
  @Override
  public String getDefaultExtension() {
    return myDefaultExtension;
  }

  @Override
  public Image getIcon() {
    return myIcon;
  }

  @Override
  public boolean isBinary() {
    return true;
  }

  @Override
  public boolean isReadOnly() {
    return false;
  }

  @Override
  public String getCharset(@Nonnull VirtualFile file, byte[] content) {
    return null;
  }

  @Override
  public boolean openFileInAssociatedApplication(ComponentManager project, @Nonnull VirtualFile file) {
    String qtTool = findQtTool(ModuleUtilCore.findModuleForFile(file, (Project)project), getToolName());
    if (qtTool == null) {
      return false;
    }
    try {
      Runtime.getRuntime().exec(new String[]{qtTool, file.getPath()});
    }
    catch (IOException e) {
      Messages.showErrorDialog((Project)project, "Failed to run Qt Designer: " + e.getMessage(), "Error");
    }
    return true;
  }

  public static String findQtTool(Module module, String toolName) {
    if (SystemInfo.isWindows) {
      if (module == null) {
        return null;
      }
      Sdk sdk = PythonSdkType.findPythonSdk(module);
      if (sdk == null) {
        return null;
      }
      String tool = findToolInPackage(toolName, module, sdk, "PyQt4");
      if (tool != null) {
        return tool;
      }
      return findToolInPackage(toolName, module, sdk, "PySide");
    }
    // TODO
    return null;
  }

  @Nullable
  private static String findToolInPackage(String toolName, Module module, Sdk sdk, String name) {
    QualifiedNameResolver visitor = new QualifiedNameResolverImpl(name).fromModule(module).withSdk(sdk);
    List<PsiDirectory> elements = visitor.resultsOfType(PsiDirectory.class);
    for (PsiDirectory directory : elements) {
      VirtualFile tool = directory.getVirtualFile().findChild(toolName + ".exe");
      if (tool != null) {
        return tool.getPath();
      }
    }
    return null;
  }

  protected abstract String getToolName();
}
