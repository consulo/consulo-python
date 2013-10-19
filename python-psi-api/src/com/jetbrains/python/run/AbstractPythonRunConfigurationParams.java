package com.jetbrains.python.run;

import java.util.Map;

import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.module.Module;
import com.intellij.util.PathMappingSettings;

/**
 * @author Leonid Shalupov
 */
public interface AbstractPythonRunConfigurationParams
{
	String getInterpreterOptions();

	void setInterpreterOptions(String interpreterOptions);

	String getWorkingDirectory();

	void setWorkingDirectory(String workingDirectory);

	@Nullable
	String getSdkHome();

	void setSdkHome(String sdkHome);

	@Nullable
	Module getModule();

	void setModule(Module module);

	@Deprecated
	boolean isUseModuleSdk();

	@Deprecated
	void setUseModuleSdk(boolean useModuleSdk);

	boolean isPassParentEnvs();

	void setPassParentEnvs(boolean passParentEnvs);

	Map<String, String> getEnvs();

	void setEnvs(final Map<String, String> envs);

	@Nullable
	PathMappingSettings getMappingSettings();

	void setMappingSettings(@Nullable PathMappingSettings mappingSettings);

	boolean addContentRoots();

	boolean addSourceRoots();

	void addContentRoots(boolean add);

	void addSourceRoots(boolean add);
}
