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
package com.jetbrains.python.impl.sdk;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.impl.PythonHelpersLocator;
import com.jetbrains.python.impl.packaging.PyCondaPackageManagerImpl;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.search.PyProjectScopeBuilder;
import com.jetbrains.python.impl.sdk.flavors.CPythonSdkFlavor;
import com.jetbrains.python.impl.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.impl.sdk.flavors.VirtualEnvSdkFlavor;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.application.platform.PlatformTarget;
import consulo.application.platform.PlatformTargetManager;
import consulo.application.util.UserHomeFileUtil;
import consulo.content.OrderRootType;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.*;
import consulo.dataContext.DataManager;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.language.psi.PsiElement;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.platform.Platform;
import consulo.platform.PlatformOperatingSystem;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.util.CapturingProcessUtil;
import consulo.process.util.ProcessOutput;
import consulo.project.Project;
import consulo.project.ui.notification.NotificationGroup;
import consulo.python.impl.localize.PyLocalize;
import consulo.python.module.extension.PyModuleExtension;
import consulo.python.psi.icon.PythonPsiIconGroup;
import consulo.ui.image.Image;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;
import jakarta.inject.Inject;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Class should be final and singleton since some code checks its instance by ref.
 *
 * @author yole
 */
@ExtensionImpl
public final class PythonSdkType extends PlatformAwareSdkType {
    private static final Logger LOG = Logger.getInstance(PythonSdkType.class);
    private static final Pattern VERSION_PATTERN = Pattern.compile("((?:Python|PyPy) \\S+)");
    private static final Pattern VERSION_NUMBER_PATTERN = Pattern.compile("(\\d+\\.\\d+)");
    private static final String[] WINDOWS_EXECUTABLE_SUFFIXES = new String[]{
        "cmd",
        "exe",
        "bat",
        "com"
    };

    static final int MINUTE = 60 * 1000; // 60 seconds, used with script timeouts
    public static final NotificationGroup SKELETONS_TOPIC = NotificationGroup.balloonGroup("Python Skeletons");
    private static final String[] DIRS_WITH_BINARY = new String[]{
        "",
        "bin",
        "Scripts"
    };
    private static final String[] UNIX_BINARY_NAMES = new String[]{
        "jython",
        "pypy",
        "python"
    };
    private static final String[] WIN_BINARY_NAMES = new String[]{
        "jython.bat",
        "ipy.exe",
        "pypy.exe",
        "python.exe"
    };

    public static PythonSdkType getInstance() {
        return Application.get().getExtensionPoint(SdkType.class).findExtensionOrFail(PythonSdkType.class);
    }

    @Inject
    PythonSdkType() {
        super("Python SDK", PyLocalize.python(), PythonPsiIconGroup.python());
    }

    /**
     * Name of directory where skeleton files (despite the value) are stored.
     */
    public static final String SKELETON_DIR_NAME = "python_stubs";

    /**
     * @return name of builtins skeleton file; for Python 2.x it is '{@code __builtins__.py}'.
     */
    public static String getBuiltinsFileName(Sdk sdk) {
        LanguageLevel level = getLanguageLevelForSdk(sdk);
        return PyBuiltinCache.getBuiltinsFileName(level);
    }

    /**
     * The interpreters of the machine. The home of an SDK is the interpreter itself: {@code /usr/bin/python3}.
     * <p>
     * The local machine is asked through the flavors (they also know virtualenvs, pyenv, conda); another machine through
     * the usual directories and its {@code PATH}.
     */
    @Override
    public void collectHomePaths(Platform platform, Consumer<Path> pathConsumer) {
        if (platform.equals(Platform.current())) {
            for (PythonSdkFlavor flavor : PythonSdkFlavor.getApplicableFlavors()) {
                for (String home : flavor.suggestHomePaths()) {
                    pathConsumer.accept(Path.of(home));
                }
            }
            return;
        }

        Set<Path> directories = new LinkedHashSet<>();
        boolean windows = platform.os().isWindows();
        if (!windows) {
            for (String directory : new String[]{"/usr/bin", "/usr/local/bin", "/opt/homebrew/bin"}) {
                directories.add(platform.fs().getPath(directory));
            }
        }
        else {
            // the installers of python.org: C:\Python314, C:\Program Files\Python314, per user in AppData
            for (Path root : getWindowsInstallRoots(platform)) {
                try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
                    for (Path child : children) {
                        Path name = child.getFileName();
                        if (name != null && StringUtil.startsWithIgnoreCase(name.toString(), "Python")) {
                            directories.add(child);
                        }
                    }
                }
                catch (IOException | DirectoryIteratorException e) {
                    LOG.debug(e);
                }
            }
        }
        String path = platform.os().getEnvironmentVariable("PATH");
        if (path != null) {
            for (String entry : StringUtil.split(path, platform.fs().getPathListSeparator())) {
                // the Microsoft Store aliases are not interpreters - they open the store
                if (windows && StringUtil.containsIgnoreCase(entry, "\\WindowsApps")) {
                    continue;
                }
                directories.add(platform.fs().getPath(entry));
            }
        }
        // virtualenvs, pyenv versions and conda environments: one interpreter in each child
        for (Path root : getEnvironmentRoots(platform)) {
            try (DirectoryStream<Path> children = Files.newDirectoryStream(root)) {
                for (Path child : children) {
                    directories.add(windows ? child.resolve("Scripts") : child.resolve("bin"));
                    if (windows) {
                        directories.add(child);
                    }
                }
            }
            catch (IOException | DirectoryIteratorException e) {
                LOG.debug(e);
            }
        }
        // one home for one interpreter - python and python3, /bin and /usr/bin are often the same one
        Set<String> interpreters = new HashSet<>();
        for (Path directory : directories) {
            for (String name : windows ? new String[]{"python.exe"} : new String[]{"python3", "python"}) {
                Path interpreter = directory.resolve(name);
                if (Files.isRegularFile(interpreter) && interpreters.add(getRealExecutable(platform, interpreter))) {
                    pathConsumer.accept(interpreter);
                }
            }
        }
    }

    private static List<Path> getWindowsInstallRoots(Platform platform) {
        List<Path> roots = new ArrayList<>();
        String localAppData = platform.os().getEnvironmentVariable("LOCALAPPDATA");
        roots.add(StringUtil.isEmpty(localAppData)
            ? platform.user().homePath().resolve("AppData").resolve("Local").resolve("Programs").resolve("Python")
            : platform.fs().getPath(localAppData).resolve("Programs").resolve("Python"));
        for (String variable : new String[]{"ProgramFiles", "ProgramFiles(x86)", "SystemDrive"}) {
            String value = platform.os().getEnvironmentVariable(variable);
            if (!StringUtil.isEmpty(value)) {
                roots.add(platform.fs().getPath(variable.equals("SystemDrive") ? value + "\\" : value));
            }
        }
        List<Path> existing = new ArrayList<>();
        for (Path root : roots) {
            if (Files.isDirectory(root)) {
                existing.add(root);
            }
        }
        return existing;
    }

    /**
     * The directories whose children are environments - as {@link VirtualEnvSdkFlavor} looks for them on the local machine.
     */
    private static List<Path> getEnvironmentRoots(Platform platform) {
        List<Path> roots = new ArrayList<>();
        Path home = platform.user().homePath();
        String workOnHome = platform.os().getEnvironmentVariable("WORKON_HOME");
        roots.add(StringUtil.isEmpty(workOnHome) ? home.resolve(".virtualenvs") : platform.fs().getPath(workOnHome));
        String pyEnvRoot = platform.os().getEnvironmentVariable("PYENV_ROOT");
        roots.add((StringUtil.isEmpty(pyEnvRoot) ? home.resolve(".pyenv") : platform.fs().getPath(pyEnvRoot)).resolve("versions"));
        for (String condaRoot : VirtualEnvSdkFlavor.CONDA_DEFAULT_ROOTS) {
            roots.add(home.resolve(condaRoot).resolve("envs"));
        }
        if (!platform.os().isWindows()) {
            roots.add(platform.fs().getPath("/opt/anaconda/envs"));
        }
        List<Path> existing = new ArrayList<>();
        for (Path root : roots) {
            if (Files.isDirectory(root)) {
                existing.add(root);
            }
        }
        return existing;
    }

    /**
     * The interpreter behind the links with its environment - told by the interpreter itself, the file system of another
     * machine does not resolve links. A virtualenv links the interpreter it was made from, its prefix tells them apart.
     */
    private static String getRealExecutable(Platform platform, Path interpreter) {
        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.withPlatform(platform);
        commandLine.withExecutablePath(interpreter);
        commandLine.addParameters("-c", "import os, sys; print(os.path.realpath(sys.executable) + os.pathsep + sys.prefix)");
        try {
            ProcessOutput output = CapturingProcessUtil.execAndGetOutput(commandLine, MINUTE);
            List<String> lines = output.getStdoutLines();
            if (output.getExitCode() == 0 && !lines.isEmpty()) {
                return lines.get(0);
            }
        }
        catch (ExecutionException e) {
            LOG.debug(e);
        }
        return interpreter.toString();
    }

    /**
     * Every interpreter of another machine becomes an SDK, so a project there always has one - the local machine keeps
     * its interpreters for the user to add, it may have many virtualenvs.
     */
    @Override
    public boolean canCreatePredefinedSdks(Platform platform) {
        return !platform.equals(Platform.current());
    }

    public static boolean hasValidSdk() {
        for (Sdk sdk : SdkTable.getInstance().getAllSdks()) {
            if (sdk.getSdkType() instanceof PythonSdkType) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isValidSdkHome(Platform platform, Path path) {
        if (platform.equals(Platform.current())) {
            return isValidLocalSdkHome(path.toString());
        }
        String name = String.valueOf(path.getFileName()).toLowerCase(Locale.ROOT);
        return Files.isRegularFile(path) && (name.startsWith("python") || name.startsWith("pypy"));
    }

    /**
     * Whether the path is a home of an SDK of the local machine.
     */
    public static boolean isValidLocalSdkHome(@Nullable String path) {
        return PythonSdkFlavor.getFlavor(path) != null;
    }

    public static boolean isInvalid(Sdk sdk) {
        VirtualFile interpreter = sdk.getHomeDirectory();
        return interpreter == null || !interpreter.exists();
    }

    @Override
    public FileChooserDescriptor getHomeChooserDescriptor(Platform platform) {
        boolean isWindows = platform.os().isWindows();
        return new FileChooserDescriptor(true, false, false, false, false, false) {
            @Override
            public void validateSelectedPaths(List<Path> paths) throws Exception {
                if (!paths.isEmpty()) {
                    Path path = paths.get(0);
                    if (!isValidSdkHome(platformOf(path, platform), path)) {
                        throw new Exception(PyLocalize.sdkErrorInvalidInterpreterName$0(String.valueOf(path.getFileName())).get());
                    }
                }
            }

            @Override
            public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
                // TODO: add a better, customizable filtering
                if (!file.isDirectory()) {
                    if (isWindows) {
                        String path = file.getPath();
                        boolean looksExecutable = false;
                        for (String ext : WINDOWS_EXECUTABLE_SUFFIXES) {
                            if (path.endsWith(ext)) {
                                looksExecutable = true;
                                break;
                            }
                        }
                        return looksExecutable && super.isFileVisible(file, showHiddenFiles);
                    }
                }
                return super.isFileVisible(file, showHiddenFiles);
            }
        }.withTitle(PyLocalize.sdkSelectPath()).withShowHiddenFiles(!isWindows).withPlatform(platform);
    }

    /**
     * The machine a chosen path is on - the chooser may offer several of them.
     */
    private static Platform platformOf(Path path, Platform fallback) {
        PlatformTarget target = Application.get().getInstance(PlatformTargetManager.class).findTargetOf(path);
        Platform platform = target == null ? null : target.getPlatform();
        return platform == null ? fallback : platform;
    }

    public static boolean isVirtualEnv(Sdk sdk) {
        String path = sdk.getHomePath();
        return isVirtualEnv(path);
    }

    public static boolean isVirtualEnv(String path) {
        return path != null && getVirtualEnvRoot(path) != null;
    }

    public static boolean isCondaVirtualEnv(Sdk sdk) {
        String path = sdk.getHomePath();
        return path != null && PyCondaPackageManagerImpl.isCondaVEnv(sdk);
    }

    @Nullable
    public Sdk getVirtualEnvBaseSdk(Sdk sdk) {
        if (isVirtualEnv(sdk)) {
            PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
            String version = getVersionString(sdk);
            if (flavor != null && version != null) {
                for (Sdk baseSdk : getAllSdks()) {
                    PythonSdkFlavor baseFlavor = PythonSdkFlavor.getFlavor(baseSdk);
                    if (!isVirtualEnv(baseSdk) && flavor.equals(baseFlavor) && version.equals(getVersionString(baseSdk))) {
                        return baseSdk;
                    }
                }
            }
        }
        return null;
    }

    /**
     * @param binaryPath must point to a Python interpreter
     * @return if the surroundings look like a virtualenv installation, its root is returned (normally the grandparent of binaryPath).
     */
    @Nullable
    public static File getVirtualEnvRoot(String binaryPath) {
        File bin = new File(binaryPath).getParentFile();
        if (bin != null) {
            String rootPath = bin.getParent();
            if (rootPath != null) {
                File root = new File(rootPath);
                File activateThis = new File(bin, "activate_this.py");
                // binaryPath should contain an 'activate' script, and root should have bin (with us) and include and libp
                if (activateThis.exists()) {
                    File activate = findExecutableFile(bin, "activate");
                    if (activate != null) {
                        return root;
                    }
                }
                // Python 3.3 virtualenvs can be found as described in PEP 405
                String pyVenvCfg = "pyvenv.cfg";
                if (new File(root, pyVenvCfg).exists() || new File(bin, pyVenvCfg).exists()) {
                    return root;
                }
            }
        }
        return null;
    }

    /**
     * Finds a file that looks executable: an .exe or .cmd under windows, plain file under *nix.
     *
     * @param parent directory to look at
     * @param name   name of the executable without suffix
     * @return File representing the executable, or null.
     */
    @Nullable
    public static File findExecutableFile(File parent, String name) {
        PlatformOperatingSystem os = Platform.current().os();
        if (os.isWindows()) {
            for (String suffix : WINDOWS_EXECUTABLE_SUFFIXES) {
                File file = new File(parent, name + "." + suffix);
                if (file.exists()) {
                    return file;
                }
            }
        }
        else if (os.isUnix()) {
            File file = new File(parent, name);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    /**
     * Alters PATH so that a virtualenv is activated, if present.
     *
     * @param commandLine           what to patch
     * @param sdkHome               home of SDK we're using
     * @param passParentEnvironment iff true, include system paths in PATH
     */
    public static void patchCommandLineForVirtualenv(GeneralCommandLine commandLine, String sdkHome, boolean passParentEnvironment) {
        File virtualEnvRoot = getVirtualEnvRoot(sdkHome);
        if (virtualEnvRoot != null) {
            String PATH = "PATH";

            // prepend virtualenv bin if it's not already on PATH
            File bin = new File(virtualEnvRoot, "bin");
            if (!bin.exists()) {
                bin = new File(virtualEnvRoot, "Scripts");   // on Windows
            }
            String virtualenvBin = bin.getPath();

            Map<String, String> env = commandLine.getEnvironment();
            String pathValue;
            if (env.containsKey(PATH)) {
                pathValue = PythonEnvUtil.appendToPathEnvVar(env.get(PATH), virtualenvBin);
            }
            else if (passParentEnvironment) {
                // append to PATH
                pathValue = PythonEnvUtil.appendToPathEnvVar(System.getenv(PATH), virtualenvBin);
            }
            else {
                pathValue = virtualenvBin;
            }
            env.put(PATH, pathValue);
        }
    }

    @Override
    public String suggestSdkName(Platform platform, @Nullable String currentSdkName, Path path) {
        String version = getVersionString(platform, path);
        if (platform.equals(Platform.current())) {
            return suggestSdkNameFromVersion(path.toString(), version);
        }
        return (version == null ? "Unknown" : version) + " (" + path + ")";
    }

    private static String suggestSdkNameFromVersion(String sdkHome, String version) {
        sdkHome = FileUtil.toSystemDependentName(sdkHome);
        String shortHomeName = UserHomeFileUtil.getLocationRelativeToUserHome(sdkHome);
        if (version != null) {
            File virtualEnvRoot = getVirtualEnvRoot(sdkHome);
            if (virtualEnvRoot != null) {
                version += " virtualenv at " + UserHomeFileUtil.getLocationRelativeToUserHome(virtualEnvRoot.getAbsolutePath());
            }
            else {
                version += " (" + shortHomeName + ")";
            }
        }
        else {
            version = "Unknown at " + shortHomeName;
        } // last resort
        return version;
    }

    @Override
    @Nullable
    public AdditionalDataConfigurable createAdditionalDataConfigurable(
        SdkModel sdkModel,
        SdkModificator sdkModificator
    ) {
        return null;
    }

    @Override
    public void saveAdditionalData(SdkAdditionalData additionalData, Element additional) {
        if (additionalData instanceof PythonSdkAdditionalData pythonSdkAdditionalData) {
            pythonSdkAdditionalData.save(additional);
        }
    }

    @Override
    public SdkAdditionalData loadAdditionalData(Sdk currentSdk, Element additional) {
        return PythonSdkAdditionalData.load(currentSdk, additional);
    }

    public static boolean isSkeletonsPath(String path) {
        return path.contains(SKELETON_DIR_NAME);
    }


    @Override
    public void setupSdkPaths(Sdk sdk) {
        PythonSdkUpdater.updateLocalSdkPaths(sdk, null, null);
    }

    public static VirtualFile getSdkRootVirtualFile(VirtualFile path) {
        String suffix = path.getExtension();
        if (suffix != null) {
            suffix = suffix.toLowerCase(); // Why on earth empty suffix is null and not ""?
        }
        if ((!path.isDirectory()) && ("zip".equals(suffix) || "egg".equals(suffix))) {
            // a .zip / .egg file must have its root extracted first
            VirtualFile jar = ArchiveVfsUtil.getJarRootForLocalFile(path);
            if (jar != null) {
                return jar;
            }
        }
        return path;
    }

    /**
     * Returns skeletons location on the local machine. Independent of SDK credentials type (e.g. ssh, Vagrant, Docker or else).
     */
    /**
     * The skeletons of an SDK - an interpreter of another machine has its own, also when its path is a path of the local one.
     */
    public static String getSkeletonsPath(String basePath, Sdk sdk) {
        Platform platform = sdk.getPlatform();
        String sdkHome = StringUtil.notNullize(sdk.getHomePath());
        return getSkeletonsPath(basePath, platform.equals(Platform.current()) ? sdkHome : platform.getId() + "/" + sdkHome);
    }

    public static String getSkeletonsPath(String basePath, String sdkHome) {
        String sep = File.separator;
        return getSkeletonsRootPath(basePath) + sep + FileUtil.toSystemIndependentName(sdkHome).hashCode() + sep;
    }

    public static String getSkeletonsRootPath(String basePath) {
        return basePath + File.separator + SKELETON_DIR_NAME;
    }

    public static List<String> getSysPath(String bin_path) throws InvalidSdkException {
        String working_dir = new File(bin_path).getParent();
        Application application = ApplicationManager.getApplication();
        if (application != null && !application.isUnitTestMode()) {
            return getSysPathsFromScript(bin_path);
        }
        else { // mock sdk
            List<String> ret = new ArrayList<>(1);
            ret.add(working_dir);
            return ret;
        }
    }

    /**
     * The sys.path of the interpreter - asked on its machine.
     */
    public static List<String> getSysPath(Sdk sdk) throws InvalidSdkException {
        Platform platform = sdk.getPlatform();
        if (platform.equals(Platform.current())) {
            return getSysPath(sdk.getHomePath());
        }
        try {
            String script = PythonHelpersLocator.toPlatformPath(PythonHelper.SYSPATH.asParamString(), platform);
            GeneralCommandLine commandLine = new GeneralCommandLine();
            commandLine.withPlatform(platform);
            commandLine.withExecutablePath(sdk.getHomeNioPath());
            commandLine.addParameter(script);
            // the directory of the script - a '.' of PYTHONPATH must not add the directory of the interpreter
            commandLine.withWorkingDirectory(platform.fs().getPath(script).getParent());
            ProcessOutput output = CapturingProcessUtil.execAndGetOutput(commandLine, MINUTE);
            if (!output.checkSuccess(LOG)) {
                throw new InvalidSdkException(String.format(
                    "Failed to determine Python's sys.path value:\nSTDOUT: %s\nSTDERR: %s",
                    output.getStdout(),
                    output.getStderr()
                ));
            }
            return output.getStdoutLines();
        }
        catch (IOException | ExecutionException e) {
            throw new InvalidSdkException("Failed to determine Python's sys.path value of " + sdk.getHomePath(), e);
        }
    }

    public static List<String> getSysPathsFromScript(String binaryPath) throws InvalidSdkException {
        // to handle the situation when PYTHONPATH contains ., we need to run the syspath script in the
        // directory of the script itself - otherwise the dir in which we run the script (e.g. /usr/bin) will be added to SDK path
        GeneralCommandLine cmd = PythonHelper.SYSPATH.newCommandLine(binaryPath, new ArrayList<>());
        ProcessOutput runResult =
            PySdkUtil.getProcessOutput(cmd, new File(binaryPath).getParent(), getVirtualEnvExtraEnv(binaryPath), MINUTE);
        if (!runResult.checkSuccess(LOG)) {
            throw new InvalidSdkException(String.format(
                "Failed to determine Python's sys.path value:\nSTDOUT: %s\nSTDERR: %s",
                runResult.getStdout(),
                runResult.getStderr()
            ));
        }
        return runResult.getStdoutLines();
    }

    /**
     * Returns a piece of env good as additional env for getProcessOutput.
     */
    @Nullable
    public static Map<String, String> getVirtualEnvExtraEnv(String binaryPath) {
        File root = getVirtualEnvRoot(binaryPath);
        if (root != null) {
            return Map.of("PATH", root.toString());
        }
        return null;
    }

    /**
     * {@code Python 3.12.3} - of {@code python -V}, run on the machine of the interpreter.
     */
    @Override
    @Nullable
    public String getVersionString(Platform platform, Path path) {
        if (platform.equals(Platform.current())) {
            String sdkHome = path.toString();
            PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdkHome);
            return flavor != null ? flavor.getVersionString(sdkHome) : null;
        }

        GeneralCommandLine commandLine = new GeneralCommandLine();
        commandLine.withPlatform(platform);
        commandLine.withExecutablePath(path);
        commandLine.addParameter("-V");
        try {
            ProcessOutput output = CapturingProcessUtil.execAndGetOutput(commandLine);
            // python 2 prints its version to stderr
            for (String line : ContainerUtil.concat(output.getStdoutLines(), output.getStderrLines())) {
                Matcher matcher = VERSION_PATTERN.matcher(line);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        catch (ExecutionException e) {
            LOG.warn("Cannot ask the version of " + path, e);
        }
        return null;
    }

    public static List<Sdk> getAllSdks() {
        return SdkTable.getInstance().getSdksOfType(getInstance());
    }

    @Nullable
    public static Sdk findPythonSdk(@Nullable Module module) {
        if (module == null) {
            return null;
        }
        return ModuleUtilCore.getSdk(module, PyModuleExtension.class);
    }

    @Nullable
    public static Sdk findSdkByPath(@Nullable String path) {
        if (path != null) {
            return findSdkByPath(getAllSdks(), path);
        }
        return null;
    }

    @Nullable
    public static Sdk findSdkByPath(List<Sdk> sdkList, @Nullable String path) {
        if (path != null) {
            for (Sdk sdk : sdkList) {
                if (sdk != null && FileUtil.pathsEqual(path, sdk.getHomePath())) {
                    return sdk;
                }
            }
        }
        return null;
    }

    public static LanguageLevel getLanguageLevelForSdk(@Nullable Sdk sdk) {
        if (sdk != null && sdk.getSdkType() instanceof PythonSdkType) {
            PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
            if (flavor != null) {
                return flavor.getLanguageLevel(sdk);
            }
            // an interpreter of another machine has no flavor of this one - its version tells the level
            String versionString = sdk.getVersionString();
            if (versionString != null) {
                Matcher matcher = VERSION_NUMBER_PATTERN.matcher(versionString);
                if (matcher.find()) {
                    return LanguageLevel.fromPythonVersion(matcher.group(1));
                }
            }
        }
        return LanguageLevel.getDefault();
    }

    @Override
    public boolean isRootTypeApplicable(String type) {
        return BinariesOrderRootType.ID.equals(type);
    }

    @Override
    public boolean sdkHasValidPath(Sdk sdk) {
        VirtualFile homeDir = sdk.getHomeDirectory();
        return homeDir != null && homeDir.isValid();
    }

    public static boolean isRemote(@Nullable Sdk sdk) {
        return sdk != null && sdk.getSdkAdditionalData() instanceof PyRemoteSdkAdditionalDataMarker;
    }

    public static boolean isStdLib(VirtualFile vFile, Sdk pythonSdk) {
        if (pythonSdk != null) {
            VirtualFile libDir = PyProjectScopeBuilder.findLibDir(pythonSdk);
            if (libDir != null && VirtualFileUtil.isAncestor(libDir, vFile, false)) {
                return isNotSitePackages(vFile, libDir);
            }
            VirtualFile venvLibDir = PyProjectScopeBuilder.findVirtualEnvLibDir(pythonSdk);
            if (venvLibDir != null && VirtualFileUtil.isAncestor(venvLibDir, vFile, false)) {
                return isNotSitePackages(vFile, venvLibDir);
            }
            VirtualFile skeletonsDir = PySdkUtil.findSkeletonsDir(pythonSdk);
            if (skeletonsDir != null && Comparing.equal(vFile.getParent(), skeletonsDir)) {
                // note: this will pick up some of the binary libraries not in packages
                return true;
            }
        }
        return false;
    }

    private static boolean isNotSitePackages(VirtualFile vFile, VirtualFile libDir) {
        VirtualFile sitePackages = libDir.findChild(PyNames.SITE_PACKAGES);
        return sitePackages == null || !VirtualFileUtil.isAncestor(sitePackages, vFile, false);
    }

    @Nullable
    public static Sdk findPython2Sdk(@Nullable Module module) {
        Sdk moduleSDK = findPythonSdk(module);
        if (moduleSDK != null && !getLanguageLevelForSdk(moduleSDK).isPy3K()) {
            return moduleSDK;
        }
        return findPython2Sdk(getAllSdks());
    }

    @Nullable
    public static Sdk findPython2Sdk(List<Sdk> sdks) {
        for (Sdk sdk : ContainerUtil.sorted(sdks, PreferredSdkComparator.INSTANCE)) {
            if (!getLanguageLevelForSdk(sdk).isPy3K()) {
                return sdk;
            }
        }
        return null;
    }

    @Nullable
    public static Sdk findLocalCPython(@Nullable Module module) {
        Sdk moduleSDK = findPythonSdk(module);
        if (moduleSDK != null && PythonSdkFlavor.getFlavor(moduleSDK) instanceof CPythonSdkFlavor) {
            return moduleSDK;
        }
        return ContainerUtil.getFirstItem(ContainerUtil.sorted(getAllSdks(), PreferredSdkComparator.INSTANCE));
    }

    public static List<Sdk> getAllLocalCPythons() {
        return getAllSdks();
    }

    @Nullable
    public static String getPythonExecutable(String rootPath) {
        File rootFile = new File(rootPath);
        if (rootFile.isFile()) {
            return rootFile.getAbsolutePath();
        }
        for (String dir : DIRS_WITH_BINARY) {
            File subDir;
            if (StringUtil.isEmpty(dir)) {
                subDir = rootFile;
            }
            else {
                subDir = new File(rootFile, dir);
            }
            if (!subDir.isDirectory()) {
                continue;
            }
            for (String binaryName : getBinaryNames()) {
                File executable = new File(subDir, binaryName);
                if (executable.isFile()) {
                    return executable.getAbsolutePath();
                }
            }
        }
        return null;
    }

    @Nullable
    public static String getExecutablePath(String homeDirectory, String name) {
        File binPath = new File(homeDirectory);
        File binDir = binPath.getParentFile();
        if (binDir == null) {
            return null;
        }
        File runner = new File(binDir, name);
        if (runner.exists()) {
            return LocalFileSystem.getInstance().extractPresentableUrl(runner.getPath());
        }
        runner = new File(new File(binDir, "Scripts"), name);
        if (runner.exists()) {
            return LocalFileSystem.getInstance().extractPresentableUrl(runner.getPath());
        }
        runner = new File(new File(binDir.getParentFile(), "Scripts"), name);
        if (runner.exists()) {
            return LocalFileSystem.getInstance().extractPresentableUrl(runner.getPath());
        }
        runner = new File(new File(binDir.getParentFile(), "local"), name);
        if (runner.exists()) {
            return LocalFileSystem.getInstance().extractPresentableUrl(runner.getPath());
        }
        runner = new File(new File(new File(binDir.getParentFile(), "local"), "bin"), name);
        if (runner.exists()) {
            return LocalFileSystem.getInstance().extractPresentableUrl(runner.getPath());
        }

        // if interpreter is a symlink
        Path homePath = Path.of(homeDirectory);
        if (Files.isSymbolicLink(homePath)) {
            String resolvedPath = null;
            try {
                resolvedPath = Files.readSymbolicLink(homePath).toString();
            }
            catch (IOException ignored) {
            }

            if (resolvedPath != null) {
                return getExecutablePath(resolvedPath, name);
            }
        }
        // Search in standard unix path
        runner = new File(new File("/usr", "bin"), name);
        if (runner.exists()) {
            return LocalFileSystem.getInstance().extractPresentableUrl(runner.getPath());
        }
        runner = new File(new File(new File("/usr", "local"), "bin"), name);
        if (runner.exists()) {
            return LocalFileSystem.getInstance().extractPresentableUrl(runner.getPath());
        }
        return null;
    }

    private static String[] getBinaryNames() {
        if (Platform.current().os().isUnix()) {
            return UNIX_BINARY_NAMES;
        }
        else {
            return WIN_BINARY_NAMES;
        }
    }

    @Nullable
    @RequiredReadAction
    public static Sdk getSdk(PsiElement element) {
        return ModuleUtilCore.getSdk(element, PyModuleExtension.class);
    }

    public static String getSdkKey(Sdk sdk) {
        return sdk.getName();
    }

    @Nullable
    public static Sdk findSdkByKey(String key) {
        return SdkTable.getInstance().findSdk(key);
    }
}
