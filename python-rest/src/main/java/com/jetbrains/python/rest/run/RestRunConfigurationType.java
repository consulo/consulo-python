// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import consulo.python.impl.localize.PyLocalize;
import consulo.project.Project;
import consulo.reStructuredText.localize.RestLocalize;
import consulo.ui.image.Image;


/**
 * User : catherine
 */
@ExtensionImpl
public final class RestRunConfigurationType implements ConfigurationType {
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
  public String getId() {
    return "RestRunConfigurationType";
  }

  @Override
  public ConfigurationFactory[] getConfigurationFactories() {
    return new ConfigurationFactory[] {DOCUTILS_FACTORY, SPHINX_FACTORY};
  }

  @Override
  public boolean isDumbAware() {
    return true;
  }

  private static abstract class RestConfigurationFactory extends ConfigurationFactory {
    private final String myId;
    private final LocalizeValue myDisplayName;

    public RestConfigurationFactory(ConfigurationType type, String id, LocalizeValue displayName) {
      super(type);
      myId = id;
      myDisplayName = displayName;
    }

    @Override
    public String getId() {
      return myId;
    }

    @Override
    public LocalizeValue getDisplayName() {
      return myDisplayName;
    }
  }

  private static class DocutilsRunConfigurationFactory extends RestConfigurationFactory {
    protected DocutilsRunConfigurationFactory(ConfigurationType type) {
      super(type, "Docutils", PyLocalize.runcfgRestDocutilsTask());
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new DocutilsRunConfiguration(project, this);
    }
  }

  private static class SphinxRunConfigurationFactory extends RestConfigurationFactory {
    protected SphinxRunConfigurationFactory(ConfigurationType type) {
      super(type, "Sphinx", PyLocalize.runcfgRestSphinxTask());
    }

    @Override
    public RunConfiguration createTemplateConfiguration(Project project) {
      return new SphinxRunConfiguration(project, this);
    }
  }
}
