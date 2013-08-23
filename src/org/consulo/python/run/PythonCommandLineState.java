package org.consulo.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.projectRoots.Sdk;
import org.consulo.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

/**
 * @author VISTALL
 * @since 23.08.13.
 */
public class PythonCommandLineState extends CommandLineState {
	private PythonRunConfiguration myPythonRunConfiguration;

	public PythonCommandLineState(ExecutionEnvironment environment, PythonRunConfiguration pythonRunConfiguration) {
		super(environment);
		myPythonRunConfiguration = pythonRunConfiguration;
	}

	@NotNull
	@Override
	protected ProcessHandler startProcess() throws ExecutionException {
		Sdk sdk = myPythonRunConfiguration.getSdk();

		if(sdk == null) {
			throw new ExecutionException("No sdk");
		}

		GeneralCommandLine commandLine = new GeneralCommandLine();
		commandLine.setExePath(PythonSdkType.getInstance().getExecutableFile(sdk));
		commandLine.addParameter(myPythonRunConfiguration.SCRIPT_NAME);
		commandLine.getParametersList().addParametersString(myPythonRunConfiguration.PARAMETERS);
		commandLine.setWorkDirectory(myPythonRunConfiguration.WORKING_DIRECTORY);
		return new OSProcessHandler(commandLine.createProcess());
	}
}
