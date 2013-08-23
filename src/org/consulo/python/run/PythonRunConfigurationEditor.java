package org.consulo.python.run;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.SdkTypeId;
import com.intellij.openapi.roots.ui.configuration.SdkComboBox;
import com.intellij.openapi.roots.ui.configuration.projectRoot.ProjectSdksModel;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.TextComponentAccessor;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RawCommandLineEditor;
import lombok.val;
import org.consulo.python.PythonFileType;
import org.consulo.python.sdk.PythonSdkType;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class PythonRunConfigurationEditor extends SettingsEditor<PythonRunConfiguration>
{
	private JPanel myRootPanel;
	private TextFieldWithBrowseButton myScriptTextField;
	private RawCommandLineEditor myParametersTextField;
	private TextFieldWithBrowseButton myWorkingDirectoryTextField;
	private SdkComboBox mySdkComboBox;

	private final Project myProject;

	public PythonRunConfigurationEditor(Project project) {
		myProject = project;
	}

	@Override
	protected void resetEditorFrom(PythonRunConfiguration s)
	{

		myScriptTextField.setText(FileUtil.toSystemDependentName(s.SCRIPT_NAME));
		myParametersTextField.setText(s.PARAMETERS);
		myWorkingDirectoryTextField.setText(FileUtil.toSystemDependentName(s.WORKING_DIRECTORY));

		FileChooserDescriptor chooserDescriptor = new FileChooserDescriptor(true, false, false, false, false, false)
		{
			@Override
			public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
				return file.isDirectory() || file.getFileType() == PythonFileType.INSTANCE;
			}
		};

		chooserDescriptor.setRoots(s.getProject().getBaseDir());

		ComponentWithBrowseButton.BrowseFolderActionListener<JTextField> listener = new ComponentWithBrowseButton.BrowseFolderActionListener<JTextField>("Select Script", "", myScriptTextField,
				s.getProject(), 
			chooserDescriptor, TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT)
		{
			@Override
			protected void onFileChoosen(VirtualFile chosenFile)
			{
				super.onFileChoosen(chosenFile);

				myWorkingDirectoryTextField.setText(chosenFile.getParent().getPath());
			}
		};
		mySdkComboBox.setSelectedSdk(s.getSdkName());
		myScriptTextField.addActionListener(listener);

		myWorkingDirectoryTextField.addBrowseFolderListener("Select Working Directory", "", s.getProject(), new FileChooserDescriptor(false, true, false, false, false, false));
	}

	@Override
	protected void applyEditorTo(PythonRunConfiguration s) throws ConfigurationException
	{
		s.SCRIPT_NAME = FileUtil.toSystemIndependentName(myScriptTextField.getText());
		s.PARAMETERS = myParametersTextField.getText();
		s.WORKING_DIRECTORY = FileUtil.toSystemIndependentName(myWorkingDirectoryTextField.getText());
		s.setSdkName(mySdkComboBox.getSelectedSdkName());
	}

	@NotNull
	@Override
	protected JComponent createEditor() {
		return myRootPanel;
	}

	@Override
	protected void disposeEditor()
	{
	}

	private void createUIComponents() {
		val projectSdksModel = new ProjectSdksModel();
		projectSdksModel.reset(myProject);

		mySdkComboBox = new SdkComboBox(projectSdksModel, new Condition<SdkTypeId>() {
			@Override
			public boolean value(SdkTypeId sdkTypeId) {
				return sdkTypeId == PythonSdkType.getInstance();
			}
		}, false);
	}
}