/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.run.*;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.run.AbstractPythonRunConfigurationParams;
import consulo.annotation.access.RequiredReadAction;
import consulo.execution.RuntimeConfigurationException;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.configuration.LegacyRunConfigurationConverter;
import consulo.execution.configuration.RuntimeConfigurationError;
import consulo.execution.configuration.fragment.RunConfigurationFragment;
import consulo.language.editor.refactoring.event.RefactoringElementAdapter;
import consulo.language.editor.refactoring.event.RefactoringElementListener;
import consulo.language.editor.refactoring.event.RefactoringListenerProvider;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author catherine
 */
public abstract class AbstractPythonTestRunConfiguration extends AbstractPythonRunConfiguration implements AbstractPythonRunConfigurationParams, AbstractPythonTestRunConfigurationParams,
  RefactoringListenerProvider {
  @RequiredReadAction
  protected AbstractPythonTestRunConfiguration(Project project, ConfigurationFactory configurationFactory) {
    super(project, configurationFactory);
  }

  @Override
  public PythonTestRunConfigurationOptions getOptions() {
    return (PythonTestRunConfigurationOptions) super.getOptions();
  }

  /**
   * Converts the common settings and the test target of an old configuration element.
   */
  protected static void convertLegacyTestState(LegacyRunConfigurationConverter converter, PythonTestRunConfigurationOptions options) {
    convertLegacyCommonState(converter, options);

    PythonTestRunConfigurationLegacyState old = converter.readXml(PythonTestRunConfigurationLegacyState.class);
    options.setScriptName(old.SCRIPT_NAME);
    options.setClassName(old.CLASS_NAME);
    options.setMethodName(old.METHOD_NAME);
    options.setFolderName(old.FOLDER_NAME);
    options.setPattern(old.PATTERN);
    options.setUsePattern(old.USE_PATTERN);
    options.setTestType(old.TEST_TYPE);
  }

  @Override
  public List<RunConfigurationFragment<?>> createFragments() {
    List<RunConfigurationFragment<?>> fragments = new ArrayList<>();
    fragments.add(new PythonTestTargetFragment<>(isPatternSupported()));
    fragments.add(new PythonInterpreterFragment<>());
    fragments.add(new PythonInterpreterOptionsFragment<>());
    fragments.add(new PythonAddContentRootsFragment<>());
    fragments.add(new PythonAddSourceRootsFragment<>());
    if (getOptions() instanceof PythonTestParamsOptions) {
      fragments.add(new PythonTestParamsFragment<>());
    }
    return fragments;
  }

  /**
   * Whether a folder target can filter its modules by a pattern.
   */
  protected boolean isPatternSupported() {
    return true;
  }

  @Override
  public String getWorkingDirectorySafe() {
    String workingDirectoryFromConfig = getWorkingDirectory();
    if (StringUtil.isNotEmpty(workingDirectoryFromConfig)) {
      return workingDirectoryFromConfig;
    }

    String folderName = getFolderName();
    if (!StringUtil.isEmptyOrSpaces(folderName)) {
      return folderName;
    }
    String scriptName = getScriptName();
    if (!StringUtil.isEmptyOrSpaces(scriptName)) {
      VirtualFile script = LocalFileSystem.getInstance().findFileByPath(scriptName);
      if (script != null) {
        return script.getParent().getPath();
      }
    }
    return super.getWorkingDirectorySafe();
  }

  @Override
  public AbstractPythonRunConfigurationParams getBaseParams() {
    return this;
  }

  @Override
  public String getClassName() {
    return StringUtil.notNullize(getOptions().getClassName());
  }

  @Override
  public void setClassName(String className) {
    getOptions().setClassName(className);
  }

  @Override
  public String getFolderName() {
    return StringUtil.notNullize(getOptions().getFolderName());
  }

  @Override
  public void setFolderName(String folderName) {
    getOptions().setFolderName(folderName);
  }

  @Override
  public String getScriptName() {
    return StringUtil.notNullize(getOptions().getScriptName());
  }

  @Override
  public void setScriptName(String scriptName) {
    getOptions().setScriptName(scriptName);
  }

  @Override
  public String getMethodName() {
    return StringUtil.notNullize(getOptions().getMethodName());
  }

  @Override
  public void setMethodName(String methodName) {
    getOptions().setMethodName(methodName);
  }

  @Override
  public TestType getTestType() {
    String testType = getOptions().getTestType();
    if (testType == null) {
      return TestType.TEST_SCRIPT;
    }
    try {
      return TestType.valueOf(testType);
    }
    catch (IllegalArgumentException e) {
      return TestType.TEST_SCRIPT;
    }
  }

  @Override
  public void setTestType(TestType testType) {
    getOptions().setTestType(testType == TestType.TEST_SCRIPT ? null : testType.name());
  }

  @Override
  public String getPattern() {
    return StringUtil.notNullize(getOptions().getPattern());
  }

  @Override
  public void setPattern(String pattern) {
    getOptions().setPattern(pattern);
  }

  @Override
  public boolean usePattern() {
    return getOptions().isUsePattern();
  }

  @Override
  public void usePattern(boolean usePattern) {
    getOptions().setUsePattern(usePattern);
  }

  public enum TestType {
    TEST_FOLDER,
    TEST_SCRIPT,
    TEST_CLASS,
    TEST_METHOD,
    TEST_FUNCTION,
  }

  @Override
  public void checkConfiguration() throws RuntimeConfigurationException {
    super.checkConfiguration();

    if (StringUtil.isEmptyOrSpaces(getFolderName()) && getTestType() == TestType.TEST_FOLDER) {
      throw new RuntimeConfigurationError(PyLocalize.runcfgUnittestNo_folder_name());
    }

    if (StringUtil.isEmptyOrSpaces(getScriptName()) && getTestType() != TestType.TEST_FOLDER) {
      throw new RuntimeConfigurationError(PyLocalize.runcfgUnittestNo_script_name());
    }

    if (StringUtil.isEmptyOrSpaces(getClassName()) && (getTestType() == TestType.TEST_METHOD || getTestType() == TestType.TEST_CLASS)) {
      throw new RuntimeConfigurationError(PyLocalize.runcfgUnittestNo_class_name());
    }

    if (StringUtil.isEmptyOrSpaces(getMethodName()) && (getTestType() == TestType.TEST_METHOD || getTestType() == TestType.TEST_FUNCTION)) {
      throw new RuntimeConfigurationError(PyLocalize.runcfgUnittestNo_method_name());
    }
  }

  public boolean compareSettings(AbstractPythonTestRunConfiguration cfg) {
    if (cfg == null) {
      return false;
    }

    if (getTestType() != cfg.getTestType()) {
      return false;
    }

    switch (getTestType()) {
      case TEST_FOLDER:
        return getFolderName().equals(cfg.getFolderName());
      case TEST_SCRIPT:
        return getScriptName().equals(cfg.getScriptName()) && getWorkingDirectory().equals(cfg.getWorkingDirectory());
      case TEST_CLASS:
        return getScriptName().equals(cfg.getScriptName()) &&
          getWorkingDirectory().equals(cfg.getWorkingDirectory()) &&
          getClassName().equals(cfg.getClassName());
      case TEST_METHOD:
        return getScriptName().equals(cfg.getScriptName()) &&
          getWorkingDirectory().equals(cfg.getWorkingDirectory()) &&
          getClassName().equals(cfg.getClassName()) &&
          getMethodName().equals(cfg.getMethodName());
      case TEST_FUNCTION:
        return getScriptName().equals(cfg.getScriptName()) &&
          getWorkingDirectory().equals(cfg.getWorkingDirectory()) &&
          getMethodName().equals(cfg.getMethodName());
      default:
        throw new IllegalStateException("Unknown test type: " + getTestType());
    }
  }

  public static void copyParams(AbstractPythonTestRunConfigurationParams source, AbstractPythonTestRunConfigurationParams target) {
    AbstractPythonRunConfiguration.copyParams(source.getBaseParams(), target.getBaseParams());
    target.setScriptName(source.getScriptName());
    target.setClassName(source.getClassName());
    target.setFolderName(source.getFolderName());
    target.setMethodName(source.getMethodName());
    target.setTestType(source.getTestType());
    target.setPattern(source.getPattern());
    target.usePattern(source.usePattern());
    target.setAddContentRoots(source.shouldAddContentRoots());
    target.setAddSourceRoots(source.shouldAddSourceRoots());
  }

  public AbstractPythonTestRunConfigurationParams getTestRunConfigurationParams() {
    return this;
  }

  @Override
  public String suggestedName() {
    switch (getTestType()) {
      case TEST_CLASS:
        return getPluralTitle() + " in " + getClassName();
      case TEST_METHOD:
        return getTitle() + " " + getClassName() + "." + getMethodName();
      case TEST_SCRIPT:
        String name = new File(getScriptName()).getName();
        name = StringUtil.trimEnd(name, ".py");
        return getPluralTitle() + " in " + name;
      case TEST_FOLDER:
        String folderName = new File(getFolderName()).getName();
        return getPluralTitle() + " in " + folderName;
      case TEST_FUNCTION:
        return getTitle() + " " + getMethodName();
      default:
        throw new IllegalStateException("Unknown test type: " + getTestType());
    }
  }

  @Nullable
  @Override
  public String getActionName() {
    if (TestType.TEST_METHOD.equals(getTestType())) {
      return getTitle() + " " + getMethodName();
    }
    return suggestedName();
  }

  protected abstract String getTitle();

  protected abstract String getPluralTitle();

  @RequiredReadAction
  @Override
  public RefactoringElementListener getRefactoringElementListener(PsiElement element) {
    if (element instanceof PsiDirectory) {
      VirtualFile vFile = ((PsiDirectory)element).getVirtualFile();
      if ((getTestType() == TestType.TEST_FOLDER && pathsEqual(vFile, getFolderName())) || pathsEqual(vFile, getWorkingDirectory())) {
        return new RefactoringElementAdapter() {
          @Override
          protected void elementRenamedOrMoved(PsiElement newElement) {
            String newPath = FileUtil.toSystemDependentName(((PsiDirectory)newElement).getVirtualFile().getPath());
            setWorkingDirectory(newPath);
            if (getTestType() == TestType.TEST_FOLDER) {
              setFolderName(newPath);
            }
          }

          @Override
          public void undoElementMovedOrRenamed(PsiElement newElement, String oldQualifiedName) {
            String systemDependant = FileUtil.toSystemDependentName(oldQualifiedName);
            setWorkingDirectory(systemDependant);
            if (getTestType() == TestType.TEST_FOLDER) {
              setFolderName(systemDependant);
            }
          }
        };
      }
      return null;
    }
    if (getTestType() == TestType.TEST_FOLDER) {
      return null;
    }
    File scriptFile = new File(getScriptName());
    if (!scriptFile.isAbsolute()) {
      scriptFile = new File(getWorkingDirectory(), getScriptName());
    }
    PsiFile containingFile = element.getContainingFile();
    VirtualFile vFile = containingFile == null ? null : containingFile.getVirtualFile();
    if (vFile != null && Comparing.equal(new File(vFile.getPath()).getAbsolutePath(), scriptFile.getAbsolutePath())) {
      if (element instanceof PsiFile) {
        return new RefactoringElementAdapter() {
          @Override
          protected void elementRenamedOrMoved(PsiElement newElement) {
            VirtualFile virtualFile = ((PsiFile)newElement).getVirtualFile();
            if (virtualFile != null) {
              setScriptName(FileUtil.toSystemDependentName(virtualFile.getPath()));
            }
          }

          @Override
          public void undoElementMovedOrRenamed(PsiElement newElement, String oldQualifiedName) {
            setScriptName(FileUtil.toSystemDependentName(oldQualifiedName));
          }
        };
      }
      if (element instanceof PyClass pyClass && (getTestType() == TestType.TEST_CLASS || getTestType() == TestType.TEST_METHOD) &&
        Comparing.equal(pyClass.getName(), getClassName())) {
        return new RefactoringElementAdapter() {
          @Override
          @RequiredReadAction
          protected void elementRenamedOrMoved(PsiElement newElement) {
            setClassName(((PyClass) newElement).getName());
          }

          @Override
          public void undoElementMovedOrRenamed(PsiElement newElement, String oldQualifiedName) {
            setClassName(oldQualifiedName);
          }
        };
      }
      if (element instanceof PyFunction function && Comparing.equal(function.getName(), getMethodName())) {
        ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(function, ScopeOwner.class);
        if ((getTestType() == TestType.TEST_FUNCTION && scopeOwner instanceof PyFile)
            || (getTestType() == TestType.TEST_METHOD && scopeOwner instanceof PyClass && Comparing.equal(scopeOwner.getName(), getClassName()))) {
          return new RefactoringElementAdapter() {
            @Override
            @RequiredReadAction
            protected void elementRenamedOrMoved(PsiElement newElement) {
              setMethodName(((PyFunction)newElement).getName());
            }

            @Override
            public void undoElementMovedOrRenamed(PsiElement newElement, String oldQualifiedName) {
              int methodIdx = oldQualifiedName.indexOf("#") + 1;
              if (methodIdx > 0 && methodIdx < oldQualifiedName.length()) {
                setMethodName(oldQualifiedName.substring(methodIdx));
              }
            }
          };
        }
      }
    }
    return null;
  }

  private static boolean pathsEqual(VirtualFile vFile, String folderName) {
    return Comparing.equal(new File(vFile.getPath()).getAbsolutePath(), new File(folderName).getAbsolutePath());
  }
}
