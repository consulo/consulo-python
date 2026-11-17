// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.hierarchy;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.ide.localize.IdeLocalize;
import consulo.language.editor.hierarchy.HierarchyNodeDescriptor;
import consulo.language.editor.localize.LanguageEditorLocalize;
import consulo.language.psi.NavigatablePsiElement;
import consulo.language.psi.PsiElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.navigation.ItemPresentation;
import consulo.navigation.Navigatable;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.ex.tree.NodeDescriptor;
import consulo.ui.ex.util.CompositeAppearance;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.Comparing;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;

public class PyHierarchyNodeDescriptor extends HierarchyNodeDescriptor implements Navigatable {

    private final List<SmartPsiElementPointer<PsiElement>> myUsages;

    public PyHierarchyNodeDescriptor(@Nullable NodeDescriptor parentDescriptor, PsiElement element, boolean isBase) {
        this(parentDescriptor, element, Collections.emptySet(), isBase);
    }

    public PyHierarchyNodeDescriptor(@Nullable NodeDescriptor parentDescriptor,
                                     PsiElement element,
                                     Collection<? extends PsiElement> usages,
                                     boolean isBase) {
        super(element.getProject(), parentDescriptor, element, isBase);
        SmartPointerManager pointerManager = SmartPointerManager.getInstance(element.getProject());
        myUsages = ContainerUtil.map(usages, it -> pointerManager.createSmartPsiElementPointer((PsiElement) it));
    }

    @RequiredUIAccess
    @Override
    public boolean update() {
        boolean changes = super.update();
        CompositeAppearance oldText = myHighlightedText;

        myHighlightedText = new CompositeAppearance();

        NavigatablePsiElement element = (NavigatablePsiElement) getPsiElement();
        if (element == null) {
            // Consulo: inlined HierarchyNodeDescriptor#invalidElement()
            String invalidPrefix = LanguageEditorLocalize.nodeHierarchyInvalid().get();
            if (!myHighlightedText.getText().startsWith(invalidPrefix)) {
                myHighlightedText.getBeginning().addText(invalidPrefix, HierarchyNodeDescriptor.getInvalidPrefixAttributes());
            }
            return true;
        }

        ItemPresentation presentation = element.getPresentation();
        if (presentation != null) {
            if (element instanceof PyFunction) {
                PyClass cls = ((PyFunction) element).getContainingClass();
                if (cls != null) {
                    myHighlightedText.getEnding().addText(cls.getName() + ".");
                }
            }
            myHighlightedText.getEnding().addText(presentation.getPresentableText());
            int count = myUsages.size();
            if (count > 1) {
                String text = IdeLocalize.nodeCallHierarchyNUsages(count).get();
                myHighlightedText.getEnding().addText(text, HierarchyNodeDescriptor.getUsageCountPrefixAttributes());
            }
            String locationString = presentation.getLocationString();
            if (locationString != null) {
                myHighlightedText.getEnding().addText(" " + locationString, HierarchyNodeDescriptor.getPackageNameAttributes());
            }
        }
        myName = myHighlightedText.getText();

        if (!Comparing.equal(myHighlightedText, oldText)) {
            changes = true;
        }
        return changes;
    }

    @Override
    public void navigate(boolean requestFocus) {
        Navigatable element = getNavigationTarget();
        if (element != null && element.canNavigate()) {
            element.navigate(requestFocus);
        }
    }

    @Override
    public boolean canNavigate() {
        Navigatable element = getNavigationTarget();
        return element != null && element.canNavigate();
    }

    @Override
    public boolean canNavigateToSource() {
        return canNavigate();
    }

    private @Nullable Navigatable getNavigationTarget() {
        if (!myUsages.isEmpty()) {
            Navigatable firstUsage = as(myUsages.get(0).getElement(), Navigatable.class);
            if (firstUsage != null) {
                return firstUsage;
            }
        }
        return as(getPsiElement(), Navigatable.class);
    }

    // Consulo: HierarchyNodeDescriptor extras
    @Override
    public boolean canBeDeleted() {
        return getPsiElement() instanceof PyClass;
    }

    @Override
    public @Nullable String getQualifiedName() {
        return getPsiElement() instanceof PyClass pyClass ? pyClass.getName() : null;
    }
}
