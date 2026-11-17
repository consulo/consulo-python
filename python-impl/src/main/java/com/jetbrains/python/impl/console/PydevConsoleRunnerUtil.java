// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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

import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.impl.console.completion.PydevConsoleElement;
import com.jetbrains.python.impl.run.PythonCommandLineState;
import com.jetbrains.python.impl.sdk.PythonEnvUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.impl.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.parsing.console.PythonConsoleData;
import consulo.content.bundle.Sdk;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.process.cmd.GeneralCommandLine;
import consulo.project.Project;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Port of JB {@code PydevConsoleRunnerUtil.kt}.
 * <p>
 * Consulo keeps its own SDK design: the Target API and {@code PythonInterpreter} based functions are not ported,
 * the {@link Sdk} based GeneralCommandLine code paths are used instead.
 */
public final class PydevConsoleRunnerUtil {
    private PydevConsoleRunnerUtil() {
    }

    /**
     * The interpreter a console starts on, and the module it runs for. The interpreter is {@code null} when the
     * project offers none.
     * <p>
     * Consulo-specific: JB resolves a {@code PythonInterpreter} of a {@code PyProject}, Consulo resolves the {@link Sdk}
     * from the Python Console settings and the module extensions.
     */
    public static Pair<Sdk, Module> findPythonSdkAndModule(Project project, @Nullable Module contextModule) {
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

    /**
     * The GeneralCommandLine counterpart of JB {@code constructPyPathAndWorkingDirCommand}: adds {@code workingDir} to
     * {@code pythonPath} and puts the paths in place of {@link PydevConsoleRunnerImpl#WORKING_DIR_AND_PYTHON_PATHS}.
     */
    public static String constructPyPathAndWorkingDirCommand(
        Collection<String> pythonPath,
        @Nullable String workingDir,
        String command
    ) {
        Set<String> paths = new LinkedHashSet<>(pythonPath);
        if (workingDir != null) {
            paths.add(workingDir);
        }
        String path = paths.stream().map(PydevConsoleRunnerUtil::toStringLiteral).collect(Collectors.joining(", "));
        return command.replace(PydevConsoleRunnerImpl.WORKING_DIR_AND_PYTHON_PATHS, path);
    }

    /**
     * Port of JB {@code PythonConsoleScripts.toStringLiteral}: returns {@code s} as Python string literal.
     * <p>
     * Whitespaces, backslashes and single quotes are escaped and the escaped string is wrapped in single quotes.
     */
    private static String toStringLiteral(String s) {
        return "'" + StringUtil.escapeCharCharacters(s) + "'";
    }

    public static Map<String, String> addDefaultEnvironments(Sdk sdk, Map<String, String> envs) {
        setCorrectStdOutEncoding(envs);
        PythonSdkFlavor.initPythonPath(envs, true, PythonCommandLineState.getAddedPaths(sdk));
        return envs;
    }

    /**
     * Add required ENV var to Python task to set its stdout charset to UTF-8 to allow it print correctly.
     *
     * @param envs map of envs to add variable
     */
    public static void setCorrectStdOutEncoding(Map<String, String> envs) {
        Charset defaultCharset = PydevConsoleRunnerImpl.CONSOLE_CHARSET;
        String encoding = defaultCharset.name();
        PythonEnvUtil.setPythonIOEncoding(PythonEnvUtil.setPythonUnbuffered(envs), encoding);
    }

    /**
     * Set command line charset as UTF-8 (the only charset supported by console)
     * Add required ENV var to Python task to set its stdout charset to current project charset to allow it print correctly.
     *
     * @param commandLine command line
     */
    public static void setCorrectStdOutEncoding(GeneralCommandLine commandLine) {
        Charset defaultCharset = PydevConsoleRunnerImpl.CONSOLE_CHARSET;
        commandLine.setCharset(defaultCharset);
        PythonEnvUtil.setPythonIOEncoding(commandLine.getEnvironment(), defaultCharset.name());
    }

    public static boolean isInPydevConsole(PsiElement element) {
        return element instanceof PydevConsoleElement || getConsoleCommunication(element) != null || hasConsoleKey(element);
    }

    private static boolean hasConsoleKey(PsiElement element) {
        PsiFile psiFile = element.getContainingFile();
        if (psiFile == null) {
            return false;
        }
        if (psiFile.getVirtualFile() == null) {
            return false;
        }
        Boolean inConsole = element.getContainingFile().getVirtualFile().getUserData(PythonConsoleView.CONSOLE_KEY);
        return inConsole != null && inConsole;
    }

    public static boolean isConsoleView(VirtualFile file) {
        return Boolean.TRUE.equals(file.getUserData(PythonConsoleView.CONSOLE_KEY));
    }

    public static @Nullable PythonConsoleData getPythonConsoleData(@Nullable ASTNode element) {
        if (element == null || element.getPsi() == null || element.getPsi().getContainingFile() == null) {
            return null;
        }
        VirtualFile file = PydevConsoleRunnerImpl.getConsoleFile(element.getPsi().getContainingFile());
        if (file == null) {
            return null;
        }
        return file.getUserData(PyConsoleUtil.PYTHON_CONSOLE_DATA);
    }

    static @Nullable ConsoleCommunication getConsoleCommunication(PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        return containingFile != null ? containingFile.getCopyableUserData(PydevConsoleRunner.CONSOLE_COMMUNICATION_KEY) : null;
    }

    public static @Nullable Sdk getConsoleSdk(PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        return containingFile != null ? containingFile.getCopyableUserData(PydevConsoleRunner.CONSOLE_SDK) : null;
    }
}
