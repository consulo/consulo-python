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
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;

/**
 * User : catherine
 */
@ExtensionImpl
public class RestLanguageSubstitutor extends LanguageSubstitutor {
  @Override
  public Language getLanguage(@Nonnull VirtualFile vFile, @Nonnull Project project) {
    Module module = ModuleUtilCore.findModuleForFile(vFile, project);
    if (module == null) {
      return null;
    }
    boolean txtIsRst = ReSTService.getInstance(module).txtIsRst();
    if (txtIsRst) {
      return RestLanguage.INSTANCE;
    }
    return null;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PlainTextLanguage.INSTANCE;
  }
}
