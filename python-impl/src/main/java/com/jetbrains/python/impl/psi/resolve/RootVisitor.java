// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import consulo.content.bundle.Sdk;
import consulo.module.Module;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Simple visitor to use with ResolveImportUtil.
 * User: dcheryasov
 */
public interface RootVisitor {
  /**
   * @param root   what we're visiting.
   * @param module the module to which the root belongs, or null
   * @param sdk the SDK to which the root belongs, or null
   * @param isModuleSource true iff the root belongs to the module in case both module and sdk are present
   *
   * @return false when visiting must stop.
   */
  boolean visitRoot(VirtualFile root, @Nullable Module module, @Nullable Sdk sdk, boolean isModuleSource);
}
