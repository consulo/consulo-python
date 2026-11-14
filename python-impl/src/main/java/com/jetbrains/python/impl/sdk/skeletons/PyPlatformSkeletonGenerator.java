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
package com.jetbrains.python.impl.sdk.skeletons;

import com.jetbrains.python.impl.PythonHelpersLocator;
import com.jetbrains.python.impl.sdk.InvalidSdkException;
import consulo.content.bundle.Sdk;
import consulo.platform.Platform;
import consulo.process.ExecutionException;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.util.CapturingProcessUtil;
import consulo.process.util.ProcessOutput;
import consulo.util.io.FileUtil;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Skeletons of an interpreter of another machine: the generator runs there and writes into a directory of that machine,
 * the skeletons are copied to the local directory of the SDK - the one the IDE reads.
 *
 * @author VISTALL
 * @since 2026-11-14
 */
public class PyPlatformSkeletonGenerator extends PySkeletonGenerator {
    private final Platform myPlatform;
    private final Path myRemoteSkeletonsPath;

    public PyPlatformSkeletonGenerator(String skeletonPath, Sdk pySdk, @Nullable String currentFolder) {
        super(skeletonPath, pySdk, currentFolder);
        myPlatform = pySdk.getPlatform();
        // the name of the local directory is the key of the SDK
        String key = Path.of(skeletonPath).getFileName().toString();
        myRemoteSkeletonsPath = myPlatform.user().homePath().resolve(".cache").resolve("consulo").resolve("python_stubs").resolve(key);
    }

    /**
     * A file of the machine of the interpreter - a binary module there.
     */
    @Override
    public boolean exists(String name) {
        return Files.exists(myPlatform.fs().getPath(name));
    }

    @Override
    protected ProcessOutput getProcessOutput(
        String homePath,
        String[] commandLine,
        Map<String, String> extraEnv,
        int timeout
    ) throws InvalidSdkException {
        List<String> arguments = Arrays.asList(commandLine.clone());
        try {
            Files.createDirectories(myRemoteSkeletonsPath);
            for (int i = 1; i < arguments.size(); i++) {
                String argument = arguments.get(i);
                if (argument.endsWith(GENERATOR3)) {
                    arguments.set(i, PythonHelpersLocator.toPlatformPath(argument, myPlatform));
                }
                else if ("-d".equals(arguments.get(i - 1))) {
                    // the skeletons are written on that machine
                    arguments.set(i, myRemoteSkeletonsPath.toString());
                }
            }
        }
        catch (IOException e) {
            throw new InvalidSdkException("Cannot prepare the skeletons on " + myPlatform.getName(), e);
        }

        GeneralCommandLine command = new GeneralCommandLine();
        command.withPlatform(myPlatform);
        command.withExecutablePath(myPlatform.fs().getPath(arguments.get(0)));
        command.addParameters(arguments.subList(1, arguments.size()));
        command.getEnvironment().putAll(extraEnv);
        command.getEnvironment().put("PYTHONDONTWRITEBYTECODE", "1");
        Path interpreterDirectory = myPlatform.fs().getPath(arguments.get(0)).getParent();
        if (interpreterDirectory != null) {
            command.withWorkingDirectory(interpreterDirectory);
        }
        try {
            return CapturingProcessUtil.execAndGetOutput(command, timeout);
        }
        catch (ExecutionException e) {
            throw new InvalidSdkException("Cannot run the skeleton generator on " + myPlatform.getName(), e);
        }
    }

    /**
     * The skeletons generated on that machine come to the local directory first - only the new and the changed ones.
     */
    @Override
    public void refreshGeneratedSkeletons() {
        Path localRoot = Path.of(getSkeletonsPath());
        try (Stream<Path> files = Files.walk(myRemoteSkeletonsPath)) {
            for (Path remote : (Iterable<Path>) files.filter(Files::isRegularFile)::iterator) {
                Path local = localRoot;
                for (Path name : myRemoteSkeletonsPath.relativize(remote)) {
                    local = local.resolve(name.toString());
                }
                if (Files.exists(local)
                    && Files.size(local) == Files.size(remote)
                    && Files.getLastModifiedTime(local).compareTo(Files.getLastModifiedTime(remote)) >= 0) {
                    continue;
                }
                Files.createDirectories(local.getParent());
                Files.copy(remote, local, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        catch (IOException e) {
            LOG.warn("Cannot copy the skeletons of " + myPlatform.getName() + " to " + FileUtil.toSystemDependentName(localRoot.toString()), e);
        }
        super.refreshGeneratedSkeletons();
    }
}
