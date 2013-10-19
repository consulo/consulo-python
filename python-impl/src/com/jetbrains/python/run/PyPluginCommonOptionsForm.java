package com.jetbrains.python.run;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JPanel;

import org.consulo.python.module.extension.PyModuleExtension;
import org.jetbrains.annotations.Nullable;
import com.intellij.application.options.ModuleListCellRenderer;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.util.PathMappingsComponent;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ui.configuration.ModulesAlphaComparator;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.HideableDecorator;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.PathMappingSettings;
import com.jetbrains.python.sdk.PySdkUtil;

/**
 * @author yole
 */
public class PyPluginCommonOptionsForm implements AbstractPyCommonOptionsForm {
  private final Project myProject;
  private TextFieldWithBrowseButton myWorkingDirectoryTextField;
  private EnvironmentVariablesComponent myEnvsComponent;
  private RawCommandLineEditor myInterpreterOptionsTextField;
  private JComboBox myModuleComboBox;
  private JPanel myMainPanel;
  private JBLabel myPythonInterpreterJBLabel;
  private JBLabel myInterpreterOptionsJBLabel;
  private JBLabel myWorkingDirectoryJBLabel;
  private JPanel myHideablePanel;
  private PathMappingsComponent myPathMappingsComponent;
  private JBCheckBox myAddContentRootsCheckbox;
  private JBCheckBox myAddSourceRootsCheckbox;
  private JComponent labelAnchor;
  private final HideableDecorator myDecorator;

  public PyPluginCommonOptionsForm(PyCommonOptionsFormData data) {
    // setting modules
    myProject = data.getProject();
    final List<Module> validModules = data.getValidModules();
    Collections.sort(validModules, new ModulesAlphaComparator());
    Module selection = validModules.size() > 0 ? validModules.get(0) : null;
    myModuleComboBox.setModel(new CollectionComboBoxModel(validModules, selection));
    myModuleComboBox.setRenderer(new ModuleListCellRenderer());

    myWorkingDirectoryTextField.addBrowseFolderListener("Select Working Directory", "", data.getProject(),
                                                  FileChooserDescriptorFactory.createSingleFolderDescriptor());

    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateControls();
      }
    };
    myModuleComboBox.addActionListener(listener);

    setAnchor(myEnvsComponent.getLabel());


    myDecorator = new HideableDecorator(myHideablePanel, "Environment", false) {
      @Override
      protected void on() {
        super.on();
        storeState();
      }

      @Override
      protected void off() {
        super.off();
        storeState();
      }
      private void storeState() {
        PropertiesComponent.getInstance().setValue(EXPAND_PROPERTY_KEY, String.valueOf(isExpanded()));
      }
    };
    myDecorator.setOn(PropertiesComponent.getInstance().getBoolean(EXPAND_PROPERTY_KEY, true));
    myDecorator.setContentComponent(myMainPanel);
    myPathMappingsComponent.setAnchor(myEnvsComponent.getLabel());
    updateControls();
  }

  private void updateControls() {
    myPathMappingsComponent.setVisible(PySdkUtil.isRemote(getSelectedSdk()));
  }

  public JPanel getMainPanel() {
    return myHideablePanel;
  }

  @Override
  public void subscribe() {
  }

  public String getInterpreterOptions() {
    return myInterpreterOptionsTextField.getText().trim();
  }

  public void setInterpreterOptions(String interpreterOptions) {
    myInterpreterOptionsTextField.setText(interpreterOptions);
  }

  public String getWorkingDirectory() {
    return FileUtil.toSystemIndependentName(myWorkingDirectoryTextField.getText().trim());
  }

  public void setWorkingDirectory(String workingDirectory) {
    myWorkingDirectoryTextField.setText(workingDirectory == null ? "" : FileUtil.toSystemDependentName(workingDirectory));
  }

  @Nullable
  public String getSdkHome() {
    return null;
  }

  public void setSdkHome(String sdkHome) {

  }

  public Module getModule() {
    return (Module)myModuleComboBox.getSelectedItem();
  }

  public void setModule(Module module) {
    myModuleComboBox.setSelectedItem(module);
  }

  public boolean isUseModuleSdk() {
    return true;
  }

  public void setUseModuleSdk(boolean useModuleSdk) {
    updateControls();
  }

  public boolean isPassParentEnvs() {
    return myEnvsComponent.isPassParentEnvs();
  }

  public void setPassParentEnvs(boolean passParentEnvs) {
    myEnvsComponent.setPassParentEnvs(passParentEnvs);
  }

  public Map<String, String> getEnvs() {
    return myEnvsComponent.getEnvs();
  }

  public void setEnvs(Map<String, String> envs) {
    myEnvsComponent.setEnvs(envs);
  }

  @Override
  public PathMappingSettings getMappingSettings() {
    return myPathMappingsComponent.getMappingSettings();
  }

  @Override
  public void setMappingSettings(@Nullable PathMappingSettings mappingSettings) {
    myPathMappingsComponent.setMappingSettings(mappingSettings);
  }

  private Sdk getSelectedSdk() {
    if (isUseModuleSdk()) {
      Module module = getModule();
      return module == null ? null : ModuleUtilCore.getSdk(module, PyModuleExtension.class);
	}
    return null;
  }

  @Override
  public JComponent getAnchor() {
    return labelAnchor;
  }

  @Override
  public void setAnchor(JComponent anchor) {
    labelAnchor = anchor;
    myPythonInterpreterJBLabel.setAnchor(anchor);
    myInterpreterOptionsJBLabel.setAnchor(anchor);
    myWorkingDirectoryJBLabel.setAnchor(anchor);
    myEnvsComponent.setAnchor(anchor);
  }

  @Override
  public boolean addContentRoots() {
    return myAddContentRootsCheckbox.isSelected();
  }

  @Override
  public boolean addSourceRoots() {
    return myAddSourceRootsCheckbox.isSelected();
  }

  @Override
  public void addContentRoots(boolean add) {
    myAddContentRootsCheckbox.setSelected(add);
  }

  @Override
  public void addSourceRoots(boolean add) {
    myAddSourceRootsCheckbox.setSelected(add);
  }

}
