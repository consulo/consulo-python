// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ApplicationConfigurable;
import consulo.configurable.BeanConfigurable;
import consulo.configurable.SearchableConfigurable;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyCodeInsightLocalize;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public class PySmartKeysOptions extends BeanConfigurable<PyCodeInsightSettings> implements SearchableConfigurable, ApplicationConfigurable {

    public PySmartKeysOptions() {
        super(PyCodeInsightSettings.getInstance());
        //CodeInsightSettings.getInstance().REFORMAT_ON_PASTE = CodeInsightSettings.NO_REFORMAT;   //TODO: remove combobox from settings
        // TODO consulo: BeanConfigurable#checkBox(title, getter, setter) missing - JB binds a check box to another bean:
        //  checkBox(PyCodeInsightBundle.message("form.edit.smart.indent.pasted.lines"), CodeInsightSettings.getInstance()::INDENT_TO_CARET_ON_PASTE)
        checkBox("PARENTHESISE_ON_ENTER", PyCodeInsightLocalize.smartkeysWrapInParenthesesInsteadOfBackslash().get());
        checkBox("INSERT_SELF_FOR_METHODS", PyLocalize.smartkeysInsertSelfInMethod().get());
        checkBox("INSERT_TYPE_DOCSTUB", PyLocalize.smartkeysInsertTypePlaceholderInDocstringStub().get());
    }

    @Override
    public LocalizeValue getDisplayName() {
        return PyCodeInsightLocalize.configurablePysmartkeysoptionsDisplayName();
    }

    @Override
    public String getId() {
        return "editor.preferences.pyOptions";
    }

    // Consulo: JB registers it as editorSmartKeysConfigurable
    @Override
    public @Nullable String getParentId() {
        return "editor.preferences.smartKeys";
    }

    @Override
    public @Nullable String getHelpTopic() {
        return "reference.settings.editor.smart.keys.python";
    }
}
