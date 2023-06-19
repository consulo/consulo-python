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

import com.jetbrains.rest.RestBundle;
import com.jetbrains.rest.RestFileType;
import com.jetbrains.python.rest.run.docutils.DocutilsRunConfiguration;
import com.jetbrains.python.rest.run.sphinx.SphinxRunConfiguration;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.ConfigurationTypeUtil;
import consulo.execution.configuration.RunConfiguration;
import consulo.project.Project;
import consulo.ui.image.Image;

import javax.annotation.Nonnull;

/**
 * User : catherine
 */
@ExtensionImpl
public class RestRunConfigurationType implements ConfigurationType {
  public final ConfigurationFactory DOCUTILS_FACTORY = new DocutilsRunConfigurationFactory(this);
  public final ConfigurationFactory SPHINX_FACTORY = new SphinxRunConfigurationFactory(this);

  private String myId = "docs";

  public String getDisplayName() {
    return RestBundle.message("runcfg.docutils.display_name");
  }

  public static RestRunConfigurationType getInstance() {
    return ConfigurationTypeUtil.findConfigurationType(RestRunConfigurationType.class);
  }

  public String getConfigurationTypeDescription() {
    return RestBundle.message("runcfg.docutils.description");
  }

  public Image getIcon() {
    return RestFileType.INSTANCE.getIcon();
  }

  @Nonnull
  public String getId() {
    return myId;
  }

  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[] {DOCUTILS_FACTORY, SPHINX_FACTORY};
  }

  private static abstract class RestConfigurationFactory extends ConfigurationFactory {
    private final String myName;

    public RestConfigurationFactory(@Nonnull final ConfigurationType type, @Nonnull String name) {
      super(type);
      myName = name;
    }

    public String getName() {
      return myName;
    }
  }

  private static class DocutilsRunConfigurationFactory extends RestConfigurationFactory {
    protected DocutilsRunConfigurationFactory(ConfigurationType type) {
      super(type, "Docutils task");
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new DocutilsRunConfiguration(project, this);
    }
  }

  private static class SphinxRunConfigurationFactory extends RestConfigurationFactory {
    protected SphinxRunConfigurationFactory(ConfigurationType type) {
      super(type, "Sphinx task");
    }

    public RunConfiguration createTemplateConfiguration(Project project) {
      return new SphinxRunConfiguration(project, this);
    }
  }
}
