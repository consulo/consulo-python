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

package com.jetbrains.python.impl.sdk.flavors;

import com.jetbrains.python.impl.PythonIcons;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.annotation.component.ExtensionImpl;
import consulo.content.bundle.Sdk;
import consulo.ui.image.Image;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;

import java.io.File;
import java.util.List;

/**
 * @author traff
 */
@ExtensionImpl
public class PyPySdkFlavor extends PythonSdkFlavor {
    @Override
    public boolean isValidSdkPath(@Nonnull File file) {
        return FileUtil.getNameWithoutExtension(file).toLowerCase().startsWith("pypy");
    }

    @Override
    public String getVersionRegexp() {
        return "\\[(PyPy \\S+).*\\]";
    }

    @Override
    public String getVersionOption() {
        return "--version";
    }

    @Nonnull
    @Override
    public String getName() {
        return "PyPy";
    }

    @Override
    public LanguageLevel getLanguageLevel(Sdk sdk) {
        final String version = sdk.getVersionString();
        final String prefix = getName() + " ";
        if (version != null && version.startsWith(prefix)) {
            String pypyVersion = version.substring(prefix.length());
            return LanguageLevel.fromPythonVersion(getPythonVersion(pypyVersion));
        }
        return LanguageLevel.getDefault();
    }

    private static String getPythonVersion(@Nonnull String pypyVersion) {
        final String DEFAULT = "2.4";
        final String LATEST = "2.7";
        final List<String> vs = StringUtil.split(pypyVersion, ".");
        try {
            if (vs.size() >= 2) {
                final int major = Integer.parseInt(vs.get(0));
                final int minor = Integer.parseInt(vs.get(1));
                if (major > 1) {
                    return LATEST;
                }
                else if (major == 1) {
                    if (minor >= 5) {
                        return "2.7";
                    }
                    else if (minor >= 4) {
                        return "2.5";
                    }
                }
            }
        }
        catch (NumberFormatException ignored) {
        }
        return DEFAULT;
    }

    @Override
    public Image getIcon() {
        return PythonIcons.Python.Pypy;
    }
}
