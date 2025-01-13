/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.fileEditor.util.FileContentUtil;
import consulo.index.io.data.DataInputOutputUtil;
import consulo.language.file.FileTypeManager;
import consulo.language.impl.file.SingleRootFileViewProvider;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.FilePropertyPusher;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.PushedFilePropertiesUpdater;
import consulo.module.content.layer.orderEntry.ModuleExtensionWithSdkOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.project.ProjectManager;
import consulo.python.module.extension.PyModuleExtension;
import consulo.util.collection.Maps;
import consulo.util.dataholder.Key;
import consulo.virtualFileSystem.FileAttribute;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.util.VirtualFileVisitor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * @author yole
 */
@ExtensionImpl
public class PythonLanguageLevelPusher implements FilePropertyPusher<LanguageLevel> {
  public static final Key<LanguageLevel> PYTHON_LANGUAGE_LEVEL = Key.create("PYTHON_LANGUAGE_LEVEL");

  private final Map<Module, Sdk> myModuleSdks = Maps.newWeakHashMap();

  public static void pushLanguageLevel(final Project project) {
    PushedFilePropertiesUpdater.getInstance(project).pushAll(new PythonLanguageLevelPusher());
  }

  @Override
  public void initExtra(@Nonnull Project project) {
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    Set<Sdk> usedSdks = new HashSet<>();
    for (Module module : modules) {
      if (isPythonModule(module)) {
        final Sdk sdk = PythonSdkType.findPythonSdk(module);
        myModuleSdks.put(module, sdk);
        if (sdk != null && !usedSdks.contains(sdk)) {
          usedSdks.add(sdk);
          updateSdkLanguageLevel(project, sdk);
        }
      }
    }
    project.putUserData(PYTHON_LANGUAGE_LEVEL, PyUtil.guessLanguageLevel(project));
  }

  @Override
  @Nonnull
  public Key<LanguageLevel> getFileDataKey() {
    return LanguageLevel.KEY;
  }

  @Override
  public boolean pushDirectoriesOnly() {
    return true;
  }

  @Override
  @Nonnull
  public LanguageLevel getDefaultValue() {
    return LanguageLevel.getDefault();
  }

  @Override
  @Nullable
  public LanguageLevel getImmediateValue(@Nonnull Project project, @Nullable VirtualFile file) {
    return getFileLanguageLevel(project, file);
  }

  @Nullable
  public static LanguageLevel getFileLanguageLevel(@Nonnull Project project, @Nullable VirtualFile file) {
    if (ApplicationManager.getApplication().isUnitTestMode() && LanguageLevel.FORCE_LANGUAGE_LEVEL != null) {
      return LanguageLevel.FORCE_LANGUAGE_LEVEL;
    }
    if (file == null) {
      return null;
    }
    final Sdk sdk = getFileSdk(project, file);
    if (sdk != null) {
      return PythonSdkType.getLanguageLevelForSdk(sdk);
    }
    return PyUtil.guessLanguageLevelWithCaching(project);
  }

  @Nullable
  private static Sdk getFileSdk(@Nonnull Project project, @Nonnull VirtualFile file) {
    final Module module = ModuleUtilCore.findModuleForFile(file, project);
    if (module != null) {
      final Sdk sdk = PythonSdkType.findPythonSdk(module);
      if (sdk != null) {
        return sdk;
      }
      return null;
    }
    else {
      return findSdkForFileOutsideTheProject(project, file);
    }
  }

  @Nullable
  private static Sdk findSdkForFileOutsideTheProject(Project project, VirtualFile file) {
    if (file != null) {
      final List<OrderEntry> orderEntries = ProjectRootManager.getInstance(project).getFileIndex().getOrderEntriesForFile(file);
      for (OrderEntry orderEntry : orderEntries) {
        if (orderEntry instanceof ModuleExtensionWithSdkOrderEntry) {
          return ((ModuleExtensionWithSdkOrderEntry)orderEntry).getSdk();
        }
      }
    }
    return null;
  }

  @Override
  public LanguageLevel getImmediateValue(@Nonnull Module module) {
    if (ApplicationManager.getApplication().isUnitTestMode() && LanguageLevel.FORCE_LANGUAGE_LEVEL != null) {
      return LanguageLevel.FORCE_LANGUAGE_LEVEL;
    }

    final Sdk sdk = PythonSdkType.findPythonSdk(module);
    return PythonSdkType.getLanguageLevelForSdk(sdk);
  }

  @Override
  public boolean acceptsFile(@Nonnull VirtualFile virtualFile, @Nonnull Project project) {
    return false;
  }

  @Override
  public boolean acceptsDirectory(@Nonnull VirtualFile file, @Nonnull Project project) {
    return true;
  }

  private static final FileAttribute PERSISTENCE = new FileAttribute("python_language_level_persistence", 2, true);

  @Override
  public void persistAttribute(@Nonnull Project project, @Nonnull VirtualFile fileOrDir, @Nonnull LanguageLevel level) throws IOException {
    final DataInputStream iStream = PERSISTENCE.readAttribute(fileOrDir);
    if (iStream != null) {
      try {
        final int oldLevelOrdinal = DataInputOutputUtil.readINT(iStream);
        if (oldLevelOrdinal == level.ordinal()) {
          return;
        }
      }
      finally {
        iStream.close();
      }
    }

    final DataOutputStream oStream = PERSISTENCE.writeAttribute(fileOrDir);
    DataInputOutputUtil.writeINT(oStream, level.ordinal());
    oStream.close();

    for (VirtualFile child : fileOrDir.getChildren()) {
      if (!child.isDirectory() && child.getFileType() == PythonFileType.INSTANCE) {
        clearSdkPathCache(child);
        PushedFilePropertiesUpdater.getInstance(project).filePropertiesChanged(child);
      }
    }
  }

  private static void clearSdkPathCache(@Nonnull final VirtualFile child) {
    final Project[] projects = ProjectManager.getInstance().getOpenProjects();
    for (Project project : projects) {
      final Sdk sdk = getFileSdk(project, child);
      if (sdk != null) {
        final PythonSdkPathCache pathCache = PythonSdkPathCache.getInstance(project, sdk);
        pathCache.clearCache();
      }
    }
  }

  @Override
  public void afterRootsChanged(@Nonnull final Project project) {
    Set<Sdk> updatedSdks = new HashSet<>();
    final Module[] modules = ModuleManager.getInstance(project).getModules();
    boolean needReparseOpenFiles = false;
    for (Module module : modules) {
      if (isPythonModule(module)) {
        Sdk newSdk = PythonSdkType.findPythonSdk(module);
        if (myModuleSdks.containsKey(module)) {
          Sdk oldSdk = myModuleSdks.get(module);
          if ((newSdk != null || oldSdk != null) && newSdk != oldSdk) {
            needReparseOpenFiles = true;
          }
        }
        myModuleSdks.put(module, newSdk);
        if (newSdk != null && !updatedSdks.contains(newSdk)) {
          updatedSdks.add(newSdk);
          updateSdkLanguageLevel(project, newSdk);
        }
      }
    }
    if (needReparseOpenFiles) {
      ApplicationManager.getApplication().invokeLater(() -> {
        if (project.isDisposed()) {
          return;
        }
        FileContentUtil.reparseFiles(project, Collections.<VirtualFile>emptyList(), true);
      });
    }
  }

  private static boolean isPythonModule(@Nonnull final Module module) {
    return ModuleUtilCore.getExtension(module, PyModuleExtension.class) != null;
  }

  private void updateSdkLanguageLevel(@Nonnull final Project project, final Sdk sdk) {
    final LanguageLevel languageLevel = PythonSdkType.getLanguageLevelForSdk(sdk);
    final VirtualFile[] files = sdk.getRootProvider().getFiles(BinariesOrderRootType.getInstance());
    final Application application = ApplicationManager.getApplication();
    PyUtil.invalidateLanguageLevelCache(project);
    application.executeOnPooledThread(() -> application.runReadAction(() -> {
      if (project.isDisposed()) {
        return;
      }
      for (VirtualFile file : files) {
        if (file.isValid()) {
          VirtualFile parent = file.getParent();
          boolean suppressSizeLimit = false;
          if (parent != null && parent.getName().equals(PythonSdkType.SKELETON_DIR_NAME)) {
            suppressSizeLimit = true;
          }
          markRecursively(project, file, languageLevel, suppressSizeLimit);
        }
      }
    }));
  }

  private void markRecursively(final Project project,
                               @Nonnull final VirtualFile file,
                               final LanguageLevel languageLevel,
                               final boolean suppressSizeLimit) {
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    VirtualFileUtil.visitChildrenRecursively(file, new VirtualFileVisitor() {
      @Override
      public boolean visitFile(@Nonnull VirtualFile file) {
        if (fileTypeManager.isFileIgnored(file)) {
          return false;
        }
        if (file.isDirectory()) {
          PushedFilePropertiesUpdater.getInstance(project).findAndUpdateValue(file, PythonLanguageLevelPusher.this, languageLevel);
        }
        if (suppressSizeLimit) {
          SingleRootFileViewProvider.doNotCheckFileSizeLimit(file);
        }
        return true;
      }
    });
  }

  public static void setForcedLanguageLevel(final Project project, @Nullable LanguageLevel languageLevel) {
    LanguageLevel.FORCE_LANGUAGE_LEVEL = languageLevel;
    pushLanguageLevel(project);
  }

  public void flushLanguageLevelCache() {
    myModuleSdks.clear();
  }
}
