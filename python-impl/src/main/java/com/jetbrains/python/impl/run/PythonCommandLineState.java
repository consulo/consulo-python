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
package com.jetbrains.python.impl.run;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.jetbrains.python.impl.console.PyDebugConsoleBuilder;
import com.jetbrains.python.impl.debugger.PyDebugRunner;
import com.jetbrains.python.impl.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.impl.facet.LibraryContributingFacet;
import com.jetbrains.python.impl.facet.PythonPathContributingFacet;
import com.jetbrains.python.impl.sdk.PythonEnvUtil;
import com.jetbrains.python.impl.sdk.PythonSdkAdditionalData;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.impl.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.run.PythonRunParams;
import consulo.compiler.ModuleCompilerPathsManager;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.content.bundle.SdkAdditionalData;
import consulo.content.library.Library;
import consulo.execution.DefaultExecutionResult;
import consulo.execution.ExecutionResult;
import consulo.execution.configuration.CommandLineState;
import consulo.execution.executor.Executor;
import consulo.execution.process.ProcessTerminatedListener;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ProgramRunner;
import consulo.execution.ui.console.ConsoleView;
import consulo.execution.ui.console.TextConsoleBuilder;
import consulo.execution.ui.console.TextConsoleBuilderFactory;
import consulo.execution.ui.console.UrlFilter;
import consulo.ide.impl.idea.execution.configurations.EncodingEnvironmentUtil;
import consulo.language.content.ProductionContentFolderTypeProvider;
import consulo.language.content.TestContentFolderTypeProvider;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.ModuleManager;
import consulo.module.content.ModuleRootManager;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.module.extension.ModuleExtension;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.cmd.GeneralCommandLine.ParentEnvironmentType;
import consulo.process.cmd.ParametersList;
import consulo.project.Project;
import consulo.ui.ex.action.AnAction;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveFileSystem;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import consulo.virtualFileSystem.encoding.EncodingProjectManager;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.util.*;

/**
 * @author traff, Leonid Shalupov
 */
public abstract class PythonCommandLineState extends CommandLineState {
  private static final Logger LOG = Logger.getInstance(PythonCommandLineState.class);

  // command line has a number of fixed groups of parameters; patchers should only operate on them and not the raw list.

  public static final String GROUP_EXE_OPTIONS = "Exe Options";
  public static final String GROUP_DEBUGGER = "Debugger";
  public static final String GROUP_PROFILER = "Profiler";
  public static final String GROUP_COVERAGE = "Coverage";
  public static final String GROUP_SCRIPT = "Script";
  private final AbstractPythonRunConfiguration myConfig;

  private Boolean myMultiprocessDebug = null;

  public boolean isDebug() {
    return PyDebugRunner.PY_DEBUG_RUNNER.equals(getEnvironment().getRunner().getRunnerId());
  }

  public static ServerSocket createServerSocket() throws ExecutionException {
    final ServerSocket serverSocket;
    try {
      //noinspection SocketOpenedButNotSafelyClosed
      serverSocket = new ServerSocket(0);
    }
    catch (IOException e) {
      throw new ExecutionException("Failed to find free socket port", e);
    }
    return serverSocket;
  }

  public PythonCommandLineState(AbstractPythonRunConfiguration runConfiguration, ExecutionEnvironment env) {
    super(env);
    myConfig = runConfiguration;
  }

  @Nullable
  public PythonSdkFlavor getSdkFlavor() {
    return PythonSdkFlavor.getFlavor(myConfig.getInterpreterPath());
  }

  public Sdk getSdk() {
    return myConfig.getSdk();
  }

  @Nonnull
  @Override
  public ExecutionResult execute(@Nonnull Executor executor, @Nonnull ProgramRunner runner) throws ExecutionException {
    return execute(executor, (CommandLinePatcher[])null);
  }

  public ExecutionResult execute(Executor executor, CommandLinePatcher... patchers) throws ExecutionException {
    final ProcessHandler processHandler = startProcess(patchers);
    final ConsoleView console = createAndAttachConsole(myConfig.getProject(), processHandler, executor);

    List<AnAction> actions = Lists.newArrayList(createActions(console, processHandler));

    return new DefaultExecutionResult(console, processHandler, actions.toArray(new AnAction[actions.size()]));
  }

  @Nonnull
  protected ConsoleView createAndAttachConsole(Project project,
                                               ProcessHandler processHandler,
                                               Executor executor) throws ExecutionException {
    final ConsoleView consoleView = createConsoleBuilder(project).getConsole();
    consoleView.addMessageFilter(createUrlFilter(processHandler));

    addTracebackFilter(project, consoleView, processHandler);

    consoleView.attachToProcess(processHandler);
    return consoleView;
  }

  protected void addTracebackFilter(Project project, ConsoleView consoleView, ProcessHandler processHandler) {
      consoleView.addMessageFilter(new PythonTracebackFilter(project, myConfig.getWorkingDirectorySafe()));
      consoleView.addMessageFilter(createUrlFilter(processHandler)); // Url filter is always nice to have
  }

  private TextConsoleBuilder createConsoleBuilder(Project project) {
    if (isDebug()) {
      return new PyDebugConsoleBuilder(project, PythonSdkType.findSdkByPath(myConfig.getInterpreterPath()));
    }
    else {
      return TextConsoleBuilderFactory.getInstance().createBuilder(project);
    }
  }

  @Override
  @Nonnull
  protected ProcessHandler startProcess() throws ExecutionException {
    return startProcess(new CommandLinePatcher[]{});
  }

  /**
   * Patches the command line parameters applying patchers from first to last, and then runs it.
   *
   * @param patchers any number of patchers; any patcher may be null, and the whole argument may be null.
   * @return handler of the started process
   * @throws ExecutionException
   */
  protected ProcessHandler startProcess(CommandLinePatcher... patchers) throws ExecutionException {
    GeneralCommandLine commandLine = generateCommandLine(patchers);

    // Extend command line
    PythonRunConfigurationExtensionsManager.getInstance()
                                           .patchCommandLine(myConfig,
                                                             getRunnerSettings(),
                                                             commandLine,
                                                             getEnvironment().getRunner().getRunnerId());
    final ProcessHandler processHandler;
    EncodingEnvironmentUtil.setLocaleEnvironmentIfMac(commandLine);
    processHandler = doCreateProcess(commandLine);
    ProcessTerminatedListener.attach(processHandler);

    // attach extensions
    PythonRunConfigurationExtensionsManager.getInstance().attachExtensionsToProcess(myConfig, processHandler, getRunnerSettings());

    return processHandler;
  }

  public GeneralCommandLine generateCommandLine(CommandLinePatcher[] patchers) {
    GeneralCommandLine commandLine = generateCommandLine();
    if (patchers != null) {
      for (CommandLinePatcher patcher : patchers) {
        if (patcher != null) {
          patcher.patchCommandLine(commandLine);
        }
      }
    }
    return commandLine;
  }

  protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine) throws ExecutionException {
    return PythonProcessRunner.createProcess(commandLine);
  }

  public GeneralCommandLine generateCommandLine() {
    GeneralCommandLine commandLine = createPythonCommandLine(myConfig.getProject(), myConfig, isDebug());

    buildCommandLineParameters(commandLine);

    customizeEnvironmentVars(commandLine.getEnvironment(), myConfig.isPassParentEnvs());

    return commandLine;
  }

  @Nonnull
  public static GeneralCommandLine createPythonCommandLine(Project project, PythonRunParams config, boolean isDebug) {
    GeneralCommandLine commandLine = new GeneralCommandLine();

    commandLine.withCharset(EncodingProjectManager.getInstance(project).getDefaultCharset());

    createStandardGroups(commandLine);

    initEnvironment(project, commandLine, config, isDebug);

    setRunnerPath(project, commandLine, config);

    return commandLine;
  }

  /**
   * Creates a number of parameter groups in the command line:
   * GROUP_EXE_OPTIONS, GROUP_DEBUGGER, GROUP_SCRIPT.
   * These are necessary for command line patchers to work properly.
   *
   * @param commandLine
   */
  public static void createStandardGroups(GeneralCommandLine commandLine) {
    ParametersList params = commandLine.getParametersList();
    params.addParamsGroup(GROUP_EXE_OPTIONS);
    params.addParamsGroup(GROUP_DEBUGGER);
    params.addParamsGroup(GROUP_PROFILER);
    params.addParamsGroup(GROUP_COVERAGE);
    params.addParamsGroup(GROUP_SCRIPT);
  }

  protected static void initEnvironment(Project project, GeneralCommandLine commandLine, PythonRunParams myConfig, boolean isDebug) {
    Map<String, String> env = Maps.newHashMap();

    setupEncodingEnvs(env, commandLine.getCharset());

    if (myConfig.getEnvs() != null) {
      env.putAll(myConfig.getEnvs());
    }

    addCommonEnvironmentVariables(getInterpreterPath(project, myConfig), env);

    setupVirtualEnvVariables(env, myConfig.getSdkHome());

    commandLine.getEnvironment().clear();
    commandLine.getEnvironment().putAll(env);
    commandLine.withParentEnvironmentType(myConfig.isPassParentEnvs() ? ParentEnvironmentType.CONSOLE : ParentEnvironmentType.NONE);


    buildPythonPath(project, commandLine, myConfig, isDebug);
  }

  private static void setupVirtualEnvVariables(Map<String, String> env, String sdkHome) {
    if (PythonSdkType.isVirtualEnv(sdkHome)) {
      PyVirtualEnvReader reader = new PyVirtualEnvReader(sdkHome);
      if (reader.getActivate() != null) {
        try {
          env.putAll(reader.readPythonEnv());
        }
        catch (Exception e) {
          LOG.error("Couldn't read virtualenv variables", e);
        }
      }
    }
  }

  protected static void addCommonEnvironmentVariables(@Nullable String homePath, Map<String, String> env) {
    PythonEnvUtil.setPythonUnbuffered(env);
    if (homePath != null) {
      PythonEnvUtil.resetHomePathChanges(homePath, env);
    }
    env.put("PYCHARM_HOSTED", "1");
  }

  public void customizeEnvironmentVars(Map<String, String> envs, boolean passParentEnvs) {
  }

  private static void setupEncodingEnvs(Map<String, String> envs, Charset charset) {
    PythonSdkFlavor.setupEncodingEnvs(envs, charset);
  }

  private static void buildPythonPath(Project project, GeneralCommandLine commandLine, PythonRunParams config, boolean isDebug) {
    Sdk pythonSdk = PythonSdkType.findSdkByPath(config.getSdkHome());
    if (pythonSdk != null) {
      List<String> pathList = Lists.newArrayList(getAddedPaths(pythonSdk));
      pathList.addAll(collectPythonPath(project, config, isDebug));
      initPythonPath(commandLine, config.isPassParentEnvs(), pathList, config.getSdkHome());
    }
  }

  public static void initPythonPath(GeneralCommandLine commandLine,
                                    boolean passParentEnvs,
                                    List<String> pathList,
                                    final String interpreterPath) {
    final PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(interpreterPath);
    if (flavor != null) {
      flavor.initPythonPath(commandLine, pathList);
    }
    else {
      PythonSdkFlavor.initPythonPath(commandLine.getEnvironment(), passParentEnvs, pathList);
    }
  }

  public static List<String> getAddedPaths(Sdk pythonSdk) {
    List<String> pathList = new ArrayList<>();
    final SdkAdditionalData sdkAdditionalData = pythonSdk.getSdkAdditionalData();
    if (sdkAdditionalData instanceof PythonSdkAdditionalData) {
      final Set<VirtualFile> addedPaths = ((PythonSdkAdditionalData)sdkAdditionalData).getAddedPathFiles();
      for (VirtualFile file : addedPaths) {
        addToPythonPath(file, pathList);
      }
    }
    return pathList;
  }

  private static void addToPythonPath(VirtualFile file, Collection<String> pathList) {
    if (file.getFileSystem() instanceof ArchiveFileSystem) {
      final VirtualFile realFile = ArchiveVfsUtil.getVirtualFileForJar(file);
      if (realFile != null) {
        addIfNeeded(realFile, pathList);
      }
    }
    else {
      addIfNeeded(file, pathList);
    }
  }

  private static void addIfNeeded(@Nonnull final VirtualFile file, @Nonnull final Collection<String> pathList) {
    addIfNeeded(pathList, file.getPath());
  }

  protected static void addIfNeeded(Collection<String> pathList, String path) {
    final Set<String> vals = Sets.newHashSet(pathList);
    final String filePath = FileUtil.toSystemDependentName(path);
    if (!vals.contains(filePath)) {
      pathList.add(filePath);
    }
  }

  protected static Collection<String> collectPythonPath(Project project, PythonRunParams config, boolean isDebug) {
    final Module module = getModule(project, config);
    final HashSet<String> pythonPath =
      Sets.newHashSet(collectPythonPath(module, config.shouldAddContentRoots(), config.shouldAddSourceRoots()));

		/*if(isDebug && PythonSdkFlavor.getFlavor(config.getSdkHome()) instanceof JythonSdkFlavor)
    {
			//that fixes Jython problem changing sys.argv on execfile, see PY-8164
			pythonPath.add(PythonHelpersLocator.getHelperPath("pycharm"));
			pythonPath.add(PythonHelpersLocator.getHelperPath("pydev"));
		}  */

    return pythonPath;
  }

  @Nullable
  private static Module getModule(Project project, PythonRunParams config) {
    String name = config.getModuleName();
    return StringUtil.isEmpty(name) ? null : ModuleManager.getInstance(project).findModuleByName(name);
  }

  @Nonnull
  public static Collection<String> collectPythonPath(@Nullable Module module) {
    return collectPythonPath(module, true, true);
  }

  @Nonnull
  public static Collection<String> collectPythonPath(@Nullable Module module, boolean addContentRoots, boolean addSourceRoots) {
    Collection<String> pythonPathList = Sets.newLinkedHashSet();
    if (module != null) {
      Set<Module> dependencies = new HashSet<>();
      ModuleUtilCore.getDependencies(module, dependencies);

      if (addContentRoots) {
        addRoots(pythonPathList, ModuleRootManager.getInstance(module).getContentRoots());
        for (Module dependency : dependencies) {
          addRoots(pythonPathList, ModuleRootManager.getInstance(dependency).getContentRoots());
        }
      }
      if (addSourceRoots) {
        addRoots(pythonPathList, ModuleRootManager.getInstance(module).getSourceRoots());
        for (Module dependency : dependencies) {
          addRoots(pythonPathList, ModuleRootManager.getInstance(dependency).getSourceRoots());
        }
      }

      addLibrariesFromModule(module, pythonPathList);
      addRootsFromModule(module, pythonPathList);
      for (Module dependency : dependencies) {
        addLibrariesFromModule(dependency, pythonPathList);
        addRootsFromModule(dependency, pythonPathList);
      }
    }
    return pythonPathList;
  }

  private static void addLibrariesFromModule(Module module, Collection<String> list) {
    final OrderEntry[] entries = ModuleRootManager.getInstance(module).getOrderEntries();
    for (OrderEntry entry : entries) {
      if (entry instanceof LibraryOrderEntry) {
        final String name = ((LibraryOrderEntry)entry).getLibraryName();
        if (name != null && name.endsWith(LibraryContributingFacet.PYTHON_FACET_LIBRARY_NAME_SUFFIX)) {
          // skip libraries from Python facet
          continue;
        }
        for (VirtualFile root : entry.getFiles(BinariesOrderRootType.getInstance())) {
          final Library library = ((LibraryOrderEntry)entry).getLibrary();
          addToPythonPath(root, list);

        }
      }
    }
  }

  private static void addRootsFromModule(Module module, Collection<String> pythonPathList) {

    // for Jython
    final ModuleCompilerPathsManager extension = ModuleCompilerPathsManager.getInstance(module);
    final VirtualFile path = extension.getCompilerOutput(ProductionContentFolderTypeProvider.getInstance());
    if (path != null) {
      pythonPathList.add(path.getPath());
    }
    final VirtualFile pathForTests = extension.getCompilerOutput(TestContentFolderTypeProvider.getInstance());
    if (pathForTests != null) {
      pythonPathList.add(pathForTests.getPath());
    }

    List<ModuleExtension> extensions = ModuleRootManager.getInstance(module).getExtensions();
    for (ModuleExtension moduleExtension : extensions) {
      if (moduleExtension instanceof PythonPathContributingFacet) {
        List<String> more_paths = ((PythonPathContributingFacet)moduleExtension).getAdditionalPythonPath();
        if (more_paths != null) {
          pythonPathList.addAll(more_paths);
        }
      }
    }
  }

  private static void addRoots(Collection<String> pythonPathList, VirtualFile[] roots) {
    for (VirtualFile root : roots) {
      addToPythonPath(root, pythonPathList);
    }
  }

  protected static void setRunnerPath(Project project, GeneralCommandLine commandLine, PythonRunParams config) {
    String interpreterPath = getInterpreterPath(project, config);
    if (StringUtil.isNotEmpty(interpreterPath)) {
      commandLine.setExePath(FileUtil.toSystemDependentName(interpreterPath));
    }
  }

  @Nullable
  public static String getInterpreterPath(Project project, PythonRunParams config) {
    String sdkHome = config.getSdkHome();
    if (config.isUseModuleSdk() || StringUtil.isEmpty(sdkHome)) {
      Module module = getModule(project, config);

      Sdk sdk = PythonSdkType.findPythonSdk(module);

      if (sdk != null) {
        sdkHome = sdk.getHomePath();
      }
    }

    return sdkHome;
  }

  protected String getInterpreterPath() throws ExecutionException {
    String interpreterPath = myConfig.getInterpreterPath();
    if (interpreterPath == null) {
      throw new ExecutionException("Cannot find Python interpreter for this run configuration");
    }
    return interpreterPath;
  }

  protected void buildCommandLineParameters(GeneralCommandLine commandLine) {
  }

  public boolean isMultiprocessDebug() {
    if (myMultiprocessDebug != null) {
      return myMultiprocessDebug;
    }
    else {
      return PyDebuggerOptionsProvider.getInstance(myConfig.getProject()).isAttachToSubprocess();
    }
  }

  public void setMultiprocessDebug(boolean multiprocessDebug) {
    myMultiprocessDebug = multiprocessDebug;
  }

  @Nonnull
  protected UrlFilter createUrlFilter(ProcessHandler handler) {
    return new UrlFilter();
  }
}
