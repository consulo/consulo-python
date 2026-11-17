// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.attach;

import com.jetbrains.python.impl.debugger.PyDebuggerOptionsProvider;
import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.impl.sdk.PreferredSdkComparator;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.bundle.Sdk;
import consulo.execution.RunManager;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.execution.attach.LocalAttachHost;
import consulo.execution.attach.XAttachHost;
import consulo.execution.attach.XAttachPresentationGroup;
import consulo.execution.configuration.RunConfiguration;
import consulo.execution.debug.attach.XAttachDebugger;
import consulo.execution.debug.attach.XAttachDebuggerProvider;
import consulo.platform.ProcessInfo;
import consulo.process.ExecutionException;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.dataholder.UserDataHolder;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@ExtensionImpl
public class PyLocalAttachDebuggerProvider implements XAttachDebuggerProvider {
    private static final Key<List<XAttachDebugger>> DEBUGGERS_KEY = Key.create("PyLocalAttachDebuggerProvider.DEBUGGERS");

    @Override
    public XAttachPresentationGroup<ProcessInfo> getPresentationGroup() {
        return PyLocalAttachGroup.INSTANCE;
    }

    @Override
    public boolean isAttachHostApplicable(XAttachHost attachHost) {
        return attachHost instanceof LocalAttachHost;
    }

    /**
     * Get all local Python Sdks, sort them by version and put the currently selected Sdk (if it exists) to the beginning.
     * Create a debuggers for attaching based on this list of Sdks.
     *
     * @return list of debuggers for attaching to process
     */
    private static List<XAttachDebugger> getAttachDebuggersForAllLocalSdks(Project project) {
        Sdk selected = null;
        RunnerAndConfigurationSettings settings = RunManager.getInstance(project).getSelectedConfiguration();
        if (settings != null) {
            RunConfiguration runConfiguration = settings.getConfiguration();
            if (runConfiguration instanceof AbstractPythonRunConfiguration) {
                selected = ((AbstractPythonRunConfiguration<?>) runConfiguration).getSdk();
            }
        }

        final Sdk selectedSdk = selected;
        // most recent python version goes first
        final List<XAttachDebugger> result = PythonSdkType.getAllLocalCPythons()
            .stream()
            .filter(sdk -> sdk != selectedSdk)
            // Runs each interpreter, which the Attach to Process action can afford: the platform asks once per running
            // process, but this list is built once per invocation and cached under DEBUGGERS_KEY, off the EDT.
            // JB Sdk.isInterpreterUsable() belongs to the new SDK backend, which is not ported; Consulo checks the interpreter here
            .filter(sdk -> !PythonSdkType.isInvalid(sdk))
            .sorted(PreferredSdkComparator.INSTANCE)
            .map(PyLocalAttachDebugger::new)
            .collect(Collectors.toList());
        if (selectedSdk != null) {
            result.add(0, new PyLocalAttachDebugger(selectedSdk));
        }
        return result;
    }

    @Override
    public List<XAttachDebugger> getAvailableDebuggers(Project project,
                                                       XAttachHost attachHost,
                                                       ProcessInfo processInfo,
                                                       UserDataHolder contextHolder) {
        final String filter = PyDebuggerOptionsProvider.getInstance(project).getAttachProcessFilter();
        if (StringUtil.containsIgnoreCase(processInfo.getCommandLine(), filter)) {
            List<XAttachDebugger> result;

            if (processInfo.getExecutableCannonicalPath().isPresent() &&
                new File(processInfo.getExecutableCannonicalPath().get()).exists()) {
                result = new ArrayList<>(Arrays.asList(new PyLocalAttachDebugger(processInfo.getExecutableCannonicalPath().get())));
            }
            else {
                result = contextHolder.getUserData(DEBUGGERS_KEY);
                if (result != null) {
                    return result;
                }

                result = getAttachDebuggersForAllLocalSdks(project);
                contextHolder.putUserData(DEBUGGERS_KEY, Collections.unmodifiableList(result));
            }

            return result;
        }
        return Collections.emptyList();
    }

    private static class PyLocalAttachDebugger implements XAttachDebugger {
        private final String mySdkHome;
        private final @Nullable Sdk mySdk;
        private final String myName;

        PyLocalAttachDebugger(Sdk sdk) {
            mySdkHome = sdk.getHomePath();
            mySdk = sdk;
            myName = PythonSdkType.getInstance().getVersionString(sdk) + " (" + mySdkHome + ")";
        }

        PyLocalAttachDebugger(String sdkHome) {
            mySdkHome = sdkHome;
            mySdk = PythonSdkType.findSdkByPath(mySdkHome);
            myName = "Python Debugger";
        }

        @Override
        public String getDebuggerDisplayName() {
            return myName;
        }

        @Override
        public void attachDebugSession(Project project,
                                       XAttachHost attachHost,
                                       ProcessInfo processInfo) throws ExecutionException {
            PyAttachToProcessDebugRunner runner = new PyAttachToProcessDebugRunner(project, processInfo.getPid(), mySdk);
            runner.launch();
        }
    }
}
