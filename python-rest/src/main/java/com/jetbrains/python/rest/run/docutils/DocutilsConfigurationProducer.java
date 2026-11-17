// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.run.docutils;

import com.jetbrains.python.rest.run.RestRunConfiguration;
import com.jetbrains.python.rest.run.RestRunConfigurationType;
import com.jetbrains.rest.RestFileType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.RunConfigurationProducer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.util.io.FileUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

@ExtensionImpl
public final class DocutilsConfigurationProducer extends RunConfigurationProducer<RestRunConfiguration> {
    public DocutilsConfigurationProducer() {
        super(RestRunConfigurationType.getInstance().DOCUTILS_FACTORY);
    }

    @Override
    @RequiredReadAction
    protected boolean setupConfigurationFromContext(
        RestRunConfiguration configuration,
        ConfigurationContext context,
        SimpleReference<PsiElement> sourceElement
    ) {
        assert (configuration instanceof DocutilsRunConfiguration);
        PsiFile script = sourceElement.get().getContainingFile();
        if (script == null || script.getFileType() != RestFileType.INSTANCE) {
            return false;
        }
        Module module = ModuleUtilCore.findModuleForPsiElement(script);
        VirtualFile vFile = script.getVirtualFile();
        if (vFile == null) {
            return false;
        }
        configuration.setInputFile(vFile.getPath());
        configuration.setName(script.getName());

        String outputPath = vFile.getPath();
        int index = outputPath.lastIndexOf('.');
        if (index > 0) {
            outputPath = outputPath.substring(0, index);
        }
        outputPath += ".html";
        VirtualFile outputFile = LocalFileSystem.getInstance().findFileByPath(outputPath);
        if (outputFile == null) {
            configuration.setOutputFile(outputPath);
            configuration.setOpenInBrowser(true);
        }

        if (configuration.getTask().isEmpty()) {
            configuration.setTask("rst2html");
        }
        VirtualFile parent = vFile.getParent();
        if (parent != null) {
            configuration.setWorkingDirectory(parent.getPath());
        }
        configuration.setName(configuration.suggestedName());
        if (module != null) {
            configuration.setUseModuleSdk(true);
            configuration.setModule(module);
        }
        return true;
    }

    @Override
    @RequiredReadAction
    public boolean isConfigurationFromContext(RestRunConfiguration configuration, ConfigurationContext context) {
        PsiElement element = context.getPsiLocation();
        if (element == null) {
            return false;
        }
        PsiFile script = element.getContainingFile();
        if (script == null) {
            return false;
        }
        VirtualFile vFile = script.getVirtualFile();
        if (vFile == null) {
            return false;
        }
        String path = vFile.getPath();
        String scriptName = configuration.getInputFile();
        return FileUtil.toSystemIndependentName(scriptName).equals(FileUtil.toSystemIndependentName(path));
    }
}
