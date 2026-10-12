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
package com.jetbrains.python.impl.psi.impl;

import consulo.annotation.access.RequiredReadAction;
import consulo.localize.LocalizeValue;
import org.jspecify.annotations.Nullable;

import consulo.ui.ex.ColoredItemPresentation;
import consulo.colorScheme.TextAttributesKey;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.ui.image.Image;

/**
 * @author vlan
 */
public class PyElementPresentation implements ColoredItemPresentation {
    private final PyElement myElement;

    public PyElementPresentation(PyElement element) {
        myElement = element;
    }

    @Override
    public @Nullable TextAttributesKey getTextAttributesKey() {
        return null;
    }

    @Override
    public LocalizeValue getPresentableText() {
        return LocalizeValue.ofNullable(myElement.getName()).orIfEmpty(PyNames.UNNAMED_ELEMENT);
    }

    @Override
    public @Nullable String getLocationString() {
        return "(" + getPackageForFile(myElement.getContainingFile()) + ")";
    }

    @Override
    @RequiredReadAction
    public @Nullable Image getIcon() {
        return IconDescriptorUpdaters.getIcon(myElement, 0);
    }

    public static String getPackageForFile(PsiFile containingFile) {
        VirtualFile vFile = containingFile.getVirtualFile();

        if (vFile != null) {
            String importableName = QualifiedNameFinder.findShortestImportableName(containingFile, vFile);
            if (importableName != null) {
                return importableName;
            }
        }
        return "";
    }
}
