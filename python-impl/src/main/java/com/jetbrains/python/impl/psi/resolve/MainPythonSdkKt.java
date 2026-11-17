// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.content.bundle.Sdk;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

/**
 * In Consulo the interpreter of a module is the SDK of its Python module extension, so the JetBrains
 * {@code PythonInterpreter} of a Python project is represented by that {@link Sdk}.
 */
public final class MainPythonSdkKt {
  private MainPythonSdkKt() {
  }

  /**
   * The interpreter of the module at the project root, for a file that belongs to no module, such as a scratch file.
   * <p>
   * Replaces {@code ProjectRootManager.getProjectSdk()}, which reads {@code project-jdk-name} from {@code .idea/misc.xml}. That attribute
   * is missing or stale in the projects PY-89831 reports.
   * <p>
   * Internal to resolve, which cannot suspend. It answers {@code null} until the first structure lands.
   * {@link PyResolveCachesInvalidator} drops what was resolved in that window.
   */
  public static @Nullable Sdk mainPythonSdk(Project project) {
    // TODO consulo: com.intellij.python.pyproject.model.evolution.findMainEvoPyProjectIfReady missing
    return null;
  }

  /**
   * The SDK of the interpreter of the Python project that resides on this module, read from the snapshot. {@code null} before
   * the first snapshot and for a module that is not Python.
   * <p>
   * Internal to resolve and code insight, which cannot suspend. {@link PyResolveCachesInvalidator} drops what was resolved
   * before a snapshot changed the interpreter of the module.
   */
  public static @Nullable Sdk pythonSdkIfReady(Module module) {
    return PythonSdkType.findPythonSdk(module);
  }

  /**
   * {@link #pythonSdkIfReady(Module)} for the module of this element.
   */
  public static @Nullable Sdk pythonSdkIfReady(PsiElement element) {
    Module module = ModuleUtilCore.findModuleForPsiElement(element);
    return module != null ? pythonSdkIfReady(module) : null;
  }

  /**
   * Whether the file of [element] is in the skeletons of the interpreter of its module, see {@link #pythonSdkIfReady(Module)}.
   */
  public static boolean isElementInSkeletons(PsiElement element) {
    PsiFile containingFile = element.getContainingFile();
    VirtualFile virtualFile = containingFile != null ? containingFile.getVirtualFile() : null;
    if (virtualFile == null) {
      return false;
    }
    Sdk sdk = pythonSdkIfReady(element);
    if (sdk == null) {
      return false;
    }
    // PythonSdkUtil.isFileInSkeletons(virtualFile, sdk)
    VirtualFile skeletonsDir = PySdkUtil.findSkeletonsDir(sdk);
    return skeletonsDir != null && VirtualFileUtil.isAncestor(skeletonsDir, virtualFile, false);
  }
}
