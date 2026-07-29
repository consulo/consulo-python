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
package com.jetbrains.python.impl.codeInsight;

import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.ui.PsiElementListCellRenderer;
import consulo.navigation.ItemPresentation;
import consulo.navigation.NavigationItem;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;

/**
 * @author yole
 */
public class PyElementListCellRenderer extends PsiElementListCellRenderer {
    @Override
    @RequiredReadAction
    public String getElementText(PsiElement element) {
        if (element instanceof PsiNamedElement namedElem) {
            String name = namedElem.getName();
            return name == null ? "" : name;
        }
        return element.getText();
    }

    @Override
    protected String getContainerText(PsiElement element, String name) {
        if (element instanceof NavigationItem navItem) {
            ItemPresentation presentation = navItem.getPresentation();
            if (presentation != null) {
                return presentation.getLocationString();
            }
        }
        return null;
    }

    @Override
    protected int getIconFlags() {
        return 0;
    }
}
