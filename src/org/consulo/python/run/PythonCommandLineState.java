package org.consulo.python.run;

import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.CommandLineState;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
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
		GeneralCommandLine commandLine = new GeneralCommandLine();
		commandLine.setExePath("I:\\Programs\\play-1.2.5\\python\\python.exe");
		commandLine.addParameter(myPythonRunConfiguration.SCRIPT_NAME);
		commandLine.getParametersList().addParametersString(myPythonRunConfiguration.PARAMETERS);
		commandLine.setWorkDirectory(myPythonRunConfiguration.WORKING_DIRECTORY);
		return new OSProcessHandler(commandLine.createProcess());
	}
}
