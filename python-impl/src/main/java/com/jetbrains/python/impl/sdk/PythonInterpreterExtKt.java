// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.sdk;

import consulo.application.Application;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Port of the SDK part of JetBrains {@code com.intellij.python.sdk.backend.PythonInterpreterExt}.
 */
public final class PythonInterpreterExtKt {
  private PythonInterpreterExtKt() {
  }

  public static @Nullable VirtualFile stdlibLibDirectory(Sdk sdk) {
    return findStdlibIn(sdk.getRootProvider().getFiles(BinariesOrderRootType.ID));
  }

  private static @Nullable VirtualFile findStdlibIn(VirtualFile[] classRoots) {
    for (VirtualFile file : classRoots) {
      if (!file.isValid()) continue;
      if ((file.findChild("__future__.py") != null || file.findChild("__future__.pyc") != null) &&
          file.findChild("xml") != null && file.findChild("email") != null) {
        return file;
      }
      // Mock SDK does not have the aforementioned modules.
      if (Application.get().isUnitTestMode() && file.getName().equals("Lib")) {
        return file;
      }
    }
    return null;
  }
}
