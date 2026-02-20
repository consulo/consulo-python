/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.configuration;

import com.google.common.collect.Lists;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.ReSTService;
import com.jetbrains.python.impl.documentation.PyDocumentationSettings;
import com.jetbrains.python.impl.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.impl.packaging.PyPackageRequirementsSettings;
import com.jetbrains.python.impl.packaging.PyPackageUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.testing.PythonTestConfigurationsModel;
import com.jetbrains.python.impl.testing.TestRunnerService;
import consulo.configurable.ConfigurationException;
import consulo.configurable.SearchableConfigurable;
import consulo.content.ContentIterator;
import consulo.document.util.FileContentUtilCore;
import consulo.fileChooser.FileChooserDescriptor;
import consulo.fileChooser.FileChooserDescriptorFactory;
import consulo.fileEditor.util.FileContentUtil;
import consulo.language.editor.DaemonCodeAnalyzer;
import consulo.language.plain.PlainTextFileType;
import consulo.localize.LocalizeValue;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.awt.CollectionComboBoxModel;
import consulo.ui.ex.awt.IdeBorderFactory;
import consulo.ui.ex.awt.ListCellRendererWrapper;
import consulo.ui.ex.awt.TextFieldWithBrowseButton;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * User: catherine
 */
public class PyIntegratedToolsConfigurable implements SearchableConfigurable {
  private JPanel myMainPanel;
  private JComboBox myTestRunnerComboBox;
  private JComboBox myDocstringFormatComboBox;
  private PythonTestConfigurationsModel myModel;
  @Nonnull
  private final Module myModule;
  @Nonnull
  private final Project myProject;
  private final PyDocumentationSettings myDocumentationSettings;
  private TextFieldWithBrowseButton myWorkDir;
  private JCheckBox txtIsRst;
  private JPanel myErrorPanel;
  private TextFieldWithBrowseButton myRequirementsPathField;
  private JCheckBox analyzeDoctest;
  private JPanel myDocStringsPanel;
  private JPanel myRestPanel;

  public PyIntegratedToolsConfigurable(@Nonnull Module module) {
    myModule = module;
    myProject = myModule.getProject();
    myDocumentationSettings = PyDocumentationSettings.getInstance(myModule);
    //noinspection unchecked
    myDocstringFormatComboBox.setModel(new CollectionComboBoxModel<>(Arrays.asList(DocStringFormat.values()),
                                                                     myDocumentationSettings.getFormat()));
    myDocstringFormatComboBox.setRenderer(new ListCellRendererWrapper<DocStringFormat>() {
      @Override
      public void customize(JList list, DocStringFormat value, int index, boolean selected, boolean hasFocus) {
        setText(value.getName());
      }
    });

    FileChooserDescriptor fileChooserDescriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
    myWorkDir.addBrowseFolderListener("Please choose working directory:", null, myProject, fileChooserDescriptor);
    ReSTService service = ReSTService.getInstance(myModule);
    myWorkDir.setText(service.getWorkdir());
    txtIsRst.setSelected(service.txtIsRst());
    analyzeDoctest.setSelected(myDocumentationSettings.isAnalyzeDoctest());
    myRequirementsPathField.addBrowseFolderListener("Choose path to the package requirements file:",
                                                    null,
                                                    myProject,
                                                    FileChooserDescriptorFactory.createSingleLocalFileDescriptor());
    myRequirementsPathField.setText(getRequirementsPath());

    myDocStringsPanel.setBorder(IdeBorderFactory.createTitledBorder("Docstrings"));
    myRestPanel.setBorder(IdeBorderFactory.createTitledBorder("reStructuredText"));
  }

  @Nonnull
  private String getRequirementsPath() {
    String path = PyPackageRequirementsSettings.getInstance(myModule).getRequirementsPath();
    if (path.equals(PyPackageRequirementsSettings.DEFAULT_REQUIREMENTS_PATH) && !PyPackageUtil.hasRequirementsTxt(myModule)) {
      return "";
    }
    else {
      return path;
    }
  }

  private void initErrorValidation() {
  /*	final FacetErrorPanel facetErrorPanel = new FacetErrorPanel();
		myErrorPanel.add(facetErrorPanel.getComponent(), BorderLayout.CENTER);

		facetErrorPanel.getValidatorsManager().registerValidator(new FacetEditorValidator()
		{
			@NotNull
			@Override
			public ValidationResult check()
			{
				final Sdk sdk = PythonSdkType.findPythonSdk(myModule);
				if(sdk != null)
				{
					final Object selectedItem = myTestRunnerComboBox.getSelectedItem();
					if(PythonTestConfigurationsModel.PY_TEST_NAME.equals(selectedItem))
					{
						if(!VFSTestFrameworkListener.getInstance().isPyTestInstalled(sdk))
						{
							return new ValidationResult(PyBundle.message("runcfg.testing.no.test.framework", "py.test"), createQuickFix(sdk, facetErrorPanel, PyNames.PY_TEST));
						}
					}
					else if(PythonTestConfigurationsModel.PYTHONS_NOSETEST_NAME.equals(selectedItem))
					{
						if(!VFSTestFrameworkListener.getInstance().isNoseTestInstalled(sdk))
						{
							return new ValidationResult(PyBundle.message("runcfg.testing.no.test.framework", "nosetest"), createQuickFix(sdk, facetErrorPanel, PyNames.NOSE_TEST));
						}
					}
					else if(PythonTestConfigurationsModel.PYTHONS_ATTEST_NAME.equals(selectedItem))
					{
						if(!VFSTestFrameworkListener.getInstance().isAtTestInstalled(sdk))
						{
							return new ValidationResult(PyBundle.message("runcfg.testing.no.test.framework", "attest"), createQuickFix(sdk, facetErrorPanel, PyNames.AT_TEST));
						}
					}
				}
				return ValidationResult.OK;
			}
		}, myTestRunnerComboBox);

		facetErrorPanel.getValidatorsManager().validate(); */
  }

	/*private FacetConfigurationQuickFix createQuickFix(final Sdk sdk, final FacetErrorPanel facetErrorPanel, final String name)
	{
		return new FacetConfigurationQuickFix()
		{
			@Override
			public void run(JComponent place)
			{
				final PyPackageManagerUI ui = new PyPackageManagerUI(myProject, sdk, new PyPackageManagerUI.Listener()
				{
					@Override
					public void started()
					{
					}

					@Override
					public void finished(List<ExecutionException> exceptions)
					{
						if(exceptions.isEmpty())
						{
							VFSTestFrameworkListener.getInstance().setTestFrameworkInstalled(true, sdk.getHomePath(), name);
							facetErrorPanel.getValidatorsManager().validate();
						}
					}
				});
				ui.install(Collections.singletonList(new PyRequirement(name)), Collections.<String>emptyList());
			}
		};
	} */

  @Nls
  @Override
  public LocalizeValue getDisplayName() {
    return LocalizeValue.localizeTODO("Python Integrated Tools");
  }

  @Override
  public String getHelpTopic() {
    return "test_runner_configuration";
  }

  @Override
  public JComponent createComponent() {
    List<String> configurations = TestRunnerService.getInstance(myModule).getConfigurations();
    myModel =
      new PythonTestConfigurationsModel(configurations, TestRunnerService.getInstance(myModule).getProjectConfiguration(), myModule);

    updateConfigurations();
    initErrorValidation();
    return myMainPanel;
  }

  private void updateConfigurations() {
    //noinspection unchecked
    myTestRunnerComboBox.setModel(myModel);
  }

  @Override
  public boolean isModified() {
    if (myTestRunnerComboBox.getSelectedItem() != myModel.getTestRunner()) {
      return true;
    }
    if (myDocstringFormatComboBox.getSelectedItem() != myDocumentationSettings.getFormat()) {
      return true;
    }
    if (analyzeDoctest.isSelected() != myDocumentationSettings.isAnalyzeDoctest()) {
      return true;
    }
    if (!ReSTService.getInstance(myModule).getWorkdir().equals(myWorkDir.getText())) {
      return true;
    }
    if (!ReSTService.getInstance(myModule).txtIsRst() == txtIsRst.isSelected()) {
      return true;
    }
    if (!getRequirementsPath().equals(myRequirementsPathField.getText())) {
      return true;
    }
    return false;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myDocstringFormatComboBox.getSelectedItem() != myDocumentationSettings.getFormat()) {
      DaemonCodeAnalyzer.getInstance(myProject).restart();
    }
    if (analyzeDoctest.isSelected() != myDocumentationSettings.isAnalyzeDoctest()) {
      final List<VirtualFile> files = Lists.newArrayList();
      ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(new ContentIterator() {
        @Override
        public boolean processFile(VirtualFile fileOrDir) {
          if (!fileOrDir.isDirectory() && PythonFileType.INSTANCE.getDefaultExtension().equals(fileOrDir.getExtension())) {
            files.add(fileOrDir);
          }
          return true;
        }
      });
      FileContentUtil.reparseFiles(myProject, Lists.newArrayList(files), false);
    }
    myModel.apply();
    myDocumentationSettings.setFormat((DocStringFormat)myDocstringFormatComboBox.getSelectedItem());
    ReSTService reSTService = ReSTService.getInstance(myModule);
    reSTService.setWorkdir(myWorkDir.getText());
    if (txtIsRst.isSelected() != reSTService.txtIsRst()) {
      reSTService.setTxtIsRst(txtIsRst.isSelected());
      reparseFiles(Collections.singletonList(PlainTextFileType.INSTANCE.getDefaultExtension()));
    }
    myDocumentationSettings.setAnalyzeDoctest(analyzeDoctest.isSelected());
    PyPackageRequirementsSettings.getInstance(myModule).setRequirementsPath(myRequirementsPathField.getText());
    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  public void reparseFiles(final List<String> extensions) {
    final List<VirtualFile> filesToReparse = Lists.newArrayList();
    ProjectRootManager.getInstance(myProject).getFileIndex().iterateContent(new ContentIterator() {
      @Override
      public boolean processFile(VirtualFile fileOrDir) {
        if (!fileOrDir.isDirectory() && extensions.contains(fileOrDir.getExtension())) {
          filesToReparse.add(fileOrDir);
        }
        return true;
      }
    });
    FileContentUtilCore.reparseFiles(filesToReparse);

    PyUtil.rehighlightOpenEditors(myProject);

    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  @Override
  public void reset() {
    myTestRunnerComboBox.setSelectedItem(myModel.getTestRunner());
    myTestRunnerComboBox.repaint();
    myModel.reset();
    myDocstringFormatComboBox.setSelectedItem(myDocumentationSettings.getFormat());
    myWorkDir.setText(ReSTService.getInstance(myModule).getWorkdir());
    txtIsRst.setSelected(ReSTService.getInstance(myModule).txtIsRst());
    analyzeDoctest.setSelected(myDocumentationSettings.isAnalyzeDoctest());
    myRequirementsPathField.setText(getRequirementsPath());
  }

  @Override
  public void disposeUIResources() {
  }

  @Nonnull
  @Override
  public String getId() {
    return "PyIntegratedToolsConfigurable";
  }
}

