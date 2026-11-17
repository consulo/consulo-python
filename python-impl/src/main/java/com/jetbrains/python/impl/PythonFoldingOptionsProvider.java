// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ApplicationConfigurable;
import consulo.configurable.SimpleConfigurableByProperties;
import consulo.disposer.Disposable;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.ui.CheckBox;
import consulo.ui.Component;
import consulo.ui.Label;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.LabeledLayout;
import consulo.ui.layout.VerticalLayout;
import consulo.ui.style.ComponentColors;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.jspecify.annotations.Nullable;

/**
 * JB: {@code CodeFoldingOptionsProvider} with UI DSL check boxes; in Consulo the Python section of Editor > Code Folding is a configurable.
 */
@ExtensionImpl
public class PythonFoldingOptionsProvider extends SimpleConfigurableByProperties implements ApplicationConfigurable {
    private final Provider<PythonFoldingSettings> mySettingsProvider;

    @Inject
    public PythonFoldingOptionsProvider(Provider<PythonFoldingSettings> settingsProvider) {
        mySettingsProvider = settingsProvider;
    }

    @Override
    public String getId() {
        return "editor.preferences.folding.python";
    }

    @Override
    public @Nullable String getParentId() {
        return "editor.preferences.folding";
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PyCodeInsightLocalize.pythonFoldingOptionsTitle();
    }

    @RequiredUIAccess
    @Override
    protected Component createLayout(PropertyBuilder propertyBuilder, Disposable uiDisposable) {
        PythonFoldingSettings settings = mySettingsProvider.get();
        VerticalLayout layout = VerticalLayout.create();

        CheckBox longStringLiterals = CheckBox.create(PyCodeInsightLocalize.pythonLongStringLiterals());
        propertyBuilder.add(longStringLiterals, () -> settings.COLLAPSE_LONG_STRINGS, value -> settings.COLLAPSE_LONG_STRINGS = value);
        layout.add(longStringLiterals);

        CheckBox longCollectionLiterals = CheckBox.create(PyCodeInsightLocalize.pythonLongCollectionLiterals());
        propertyBuilder.add(longCollectionLiterals, () -> settings.COLLAPSE_LONG_COLLECTIONS, value -> settings.COLLAPSE_LONG_COLLECTIONS = value);
        layout.add(longCollectionLiterals);

        CheckBox sequentialComments = CheckBox.create(PyCodeInsightLocalize.pythonSequentialComments());
        propertyBuilder.add(sequentialComments, () -> settings.COLLAPSE_SEQUENTIAL_COMMENTS, value -> settings.COLLAPSE_SEQUENTIAL_COMMENTS = value);
        layout.add(sequentialComments);

        CheckBox typeAnnotations = CheckBox.create(PyCodeInsightLocalize.pythonTypeAnnotations());
        propertyBuilder.add(typeAnnotations, () -> settings.COLLAPSE_TYPE_ANNOTATIONS, value -> settings.COLLAPSE_TYPE_ANNOTATIONS = value);
        layout.add(typeAnnotations);
        // JB: CheckboxDescriptor comment
        Label typeAnnotationsHint = Label.create(PyCodeInsightLocalize.pythonTypeAnnotationsHint());
        typeAnnotationsHint.setForegroundColor(ComponentColors.DISABLED_TEXT);
        layout.add(typeAnnotationsHint);

        return LabeledLayout.create(PyCodeInsightLocalize.pythonFoldingOptionsTitle(), layout);
    }

    // TODO consulo: ConfigurableWithOptionDescriptors#getOptionDescriptors missing
}
