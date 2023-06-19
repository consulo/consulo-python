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
package com.jetbrains.python.impl.console;

import com.google.common.collect.Maps;
import com.jetbrains.python.impl.run.PythonCommandLineState;
import com.jetbrains.python.impl.sdk.PythonEnvUtil;
import consulo.annotation.component.ServiceImpl;
import consulo.content.bundle.Sdk;
import consulo.ide.impl.idea.util.PathMapper;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.project.Project;
import consulo.python.buildout.module.extension.BuildoutModuleExtension;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.inject.Singleton;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * @author traff
 */
@ServiceImpl
@Singleton
public class PydevConsoleRunnerFactory extends PythonConsoleRunnerFactory {
  @Override
  @Nonnull
  public PydevConsoleRunnerImpl createConsoleRunner(@Nonnull Project project, @Nullable Module contextModule) {
    Pair<Sdk, Module> sdkAndModule = PydevConsoleRunner.findPythonSdkAndModule(project, contextModule);

    Module module = sdkAndModule.second;
    Sdk sdk = sdkAndModule.first;

    assert sdk != null;

    PyConsoleOptions.PyConsoleSettings settingsProvider = PyConsoleOptions.getInstance(project).getPythonConsoleSettings();

    PathMapper pathMapper = PydevConsoleRunner.getPathMapper(project, sdk, settingsProvider);

    String[] setupFragment;

    Collection<String> pythonPath =
      PythonCommandLineState.collectPythonPath(module, settingsProvider.shouldAddContentRoots(), settingsProvider.shouldAddSourceRoots());

    if (pathMapper != null) {
      pythonPath = pathMapper.convertToRemote(pythonPath);
    }

    String customStartScript = settingsProvider.getCustomStartScript();

    if (customStartScript.trim().length() > 0) {
      customStartScript = "\n" + customStartScript;
    }

    String selfPathAppend = PydevConsoleRunner.constructPythonPathCommand(pythonPath, customStartScript);

    String workingDir = settingsProvider.getWorkingDirectory();
    if (StringUtil.isEmpty(workingDir)) {
      if (module != null && ModuleRootManager.getInstance(module).getContentRoots().length > 0) {
        workingDir = ModuleRootManager.getInstance(module).getContentRoots()[0].getPath();
      }
      else {
        if (ModuleManager.getInstance(project).getModules().length > 0) {
          VirtualFile[] roots = ModuleRootManager.getInstance(ModuleManager.getInstance(project).getModules()[0]).getContentRoots();
          if (roots.length > 0) {
            workingDir = roots[0].getPath();
          }
        }
      }
    }

    if (pathMapper != null && workingDir != null) {
      workingDir = pathMapper.convertToRemote(workingDir);
    }

    BuildoutModuleExtension facet = null;
    if (module != null) {
      facet = ModuleUtilCore.getExtension(module, BuildoutModuleExtension.class);
    }

    if (facet != null) {
      List<String> path = facet.getAdditionalPythonPath();
      if (pathMapper != null) {
        path = pathMapper.convertToRemote(path);
      }
      String prependStatement = facet.getPathPrependStatement(path);
      setupFragment = new String[]{
        prependStatement,
        selfPathAppend
      };
    }
    else {
      setupFragment = new String[]{selfPathAppend};
    }

    Map<String, String> envs = Maps.newHashMap(settingsProvider.getEnvs());
    putIPythonEnvFlag(project, envs);

    Consumer<String> rerunAction = title -> {
      PydevConsoleRunnerImpl runner = createConsoleRunner(project, module);
      runner.setConsoleTitle(title);
      runner.run();
    };

    return createConsoleRunner(project, sdk, workingDir, envs, PyConsoleType.PYTHON, settingsProvider, rerunAction, setupFragment);
  }

  public static void putIPythonEnvFlag(@Nonnull Project project, Map<String, String> envs) {
    String ipythonEnabled = PyConsoleOptions.getInstance(project).isIpythonEnabled() ? "True" : "False";
    envs.put(PythonEnvUtil.IPYTHONENABLE, ipythonEnabled);
  }

  @Nonnull
  protected PydevConsoleRunnerImpl createConsoleRunner(Project project,
                                                       Sdk sdk,
                                                       String workingDir,
                                                       Map<String, String> envs,
                                                       PyConsoleType consoleType,
                                                       PyConsoleOptions.PyConsoleSettings settingsProvider,
                                                       Consumer<String> rerunAction,
                                                       String... setupFragment) {
    return new PydevConsoleRunnerImpl(project, sdk, consoleType, workingDir, envs, settingsProvider, rerunAction, setupFragment);
  }
}
