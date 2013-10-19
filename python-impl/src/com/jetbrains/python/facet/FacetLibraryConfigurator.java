package com.jetbrains.python.facet;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jetbrains.annotations.Nullable;
import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableModelsProvider;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.OrderEntryUtil;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.util.ArchiveVfsUtil;

/**
 * @author yole
 */
public class FacetLibraryConfigurator {
  private FacetLibraryConfigurator() {
  }

  public static void attachLibrary(final Module module, @Nullable final ModifiableRootModel existingModel, final String libraryName, final List<String> paths) {
    final ModifiableModelsProvider modelsProvider = ModifiableModelsProvider.SERVICE.getInstance();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        // add all paths to library
        final ModifiableRootModel model = existingModel != null ? existingModel : modelsProvider.getModuleModifiableModel(module);
        final LibraryOrderEntry orderEntry = OrderEntryUtil.findLibraryOrderEntry(model, libraryName);
        if (orderEntry != null) {
          // update existing
          Library lib = orderEntry.getLibrary();
          if (lib != null) {
            fillLibrary(module.getProject(), lib, paths);
            if (existingModel == null) {
              modelsProvider.commitModuleModifiableModel(model);
            }
            return;
          }
        }
        // create new
        final LibraryTable.ModifiableModel projectLibrariesModel = modelsProvider.getLibraryTableModifiableModel(model.getProject());
        Library lib = projectLibrariesModel.createLibrary(libraryName);
        fillLibrary(module.getProject(), lib, paths);
        projectLibrariesModel.commit();
        model.addLibraryEntry(lib);
        if (existingModel == null) {
          modelsProvider.commitModuleModifiableModel(model);
        }
      }
    });
  }

  private static void fillLibrary(Project project, Library lib, List<String> paths) {
    Library.ModifiableModel modifiableModel = lib.getModifiableModel();
    for (String root : lib.getUrls(OrderRootType.CLASSES)) {
      modifiableModel.removeRoot(root, OrderRootType.CLASSES);
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
          modifiableModel.addRoot(pathEntry, OrderRootType.CLASSES);
        }
        else {
          modifiableModel.addRoot("file://"+dir, OrderRootType.CLASSES);
        }
      }
    }
    modifiableModel.commit();
  }

  public static void detachLibrary(final Module module, final String libraryName) {
    final ModifiableModelsProvider modelsProvider = ModifiableModelsProvider.SERVICE.getInstance();
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        // remove the library
        final ModifiableRootModel model = modelsProvider.getModuleModifiableModel(module);
        OrderEntry entry = OrderEntryUtil.findLibraryOrderEntry(model, libraryName);
        if (entry == null) {
          modelsProvider.disposeModuleModifiableModel(model);
        }
        else {
          model.removeOrderEntry(entry);
          modelsProvider.commitModuleModifiableModel(model);
        }
      }
    });
  }
}
