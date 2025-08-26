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
package com.jetbrains.python.impl.run;

import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.ConfigurationType;
import consulo.execution.configuration.RunConfiguration;
import consulo.localize.LocalizeValue;
import consulo.module.extension.ModuleExtensionHelper;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.python.module.extension.PyModuleExtension;
import consulo.python.psi.icon.PythonPsiIconGroup;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

/**
 * @author yole
 */
@ExtensionImpl
public class PythonConfigurationType implements ConfigurationType {
    private final PythonConfigurationFactory myFactory = new PythonConfigurationFactory(this);

    public static PythonConfigurationType getInstance() {
        return Application.get().getExtensionPoint(ConfigurationType.class)
            .findExtensionOrFail(PythonConfigurationType.class);
    }

    private static class PythonConfigurationFactory extends ConfigurationFactory {
        protected PythonConfigurationFactory(ConfigurationType configurationType) {
            super(configurationType);
        }

        @Nonnull
        @Override
        public String getId() {
            return "Python";
        }

        @Override
        public boolean isApplicable(@Nonnull Project project) {
            return ModuleExtensionHelper.getInstance(project).hasModuleExtension(PyModuleExtension.class);
        }

        @Override
        public RunConfiguration createTemplateConfiguration(Project project) {
            return new PythonRunConfiguration(project, this);
        }
    }

    @Nonnull
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.pythonConfigurationName();
    }

    @Nonnull
    @Override
    public LocalizeValue getConfigurationTypeDescription() {
        return PyLocalize.pythonConfigurationDescription();
    }

    @Override
    public Image getIcon() {
        return PythonPsiIconGroup.python();
    }

    @Override
    public ConfigurationFactory[] getConfigurationFactories() {
        return new ConfigurationFactory[]{myFactory};
    }

    public PythonConfigurationFactory getFactory() {
        return myFactory;
    }

    @Nonnull
    @Override
    public String getId() {
        return "PythonConfigurationType";
    }
}
