package com.jetbrains.python.run;

import javax.swing.JComponent;

import org.jetbrains.annotations.NonNls;
import com.intellij.ui.PanelWithAnchor;

/**
 * @author yole
 */
public interface AbstractPyCommonOptionsForm extends AbstractPythonRunConfigurationParams, PanelWithAnchor {
  @NonNls String EXPAND_PROPERTY_KEY = "ExpandEnvironmentPanel";

  JComponent getMainPanel();

  void subscribe();
}
