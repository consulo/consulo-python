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

package com.jetbrains.python.impl.buildout.config;

import consulo.language.file.LanguageFileType;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import com.jetbrains.python.impl.PythonIcons;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author traff
 */
public class BuildoutCfgFileType extends LanguageFileType {
  public static final BuildoutCfgFileType INSTANCE = new BuildoutCfgFileType();
  public static final String DEFAULT_EXTENSION = "cfg";

  private BuildoutCfgFileType() {
    super(BuildoutCfgLanguage.INSTANCE);
  }

  @Nonnull
  public String getId() {
    return "BuildoutCfg";
  }

  @Nonnull
  public LocalizeValue getDescription() {
    return LocalizeValue.localizeTODO("Buildout config files");
  }

  @Nonnull
  public String getDefaultExtension() {
    return DEFAULT_EXTENSION;
  }

  @Nullable
  public Image getIcon() {
    return PythonIcons.Python.Buildout.Buildout;
  }
}

