// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ApplicationConfigurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public class PythonCodeCompletionConfigurable extends SimpleConfigurableByProperties implements ApplicationConfigurable {
    @Override
    public String getId() {
        return "editor.preferences.completion.python";
    }

    @Override
    public @Nullable String getParentId() {
        return "editor.preferences.completion";
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PyCodeInsightLocalize.configurablePythoncodecompletionconfigurableDisplayNamePython();
    }

    @RequiredUIAccess
    @Override
    protected Component createLayout(PropertyBuilder propertyBuilder, Disposable uiDisposable) {
        PyCodeInsightSettings settings = PyCodeInsightSettings.getInstance();

        VerticalLayout group = VerticalLayout.create();

        CheckBox suggestImportableNames =
            CheckBox.create(PyCodeInsightLocalize.configurablePythoncodecompletionconfigurableCheckboxSuggestImportableNames());
        // JB: contextHelp(...)
        suggestImportableNames.setToolTipText(
            PyCodeInsightLocalize.configurablePythoncodecompletionconfigurableCheckboxSuggestImportableNamesHelp()
        );
        propertyBuilder.add(
            suggestImportableNames,
            () -> settings.INCLUDE_IMPORTABLE_NAMES_IN_BASIC_COMPLETION,
            value -> settings.INCLUDE_IMPORTABLE_NAMES_IN_BASIC_COMPLETION = value
        );
        group.add(suggestImportableNames);

        CheckBox copyAnnotationsFromStubs =
            CheckBox.create(PyCodeInsightLocalize.configurablePythoncodecompletionconfigurableCheckboxCopyAnnotationsFromStubs());
        propertyBuilder.add(
            copyAnnotationsFromStubs,
            () -> settings.COPY_TYPE_ANNOTATIONS_FROM_STUBS,
            value -> settings.COPY_TYPE_ANNOTATIONS_FROM_STUBS = value
        );
        group.add(copyAnnotationsFromStubs);

        return LabeledLayout.create(PyCodeInsightLocalize.configurablePythoncodecompletionconfigurableBorderTitle(), group);
    }
}
