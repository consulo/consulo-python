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
package com.jetbrains.python.impl.hierarchy;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.language.editor.hierarchy.HierarchyNodeDescriptor;
import consulo.language.editor.localize.LanguageEditorLocalize;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.navigation.ItemPresentation;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.ui.ex.util.CompositeAppearance;
import consulo.util.lang.Comparing;
import org.jspecify.annotations.Nullable;

/**
 * @author Alexey.Ivanov
 * @since 2009-07-31
 */
public class PyHierarchyNodeDescriptor extends HierarchyNodeDescriptor {
    public PyHierarchyNodeDescriptor(NodeDescriptor parentDescriptor, PsiElement element, boolean isBase) {
        super(element.getProject(), parentDescriptor, element, isBase);
    }

    @RequiredUIAccess
    @Override
    public boolean update() {
        boolean changes = super.update();
        CompositeAppearance oldText = myHighlightedText;

        myHighlightedText = new CompositeAppearance();

        NavigatablePsiElement element = (NavigatablePsiElement)getPsiElement();
        if (element == null) {
            String invalidPrefix = LanguageEditorLocalize.nodeHierarchyInvalid().get();
            if (!myHighlightedText.getText().startsWith(invalidPrefix)) {
                myHighlightedText.getBeginning().addText(invalidPrefix, HierarchyNodeDescriptor.getInvalidPrefixAttributes());
            }
            return true;
        }

        ItemPresentation presentation = element.getPresentation();
        if (presentation != null) {
            if (element instanceof PyFunction function) {
                PyClass cls = function.getContainingClass();
                if (cls != null) {
                    myHighlightedText.getEnding().addText(cls.getName() + ".");
                }
            }
            myHighlightedText.getEnding().addText(presentation.getPresentableText());
            myHighlightedText.getEnding()
                .addText(" " + presentation.getLocationString(), HierarchyNodeDescriptor.getPackageNameAttributes());
        }
        myName = myHighlightedText.getText();

        if (!Comparing.equal(myHighlightedText, oldText)) {
            changes = true;
        }
        return changes;
    }

    @Override
    public boolean canBeDeleted() {
        return getPsiElement() instanceof PyClass;
    }

    @Override
    public @Nullable String getQualifiedName() {
        return getPsiElement() instanceof PyClass pyClass ? pyClass.getName() : null;
    }
}
