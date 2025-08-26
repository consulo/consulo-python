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
package com.jetbrains.python.impl.testing;

import com.google.common.collect.Sets;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.run.PythonRunConfigurationProducer;
import com.jetbrains.python.impl.testing.unittest.PythonUnitTestRunConfiguration;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.ReadAction;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.ConfigurationFromContext;
import consulo.execution.action.Location;
import consulo.execution.action.RunConfigurationProducer;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.RunConfiguration;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.python.module.extension.PyModuleExtension;
import consulo.util.lang.StringUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * User: ktisha
 */
abstract public class PythonTestConfigurationProducer extends RunConfigurationProducer<AbstractPythonTestRunConfiguration> {
    public PythonTestConfigurationProducer(ConfigurationFactory configurationFactory) {
        super(configurationFactory);
    }

    @Override
    @RequiredReadAction
    public boolean isConfigurationFromContext(AbstractPythonTestRunConfiguration configuration, ConfigurationContext context) {
        Location location = context.getLocation();
        if (location == null || !isAvailable(location)) {
            return false;
        }
        PsiElement element = location.getPsiElement();
        PsiFileSystemItem file = element.getContainingFile();
        if (file == null) {
            return false;
        }
        VirtualFile virtualFile = element instanceof PsiDirectory directory ? directory.getVirtualFile() : file.getVirtualFile();
        if (virtualFile == null) {
            return false;
        }
        PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
        PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);

        AbstractPythonTestRunConfiguration.TestType confType = configuration.getTestType();
        String workingDirectory = configuration.getWorkingDirectory();

        if (element instanceof PsiDirectory directory) {
            String path = directory.getVirtualFile().getPath();
            return confType == AbstractPythonTestRunConfiguration.TestType.TEST_FOLDER && path.equals(configuration.getFolderName())
                || path.equals(new File(workingDirectory, configuration.getFolderName()).getAbsolutePath());
        }

        String scriptName = configuration.getScriptName();
        String path = virtualFile.getPath();
        boolean isTestFileEquals = scriptName.equals(path) || path.equals(new File(workingDirectory, scriptName).getAbsolutePath());

        if (pyFunction != null) {
            String methodName = configuration.getMethodName();
            if (pyFunction.getContainingClass() == null) {
                return confType == AbstractPythonTestRunConfiguration.TestType.TEST_FUNCTION
                    && methodName.equals(pyFunction.getName()) && isTestFileEquals;
            }
            else {
                String className = configuration.getClassName();

                return confType == AbstractPythonTestRunConfiguration.TestType.TEST_METHOD
                    && methodName.equals(pyFunction.getName())
                    && pyClass != null && className.equals(pyClass.getName()) && isTestFileEquals;
            }
        }
        if (pyClass != null) {
            String className = configuration.getClassName();
            return confType == AbstractPythonTestRunConfiguration.TestType.TEST_CLASS
                && className.equals(pyClass.getName()) && isTestFileEquals;
        }
        return confType == AbstractPythonTestRunConfiguration.TestType.TEST_SCRIPT && isTestFileEquals;
    }

    @Override
    protected boolean setupConfigurationFromContext(
        AbstractPythonTestRunConfiguration configuration,
        ConfigurationContext context,
        SimpleReference<PsiElement> sourceElement
    ) {
        if (context == null) {
            return false;
        }
        Location location = context.getLocation();
        if (location == null || !ReadAction.compute(() -> isAvailable(location))) {
            return false;
        }
        PsiElement element = location.getPsiElement();
        if (element instanceof PsiWhiteSpace) {
            element = PyUtil.findNonWhitespaceAtOffset(element.getContainingFile(), element.getTextOffset());
        }

        if (PythonUnitTestRunnableScriptFilter.isIfNameMain(location)) {
            return false;
        }
        Module module = location.getModule();
        if (!isPythonModule(module)) {
            return false;
        }

        if (element instanceof PsiDirectory directory) {
            return setupConfigurationFromFolder(directory, configuration);
        }

        PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
        if (pyFunction != null && isTestFunction(pyFunction, configuration)) {
            return setupConfigurationFromFunction(pyFunction, configuration);
        }
        PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
        if (pyClass != null && isTestClass(
            pyClass,
            configuration,
            TypeEvalContext.userInitiated(pyClass.getProject(), element.getContainingFile())
        )) {
            return setupConfigurationFromClass(pyClass, configuration);
        }
        if (element == null) {
            return false;
        }
        PsiFile file = element.getContainingFile();
        return file instanceof PyFile pyFile && isTestFile(pyFile) && setupConfigurationFromFile(pyFile, configuration);
    }

    private boolean setupConfigurationFromFolder(@Nonnull PsiDirectory element, @Nonnull AbstractPythonTestRunConfiguration configuration) {
        VirtualFile virtualFile = element.getVirtualFile();
        if (!isTestFolder(virtualFile, element.getProject())) {
            return false;
        }
        String path = virtualFile.getPath();

        configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_FOLDER);
        configuration.setFolderName(path);
        configuration.setWorkingDirectory(path);
        configuration.setGeneratedName();
        setModuleSdk(element, configuration);
        return true;
    }

    private static void setModuleSdk(@Nonnull PsiElement element, @Nonnull AbstractPythonTestRunConfiguration configuration) {
        configuration.setUseModuleSdk(true);
        configuration.setModule(element.getModule());
    }

    protected boolean setupConfigurationFromFunction(
        @Nonnull PyFunction pyFunction,
        @Nonnull AbstractPythonTestRunConfiguration configuration
    ) {
        PyClass containingClass = pyFunction.getContainingClass();
        configuration.setMethodName(pyFunction.getName());

        if (containingClass != null) {
            configuration.setClassName(containingClass.getName());
            configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_METHOD);
        }
        else {
            configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_FUNCTION);
        }
        return setupConfigurationScript(configuration, pyFunction);
    }

    protected boolean setupConfigurationFromClass(
        @Nonnull PyClass pyClass,
        @Nonnull AbstractPythonTestRunConfiguration configuration
    ) {
        configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_CLASS);
        configuration.setClassName(pyClass.getName());
        return setupConfigurationScript(configuration, pyClass);
    }

    protected boolean setupConfigurationFromFile(
        @Nonnull PyFile pyFile,
        @Nonnull AbstractPythonTestRunConfiguration configuration
    ) {
        configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_SCRIPT);
        return setupConfigurationScript(configuration, pyFile);
    }

    protected static boolean setupConfigurationScript(
        @Nonnull AbstractPythonTestRunConfiguration cfg,
        @Nonnull PyElement element
    ) {
        PyFile containingFile = PyUtil.getContainingPyFile(element);
        if (containingFile == null) {
            return false;
        }
        VirtualFile vFile = containingFile.getVirtualFile();
        if (vFile == null) {
            return false;
        }
        VirtualFile parent = vFile.getParent();
        if (parent == null) {
            return false;
        }

        cfg.setScriptName(vFile.getPath());

        if (StringUtil.isEmptyOrSpaces(cfg.getWorkingDirectory())) {
            cfg.setWorkingDirectory(parent.getPath());
        }
        cfg.setGeneratedName();
        setModuleSdk(element, cfg);
        return true;
    }

    @RequiredReadAction
    protected boolean isTestFolder(@Nonnull VirtualFile virtualFile, @Nonnull Project project) {
        String name = virtualFile.getName();
        HashSet<VirtualFile> roots = Sets.newHashSet();
        Module[] modules = ModuleManager.getInstance(project).getModules();
        for (Module module : modules) {
            roots.addAll(PyUtil.getSourceRoots(module));
        }
        Collections.addAll(roots, ProjectRootManager.getInstance(project).getContentRoots());
        return name.toLowerCase().contains("test") || roots.contains(virtualFile);
    }

    @RequiredReadAction
    protected boolean isAvailable(@Nonnull Location location) {
        return false;
    }

    protected boolean isTestClass(
        @Nonnull PyClass pyClass,
        @Nullable AbstractPythonTestRunConfiguration configuration,
        @Nullable TypeEvalContext context
    ) {
        return PythonUnitTestUtil.isTestCaseClass(pyClass, context);
    }

    protected boolean isTestFunction(
        @Nonnull PyFunction pyFunction,
        @Nullable AbstractPythonTestRunConfiguration configuration
    ) {
        return PythonUnitTestUtil.isTestCaseFunction(pyFunction);
    }

    protected boolean isTestFile(@Nonnull PyFile file) {
        List<PyStatement> testCases = getTestCaseClassesFromFile(file);
        return !testCases.isEmpty();
    }

    protected static boolean isPythonModule(Module module) {
        return module != null && ModuleUtilCore.getExtension(module, PyModuleExtension.class) != null;
    }

    protected List<PyStatement> getTestCaseClassesFromFile(@Nonnull PyFile pyFile) {
        return PythonUnitTestUtil.getTestCaseClassesFromFile(pyFile, TypeEvalContext.userInitiated(pyFile.getProject(), pyFile));
    }

    @Override
    public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other) {
        RunConfiguration configuration = self.getConfiguration();
        return configuration instanceof PythonUnitTestRunConfiguration unitTestRunConfiguration
            && unitTestRunConfiguration.getTestType() == AbstractPythonTestRunConfiguration.TestType.TEST_FOLDER
            || other.isProducedBy(PythonTestConfigurationProducer.class)
            || other.isProducedBy(PythonRunConfigurationProducer.class);
    }
}