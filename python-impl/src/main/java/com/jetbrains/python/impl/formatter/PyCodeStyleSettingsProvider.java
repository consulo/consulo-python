/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

package com.jetbrains.python.impl.formatter;

import com.jetbrains.python.PythonLanguage;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.Configurable;
import consulo.language.Language;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CustomCodeStyleSettings;
import consulo.language.codeStyle.setting.CodeStyleSettingsProvider;
import consulo.language.codeStyle.ui.setting.CodeStyleAbstractConfigurable;
import consulo.language.codeStyle.ui.setting.CodeStyleAbstractPanel;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author yole
 */
@ExtensionImpl
public class PyCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
    @Override
    public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
        return new PyCodeStyleSettings(settings);
    }

    @Nonnull
    @Override
    public Configurable createSettingsPage(CodeStyleSettings settings, CodeStyleSettings originalSettings) {
        return new CodeStyleAbstractConfigurable(settings, originalSettings, getConfigurableDisplayName().get()) {
            @Override
            protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
                return new PyCodeStyleMainPanel(getCurrentSettings(), settings);
            }

            @Override
            public String getHelpTopic() {
                return "reference.settingsdialog.codestyle.python";
            }
        };
    }

    @Nullable
    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
