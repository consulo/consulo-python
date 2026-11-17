// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.pyi;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.codeEditor.markup.GutterIconRenderer;
import consulo.language.Language;
import consulo.language.editor.Pass;
import consulo.language.editor.gutter.RelatedItemLineMarkerInfo;
import consulo.language.editor.gutter.RelatedItemLineMarkerProvider;
import consulo.language.navigation.GotoRelatedItem;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNameIdentifierOwner;
import consulo.language.psi.PsiNavigationSupport;
import consulo.language.psi.PsiUtilCore;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.ui.image.Image;
import consulo.virtualFileSystem.VirtualFile;

import java.util.Collection;
import java.util.Collections;

@ExtensionImpl
public final class PyiRelatedItemLineMarkerProvider extends RelatedItemLineMarkerProvider {
    // TODO: Create an icon for a related Python stub item
    public static final Image ICON = AllIcons.Gutter.Unique;

    @Override
    @RequiredReadAction
    protected void collectNavigationMarkers(PsiElement element, Collection<? super RelatedItemLineMarkerInfo> result) {
        if (element instanceof PyFunction || element instanceof PyTargetExpression || element instanceof PyClass) {
            PsiElement pythonStub = PyiUtil.getPythonStub((PyElement) element);
            if (pythonStub != null) {
                PsiElement identifier = ((PsiNameIdentifierOwner) element).getNameIdentifier();
                if (identifier != null) {
                    result.add(createLineMarkerInfo(identifier, pythonStub, "Has stub item"));
                }
            }
            PsiElement originalElement = PyiUtil.getOriginalElement((PyElement) element);
            if (originalElement != null) {
                PsiElement identifier = ((PsiNameIdentifierOwner) element).getNameIdentifier();
                if (identifier != null) {
                    result.add(createLineMarkerInfo(identifier, originalElement, "Stub for item"));
                }
            }
        }
    }

    @RequiredReadAction
    private static RelatedItemLineMarkerInfo<PsiElement> createLineMarkerInfo(PsiElement element,
                                                                             PsiElement relatedElement,
                                                                             String itemTitle) {
        SmartPointerManager pointerManager = SmartPointerManager.getInstance(element.getProject());
        SmartPsiElementPointer<PsiElement> relatedElementPointer = pointerManager.createSmartPsiElementPointer(relatedElement);
        String stubFileName = relatedElement.getContainingFile().getName();
        return new RelatedItemLineMarkerInfo<>(
            element, element.getTextRange(), ICON, Pass.LINE_MARKERS,
            element1 -> itemTitle + " in " + stubFileName, (e, elt) -> {
                PsiElement restoredRelatedElement = relatedElementPointer.getElement();
                if (restoredRelatedElement == null) {
                    return;
                }
                int offset = restoredRelatedElement instanceof PsiFile ? -1 : restoredRelatedElement.getTextOffset();
                VirtualFile virtualFile = PsiUtilCore.getVirtualFile(restoredRelatedElement);
                if (virtualFile != null && virtualFile.isValid()) {
                    PsiNavigationSupport.getInstance()
                        .createNavigatable(restoredRelatedElement.getProject(), virtualFile, offset)
                        .navigate(true);
                }
            }, GutterIconRenderer.Alignment.RIGHT, () -> GotoRelatedItem.createItems(Collections.singletonList(relatedElement)));
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
