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

import com.google.common.collect.Maps;
import com.jetbrains.python.impl.PythonHelpersLocator;
import com.jetbrains.python.impl.sdk.InvalidSdkException;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.impl.sdk.PythonEnvUtil;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.impl.sdk.flavors.PythonSdkFlavor;
import consulo.application.ApplicationProperties;
import consulo.content.bundle.Sdk;
import consulo.logging.Logger;
import consulo.process.util.ProcessOutput;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.LocalFileSystem;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.*;
import java.util.function.Consumer;

import static com.jetbrains.python.impl.sdk.skeletons.SkeletonVersionChecker.fromVersionString;

/**
 * @author traff
 */
public class PySkeletonGenerator {
    protected static final Logger LOG = Logger.getInstance(PySkeletonGenerator.class);
    protected static final int MINUTE = 60 * 1000;
    protected static final String GENERATOR3 = "generator3.py";

    private final String mySkeletonsPath;
    @Nonnull
    private final Map<String, String> myEnv;

    public void finishSkeletonsGeneration() {
    }

    public boolean exists(String name) {
        return new File(name).exists();
    }

    public static class ListBinariesResult {
        public final int generatorVersion;
        public final Map<String, PySkeletonRefresher.PyBinaryItem> modules;

        public ListBinariesResult(int generatorVersion, Map<String, PySkeletonRefresher.PyBinaryItem> modules) {
            this.generatorVersion = generatorVersion;
            this.modules = modules;
        }
    }

    /**
     * @param skeletonPath  path where skeletons should be generated
     * @param pySdk         SDK
     * @param currentFolder current folder (some flavors may search for binary files there) or null if unknown
     */
    public PySkeletonGenerator(String skeletonPath, @Nonnull Sdk pySdk, @Nullable String currentFolder) {
        mySkeletonsPath = skeletonPath;
        PythonSdkFlavor flavor = PythonSdkFlavor.getFlavor(pySdk);
        if (flavor != null) {
            myEnv = new HashMap<>();
            flavor.addPredefinedEnvironmentVariables(myEnv);
        }
        else {
            myEnv = Collections.emptyMap();
        }
    }

    public String getSkeletonsPath() {
        return mySkeletonsPath;
    }

    public void prepare() {
    }

    protected void generateSkeleton(
        String modname,
        String modfilename,
        List<String> assemblyRefs,
        String syspath,
        String sdkHomePath,
        Consumer<Boolean> resultConsumer
    ) throws InvalidSdkException {
        ProcessOutput genResult = runSkeletonGeneration(modname, modfilename, assemblyRefs, sdkHomePath, syspath);

        if (genResult.getStderrLines().size() > 0) {
            StringBuilder sb = new StringBuilder("Skeleton for ");
            sb.append(modname);
            if (genResult.getExitCode() != 0) {
                sb.append(" failed on ");
            }
            else {
                sb.append(" had some minor errors on ");
            }
            sb.append(sdkHomePath).append(". stderr: --\n");
            for (String err_line : genResult.getStderrLines()) {
                sb.append(err_line).append("\n");
            }
            sb.append("--");
            if (ApplicationProperties.isInSandbox()) {
                LOG.warn(sb.toString());
            }
            else {
                LOG.info(sb.toString());
            }
        }

        resultConsumer.accept(genResult.getExitCode() == 0);
    }

    public ProcessOutput runSkeletonGeneration(
        String modname,
        String modfilename,
        List<String> assemblyRefs,
        String binaryPath,
        String extraSyspath
    ) throws InvalidSdkException {
        String parent_dir = new File(binaryPath).getParent();
        List<String> commandLine = new ArrayList<>();
        commandLine.add(binaryPath);
        commandLine.add(PythonHelpersLocator.getHelperPath(GENERATOR3));
        commandLine.add("-d");
        commandLine.add(getSkeletonsPath());
        if (assemblyRefs != null && !assemblyRefs.isEmpty()) {
            commandLine.add("-c");
            commandLine.add(StringUtil.join(assemblyRefs, ";"));
        }
        if (ApplicationProperties.isInSandbox()) {
            commandLine.add("-x");
        }
        if (!StringUtil.isEmpty(extraSyspath)) {
            commandLine.add("-s");
            commandLine.add(extraSyspath);
        }
        commandLine.add(modname);
        if (modfilename != null) {
            commandLine.add(modfilename);
        }

        Map<String, String> extraEnv = PythonSdkType.getVirtualEnvExtraEnv(binaryPath);
        Map<String, String> env = extraEnv != null ? PySdkUtil.mergeEnvVariables(myEnv, extraEnv) : myEnv;

        return getProcessOutput(parent_dir, ArrayUtil.toStringArray(commandLine), env, MINUTE * 10);
    }

    protected ProcessOutput getProcessOutput(
        String homePath,
        String[] commandLine,
        Map<String, String> extraEnv,
        int timeout
    ) throws InvalidSdkException {
        Map<String, String> env = extraEnv != null ? new HashMap<>(extraEnv) : new HashMap<>();
        PythonEnvUtil.setPythonDontWriteBytecode(env);
        return PySdkUtil.getProcessOutput(homePath, commandLine, env, timeout);
    }

    public void generateBuiltinSkeletons(@Nonnull Sdk sdk) throws InvalidSdkException {
        //noinspection ResultOfMethodCallIgnored
        new File(mySkeletonsPath).mkdirs();
        String binaryPath = sdk.getHomePath();
        if (binaryPath == null) {
            throw new InvalidSdkException("Broken home path for " + sdk.getName());
        }

        long startTime = System.currentTimeMillis();
        ProcessOutput runResult = getProcessOutput(new File(binaryPath).getParent(), new String[]{
            binaryPath,
            PythonHelpersLocator.getHelperPath(GENERATOR3),
            "-d",
            mySkeletonsPath,
            // output dir
            "-b",
            // for builtins
        }, PythonSdkType.getVirtualEnvExtraEnv(binaryPath), MINUTE * 5);
        runResult.checkSuccess(LOG);
        LOG.info("Rebuilding builtin skeletons took " + (System.currentTimeMillis() - startTime) + " ms");
    }

    @Nonnull
    public ListBinariesResult listBinaries(@Nonnull Sdk sdk, @Nonnull String extraSysPath) throws InvalidSdkException {
        String homePath = sdk.getHomePath();
        long startTime = System.currentTimeMillis();
        if (homePath == null) {
            throw new InvalidSdkException("Broken home path for " + sdk.getName());
        }
        String parentDir = new File(homePath).getParent();

        List<String> cmd = new ArrayList<>(Arrays.asList(homePath, PythonHelpersLocator.getHelperPath(GENERATOR3), "-v", "-L"));
        if (!StringUtil.isEmpty(extraSysPath)) {
            cmd.add("-s");
            cmd.add(extraSysPath);
        }

        ProcessOutput process = getProcessOutput(
            parentDir,
            ArrayUtil.toStringArray(cmd),
            PythonSdkType.getVirtualEnvExtraEnv(homePath),
            MINUTE * 4
        ); // see PY-3898

        LOG.info("Retrieving binary module list took " + (System.currentTimeMillis() - startTime) + " ms");
        if (process.getExitCode() != 0) {
            StringBuilder sb = new StringBuilder("failed to run ").append(GENERATOR3).append(" for ").append(homePath);
            if (process.isTimeout()) {
                sb.append(": timed out.");
            }
            else {
                sb.append(", exit code ").append(process.getExitCode()).append(", stderr: \n-----\n");
                for (String line : process.getStderrLines()) {
                    sb.append(line).append("\n");
                }
                sb.append("-----");
            }
            throw new InvalidSdkException(sb.toString());
        }
        List<String> lines = process.getStdoutLines();
        if (lines.size() < 1) {
            throw new InvalidSdkException("Empty output from " + GENERATOR3 + " for " + homePath);
        }
        Iterator<String> iter = lines.iterator();
        int generatorVersion = fromVersionString(iter.next().trim());
        Map<String, PySkeletonRefresher.PyBinaryItem> binaries = Maps.newHashMap();
        while (iter.hasNext()) {
            String line = iter.next();
            int cutpos = line.indexOf('\t');
            if (cutpos >= 0) {
                String[] strs = line.split("\t");
                String moduleName = strs[0];
                String path = strs[1];
                int length = Integer.parseInt(strs[2]);
                int lastModified = Integer.parseInt(strs[3]);

                binaries.put(moduleName, new PySkeletonRefresher.PyBinaryItem(moduleName, path, length, lastModified));
            }
            else {
                LOG.error("Bad binaries line: '" + line + "', SDK " + homePath); // but don't die yet
            }
        }
        return new ListBinariesResult(generatorVersion, binaries);
    }

    public boolean deleteOrLog(@Nonnull File item) {
        boolean deleted = item.delete();
        if (!deleted) {
            LOG.warn("Failed to delete skeleton file " + item.getAbsolutePath());
        }
        return deleted;
    }

    public void refreshGeneratedSkeletons() {
        VirtualFile skeletonsVFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(getSkeletonsPath());
        assert skeletonsVFile != null;
        skeletonsVFile.refresh(false, true);
    }
}
