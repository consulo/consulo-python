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
import com.jetbrains.python.impl.packaging.PyCondaPackageManagerImpl;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.search.PyProjectScopeBuilder;
import com.jetbrains.python.impl.sdk.flavors.CPythonSdkFlavor;
import com.jetbrains.python.impl.sdk.flavors.PythonSdkFlavor;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
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
import consulo.process.cmd.GeneralCommandLine;
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
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import org.jdom.Element;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Class should be final and singleton since some code checks its instance by ref.
 *
 * @author yole
 */
@ExtensionImpl
public final class PythonSdkType extends SdkType {
    public static final String REMOTE_SOURCES_DIR_NAME = "remote_sources";
    private static final Logger LOG = Logger.getInstance(PythonSdkType.class);
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

    private static final Key<WeakReference<Component>> SDK_CREATOR_COMPONENT_KEY = Key.create("#com.jetbrains.python.sdk.creatorComponent");
    public static final Predicate<Sdk> REMOTE_SDK_PREDICATE = sdk -> isRemote(sdk);

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
    @Nonnull
    public static String getBuiltinsFileName(@Nonnull Sdk sdk) {
        LanguageLevel level = getLanguageLevelForSdk(sdk);
        return level.isOlderThan(LanguageLevel.PYTHON30) ? PyBuiltinCache.BUILTIN_FILE : PyBuiltinCache.BUILTIN_FILE_3K;
    }

    @Nullable
    public String suggestHomePath() {
        String pythonFromPath = findPythonInPath();
        if (pythonFromPath != null) {
            return pythonFromPath;
        }
        for (PythonSdkFlavor flavor : PythonSdkFlavor.getApplicableFlavors()) {
            TreeSet<String> candidates = createVersionSet();
            candidates.addAll(flavor.suggestHomePaths());
            if (!candidates.isEmpty()) {
                // return latest version
                String[] candidateArray = ArrayUtil.toStringArray(candidates);
                return candidateArray[candidateArray.length - 1];
            }
        }
        return null;
    }

    @Nullable
    private static String findPythonInPath() {
        String defaultCommand = Platform.current().os().isWindows() ? "python.exe" : "python";
        String path = System.getenv("PATH");
        for (String root : path.split(File.pathSeparator)) {
            File file = new File(root, defaultCommand);
            if (file.exists()) {
                try {
                    return file.getCanonicalPath();
                }
                catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    @Nonnull
    @Override
    public Collection<String> suggestHomePaths() {
        List<String> candidates = new ArrayList<>();
        for (PythonSdkFlavor flavor : PythonSdkFlavor.getApplicableFlavors()) {
            candidates.addAll(flavor.suggestHomePaths());
        }
        return candidates;
    }

    private static TreeSet<String> createVersionSet() {
        return new TreeSet<>((o1, o2) -> findDigits(o1).compareTo(findDigits(o2)));
    }

    private static String findDigits(String s) {
        int pos = StringUtil.findFirst(s, Character::isDigit);
        if (pos >= 0) {
            return s.substring(pos);
        }
        return s;
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
    public boolean isValidSdkHome(@Nullable String path) {
        return PythonSdkFlavor.getFlavor(path) != null;
    }

    public static boolean isInvalid(@Nonnull Sdk sdk) {
        VirtualFile interpreter = sdk.getHomeDirectory();
        return interpreter == null || !interpreter.exists();
    }

    @Deprecated
    public static boolean isRemote(@Nullable Sdk sdk) {
        return false;
    }

    @Deprecated
    public static boolean isVagrant(@Nullable Sdk sdk) {
        return false;
    }

    public static boolean isRemote(@Nullable String sdkPath) {
        return isRemote(findSdkByPath(sdkPath));
    }

    @Nonnull
    @Override
    public FileChooserDescriptor getHomeChooserDescriptor() {
        boolean isWindows = Platform.current().os().isWindows();
        return new FileChooserDescriptor(true, false, false, false, false, false) {
            @Override
            public void validateSelectedFiles(VirtualFile[] files) throws Exception {
                if (files.length != 0) {
                    if (!isValidSdkHome(files[0].getPath())) {
                        throw new Exception(PyLocalize.sdkErrorInvalidInterpreterName$0(files[0].getName()).get());
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
        }.withTitleValue(PyLocalize.sdkSelectPath()).withShowHiddenFiles(Platform.current().os().isUnix());
    }

    public boolean supportsCustomCreateUI() {
        return true;
    }

    public void showCustomCreateUI(
        @Nonnull SdkModel sdkModel,
        @Nonnull JComponent parentComponent,
        @Nonnull Consumer<Sdk> sdkCreatedCallback
    ) {
        Project project = DataManager.getInstance().getDataContext(parentComponent).getData(Project.KEY);
        PointerInfo pointerInfo = MouseInfo.getPointerInfo();
        if (pointerInfo == null) {
            return;
        }
        Point point = pointerInfo.getLocation();
        PythonSdkDetailsStep.show(
            project,
            sdkModel.getSdks(),
            null,
            parentComponent,
            point,
            sdk -> {
                if (sdk != null) {
                    sdk.putUserData(SDK_CREATOR_COMPONENT_KEY, new WeakReference<>(parentComponent));
                    sdkCreatedCallback.accept(sdk);
                }
            }
        );
    }

    public static boolean isVirtualEnv(@Nonnull Sdk sdk) {
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
                    if (!isRemote(baseSdk)) {
                        PythonSdkFlavor baseFlavor = PythonSdkFlavor.getFlavor(baseSdk);
                        if (!isVirtualEnv(baseSdk) && flavor.equals(baseFlavor) && version.equals(getVersionString(baseSdk))) {
                            return baseSdk;
                        }
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
    public static File getVirtualEnvRoot(@Nonnull String binaryPath) {
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
    public String suggestSdkName(String currentSdkName, String sdkHome) {
        String name = getVersionString(sdkHome);
        return suggestSdkNameFromVersion(sdkHome, name);
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
        @Nonnull SdkModel sdkModel,
        @Nonnull SdkModificator sdkModificator
    ) {
        return null;
    }

    @Override
    public void saveAdditionalData(@Nonnull SdkAdditionalData additionalData, @Nonnull Element additional) {
        if (additionalData instanceof PythonSdkAdditionalData pythonSdkAdditionalData) {
            pythonSdkAdditionalData.save(additional);
        }
    }

    @Override
    public SdkAdditionalData loadAdditionalData(@Nonnull Sdk currentSdk, Element additional) {
        return PythonSdkAdditionalData.load(currentSdk, additional);
    }

    public static boolean isSkeletonsPath(String path) {
        return path.contains(SKELETON_DIR_NAME);
    }

    @Override
    public String sdkPath(@Nonnull VirtualFile homePath) {
        String path = super.sdkPath(homePath);
        PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(path);
        if (flavor != null) {
            VirtualFile sdkPath = flavor.getSdkPath(homePath);
            if (sdkPath != null) {
                return FileUtil.toSystemDependentName(sdkPath.getPath());
            }
        }
        return FileUtil.toSystemDependentName(path);
    }


    @Override
    public void setupSdkPaths(@Nonnull Sdk sdk) {
        PythonSdkUpdater.updateLocalSdkPaths(sdk, null, null);
    }

    @Nonnull
    public static VirtualFile getSdkRootVirtualFile(@Nonnull VirtualFile path) {
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
    public static String getSkeletonsPath(String basePath, String sdkHome) {
        String sep = File.separator;
        return getSkeletonsRootPath(basePath) + sep + FileUtil.toSystemIndependentName(sdkHome).hashCode() + sep;
    }

    public static String getSkeletonsRootPath(String basePath) {
        return basePath + File.separator + SKELETON_DIR_NAME;
    }

    @Nonnull
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

    @Nonnull
    public static List<String> getSysPathsFromScript(@Nonnull String binaryPath) throws InvalidSdkException {
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
    public static Map<String, String> getVirtualEnvExtraEnv(@Nonnull String binaryPath) {
        File root = getVirtualEnvRoot(binaryPath);
        if (root != null) {
            return Map.of("PATH", root.toString());
        }
        return null;
    }

    @Override
    @Nullable
    public String getVersionString(String sdkHome) {
        PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdkHome);
        return flavor != null ? flavor.getVersionString(sdkHome) : null;
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

    @Nonnull
    public static LanguageLevel getLanguageLevelForSdk(@Nullable Sdk sdk) {
        if (sdk != null && sdk.getSdkType() instanceof PythonSdkType) {
            PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(sdk);
            if (flavor != null) {
                return flavor.getLanguageLevel(sdk);
            }
        }
        return LanguageLevel.getDefault();
    }

    @Override
    public boolean isRootTypeApplicable(@Nonnull OrderRootType type) {
        return type == BinariesOrderRootType.getInstance();
    }

    @Override
    public boolean sdkHasValidPath(@Nonnull Sdk sdk) {
        if (PySdkUtil.isRemote(sdk)) {
            return true;
        }
        VirtualFile homeDir = sdk.getHomeDirectory();
        return homeDir != null && homeDir.isValid();
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
    public static Sdk findPython2Sdk(@Nonnull List<Sdk> sdks) {
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
        if (moduleSDK != null && !isRemote(moduleSDK) && PythonSdkFlavor.getFlavor(moduleSDK) instanceof CPythonSdkFlavor) {
            return moduleSDK;
        }
        for (Sdk sdk : ContainerUtil.sorted(getAllSdks(), PreferredSdkComparator.INSTANCE)) {
            if (!isRemote(sdk)) {
                return sdk;
            }
        }
        return null;
    }

    public static List<Sdk> getAllLocalCPythons() {
        return getAllSdks().stream().filter(REMOTE_SDK_PREDICATE.negate()).collect(Collectors.toList());
    }

    @Nullable
    public static String getPythonExecutable(@Nonnull String rootPath) {
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
    public static String getExecutablePath(@Nonnull String homeDirectory, @Nonnull String name) {
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
    public static Sdk getSdk(@Nonnull PsiElement element) {
        return ModuleUtilCore.getSdk(element, PyModuleExtension.class);
    }

    @Nonnull
    public static String getSdkKey(@Nonnull Sdk sdk) {
        return sdk.getName();
    }

    @Nullable
    public static Sdk findSdkByKey(@Nonnull String key) {
        return SdkTable.getInstance().findSdk(key);
    }
}
