// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import com.jetbrains.python.impl.PythonRuntimeService;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.base.SourcesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.language.content.LanguageContentFolderScopes;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.ContentEntry;
import consulo.module.content.layer.ModuleRootModel;
import consulo.module.content.layer.OrderEnumerator;
import consulo.module.content.layer.orderEntry.ModuleExtensionWithSdkOrderEntry;
import consulo.module.content.layer.orderEntry.ModuleOrderEntry;
import consulo.module.content.layer.orderEntry.ModuleSourceOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;


public final class RootVisitorHost {
  public static void visitRoots(final PsiElement elt, final RootVisitor visitor) {
    // real search
    final Module module = ModuleUtilCore.findModuleForPsiElement(elt);
    if (module != null) {
      visitRoots(module, false, visitor);
    }
    else {
      if (PythonRuntimeService.getInstance().isInScratchFile(elt)) {
        visitRootsInAllModules(elt.getProject(), visitor);
      }
      final PsiFile containingFile = elt.getContainingFile();
      if (containingFile != null) {
        visitSdkRoots(containingFile, visitor);
      }
    }
  }

  public static void visitRootsInAllModules(Project project, RootVisitor visitor) {
    for (Module mod : ModuleManager.getInstance(project).getModules()) {
      visitRoots(mod, true, false, visitor);
    }
  }

  public static void visitRoots(Module module, final boolean skipSdk, final RootVisitor visitor) {
    visitRoots(module, skipSdk, true, visitor);
  }

  private static void visitRoots(Module module, final boolean skipSdk, boolean processModuleDeps,
                                 final RootVisitor visitor) {
    OrderEnumerator enumerator = OrderEnumerator.orderEntries(module);
    if (processModuleDeps) {
      enumerator = enumerator.recursively();
    }
    if (skipSdk) {
      // In Consulo the interpreter of a module is the SDK of its module extension, there is no separate interpreter library.
      enumerator = enumerator.withoutSdk();
    }
    enumerator.forEach(orderEntry -> {
      if (orderEntry instanceof ModuleSourceOrderEntry) {
        return visitModuleContentEntries(((ModuleSourceOrderEntry)orderEntry).getRootModel(), visitor);
      }
      return visitOrderEntryRoots(visitor, orderEntry);
    });
  }

  static void visitSdkRoots(PsiFile file, RootVisitor visitor) {
    // formality
    final VirtualFile elt_vfile = file.getOriginalFile().getVirtualFile();
    List<OrderEntry> orderEntries = null;
    if (elt_vfile != null) { // reality TODO: check this reality
      final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(file.getProject()).getFileIndex();
      orderEntries = fileIndex.getOrderEntriesForFile(elt_vfile);
      if (!orderEntries.isEmpty()) {
        for (OrderEntry entry : orderEntries) {
          if (!visitOrderEntryRoots(visitor, entry)) break;
        }
      }
      else {
        orderEntries = null;
      }
    }

    // out-of-project file or non-file(e.g. console) - use roots of the main PyProject's interpreter
    if (orderEntries == null) {
      final Sdk interpreter = MainPythonSdkKt.mainPythonSdk(file.getProject());
      if (interpreter != null) {
        visitInterpreterRoots(file.getProject(), interpreter, visitor);
      }
    }
  }

  /**
   * Visits the class roots of [interpreter] in [project]. Returns whether the visitor stopped.
   */
  public static boolean visitInterpreterRoots(Project project, Sdk interpreter, RootVisitor visitor) {
    for (VirtualFile root : interpreter.getRootProvider().getFiles(BinariesOrderRootType.ID)) {
      if (!visitor.visitRoot(root, null, interpreter, false)) {
        return true;
      }
    }
    return false;
  }

  private static boolean visitModuleContentEntries(ModuleRootModel rootModel, RootVisitor visitor) {
    // look in module sources
    for (ContentEntry entry : rootModel.getContentEntries()) {
      VirtualFile rootFile = entry.getFile();

      if (rootFile != null && !visitor.visitRoot(rootFile, null, null, true)) return false;
      for (VirtualFile folder : entry.getFolderFiles(LanguageContentFolderScopes.productionAndTest())) {
        if (!visitor.visitRoot(folder, rootModel.getModule(), null, true)) return false;
      }
    }
    return true;
  }

  private static boolean visitOrderEntryRoots(RootVisitor visitor, OrderEntry entry) {
    Set<VirtualFile> allRoots = new LinkedHashSet<>();
    Collections.addAll(allRoots, entry.getFiles(SourcesOrderRootType.ID));
    Collections.addAll(allRoots, entry.getFiles(BinariesOrderRootType.ID));
    Module module = entry instanceof ModuleOrderEntry ? ((ModuleOrderEntry)entry).getModule() : null;
    Sdk sdk = entry instanceof ModuleExtensionWithSdkOrderEntry ? ((ModuleExtensionWithSdkOrderEntry)entry).getSdk() : null;
    for (VirtualFile root : allRoots) {
      if (!visitor.visitRoot(root, module, sdk, false)) {
        return false;
      }
    }
    return true;
  }
}
