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
package com.jetbrains.python.impl.sdk.skeletons;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PyBundle;
import com.jetbrains.python.impl.codeInsight.userSkeletons.PyUserSkeletonsUtil;
import com.jetbrains.python.impl.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.impl.sdk.InvalidSdkException;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.application.ApplicationManager;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.util.SystemInfo;
import consulo.application.util.UserHomeFileUtil;
import consulo.container.boot.ContainerPathManager;
import consulo.content.base.BinariesOrderRootType;
import consulo.content.bundle.Sdk;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.python.buildout.module.extension.BuildoutModuleExtension;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import consulo.util.io.FilePermissionCopier;
import consulo.util.io.FileUtil;
import consulo.util.io.zip.ZipUtil;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.archive.ArchiveVfsUtil;
import org.jetbrains.annotations.NonNls;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.impl.sdk.skeletons.SkeletonVersionChecker.fromVersionString;

/**
 * Handles a refresh of SDK's skeletons.
 * Does all the heavy lifting calling skeleton generator, managing blacklists, etc.
 * One-time, non-reusable instances.
 * <br/>
 * User: dcheryasov
 * Date: 4/15/11 5:38 PM
 */
public class PySkeletonRefresher {
    private static final Logger LOG = Logger.getInstance("#" + PySkeletonRefresher.class.getName());

    @Nullable
    private Project myProject;
    private
    @Nullable
    final ProgressIndicator myIndicator;
    @Nonnull
    private final Sdk mySdk;
    private String mySkeletonsPath;

    @NonNls
    public static final String BLACKLIST_FILE_NAME = ".blacklist";
    private final static Pattern BLACKLIST_LINE = Pattern.compile("^([^=]+) = (\\d+\\.\\d+) (\\d+)\\s*$");
    // we use the equals sign after filename so that we can freely include space in the filename

    // Path (the first component) may contain spaces, this header spec is deprecated
    private static final Pattern VERSION_LINE_V1 = Pattern.compile("# from (\\S+) by generator (\\S+)\\s*");

    // Skeleton header spec v2
    private static final Pattern FROM_LINE_V2 = Pattern.compile("# from (.*)$");
    private static final Pattern BY_LINE_V2 = Pattern.compile("# by generator (.*)$");

    private static int ourGeneratingCount = 0;

    private String myExtraSyspath;
    private VirtualFile myPregeneratedSkeletons;
    private int myGeneratorVersion;
    private Map<String, Pair<Integer, Long>> myBlacklist;
    private SkeletonVersionChecker myVersionChecker;

    private PySkeletonGenerator mySkeletonsGenerator;

    public static synchronized boolean isGeneratingSkeletons() {
        return ourGeneratingCount > 0;
    }

    private static synchronized void changeGeneratingSkeletons(int increment) {
        ourGeneratingCount += increment;
    }

    public static void refreshSkeletonsOfSdk(
        @Nullable Project project,
        Component ownerComponent,
        String skeletonsPath,
        @Nonnull Sdk sdk
    ) throws InvalidSdkException {
        final Map<String, List<String>> errors = new TreeMap<>();
        final List<String> failedSdks = new SmartList<>();
        final ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        final String homePath = sdk.getHomePath();
        if (skeletonsPath == null) {
            LOG.info("Could not find skeletons path for SDK path " + homePath);
        }
        else {
            LOG.info("Refreshing skeletons for " + homePath);
            SkeletonVersionChecker checker = new SkeletonVersionChecker(0); // this default version won't be used
            final PySkeletonRefresher refresher = new PySkeletonRefresher(project, ownerComponent, sdk, skeletonsPath, indicator, null);

            changeGeneratingSkeletons(1);
            try {
                List<String> sdkErrors = refresher.regenerateSkeletons(checker);
                if (sdkErrors.size() > 0) {
                    String sdkName = sdk.getName();
                    List<String> knownErrors = errors.get(sdkName);
                    if (knownErrors == null) {
                        errors.put(sdkName, sdkErrors);
                    }
                    else {
                        knownErrors.addAll(sdkErrors);
                    }
                }
            }
            finally {
                changeGeneratingSkeletons(-1);
            }
        }
        if (failedSdks.size() > 0 || errors.size() > 0) {
            int module_errors = 0;
            for (String sdk_name : errors.keySet()) {
                module_errors += errors.get(sdk_name).size();
            }
            String message;
            if (failedSdks.size() > 0) {
                message =
                    PyBundle.message("sdk.errorlog.$0.mods.fail.in.$1.sdks.$2.completely", module_errors, errors.size(), failedSdks.size());
            }
            else {
                message = PyBundle.message("sdk.errorlog.$0.mods.fail.in.$1.sdks", module_errors, errors.size());
            }
            logErrors(errors, failedSdks, message);
        }
    }

    private static void logErrors(
        @Nonnull final Map<String, List<String>> errors,
        @Nonnull final List<String> failedSdks,
        @Nonnull final String message
    ) {
        LOG.warn(PyBundle.message("sdk.some.skeletons.failed"));
        LOG.warn(message);

        if (failedSdks.size() > 0) {
            LOG.warn(PyBundle.message("sdk.error.dialog.failed.sdks"));
            LOG.warn(StringUtil.join(failedSdks, ", "));
        }

        if (errors.size() > 0) {
            LOG.warn(PyBundle.message("sdk.error.dialog.failed.modules"));
            for (String sdkName : errors.keySet()) {
                for (String moduleName : errors.get(sdkName)) {
                    LOG.warn(moduleName);
                }
            }
        }
    }

    /**
     * Creates a new object that refreshes skeletons of given SDK.
     *
     * @param sdk           a Python SDK
     * @param skeletonsPath if known; null means 'determine and create as needed'.
     * @param indicator     to report progress of long operations
     */
    public PySkeletonRefresher(
        @Nullable Project project,
        @Nullable Component ownerComponent,
        @Nonnull Sdk sdk,
        @Nullable String skeletonsPath,
        @Nullable ProgressIndicator indicator,
        @Nullable String folder
    ) throws InvalidSdkException {
        myProject = project;
        myIndicator = indicator;
        mySdk = sdk;
        mySkeletonsPath = skeletonsPath;
        mySkeletonsGenerator = new PySkeletonGenerator(getSkeletonsPath(), mySdk, folder);
    }

    private void indicate(String msg) {
        if (myIndicator != null) {
            myIndicator.checkCanceled();
            myIndicator.setText(msg);
            myIndicator.setText2("");
        }
    }

    private void indicateMinor(String msg) {
        if (myIndicator != null) {
            myIndicator.setText2(msg);
        }
    }

    private void checkCanceled() {
        if (myIndicator != null) {
            myIndicator.checkCanceled();
        }
    }

    private static String calculateExtraSysPath(@Nonnull final Sdk sdk, @Nullable final String skeletonsPath) {
        final File skeletons = skeletonsPath != null ? new File(skeletonsPath) : null;

        final VirtualFile userSkeletonsDir = PyUserSkeletonsUtil.getUserSkeletonsDirectory();
        final File userSkeletons = userSkeletonsDir != null ? new File(userSkeletonsDir.getPath()) : null;

        final VirtualFile remoteSourcesDir = PySdkUtil.findAnyRemoteLibrary(sdk);
        final File remoteSources = remoteSourcesDir != null ? new File(remoteSourcesDir.getPath()) : null;

        final List<VirtualFile> paths = new ArrayList<>();

        paths.addAll(Arrays.asList(sdk.getRootProvider().getFiles(BinariesOrderRootType.getInstance())));
        paths.addAll(BuildoutModuleExtension.getExtraPathForAllOpenModules());

        return Joiner.on(File.pathSeparator).join(ContainerUtil.mapNotNull(paths, (Function<VirtualFile, Object>)file -> {
            if (file.isInLocalFileSystem()) {
                // We compare canonical files, not strings because "c:/some/folder" equals "c:\\some\\bin\\..\\folder\\"
                final File canonicalFile = new File(file.getPath());
                if (canonicalFile.exists() &&
                    !FileUtil.filesEqual(canonicalFile, skeletons) &&
                    !FileUtil.filesEqual(canonicalFile, userSkeletons) &&
                    !FileUtil.filesEqual(canonicalFile, remoteSources)) {
                    return file.getPath();
                }
            }
            return null;
        }));
    }

    /**
     * Creates if needed all path(s) used to store skeletons of its SDK.
     *
     * @return path name of skeleton dir for the SDK, guaranteed to be already created.
     */
    @Nonnull
    public String getSkeletonsPath() throws InvalidSdkException {
        if (mySkeletonsPath == null) {
            mySkeletonsPath = PythonSdkType.getSkeletonsPath(ContainerPathManager.get().getSystemPath(), mySdk.getHomePath());
            final File skeletonsDir = new File(mySkeletonsPath);
            if (!skeletonsDir.exists() && !skeletonsDir.mkdirs()) {
                throw new InvalidSdkException("Can't create skeleton dir " + String.valueOf(mySkeletonsPath));
            }
        }
        return mySkeletonsPath;
    }

    public List<String> regenerateSkeletons(@Nullable SkeletonVersionChecker cachedChecker) throws InvalidSdkException {
        final List<String> errorList = new SmartList<>();
        final String homePath = mySdk.getHomePath();
        final String skeletonsPath = getSkeletonsPath();
        final File skeletonsDir = new File(skeletonsPath);
        if (!skeletonsDir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            skeletonsDir.mkdirs();
        }
        final String readablePath = UserHomeFileUtil.getLocationRelativeToUserHome(homePath);

        mySkeletonsGenerator.prepare();
        myBlacklist = loadBlacklist();

        indicate(PyBundle.message("sdk.gen.querying.$0", readablePath));
        // get generator version and binary libs list in one go

        final String extraSysPath = calculateExtraSysPath(mySdk, getSkeletonsPath());
        final PySkeletonGenerator.ListBinariesResult binaries = mySkeletonsGenerator.listBinaries(mySdk, extraSysPath);
        myGeneratorVersion = binaries.generatorVersion;
        myPregeneratedSkeletons = findPregeneratedSkeletons();

        indicate(PyBundle.message("sdk.gen.reading.versions.file"));
        if (cachedChecker != null) {
            myVersionChecker = cachedChecker.withDefaultVersionIfUnknown(myGeneratorVersion);
        }
        else {
            myVersionChecker = new SkeletonVersionChecker(myGeneratorVersion);
        }

        // check builtins
        final String builtinsFileName = PythonSdkType.getBuiltinsFileName(mySdk);
        final File builtinsFile = new File(skeletonsPath, builtinsFileName);

        final SkeletonHeader oldHeader = readSkeletonHeader(builtinsFile);
        final boolean oldOrNonExisting = oldHeader == null || oldHeader.getVersion() == 0;

        if (myPregeneratedSkeletons != null && oldOrNonExisting) {
            unpackPreGeneratedSkeletons();
        }

        if (oldOrNonExisting) {
            copyBaseSdkSkeletonsToVirtualEnv(skeletonsPath, binaries);
        }

        final boolean builtinsUpdated = updateSkeletonsForBuiltins(readablePath, builtinsFile);

        if (!binaries.modules.isEmpty()) {
            indicate(PyBundle.message("sdk.gen.updating.$0", readablePath));
            final List<UpdateResult> updateErrors = updateOrCreateSkeletons(binaries.modules);
            if (updateErrors.size() > 0) {
                indicateMinor(BLACKLIST_FILE_NAME);
                for (UpdateResult error : updateErrors) {
                    if (error.isFresh()) {
                        errorList.add(error.getName());
                    }
                    myBlacklist.put(error.getPath(), new Pair<>(myGeneratorVersion, error.getTimestamp()));
                }
                storeBlacklist(skeletonsDir, myBlacklist);
            }
            else {
                removeBlacklist(skeletonsDir);
            }
        }

        indicate(PyBundle.message("sdk.gen.reloading"));
        mySkeletonsGenerator.refreshGeneratedSkeletons();

        if (!oldOrNonExisting) {
            indicate(PyBundle.message("sdk.gen.cleaning.$0", readablePath));
            cleanUpSkeletons(skeletonsDir);
        }

        if ((builtinsUpdated || PySdkUtil.isRemote(mySdk)) && myProject != null) {
            ApplicationManager.getApplication()
                .invokeLater(() -> DaemonCodeAnalyzer.getInstance(myProject).restart(), myProject.getDisposed());
        }

        return errorList;
    }

    private boolean updateSkeletonsForBuiltins(String readablePath, File builtinsFile) throws InvalidSdkException {
        final SkeletonHeader newHeader = readSkeletonHeader(builtinsFile);
        final boolean mustUpdateBuiltins =
            myPregeneratedSkeletons == null && (newHeader == null || newHeader.getVersion() < myVersionChecker.getBuiltinVersion());
        if (mustUpdateBuiltins) {
            indicate(PyBundle.message("sdk.gen.updating.builtins.$0", readablePath));
            mySkeletonsGenerator.generateBuiltinSkeletons(mySdk);
            if (myProject != null) {
                PythonSdkPathCache.getInstance(myProject, mySdk).clearBuiltins();
            }
        }
        return mustUpdateBuiltins;
    }

    private void copyBaseSdkSkeletonsToVirtualEnv(
        String skeletonsPath,
        PySkeletonGenerator.ListBinariesResult binaries
    ) throws InvalidSdkException {
        final Sdk base = PythonSdkType.getInstance().getVirtualEnvBaseSdk(mySdk);
        if (base != null) {
            indicate("Copying base SDK skeletons for virtualenv...");
            final String baseSkeletonsPath = PythonSdkType.getSkeletonsPath(ContainerPathManager.get().getSystemPath(), base.getHomePath());
            final PySkeletonGenerator.ListBinariesResult baseBinaries =
                mySkeletonsGenerator.listBinaries(base, calculateExtraSysPath(base, baseSkeletonsPath));
            for (Map.Entry<String, PyBinaryItem> entry : binaries.modules.entrySet()) {
                final String module = entry.getKey();
                final PyBinaryItem binary = entry.getValue();
                final PyBinaryItem baseBinary = baseBinaries.modules.get(module);
                final File fromFile = getSkeleton(module, baseSkeletonsPath);
                if (baseBinaries.modules.containsKey(module) &&
                    fromFile.exists() &&
                    binary.length() == baseBinary.length()) { // Weak binary modules equality check
                    final File toFile =
                        fromFile.isDirectory() ? getPackageSkeleton(module, skeletonsPath) : getModuleSkeleton(module, skeletonsPath);
                    try {
                        FileUtil.copy(fromFile, toFile, FilePermissionCopier.BY_NIO2);
                    }
                    catch (IOException e) {
                        LOG.info("Error copying base virtualenv SDK skeleton for " + module, e);
                    }
                }
            }
        }
    }

    private void unpackPreGeneratedSkeletons() throws InvalidSdkException {
        indicate("Unpacking pregenerated skeletons...");
        try {
            final VirtualFile jar = ArchiveVfsUtil.getVirtualFileForJar(myPregeneratedSkeletons);
            if (jar != null) {
                ZipUtil.extract(new File(jar.getPath()), new File(getSkeletonsPath()), null);
            }
        }
        catch (IOException e) {
            LOG.info("Error unpacking pregenerated skeletons", e);
        }
    }

    @Nullable
    public static SkeletonHeader readSkeletonHeader(@Nonnull File file) {
        try {
            final LineNumberReader reader = new LineNumberReader(new FileReader(file));
            try {
                String line = null;
                // Read 3 lines, skip first 2: encoding, module name
                for (int i = 0; i < 3; i++) {
                    line = reader.readLine();
                    if (line == null) {
                        return null;
                    }
                }
                // Try the old whitespace-unsafe header format v1 first
                final Matcher v1Matcher = VERSION_LINE_V1.matcher(line);
                if (v1Matcher.matches()) {
                    return new SkeletonHeader(v1Matcher.group(1), fromVersionString(v1Matcher.group(2)));
                }
                final Matcher fromMatcher = FROM_LINE_V2.matcher(line);
                if (fromMatcher.matches()) {
                    final String binaryFile = fromMatcher.group(1);
                    line = reader.readLine();
                    if (line != null) {
                        final Matcher byMatcher = BY_LINE_V2.matcher(line);
                        if (byMatcher.matches()) {
                            final int version = fromVersionString(byMatcher.group(1));
                            return new SkeletonHeader(binaryFile, version);
                        }
                    }
                }
            }
            finally {
                reader.close();
            }
        }
        catch (IOException ignored) {
        }
        return null;
    }

    public static class SkeletonHeader {
        @Nonnull
        private final String myFile;
        private final int myVersion;

        public SkeletonHeader(@Nonnull String binaryFile, int version) {
            myFile = binaryFile;
            myVersion = version;
        }

        @Nonnull
        public String getBinaryFile() {
            return myFile;
        }

        public int getVersion() {
            return myVersion;
        }
    }

    private Map<String, Pair<Integer, Long>> loadBlacklist() {
        Map<String, Pair<Integer, Long>> ret = new HashMap<>();
        File blacklistFile = new File(mySkeletonsPath, BLACKLIST_FILE_NAME);
        if (blacklistFile.exists() && blacklistFile.canRead()) {
            Reader input;
            try {
                input = new FileReader(blacklistFile);
                LineNumberReader lines = new LineNumberReader(input);
                try {
                    String line;
                    do {
                        line = lines.readLine();
                        if (line != null && line.length() > 0 && line.charAt(0) != '#') { // '#' begins a comment
                            Matcher matcher = BLACKLIST_LINE.matcher(line);
                            boolean notParsed = true;
                            if (matcher.matches()) {
                                final int version = fromVersionString(matcher.group(2));
                                if (version > 0) {
                                    try {
                                        final long timestamp = Long.parseLong(matcher.group(3));
                                        final String filename = matcher.group(1);
                                        ret.put(filename, new Pair<>(version, timestamp));
                                        notParsed = false;
                                    }
                                    catch (NumberFormatException ignore) {
                                    }
                                }
                            }
                            if (notParsed) {
                                LOG.warn("In blacklist at " + mySkeletonsPath + " strange line '" + line + "'");
                            }
                        }
                    }
                    while (line != null);
                }
                catch (IOException ex) {
                    LOG.warn("Failed to read blacklist in " + mySkeletonsPath, ex);
                }
                finally {
                    lines.close();
                }
            }
            catch (IOException ignore) {
            }
        }
        return ret;
    }

    private static void storeBlacklist(File skeletonDir, Map<String, Pair<Integer, Long>> blacklist) {
        File blacklistFile = new File(skeletonDir, BLACKLIST_FILE_NAME);
        PrintWriter output;
        try {
            output = new PrintWriter(blacklistFile);
            try {
                output.println("# PyCharm failed to generate skeletons for these modules.");
                output.println("# These skeletons will be re-generated automatically");
                output.println("# when a newer module version or an updated generator becomes available.");
                // each line:   filename = version.string timestamp
                for (String fname : blacklist.keySet()) {
                    Pair<Integer, Long> data = blacklist.get(fname);
                    output.print(fname);
                    output.print(" = ");
                    output.print(SkeletonVersionChecker.toVersionString(data.getFirst()));
                    output.print(" ");
                    output.print(data.getSecond());
                    output.println();
                }
            }
            finally {
                output.close();
            }
        }
        catch (IOException ex) {
            LOG.warn("Failed to store blacklist in " + skeletonDir.getPath(), ex);
        }
    }

    private static void removeBlacklist(File skeletonDir) {
        File blacklistFile = new File(skeletonDir, BLACKLIST_FILE_NAME);
        if (blacklistFile.exists()) {
            boolean okay = blacklistFile.delete();
            if (!okay) {
                LOG.warn("Could not delete blacklist file in " + skeletonDir.getPath());
            }
        }
    }

    /**
     * For every existing skeleton file, take its module file name,
     * and remove the skeleton if the module file does not exist.
     * Works recursively starting from dir. Removes dirs that become empty.
     */
    private void cleanUpSkeletons(final File dir) {
        indicateMinor(dir.getPath());
        final File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File item : files) {
            if (item.isDirectory()) {
                cleanUpSkeletons(item);
                // was the dir emptied?
                File[] remaining = item.listFiles();
                if (remaining != null && remaining.length == 0) {
                    mySkeletonsGenerator.deleteOrLog(item);
                }
                else if (remaining != null && remaining.length == 1) { //clean also if contains only __init__.py
                    File lastFile = remaining[0];
                    if (PyNames.INIT_DOT_PY.equals(lastFile.getName()) && lastFile.length() == 0) {
                        boolean deleted = mySkeletonsGenerator.deleteOrLog(lastFile);
                        if (deleted) {
                            mySkeletonsGenerator.deleteOrLog(item);
                        }
                    }
                }
            }
            else if (item.isFile()) {
                // clean up an individual file
                final String itemName = item.getName();
                if (PyNames.INIT_DOT_PY.equals(itemName) && item.length() == 0) {
                    continue; // these are versionless
                }
                if (BLACKLIST_FILE_NAME.equals(itemName)) {
                    continue; // don't touch the blacklist
                }
                if (PythonSdkType.getBuiltinsFileName(mySdk).equals(itemName)) {
                    continue;
                }
                final SkeletonHeader header = readSkeletonHeader(item);
                boolean canLive = header != null;
                if (canLive) {
                    final String binaryFile = header.getBinaryFile();
                    canLive = SkeletonVersionChecker.BUILTIN_NAME.equals(binaryFile) || mySkeletonsGenerator.exists(binaryFile);
                }
                if (!canLive) {
                    mySkeletonsGenerator.deleteOrLog(item);
                }
            }
        }
    }

    private static class UpdateResult {
        private final String myPath;
        private final String myName;
        private final long myTimestamp;

        public boolean isFresh() {
            return myIsFresh;
        }

        private final boolean myIsFresh;

        private UpdateResult(String name, String path, long timestamp, boolean fresh) {
            myName = name;
            myPath = path;
            myTimestamp = timestamp;
            myIsFresh = fresh;
        }

        public String getName() {
            return myName;
        }

        public String getPath() {
            return myPath;
        }

        public Long getTimestamp() {
            return myTimestamp;
        }
    }

    /**
     * (Re-)generates skeletons for all binary python modules. Up-to-date skeletons are not regenerated.
     * Does one module at a time: slower, but avoids certain conflicts.
     *
     * @param modules output of generator3 -L
     * @return blacklist data; whatever was not generated successfully is put here.
     */
    private List<UpdateResult> updateOrCreateSkeletons(Map<String, PyBinaryItem> modules) throws InvalidSdkException {
        long startTime = System.currentTimeMillis();

        final List<String> names = Lists.newArrayList(modules.keySet());
        Collections.sort(names);
        final List<UpdateResult> results = new ArrayList<>();
        final int count = names.size();
        for (int i = 0; i < count; i++) {
            checkCanceled();
            if (myIndicator != null) {
                myIndicator.setFraction((double)i / count);
            }
            final String name = names.get(i);
            final PyBinaryItem module = modules.get(name);
            if (module != null) {
                updateOrCreateSkeleton(module, results);
            }
        }
        finishSkeletonsGeneration();


        long doneInMs = System.currentTimeMillis() - startTime;

        LOG.info("Rebuilding skeletons for binaries took " + doneInMs + " ms");

        return results;
    }

    private void finishSkeletonsGeneration() {
        mySkeletonsGenerator.finishSkeletonsGeneration();
    }

    private static File getSkeleton(String moduleName, String skeletonsPath) {
        final File module = getModuleSkeleton(moduleName, skeletonsPath);
        return module.exists() ? module : getPackageSkeleton(moduleName, skeletonsPath);
    }

    private static File getModuleSkeleton(String module, String skeletonsPath) {
        final String modulePath = module.replace('.', '/');
        return new File(skeletonsPath, modulePath + ".py");
    }

    private static File getPackageSkeleton(String pkg, String skeletonsPath) {
        final String packagePath = pkg.replace('.', '/');
        return new File(new File(skeletonsPath, packagePath), PyNames.INIT_DOT_PY);
    }

    private boolean updateOrCreateSkeleton(final PyBinaryItem binaryItem, final List<UpdateResult> errorList) throws InvalidSdkException {
        final String moduleName = binaryItem.getModule();

        final File skeleton = getSkeleton(moduleName, getSkeletonsPath());
        final SkeletonHeader header = readSkeletonHeader(skeleton);
        boolean mustRebuild = true; // guilty unless proven fresh enough
        if (header != null) {
            int requiredVersion = myVersionChecker.getRequiredVersion(moduleName);
            mustRebuild = header.getVersion() < requiredVersion;
        }
        if (!mustRebuild) { // ...but what if the lib was updated?
            mustRebuild = (skeleton.exists() && binaryItem.lastModified() > skeleton.lastModified());
            // really we can omit both exists() calls but I keep these to make the logic clear
        }
        if (myBlacklist != null) {
            Pair<Integer, Long> versionInfo = myBlacklist.get(binaryItem.getPath());
            if (versionInfo != null) {
                int failedGeneratorVersion = versionInfo.getFirst();
                long failedTimestamp = versionInfo.getSecond();
                mustRebuild &= failedGeneratorVersion < myGeneratorVersion || failedTimestamp < binaryItem.lastModified();
                if (!mustRebuild) { // we're still failing to rebuild, it, keep it in blacklist
                    errorList.add(new UpdateResult(moduleName, binaryItem.getPath(), binaryItem.lastModified(), false));
                }
            }
        }
        if (mustRebuild) {
            indicateMinor(moduleName);
            if (myPregeneratedSkeletons != null && copyPregeneratedSkeleton(moduleName)) {
                return true;
            }
            LOG.info("Skeleton for " + moduleName);

            generateSkeleton(moduleName, binaryItem.getPath(), null, generated -> {
                if (!generated) {
                    errorList.add(new UpdateResult(moduleName, binaryItem.getPath(), binaryItem.lastModified(), true));
                }
            });
        }
        return false;
    }

    public static class PyBinaryItem {
        private String myPath;
        private String myModule;
        private long myLength;
        private long myLastModified;

        PyBinaryItem(String module, String path, long length, long lastModified) {
            myPath = path;
            myModule = module;
            myLength = length;
            myLastModified = lastModified * 1000;
        }

        public String getPath() {
            return myPath;
        }

        public String getModule() {
            return myModule;
        }

        public long length() {
            return myLength;
        }

        public long lastModified() {
            return myLastModified;
        }
    }

    private boolean copyPregeneratedSkeleton(String moduleName) throws InvalidSdkException {
        File targetDir;
        final String modulePath = moduleName.replace('.', '/');
        File skeletonsDir = new File(getSkeletonsPath());
        VirtualFile pregenerated = myPregeneratedSkeletons.findFileByRelativePath(modulePath + ".py");
        if (pregenerated == null) {
            pregenerated = myPregeneratedSkeletons.findFileByRelativePath(modulePath + "/" + PyNames.INIT_DOT_PY);
            targetDir = new File(skeletonsDir, modulePath);
        }
        else {
            int pos = modulePath.lastIndexOf('/');
            if (pos < 0) {
                targetDir = skeletonsDir;
            }
            else {
                final String moduleParentPath = modulePath.substring(0, pos);
                targetDir = new File(skeletonsDir, moduleParentPath);
            }
        }
        if (pregenerated != null && (targetDir.exists() || targetDir.mkdirs())) {
            LOG.info("Pregenerated skeleton for " + moduleName);
            File target = new File(targetDir, pregenerated.getName());
            try {
                FileOutputStream fos = new FileOutputStream(target);
                try {
                    FileUtil.copy(pregenerated.getInputStream(), fos);
                }
                finally {
                    fos.close();
                }
            }
            catch (IOException e) {
                LOG.info("Error copying pregenerated skeleton", e);
                return false;
            }
            return true;
        }
        return false;
    }

    @Nullable
    private VirtualFile findPregeneratedSkeletons() {
        final File root = findPregeneratedSkeletonsRoot();
        if (root == null) {
            return null;
        }
        LOG.info("Pregenerated skeletons root is " + root);
        @NonNls final String versionString = mySdk.getVersionString();
        if (versionString == null) {
            return null;
        }

        if (PySdkUtil.isRemote(mySdk)) {
            return null;
        }

        String version = versionString.toLowerCase().replace(" ", "-");
        File f;
        if (SystemInfo.isMac) {
            String osVersion = SystemInfo.OS_VERSION;
            int dot = osVersion.indexOf('.');
            if (dot >= 0) {
                int secondDot = osVersion.indexOf('.', dot + 1);
                if (secondDot >= 0) {
                    osVersion = osVersion.substring(0, secondDot);
                }
            }
            f = new File(root, "skeletons-mac-" + myGeneratorVersion + "-" + osVersion + "-" + version + ".zip");
        }
        else {
            String os = SystemInfo.isWindows ? "win" : "nix";
            f = new File(root, "skeletons-" + os + "-" + myGeneratorVersion + "-" + version + ".zip");
        }
        if (f.exists()) {
            LOG.info("Found pregenerated skeletons at " + f.getPath());
            final VirtualFile virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(f);
            if (virtualFile == null) {
                LOG.info("Could not find pregenerated skeletons in VFS");
                return null;
            }
            return ArchiveVfsUtil.getJarRootForLocalFile(virtualFile);
        }
        else {
            LOG.info("Not found pregenerated skeletons at " + f.getPath());
            return null;
        }
    }

    @Nullable
    private static File findPregeneratedSkeletonsRoot() {
        final String path = ContainerPathManager.get().getHomePath();
        LOG.info("Home path is " + path);
        File f = new File(path, "python/skeletons");  // from sources
        if (f.exists()) {
            return f;
        }
        f = new File(path, "skeletons");              // compiled binary
        if (f.exists()) {
            return f;
        }
        return null;
    }

    /**
     * Generates a skeleton for a particular binary module.
     *
     * @param modname        name of the binary module as known to Python (e.g. 'foo.bar')
     * @param modfilename    name of file which defines the module, null for built-in modules
     * @param assemblyRefs   refs that generator wants to know in .net environment, if applicable
     * @param resultConsumer accepts true if generation completed successfully
     */
    public void generateSkeleton(
        @Nonnull String modname,
        @Nullable String modfilename,
        @Nullable List<String> assemblyRefs,
        Consumer<Boolean> resultConsumer
    ) throws InvalidSdkException {
        mySkeletonsGenerator.generateSkeleton(modname, modfilename, assemblyRefs, getExtraSyspath(), mySdk.getHomePath(), resultConsumer);
    }

    private String getExtraSyspath() {
        if (myExtraSyspath == null) {
            myExtraSyspath = calculateExtraSysPath(mySdk, mySkeletonsPath);
        }
        return myExtraSyspath;
    }
}
