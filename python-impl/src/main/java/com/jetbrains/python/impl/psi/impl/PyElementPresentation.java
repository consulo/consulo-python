// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.ui.ex.ColoredItemPresentation;
import consulo.colorScheme.TextAttributesKey;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiFile;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyPossibleClassMember;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.pyi.PyiFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

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
        String name = myElement.getName();
        return LocalizeValue.of(name != null ? name : PyNames.UNNAMED_ELEMENT);
    }

    @Override
    @RequiredReadAction
    public @Nullable String getLocationString() {
        PsiFile containingFile = myElement.getContainingFile();

        String packageForFile = getPackageForFile(containingFile);
        if (packageForFile == null) {
            return null;
        }

        boolean isPyiFile = containingFile instanceof PyiFile;

        PyClass containingClass = myElement instanceof PyPossibleClassMember ? ((PyPossibleClassMember)myElement).getContainingClass() : null;
        if (containingClass != null) {
            if (isPyiFile) {
                return PyLocalize.elementPresentationLocationStringInClassStub(containingClass.getName(), packageForFile).get();
            }
            else {
                return PyLocalize.elementPresentationLocationStringInClass(containingClass.getName(), packageForFile).get();
            }
        }

        if (isPyiFile) {
            return PyLocalize.elementPresentationLocationStringModuleStub(packageForFile).get();
        }
        else {
            return PyLocalize.elementPresentationLocationStringModule(packageForFile).get();
        }
    }

    @Override
    @RequiredReadAction
    public @Nullable Image getIcon() {
        return IconDescriptorUpdaters.getIcon(myElement, 0);
    }

    public static @Nullable String getPackageForFile(PsiFile containingFile) {
        VirtualFile vFile = containingFile.getVirtualFile();

        if (vFile != null) {
            return QualifiedNameFinder.findShortestImportableName(containingFile, vFile);
        }
        return null;
    }
}
