/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.packaging.ui;

import com.jetbrains.python.packaging.PyRequirement;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.ui.ex.awt.ElementsChooser;
import consulo.ui.ex.awt.JBLabel;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author vlan
 */
public class PyChooseRequirementsDialog extends DialogWrapper {
  private final ElementsChooser<PyRequirement> myRequirementsChooser;

  public PyChooseRequirementsDialog(@Nonnull Project project, @Nonnull List<PyRequirement> requirements) {
    super(project, false);
    setTitle("Choose Packages to Install");
    setOKButtonText("Install");
    myRequirementsChooser = new ElementsChooser<PyRequirement>(true) {
      @Override
      public String getItemText(@Nonnull PyRequirement requirement) {
        return requirement.toString();
      }
    };
    myRequirementsChooser.setElements(requirements, true);
    myRequirementsChooser.addElementsMarkListener(new ElementsChooser.ElementsMarkListener<PyRequirement>() {
      @Override
      public void elementMarkChanged(PyRequirement element, boolean isMarked) {
        setOKActionEnabled(!myRequirementsChooser.getMarkedElements().isEmpty());
      }
    });
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    final JPanel panel = new JPanel(new BorderLayout());
    panel.setPreferredSize(new Dimension(400, 300));
    final JBLabel label = new JBLabel("Choose packages to install:");
    label.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));
    panel.add(label, BorderLayout.NORTH);
    panel.add(myRequirementsChooser, BorderLayout.CENTER);
    return panel;
  }

  public List<PyRequirement> getMarkedElements() {
    return myRequirementsChooser.getMarkedElements();
  }
}
