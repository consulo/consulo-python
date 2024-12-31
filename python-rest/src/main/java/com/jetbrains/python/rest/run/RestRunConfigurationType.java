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

package com.jetbrains.python.rest.run;

import com.jetbrains.python.rest.run.docutils.DocutilsRunConfiguration;
import com.jetbrains.python.rest.run.sphinx.SphinxRunConfiguration;
import com.jetbrains.rest.RestFileType;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.ConfigurationTypeUtil;
import consulo.execution.configuration.RunConfiguration;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.reStructuredText.localize.RestLocalize;
import consulo.ui.image.Image;

import jakarta.annotation.Nonnull;

/**
 * User : catherine
 */
@ExtensionImpl
public class RestRunConfigurationType implements ConfigurationType {
  public final ConfigurationFactory DOCUTILS_FACTORY = new DocutilsRunConfigurationFactory(this);
  public final ConfigurationFactory SPHINX_FACTORY = new SphinxRunConfigurationFactory(this);

  @Override
  public LocalizeValue getDisplayName() {
    return RestLocalize.runcfgDocutilsDisplay_name();
  }

  public static RestRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(RestRunConfigurationType.class);
  }

  @Override
  public LocalizeValue getConfigurationTypeDescription() {
    return RestLocalize.runcfgDocutilsDescription();
  }

  @Override
  public Image getIcon() {
    return RestFileType.INSTANCE.getIcon();
  }

  @Override
  @Nonnull
  public String getId() {
    return "RestRunConfigurationType";
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[] {DOCUTILS_FACTORY, SPHINX_FACTORY};
  }

  private static abstract class RestConfigurationFactory extends ConfigurationFactory {
    private final String myId;
    @Nonnull
    private final LocalizeValue myDisplayName;

    public RestConfigurationFactory(@Nonnull final ConfigurationType type, @Nonnull String id, @Nonnull LocalizeValue displayName) {
      super(type);
      myId = id;
      myDisplayName = displayName;
    }

    @Override
    public String getId() {
      return myId;
    }

    @Override
    @Nonnull
    public LocalizeValue getDisplayName() {
      return myDisplayName;
    }
  }

  private static class DocutilsRunConfigurationFactory extends RestConfigurationFactory {
    protected DocutilsRunConfigurationFactory(ConfigurationType type) {
      super(type, "Docutils", LocalizeValue.localizeTODO("Docutils task"));
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new DocutilsRunConfiguration(project, this);
    }
  }

  private static class SphinxRunConfigurationFactory extends RestConfigurationFactory {
    protected SphinxRunConfigurationFactory(ConfigurationType type) {
      super(type, "Sphinx", LocalizeValue.localizeTODO("Sphinx task"));
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new SphinxRunConfiguration(project, this);
    }
  }
}
