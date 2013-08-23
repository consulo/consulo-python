package org.consulo.python.sdk;

import com.intellij.openapi.projectRoots.*;
import com.intellij.openapi.util.SystemInfo;
import org.consulo.python.PythonIcons;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.File;

/**
 * @author VISTALL
 * @since 23.08.13.
 */
public class PythonSdkType extends SdkType {
	@NotNull
	public static PythonSdkType getInstance() {
		return findInstance(PythonSdkType.class);
	}

	public PythonSdkType() {
		super("Python SDK");
	}

	@Nullable
	@Override
	public Icon getIcon() {
		return PythonIcons.PythonRun;
	}

	@Nullable
	@Override
	public Icon getGroupIcon() {
		return PythonIcons.PythonRun;
	}

	@Nullable
	@Override
	public String suggestHomePath() {
		return null;
	}

	@Override
	public boolean isValidSdkHome(String s) {
		File file = new File(getExecutableFile(s));

		return file.exists();
	}

	@NotNull
	public String getExecutableFile(@NotNull Sdk sdk) {
		return getExecutableFile(sdk.getHomePath());
	}

	@NotNull
	public String getExecutableFile(@NotNull String home) {
		String executable = null;
		if(SystemInfo.isWindows) {
			executable = "python.exe";
		}
		else {
			executable = "python";
		}
		return home + File.separator + executable;
	}

	@Nullable
	@Override
	public String getVersionString(String s) {
		return "1.0";
	}

	@Override
	public String suggestSdkName(String currentSdkHome, String s2) {
		return "python";
	}

	@Nullable
	@Override
	public AdditionalDataConfigurable createAdditionalDataConfigurable(SdkModel sdkModel, SdkModificator sdkModificator) {
		return null;
	}

	@NotNull
	@Override
	public String getPresentableName() {
		return getName();
	}

	@Override
	public void saveAdditionalData(SdkAdditionalData sdkAdditionalData, Element element) {

	}
}
