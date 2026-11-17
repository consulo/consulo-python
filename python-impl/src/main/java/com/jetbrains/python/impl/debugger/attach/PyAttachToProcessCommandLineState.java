// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger.attach;

import com.jetbrains.python.impl.PythonHelper;
import com.jetbrains.python.impl.debugger.PyRemoteDebugProcess;
import com.jetbrains.python.impl.debugger.PyRemoteDebugProcessAware;
import com.jetbrains.python.impl.run.PythonConfigurationType;
import com.jetbrains.python.impl.run.PythonRunConfiguration;
import com.jetbrains.python.impl.run.PythonScriptCommandLineState;
import consulo.content.bundle.Sdk;
import consulo.execution.configuration.ConfigurationFactory;
import consulo.execution.debug.DefaultDebugExecutor;
import consulo.execution.runner.ExecutionEnvironment;
import consulo.execution.runner.ExecutionEnvironmentBuilder;
import consulo.process.BaseProcessHandler;
import consulo.process.ExecutionException;
import consulo.process.ProcessHandler;
import consulo.process.ProcessOutputTypes;
import consulo.process.cmd.GeneralCommandLine;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

import java.io.OutputStream;

public final class PyAttachToProcessCommandLineState extends PythonScriptCommandLineState {


    private PyAttachToProcessCommandLineState(PythonRunConfiguration runConfiguration,
                                              ExecutionEnvironment env) {
        super(runConfiguration, env);
    }

    public static PyAttachToProcessCommandLineState create(Project project, @Nullable Sdk sdk, int port, int pid)
        throws ExecutionException {
        // Consulo's PythonConfigurationFactory is private, so its template is created through the ConfigurationFactory type
        ConfigurationFactory factory = PythonConfigurationType.getInstance().getFactory();
        PythonRunConfiguration conf = (PythonRunConfiguration) factory.createTemplateConfiguration(project);
        conf.setScriptName(PythonHelper.ATTACH_DEBUGGER.asParamString());
        // JB conf.setSdk(sdk): Consulo run configurations keep the interpreter as an SDK home path
        conf.setUseModuleSdk(false);
        conf.setSdkHome(sdk != null ? sdk.getHomePath() : null);
        conf.setScriptParameters("--port " + port + " --pid " + pid);

        ExecutionEnvironment env =
            ExecutionEnvironmentBuilder.create(project, DefaultDebugExecutor.getDebugExecutorInstance(), conf).build();


        return new PyAttachToProcessCommandLineState(conf, env);
    }


    @Override
    protected ProcessHandler doCreateProcess(GeneralCommandLine commandLine) throws ExecutionException {
        ProcessHandler handler = super.doCreateProcess(commandLine);

        return new PyRemoteDebugProcessHandler(handler);
    }

    public static class PyRemoteDebugProcessHandler extends BaseProcessHandler implements PyRemoteDebugProcessAware {
        private final ProcessHandler myHandler;
        private @Nullable PyRemoteDebugProcess myProcess = null;

        public PyRemoteDebugProcessHandler(ProcessHandler handler) {
            myHandler = handler;
            myHandler.addProcessListener(new ProcessListener() {
                @Override
                public void onTextAvailable(ProcessEvent event, Key outputType) {
                    PyRemoteDebugProcessHandler.this.notifyTextAvailable(event.getText(), outputType);
                }
            });
        }

        @Override
        public void startNotify() {
            super.startNotify();
            myHandler.startNotify();
        }

        @Override
        protected void destroyProcessImpl() {
            if (myProcess != null) {
                myProcess.stop();
            }
            detachProcessImpl();
        }

        @Override
        protected void detachProcessImpl() {
            notifyProcessTerminated(0);
            notifyTextAvailable("Server stopped.\n", ProcessOutputTypes.SYSTEM);
        }

        @Override
        public boolean detachIsDefault() {
            return false;
        }

        @Override
        public @Nullable OutputStream getProcessInput() {
            return null;
        }

        @Override
        public void setRemoteDebugProcess(PyRemoteDebugProcess process) {
            myProcess = process;
        }
    }
}
