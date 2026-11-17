// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ApplicationConfigurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.RadioGroup;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import org.jspecify.annotations.Nullable;

/**
 * JB registers it as {@code autoImportOptionsProvider}, in Consulo it is a child configurable of "editor.preferences.import".
 */
@ExtensionImpl
public class PyAutoImportOptions extends SimpleConfigurableByProperties implements ApplicationConfigurable {
    @Override
    public String getId() {
        return "editor.preferences.import.python";
    }

    @Override
    public @Nullable String getParentId() {
        return "editor.preferences.import";
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PyCodeInsightLocalize.formAutoImportPython();
    }

    @RequiredUIAccess
    @Override
    protected Component createLayout(PropertyBuilder propertyBuilder, Disposable uiDisposable) {
        PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();

        VerticalLayout group = VerticalLayout.create();

        CheckBox showPopup = CheckBox.create(PyCodeInsightLocalize.formAutoImportAutoImportShowPopup());
        propertyBuilder.add(showPopup, () -> settings.SHOW_IMPORT_POPUP, value -> settings.SHOW_IMPORT_POPUP = value);
        group.add(showPopup);

        RadioGroup<Boolean> importStyle = RadioGroup.create();
        VerticalLayout buttons = VerticalLayout.create();
        buttons.add(importStyle.newButton(PyCodeInsightLocalize.formAutoImportFromModuleImportName(), true));
        buttons.add(importStyle.newButton(PyCodeInsightLocalize.formAutoImportImportModuleName(), false));
        propertyBuilder.add(importStyle, () -> settings.PREFER_FROM_IMPORT, value -> settings.PREFER_FROM_IMPORT = value);
        group.add(LabeledLayout.create(PyCodeInsightLocalize.formAutoImportPreferredImportStyle(), buttons));

        return LabeledLayout.create(PyCodeInsightLocalize.formAutoImportPython(), group);
    }
}
