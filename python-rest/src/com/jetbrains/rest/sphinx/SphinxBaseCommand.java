package com.jetbrains.rest.sphinx;

import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonIOEncoding;
import static com.jetbrains.python.sdk.PythonEnvUtil.setPythonUnbuffered;

import java.awt.Dimension;
import java.util.Collections;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;

import org.consulo.python.buildout.module.extension.BuildoutModuleExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.google.common.collect.Lists;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.RunContentExecutor;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.configurations.ParamsGroup;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessTerminatedListener;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.python.ReSTService;
import com.jetbrains.python.run.PythonCommandLineState;
import com.jetbrains.python.run.PythonProcessRunner;
import com.jetbrains.python.run.PythonTracebackFilter;
import com.jetbrains.python.sdk.PythonSdkType;
import com.jetbrains.rest.RestPythonUtil;

/**
 * User : catherine
 * base command for the sphinx actions
 * asks for the "Sphinx documentation sources"
 */
public class SphinxBaseCommand {

  protected boolean setWorkDir(Module module) {
    final ReSTService service = ReSTService.getInstance(module);
    String workDir = service.getWorkdir();
    if (workDir.isEmpty()) {
      AskForWorkDir dialog = new AskForWorkDir(module.getProject());
      dialog.show();
      if(!dialog.isOK())
        return false;
      service.setWorkdir(dialog.getInputFile());
    }
    return true;
  }

  public static class AskForWorkDir extends DialogWrapper {
    private TextFieldWithBrowseButton myInputFile;
    private JPanel myPanel;

    private AskForWorkDir(Project project) {
      super(project);

      setTitle("Set Sphinx Working Directory: ");
      init();
      VirtualFile baseDir =  project.getBaseDir();
      String path = baseDir != null? baseDir.getPath() : "";
      myInputFile.setText(path);
      myInputFile.setEditable(false);
      myInputFile.addBrowseFolderListener("Choose sphinx working directory (containing makefile): ", null, project,
                                          FileChooserDescriptorFactory.createSingleFolderDescriptor());

      myPanel.setPreferredSize(new Dimension(600, 20));
    }

    @Override
    protected JComponent createCenterPanel() {
      return myPanel;
    }

    public String getInputFile() {
      return myInputFile.getText();
    }
  }

  public void execute(@NotNull final Module module) {
    final Project project = module.getProject();

    try {
      if (!setWorkDir(module)) return;
      final ProcessHandler process = createProcess(module);
      new RunContentExecutor(project, process)
        .withFilter(new PythonTracebackFilter(project))
        .withTitle("reStructuredText")
        .withRerun(new Runnable() {
          @Override
          public void run() {
            execute(module);
          }
        })
        .withAfterCompletion(getAfterTask(module))
        .run();
    }
    catch (ExecutionException e) {
      Messages.showErrorDialog(e.getMessage(), "ReStructuredText Error");
    }
  }

  @Nullable
  protected Runnable getAfterTask(final Module module) {
    return new Runnable() {
      public void run() {
        final ReSTService service = ReSTService.getInstance(module);
        LocalFileSystem.getInstance().refreshAndFindFileByPath(service.getWorkdir());
      }
    };
  }

  private ProcessHandler createProcess(Module module) throws ExecutionException {
    GeneralCommandLine commandLine = createCommandLine(module, Collections.<String>emptyList());
    ProcessHandler handler = PythonProcessRunner.createProcess(commandLine);
    ProcessTerminatedListener.attach(handler);
    return handler;
  }

  protected GeneralCommandLine createCommandLine(Module module, List<String> params) throws ExecutionException {
    GeneralCommandLine cmd = new GeneralCommandLine();

    Sdk sdk = PythonSdkType.findPythonSdk(module);
    if (sdk == null) {
      throw new ExecutionException("No sdk specified");
    }

    ReSTService service = ReSTService.getInstance(module);
    cmd.setWorkDirectory(service.getWorkdir().isEmpty()? module.getProject().getBaseDir().getPath(): service.getWorkdir());
    PythonCommandLineState.createStandardGroupsIn(cmd);
    ParamsGroup script_params = cmd.getParametersList().getParamsGroup(PythonCommandLineState.GROUP_SCRIPT);
    assert script_params != null;

    String commandPath = getCommandPath(sdk);
    if (commandPath == null) {
      throw new ExecutionException("Cannot find sphinx-quickstart.");
    }
    cmd.setExePath(commandPath);

    if (params != null) {
      for (String p : params) {
        script_params.addParameter(p);
      }
    }

    cmd.setPassParentEnvironment(true);
    setPythonIOEncoding(cmd.getEnvironment(), "utf-8");
    setPythonUnbuffered(cmd.getEnvironment());

    List<String> pathList = Lists.newArrayList(PythonCommandLineState.getAddedPaths(sdk));
    pathList.addAll(PythonCommandLineState.collectPythonPath(module));

    PythonCommandLineState.initPythonPath(cmd, true, pathList, sdk.getHomePath());

    PythonSdkType.patchCommandLineForVirtualenv(cmd, sdk.getHomePath(), true);
    BuildoutModuleExtension facet = ModuleUtilCore.getExtension(module, BuildoutModuleExtension.class);
    if (facet != null) {
      facet.patchCommandLineForBuildout(cmd);
    }

    return cmd;
  }

  @Nullable
  private static String getCommandPath(Sdk sdk) {
    return RestPythonUtil.findQuickStart(sdk);
  }
}
