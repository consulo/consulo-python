// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.debugger;

import consulo.process.ExecutionException;
import consulo.execution.RunManager;
import consulo.execution.RunnerAndConfigurationSettings;
import consulo.process.cmd.GeneralCommandLine;
import consulo.execution.configuration.RunConfiguration;
import consulo.process.ProcessHandler;
import consulo.process.ProcessHandlerBuilder;
import consulo.process.util.CapturingProcessRunner;
import consulo.process.event.ProcessEvent;
import consulo.process.event.ProcessListener;
import consulo.process.util.ProcessOutput;
import consulo.process.ProcessOutputType;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationAction;
import consulo.project.ui.notification.event.NotificationListener;
import consulo.project.ui.notification.NotificationType;
import consulo.ui.ex.action.AnAction;
import consulo.ui.ex.action.AnActionEvent;
import consulo.logging.Logger;
import consulo.application.progress.ProgressIndicator;
import consulo.application.progress.ProgressManager;
import consulo.application.progress.Task;
import consulo.ui.ex.action.DumbAwareAction;
import consulo.project.Project;
import consulo.content.bundle.Sdk;
import consulo.ui.ex.awt.Messages;
import consulo.util.dataholder.Key;
import consulo.application.util.SystemInfo;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import com.jetbrains.python.impl.PythonHelpersLocator;
import consulo.content.bundle.Sdk;
import consulo.platform.Platform;
import consulo.platform.base.localize.CommonLocalize;
import consulo.ui.ex.awt.UIUtil;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.impl.run.AbstractPythonRunConfiguration;
import com.jetbrains.python.impl.run.PythonRunConfiguration;
import com.jetbrains.python.impl.sdk.PythonEnvUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public final class PyDebugNotificationForCythonExtension {
    private static final Logger LOG = Logger.getInstance(PyDebugNotificationForCythonExtension.class);

    private static final String CYTHON_WARNING_GROUP_ID = "CythonWarning";
    public static final String SETUP_CYTHON_PATH = "pydev/setup_cython.py";


    public static void showCythonExtensionWarning(Project project) {
        if (shouldSuppressNotification(project)) {
            return;
        }
        // Consulo: the CythonWarning notification group is registered by PyDebuggerNotificationGroupContributor
        Notification notification =
          PyDebuggerNotificationGroupContributor.CYTHON_WARNING.createNotification(PyLocalize.compileCythonExtensionsNotification().get(),
                                                                                    PyLocalize.debuggerCythonExtensionSpeedsUpPythonDebugging().get(),
                                                                                    NotificationType.INFORMATION, null);
        // TODO consulo: Notification.setSuggestionType missing
        notification.setListener(NotificationListener.URL_OPENING_LISTENER);

        notification.addAction(createInstallAction(notification, project));

        notification.addAction(new NotificationAction(CommonLocalize.dialogOptionsDoNotShow()) {
            @Override
            public void actionPerformed(AnActionEvent e, Notification notification) {
                // TODO consulo: Notification.setDoNotAskFor and configureDoNotAskOption("CythonDebuggerExtensionAvailable", ...) missing,
                //  JB remembers the choice for the notification
                notification.expire();
            }
        });
        notification.notify(project);
    }

    private static AnAction createInstallAction(Notification notification, Project project) {
        return new DumbAwareAction(PyLocalize.compileCythonExtensionsInstall().get()) {

            @Override
            public void actionPerformed(AnActionEvent e) {
                compileCythonExtension(project);
                notification.expire();
            }
        };
    }

    private static boolean shouldSuppressNotification(Project project) {
        // TODO consulo: ProjectUtil.isNotificationSilentMode missing
        RunManager runManager = RunManager.getInstance(project);
        RunnerAndConfigurationSettings selectedConfiguration = runManager.getSelectedConfiguration();
        if (selectedConfiguration == null) {
            return true;
        }
        RunConfiguration configuration = selectedConfiguration.getConfiguration();
        if (!(configuration instanceof AbstractPythonRunConfiguration runConfiguration)) {
            return true;
        }
        // Temporarily disable notification for Remote interpreters
        // Consulo: the interpreter of a run configuration is its SDK, it is on a target when it is on another machine
        Sdk interpreter = runConfiguration.getSdk();
        return interpreter != null && !interpreter.getPlatform().equals(Platform.current());
    }

    private static void showErrorDialog(Project project, String message) {
        Messages.showMessageDialog(project, message, PyLocalize.compileCythonExtensionsError().get(), null);
    }

    private static void compileCythonExtension(Project project) {
        try {
            RunManager runManager = RunManager.getInstance(project);
            RunnerAndConfigurationSettings selectedConfiguration = runManager.getSelectedConfiguration();
            if (selectedConfiguration == null) {
                throw new ExecutionException(PyLocalize.debuggerCythonPythonRunConfigurationShouldBeSelected().get());
            }
            RunConfiguration configuration = selectedConfiguration.getConfiguration();
            if (!(configuration instanceof AbstractPythonRunConfiguration runConfiguration)) {
                throw new ExecutionException(PyLocalize.debuggerCythonPythonRunConfigurationShouldBeSelected().get());
            }
            String interpreterPath = runConfiguration.getInterpreterPath();
            String cythonPathString = PythonHelpersLocator.getHelperPath(SETUP_CYTHON_PATH);

            String cythonExtensionsDir = PyDebugRunner.CYTHON_EXTENSIONS_DIR;
            String[] cythonArgs =
              {"build_ext", "--build-lib", cythonExtensionsDir, "--build-temp", String.format("%s%sbuild", cythonExtensionsDir, File.separator)};

            List<String> cmdline = new ArrayList<>();
            cmdline.add(interpreterPath);
            cmdline.add(cythonPathString);
            cmdline.addAll(Arrays.asList(cythonArgs));
            LOG.info("Compile Cython Extensions " + StringUtil.join(cmdline, " "));

            Map<String, String> environment = new HashMap<>(System.getenv());
            PythonEnvUtil.addToPythonPath(environment, cythonExtensionsDir);
            PythonEnvUtil.setPythonUnbuffered(environment);
            PythonEnvUtil.setPythonDontWriteBytecode(environment);
            if (interpreterPath != null) {
                PythonEnvUtil.resetHomePathChanges(interpreterPath, environment);
            }
            GeneralCommandLine commandLine = new GeneralCommandLine(cmdline).withEnvironment(environment);

            boolean canCreate = FileUtil.ensureCanCreateFile(new File(cythonExtensionsDir));
            boolean useSudo = !canCreate && !SystemInfo.isWindows;
            // Consulo: CapturingProcessHandler(Process, Charset, String) is internal in consulo-process-api,
            // so sudo is requested through GeneralCommandLine.withSudo and the output is captured by CapturingProcessRunner
            if (useSudo) {
                commandLine.withSudo(PyLocalize.debuggerCythonPleaseEnterYourPasswordToCompileCythonExtensions().get());
            }
            ProcessHandler handler = ProcessHandlerBuilder.create(commandLine).build();

            ProgressManager.getInstance().run(new Task.Backgroundable(project, PyLocalize.compileCythonExtensionsTitle().get()) {
                @Override
                public void run(ProgressIndicator indicator) {
                    handler.addProcessListener(new ProcessListener() {
                        @Override
                        public void onTextAvailable(ProcessEvent event, Key outputType) {
                            if (ProcessOutputType.isStdout(outputType) || ProcessOutputType.isStderr(outputType)) {
                                for (String line : StringUtil.splitByLines(event.getText())) {
                                    if (isSignificantOutput(line)) {
                                        indicator.setText2(line.trim()); //NON-NLS
                                    }
                                }
                            }
                        }

                        private static boolean isSignificantOutput(String line) {
                            return line.trim().length() > 3;
                        }
                    });
                    ProcessOutput result = new CapturingProcessRunner(handler).runProcess(indicator);
                    int exitCode = result.getExitCode();
                    if (exitCode != 0) {
                        String message = StringUtil.isEmptyOrSpaces(result.getStdout()) && StringUtil.isEmptyOrSpaces(result.getStderr())
                                               ? PyLocalize.debuggerCythonExtensionPermissionDenied().get()
                                               : PyLocalize.debuggerCythonExtensionNonZeroExitCode(exitCode, result.getStderr()).get();
                        UIUtil.invokeLaterIfNeeded(() -> showClearErrorMessage(message, project));
                    }
                }
            });
        }
        catch (ExecutionException e) {
            showClearErrorMessage(e.getMessage(), project);
        }
    }

    private static void showClearErrorMessage(String errMessage, Project project) {
        String suggestion = "";
        if (errMessage.contains(PyLocalize.debuggerCythonExtensionPythonH().get())) {
            suggestion = PyLocalize.debuggerCythonExtensionPythonHNotFound(getInstallPythonCommand(project)).get();
        }
        else if (errMessage.contains(PyLocalize.debuggerCythonExtensionGcc().get())) {
            suggestion = PyLocalize.debuggerCythonExtensionGccFailed().get();
        }
        else if(errMessage.contains(PyLocalize.debuggerCythonExtensionGccNotFound().get())) {
            suggestion = PyLocalize.debuggerCythonExtensionGccNotFoundSuggestion().get();
        }
        showErrorDialog(project, errMessage + suggestion);
    }

    private static String getInstallPythonCommand(Project project) {
        String version = "";
        int pythonStringLength = "Python ".length();
        RunnerAndConfigurationSettings selectedConfiguration = RunManager.getInstance(project).getSelectedConfiguration();
          if (selectedConfiguration != null) {
              RunConfiguration runConfiguration = selectedConfiguration.getConfiguration();
              if (runConfiguration instanceof PythonRunConfiguration) {
                  Sdk pythonSdk = ((PythonRunConfiguration)runConfiguration ).getSdk();
                  if (pythonSdk != null) {
                      String versionString = pythonSdk.getVersionString();
                      if (versionString != null && versionString.length() > pythonStringLength) {
                          version = versionString.substring(pythonStringLength, versionString.lastIndexOf('.'));
                      }
                  }
              }
          }

        return PyLocalize.debuggerCythonExtensionInstallPythonCommand(version).get();
    }
}
