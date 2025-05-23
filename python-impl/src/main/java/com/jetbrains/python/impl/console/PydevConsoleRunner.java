/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Collections2;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.impl.console.completion.PydevConsoleElement;
import com.jetbrains.python.impl.console.parsing.PythonConsoleData;
import com.jetbrains.python.impl.run.PythonCommandLineState;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.impl.sdk.flavors.PythonSdkFlavor;
import consulo.content.bundle.Sdk;
import consulo.execution.ui.console.language.LanguageConsoleView;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.encoding.EncodingProjectManager;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.Map;

import static com.jetbrains.python.impl.sdk.PythonEnvUtil.setPythonIOEncoding;
import static com.jetbrains.python.impl.sdk.PythonEnvUtil.setPythonUnbuffered;

/**
 * Created by Yuli Fiterman on 9/13/2016.
 */
public interface PydevConsoleRunner {

  Key<ConsoleCommunication> CONSOLE_KEY = new Key<>("PYDEV_CONSOLE_KEY");
  Key<Sdk> CONSOLE_SDK = new Key<>("PYDEV_CONSOLE_SDK_KEY");

  interface ConsoleListener {
    void handleConsoleInitialized(LanguageConsoleView consoleView);
  }

  @Nonnull
  static Pair<Sdk, Module> findPythonSdkAndModule(@Nonnull Project project, @Nullable Module contextModule) {
    Sdk sdk = null;
    Module module = null;
    PyConsoleOptions.PyConsoleSettings settings = PyConsoleOptions.getInstance(project).getPythonConsoleSettings();
    String sdkHome = settings.getSdkHome();
    if (sdkHome != null) {
      sdk = PythonSdkType.findSdkByPath(sdkHome);
      if (settings.getModuleName() != null) {
        module = ModuleManager.getInstance(project).findModuleByName(settings.getModuleName());
      }
      else {
        module = contextModule;
        if (module == null && ModuleManager.getInstance(project).getModules().length > 0) {
          module = ModuleManager.getInstance(project).getModules()[0];
        }
      }
    }
    if (sdk == null && settings.isUseModuleSdk()) {
      if (contextModule != null) {
        module = contextModule;
      }
      else if (settings.getModuleName() != null) {
        module = ModuleManager.getInstance(project).findModuleByName(settings.getModuleName());
      }
      if (module != null) {
        if (PythonSdkType.findPythonSdk(module) != null) {
          sdk = PythonSdkType.findPythonSdk(module);
        }
      }
    }
    else if (contextModule != null) {
      if (module == null) {
        module = contextModule;
      }
      if (sdk == null) {
        sdk = PythonSdkType.findPythonSdk(module);
      }
    }

    if (sdk == null) {
      for (Module m : ModuleManager.getInstance(project).getModules()) {
        if (PythonSdkType.findPythonSdk(m) != null) {
          sdk = PythonSdkType.findPythonSdk(m);
          module = m;
          break;
        }
      }
    }
    if (sdk == null) {
      if (PythonSdkType.getAllSdks().size() > 0) {
        //noinspection UnusedAssignment
        sdk = PythonSdkType.getAllSdks().get(0); //take any python sdk
      }
    }
    return Pair.create(sdk, module);
  }

  static String constructPythonPathCommand(Collection<String> pythonPath, String command) {
    final String path = Joiner.on(", ").join(Collections2.transform(pythonPath, new Function<String, String>() {
      @Override
      public String apply(String input) {
        return "'" + input.replace("\\", "\\\\").replace("'", "\\'") + "'";
      }
    }));

    return command.replace(PydevConsoleRunnerImpl.WORKING_DIR_ENV, path);
  }

  static Map<String, String> addDefaultEnvironments(Sdk sdk, Map<String, String> envs, @Nonnull Project project) {
    setCorrectStdOutEncoding(envs, project);

    PythonSdkFlavor.initPythonPath(envs, true, PythonCommandLineState.getAddedPaths(sdk));
    return envs;
  }

  /**
   * Add required ENV var to Python task to set its stdout charset to current project charset to allow it print correctly.
   *
   * @param envs    map of envs to add variable
   * @param project current project
   */
  static void setCorrectStdOutEncoding(@Nonnull Map<String, String> envs, @Nonnull Project project) {
    final Charset defaultCharset = EncodingProjectManager.getInstance(project).getDefaultCharset();
    final String encoding = defaultCharset.name();
    setPythonIOEncoding(setPythonUnbuffered(envs), encoding);
  }

  /**
   * Set command line charset as current project charset.
   * Add required ENV var to Python task to set its stdout charset to current project charset to allow it print correctly.
   *
   * @param commandLine command line
   * @param project     current project
   */
  static void setCorrectStdOutEncoding(@Nonnull GeneralCommandLine commandLine, @Nonnull Project project) {
    final Charset defaultCharset = EncodingProjectManager.getInstance(project).getDefaultCharset();
    commandLine.setCharset(defaultCharset);
    setPythonIOEncoding(commandLine.getEnvironment(), defaultCharset.name());
  }

  static boolean isInPydevConsole(PsiElement element) {
    return element instanceof PydevConsoleElement || getConsoleCommunication(element) != null;
  }

  static boolean isPythonConsole(@Nullable ASTNode element) {
    return getPythonConsoleData(element) != null;
  }

  @Nullable
  static PythonConsoleData getPythonConsoleData(@Nullable ASTNode element) {
    if (element == null || element.getPsi() == null || element.getPsi().getContainingFile() == null) {
      return null;
    }

    VirtualFile file = PydevConsoleRunnerImpl.getConsoleFile(element.getPsi().getContainingFile());

    if (file == null) {
      return null;
    }
    return file.getUserData(PyConsoleUtil.PYTHON_CONSOLE_DATA);
  }

  @Nullable
  static ConsoleCommunication getConsoleCommunication(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    return containingFile != null ? containingFile.getCopyableUserData(CONSOLE_KEY) : null;
  }

  @Nullable
  static Sdk getConsoleSdk(PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    return containingFile != null ? containingFile.getCopyableUserData(CONSOLE_SDK) : null;
  }

  void open();

  void runSync();

  void run();

  PydevConsoleCommunication getPydevConsoleCommunication();

  void addConsoleListener(PydevConsoleRunnerImpl.ConsoleListener consoleListener);

  PydevConsoleExecuteActionHandler getConsoleExecuteActionHandler();

  ProcessHandler getProcessHandler();

  PythonConsoleView getConsoleView();
}
