// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest.editor;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ApplicationConfigurable;
import consulo.configurable.Configurable;
import consulo.configurable.SearchableConfigurable;
import consulo.configurable.StandardConfigurableIds;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.python.rest.localize.PythonRestLocalize;
import consulo.ui.ComboBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.DockLayout;
import consulo.ui.util.LabeledBuilder;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public class RestConfigurable implements SearchableConfigurable, Configurable.NoScroll, ApplicationConfigurable {
  public static final String ID = "restructured.text.topic";
  public static final String SWING = "Swing";
  public static final String JCEF = "JCEF";

  private @Nullable DockLayout myPanel;
  private @Nullable ComboBox<String> myComboBox;

  @RequiredUIAccess
  @Override
  public Component createUIComponent(Disposable parentDisposable) {
    if (myPanel == null) {
      List<String> items = new ArrayList<>();
      // TODO consulo: JBCefApp missing, JCEF item is never added
      items.add(SWING);
      myComboBox = ComboBox.create(items);
      myComboBox.setValue(RestSettings.getInstance().getCurrentPanel());
      myPanel = DockLayout.create();
      myPanel.top(LabeledBuilder.sided(PythonRestLocalize.previewPanel(), myComboBox));
    }
    return myPanel;
  }

  @Override
  public String getId() {
    return ID;
  }

  @Override
  public @Nullable String getParentId() {
    // TODO consulo: JB registers it in the "language" group, which is missing in Consulo
    return StandardConfigurableIds.EDITOR_GROUP;
  }

  @Override
  public LocalizeValue getDisplayName() {
    return PythonRestLocalize.configurableRestconfigurableDisplayName();
  }

  @Override
  public String getHelpTopic() {
    return ID;
  }

  @RequiredUIAccess
  @Override
  public boolean isModified() {
    return myComboBox != null && !RestSettings.getInstance().getCurrentPanel().equals(myComboBox.getValue());
  }

  @RequiredUIAccess
  @Override
  public void apply() {
    final String selectedItem = myComboBox == null ? null : myComboBox.getValue();
    if (selectedItem != null) {
      RestSettings.getInstance().setCurrentPanel(selectedItem);
    }
  }

  @RequiredUIAccess
  @Override
  public void disposeUIResources() {
    myPanel = null;
    myComboBox = null;
  }
}
