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

package com.jetbrains.python.facet;

import consulo.application.ApplicationManager;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.library.Library;
import consulo.content.library.LibraryTable;
import consulo.module.Module;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.ModifiableRootModel;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.module.content.util.OrderEntryUtil;
import consulo.project.Project;
import consulo.project.content.library.ProjectLibraryTable;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveFileType;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author yole
 */
public class FacetLibraryConfigurator {
  private FacetLibraryConfigurator() {
  }

  public static void attachLibrary(final Module module,
                                   @Nullable final ModifiableRootModel existingModel,
                                   final String libraryName,
                                   final List<String> paths) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        // add all paths to library
        final ModifiableRootModel model =
          existingModel != null ? existingModel : ModuleRootManager.getInstance(module).getModifiableModel();
        final LibraryOrderEntry orderEntry = OrderEntryUtil.findLibraryOrderEntry(model, libraryName);
        if (orderEntry != null) {
          // update existing
          Library lib = orderEntry.getLibrary();
          if (lib != null) {
            fillLibrary(module.getProject(), lib, paths);
            if (existingModel == null) {
              model.commit();
            }
            return;
          }
        }
        // create new
        final LibraryTable.ModifiableModel projectLibrariesModel = ProjectLibraryTable.getInstance(model.getProject()).getModifiableModel();
        Library lib = projectLibrariesModel.createLibrary(libraryName);
        fillLibrary(module.getProject(), lib, paths);
        projectLibrariesModel.commit();
        model.addLibraryEntry(lib);
        if (existingModel == null) {
          model.commit();
        }
      }
    });
  }

  private static void fillLibrary(Project project, Library lib, List<String> paths) {
    Library.ModifiableModel modifiableModel = lib.getModifiableModel();
    for (String root : lib.getUrls(BinariesOrderRootType.getInstance())) {
      modifiableModel.removeRoot(root, BinariesOrderRootType.getInstance());
    }
    Set<VirtualFile> roots = new HashSet<VirtualFile>();
    ProjectRootManager rootManager = ProjectRootManager.getInstance(project);
    Collections.addAll(roots, rootManager.getContentRoots());
    Collections.addAll(roots, rootManager.getContentSourceRoots());
    if (paths != null) {
      for (String dir : paths) {
        VirtualFile pathEntry = LocalFileSystem.getInstance().findFileByPath(dir);
        if (pathEntry != null && !pathEntry.isDirectory() && pathEntry.getFileType() instanceof ArchiveFileType) {
          pathEntry = ArchiveVfsUtil.getJarRootForLocalFile(pathEntry);
        }
        // buildout includes source root of project in paths; don't add it as library home
        if (pathEntry != null && roots.contains(pathEntry)) {
          continue;
        }
        if (pathEntry != null) {
          modifiableModel.addRoot(pathEntry, BinariesOrderRootType.getInstance());
        }
        else {
          modifiableModel.addRoot("file://" + dir, BinariesOrderRootType.getInstance());
        }
      }
    }
    modifiableModel.commit();
  }

  public static void detachLibrary(final Module module, final String libraryName) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        // remove the library
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        OrderEntry entry = OrderEntryUtil.findLibraryOrderEntry(model, libraryName);
        if (entry == null) {
          model.dispose();
        }
        else {
          model.removeOrderEntry(entry);
          model.commit();
        }
      }
    });
  }
}
