// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest;

import com.jetbrains.python.impl.ReSTService;
import com.jetbrains.rest.RestLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.plain.PlainTextLanguage;
import consulo.language.psi.LanguageSubstitutor;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.project.Project;
import consulo.virtualFileSystem.NonPhysicalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class RestLanguageSubstitutor extends LanguageSubstitutor {
  @Override
  public @Nullable Language getLanguage(VirtualFile vFile, Project project) {
    if (vFile.getFileSystem() instanceof NonPhysicalFileSystem) {
      return null;
    }

    Module module = ModuleUtilCore.findModuleForFile(vFile, project);
    if (module == null || module.isDisposed()) {
      return null;
    }
    return ReSTService.getInstance(module).txtIsRst() ? RestLanguage.INSTANCE : null;
  }

  @Override
  public Language getLanguage() {
    return PlainTextLanguage.INSTANCE;
  }
}
