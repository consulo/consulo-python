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
package com.jetbrains.python.testing;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import javax.annotation.Nonnull;

import consulo.python.module.extension.PyModuleExtension;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nullable;
import com.google.common.collect.Sets;
import consulo.execution.action.Location;
import consulo.execution.action.ConfigurationContext;
import consulo.execution.action.ConfigurationFromContext;
import consulo.execution.action.RunConfigurationProducer;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.RunConfiguration;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.language.util.ModuleUtilCore;
import consulo.project.Project;
import consulo.module.content.ProjectRootManager;
import consulo.util.lang.ref.Ref;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.run.PythonRunConfigurationProducer;
import com.jetbrains.python.testing.unittest.PythonUnitTestRunConfiguration;

/**
 * User: ktisha
 */
abstract public class PythonTestConfigurationProducer extends RunConfigurationProducer<AbstractPythonTestRunConfiguration>
{

	public PythonTestConfigurationProducer(final ConfigurationFactory configurationFactory)
	{
		super(configurationFactory);
	}

	@Override
	public boolean isConfigurationFromContext(AbstractPythonTestRunConfiguration configuration, ConfigurationContext context)
	{
		final Location location = context.getLocation();
		if(location == null || !isAvailable(location))
		{
			return false;
		}
		final PsiElement element = location.getPsiElement();
		final PsiFileSystemItem file = element.getContainingFile();
		if(file == null)
		{
			return false;
		}
		final VirtualFile virtualFile = element instanceof PsiDirectory ? ((PsiDirectory) element).getVirtualFile() : file.getVirtualFile();
		if(virtualFile == null)
		{
			return false;
		}
		final PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
		final PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class);

		final AbstractPythonTestRunConfiguration.TestType confType = configuration.getTestType();
		final String workingDirectory = configuration.getWorkingDirectory();

		if(element instanceof PsiDirectory)
		{
			final String path = ((PsiDirectory) element).getVirtualFile().getPath();
			return confType == AbstractPythonTestRunConfiguration.TestType.TEST_FOLDER && path.equals(configuration.getFolderName()) || path.equals(new File(workingDirectory, configuration
					.getFolderName()).getAbsolutePath());
		}

		final String scriptName = configuration.getScriptName();
		final String path = virtualFile.getPath();
		final boolean isTestFileEquals = scriptName.equals(path) || path.equals(new File(workingDirectory, scriptName).getAbsolutePath());

		if(pyFunction != null)
		{
			final String methodName = configuration.getMethodName();
			if(pyFunction.getContainingClass() == null)
			{
				return confType == AbstractPythonTestRunConfiguration.TestType.TEST_FUNCTION &&
						methodName.equals(pyFunction.getName()) && isTestFileEquals;
			}
			else
			{
				final String className = configuration.getClassName();

				return confType == AbstractPythonTestRunConfiguration.TestType.TEST_METHOD &&
						methodName.equals(pyFunction.getName()) &&
						pyClass != null && className.equals(pyClass.getName()) && isTestFileEquals;
			}
		}
		if(pyClass != null)
		{
			final String className = configuration.getClassName();
			return confType == AbstractPythonTestRunConfiguration.TestType.TEST_CLASS &&
					className.equals(pyClass.getName()) && isTestFileEquals;
		}
		return confType == AbstractPythonTestRunConfiguration.TestType.TEST_SCRIPT && isTestFileEquals;
	}

	@Override
	protected boolean setupConfigurationFromContext(AbstractPythonTestRunConfiguration configuration, ConfigurationContext context, Ref<PsiElement> sourceElement)
	{
		if(context == null)
		{
			return false;
		}
		final Location location = context.getLocation();
		if(location == null || !isAvailable(location))
		{
			return false;
		}
		PsiElement element = location.getPsiElement();
		if(element instanceof PsiWhiteSpace)
		{
			element = PyUtil.findNonWhitespaceAtOffset(element.getContainingFile(), element.getTextOffset());
		}

		if(PythonUnitTestRunnableScriptFilter.isIfNameMain(location))
		{
			return false;
		}
		final Module module = location.getModule();
		if(!isPythonModule(module))
		{
			return false;
		}

		if(element instanceof PsiDirectory)
		{
			return setupConfigurationFromFolder((PsiDirectory) element, configuration);
		}

		final PyFunction pyFunction = PsiTreeUtil.getParentOfType(element, PyFunction.class, false);
		if(pyFunction != null && isTestFunction(pyFunction, configuration))
		{
			return setupConfigurationFromFunction(pyFunction, configuration);
		}
		final PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
		if(pyClass != null && isTestClass(pyClass, configuration, TypeEvalContext.userInitiated(pyClass.getProject(), element.getContainingFile())))
		{
			return setupConfigurationFromClass(pyClass, configuration);
		}
		if(element == null)
		{
			return false;
		}
		final PsiFile file = element.getContainingFile();
		if(file instanceof PyFile && isTestFile((PyFile) file))
		{
			return setupConfigurationFromFile((PyFile) file, configuration);
		}

		return false;
	}

	private boolean setupConfigurationFromFolder(@Nonnull final PsiDirectory element, @Nonnull final AbstractPythonTestRunConfiguration configuration)
	{
		final VirtualFile virtualFile = element.getVirtualFile();
		if(!isTestFolder(virtualFile, element.getProject()))
		{
			return false;
		}
		final String path = virtualFile.getPath();

		configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_FOLDER);
		configuration.setFolderName(path);
		configuration.setWorkingDirectory(path);
		configuration.setGeneratedName();
		setModuleSdk(element, configuration);
		return true;
	}

	private static void setModuleSdk(@Nonnull final PsiElement element, @Nonnull final AbstractPythonTestRunConfiguration configuration)
	{
		configuration.setUseModuleSdk(true);
		configuration.setModule(ModuleUtilCore.findModuleForPsiElement(element));
	}

	protected boolean setupConfigurationFromFunction(@Nonnull final PyFunction pyFunction, @Nonnull final AbstractPythonTestRunConfiguration configuration)
	{
		final PyClass containingClass = pyFunction.getContainingClass();
		configuration.setMethodName(pyFunction.getName());

		if(containingClass != null)
		{
			configuration.setClassName(containingClass.getName());
			configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_METHOD);
		}
		else
		{
			configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_FUNCTION);
		}
		return setupConfigurationScript(configuration, pyFunction);
	}

	protected boolean setupConfigurationFromClass(@Nonnull final PyClass pyClass, @Nonnull final AbstractPythonTestRunConfiguration configuration)
	{
		configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_CLASS);
		configuration.setClassName(pyClass.getName());
		return setupConfigurationScript(configuration, pyClass);
	}

	protected boolean setupConfigurationFromFile(@Nonnull final PyFile pyFile, @Nonnull final AbstractPythonTestRunConfiguration configuration)
	{
		configuration.setTestType(AbstractPythonTestRunConfiguration.TestType.TEST_SCRIPT);
		return setupConfigurationScript(configuration, pyFile);
	}

	protected static boolean setupConfigurationScript(@Nonnull final AbstractPythonTestRunConfiguration cfg, @Nonnull final PyElement element)
	{
		final PyFile containingFile = PyUtil.getContainingPyFile(element);
		if(containingFile == null)
		{
			return false;
		}
		final VirtualFile vFile = containingFile.getVirtualFile();
		if(vFile == null)
		{
			return false;
		}
		final VirtualFile parent = vFile.getParent();
		if(parent == null)
		{
			return false;
		}

		cfg.setScriptName(vFile.getPath());

		if(StringUtil.isEmptyOrSpaces(cfg.getWorkingDirectory()))
		{
			cfg.setWorkingDirectory(parent.getPath());
		}
		cfg.setGeneratedName();
		setModuleSdk(element, cfg);
		return true;
	}

	protected boolean isTestFolder(@Nonnull final VirtualFile virtualFile, @Nonnull final Project project)
	{
		@NonNls final String name = virtualFile.getName();
		final HashSet<VirtualFile> roots = Sets.newHashSet();
		final Module[] modules = ModuleManager.getInstance(project).getModules();
		for(Module module : modules)
		{
			roots.addAll(PyUtil.getSourceRoots(module));
		}
		Collections.addAll(roots, ProjectRootManager.getInstance(project).getContentRoots());
		return name.toLowerCase().contains("test") || roots.contains(virtualFile);
	}

	protected boolean isAvailable(@Nonnull final Location location)
	{
		return false;
	}

	protected boolean isTestClass(@Nonnull final PyClass pyClass, @Nullable final AbstractPythonTestRunConfiguration configuration, @Nullable final TypeEvalContext context)
	{
		return PythonUnitTestUtil.isTestCaseClass(pyClass, context);
	}

	protected boolean isTestFunction(@Nonnull final PyFunction pyFunction, @Nullable final AbstractPythonTestRunConfiguration configuration)
	{
		return PythonUnitTestUtil.isTestCaseFunction(pyFunction);
	}

	protected boolean isTestFile(@Nonnull final PyFile file)
	{
		final List<PyStatement> testCases = getTestCaseClassesFromFile(file);
		if(testCases.isEmpty())
		{
			return false;
		}
		return true;
	}

	protected static boolean isPythonModule(Module module)
	{
		if(module == null)
		{
			return false;
		}
		return ModuleUtilCore.getExtension(module, PyModuleExtension.class) != null;
	}

	protected List<PyStatement> getTestCaseClassesFromFile(@Nonnull final PyFile pyFile)
	{
		return PythonUnitTestUtil.getTestCaseClassesFromFile(pyFile, TypeEvalContext.userInitiated(pyFile.getProject(), pyFile));
	}

	@Override
	public boolean isPreferredConfiguration(ConfigurationFromContext self, ConfigurationFromContext other)
	{
		final RunConfiguration configuration = self.getConfiguration();
		if(configuration instanceof PythonUnitTestRunConfiguration && ((PythonUnitTestRunConfiguration) configuration).getTestType() == AbstractPythonTestRunConfiguration.TestType.TEST_FOLDER)
		{
			return true;
		}
		return other.isProducedBy(PythonTestConfigurationProducer.class) || other.isProducedBy(PythonRunConfigurationProducer.class);
	}
}