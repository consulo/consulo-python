// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.run.sphinx;

import com.jetbrains.python.rest.run.RestRunConfiguration;
import com.jetbrains.python.rest.run.RestRunConfigurationType;
import com.jetbrains.rest.RestFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.RunConfigurationProducer;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.util.io.FileUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;

@ExtensionImpl
public final class SphinxConfigurationProducer extends RunConfigurationProducer<RestRunConfiguration> {
    public SphinxConfigurationProducer() {
        super(RestRunConfigurationType.getInstance().SPHINX_FACTORY);
    }

    @Override
    @RequiredReadAction
    protected boolean setupConfigurationFromContext(
        RestRunConfiguration configuration,
        ConfigurationContext context,
        SimpleReference<PsiElement> sourceElement
    ) {
        assert (configuration instanceof SphinxRunConfiguration);
        PsiElement element = sourceElement.get();
        if (!(element instanceof PsiDirectory directory)) {
            return false;
        }

        boolean hasRstFile = false;
        boolean hasConf = false;
        for (PsiFile file : directory.getFiles()) {
            if ("conf.py".equals(file.getName())) {
                hasConf = true;
            }
            if (file instanceof RestFile) {
                hasRstFile = true;
            }
        }
        if (!hasRstFile || !hasConf) {
            return false;
        }
        VirtualFile vFile = directory.getVirtualFile();
        configuration.setInputFile(vFile.getPath());

        configuration.setName(directory.getName());
        if (configuration.getTask().isEmpty()) {
            configuration.setTask("html");
        }
        VirtualFile parent = vFile.getParent();
        if (parent != null) {
            configuration.setWorkingDirectory(parent.getPath());
        }
        configuration.setName(configuration.suggestedName());
        Module module = ModuleUtilCore.findModuleForPsiElement(element);
        if (module != null) {
            configuration.setUseModuleSdk(true);
            configuration.setModule(module);
        }
        return true;
    }

    @Override
    public boolean isConfigurationFromContext(RestRunConfiguration configuration, ConfigurationContext context) {
        PsiElement element = context.getPsiLocation();
        if (!(element instanceof PsiDirectory)) {
            return false;
        }
        VirtualFile vFile = ((PsiDirectory) element).getVirtualFile();
        String path = vFile.getPath();
        String scriptName = configuration.getInputFile();
        return FileUtil.toSystemIndependentName(scriptName).equals(FileUtil.toSystemIndependentName(path));
    }
}
