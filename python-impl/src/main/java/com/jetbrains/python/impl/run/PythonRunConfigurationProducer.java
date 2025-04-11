/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.ConfigurationFromContext;
import consulo.execution.action.Location;
import consulo.execution.action.RunConfigurationProducer;
import consulo.language.file.light.LightVirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.module.Module;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;

/**
 * @author yole
 */
@ExtensionImpl
public class PythonRunConfigurationProducer extends RunConfigurationProducer<PythonRunConfiguration> {
    public PythonRunConfigurationProducer() {
        super(PythonConfigurationType.getInstance().getFactory());
    }

    @Override
    @RequiredReadAction
    protected boolean setupConfigurationFromContext(
        PythonRunConfiguration configuration,
        ConfigurationContext context,
        SimpleReference<PsiElement> sourceElement
    ) {
        Location location = context.getLocation();
        if (location == null) {
            return false;
        }
        PsiFile script = location.getPsiElement().getContainingFile();
        if (!isAvailable(location, script)) {
            return false;
        }

        VirtualFile vFile = script.getVirtualFile();
        if (vFile == null) {
            return false;
        }
        configuration.setScriptName(vFile.getPath());
        VirtualFile parent = vFile.getParent();
        if (parent != null) {
            configuration.setWorkingDirectory(parent.getPath());
        }
        Module module = script.getModule();
        if (module != null) {
            configuration.setUseModuleSdk(true);
            configuration.setModule(module);
        }
        configuration.setName(configuration.suggestedName());
        return true;
    }

    @Override
    @RequiredReadAction
    public boolean isConfigurationFromContext(PythonRunConfiguration configuration, ConfigurationContext context) {
        Location location = context.getLocation();
        if (location == null) {
            return false;
        }
        PsiFile script = location.getPsiElement().getContainingFile();
        if (!isAvailable(location, script)) {
            return false;
        }
        VirtualFile virtualFile = script.getVirtualFile();
        if (virtualFile == null) {
            return false;
        }
        if (virtualFile instanceof LightVirtualFile) {
            return false;
        }
        String workingDirectory = configuration.getWorkingDirectory();
        String scriptName = configuration.getScriptName();
        String path = virtualFile.getPath();
        return scriptName.equals(path) || path.equals(new File(workingDirectory, scriptName).getAbsolutePath());
    }

    @RequiredReadAction
    private static boolean isAvailable(@Nonnull Location location, @Nullable PsiFile script) {
        if (script == null || script.getFileType() != PythonFileType.INSTANCE) {
            return false;
        }
        Module module = script.getModule();
        if (module != null) {
            for (RunnableScriptFilter f : RunnableScriptFilter.EP_NAME.getExtensions()) {
                // Configuration producers always called by user
                if (f.isRunnableScript(script, module, location, TypeEvalContext.userInitiated(location.getProject(), null))) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
        return other.isProducedBy(PythonRunConfigurationProducer.class);
    }
}
