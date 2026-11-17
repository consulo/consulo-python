// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.findUsages;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.PyFile;
import consulo.content.scope.SearchScope;
import consulo.find.ui.AbstractFindUsagesDialog;
import consulo.find.ui.CommonFindUsagesDialog;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiReference;
import consulo.language.psi.search.ReferencesSearch;
import consulo.ui.ex.SimpleTextAttributes;
import consulo.ui.ex.awt.SimpleColoredComponent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Important note: please update PyFindUsagesHandlerFactory#proxy on any changes here.
 */
public class PyModuleFindUsagesHandler extends PyFindUsagesHandler {
    protected final PsiFileSystemItem myElement;

    protected PyModuleFindUsagesHandler(PsiFileSystemItem file) {
        super(file);
        PsiElement e = PyUtil.turnInitIntoDir(file);
        myElement = e instanceof PsiFileSystemItem ? (PsiFileSystemItem) e : file;
    }

    @Override
    public PsiElement[] getPrimaryElements() {
        return new PsiElement[]{myElement};
    }

    // Consulo: label customization of the (deprecated) Swing find usages dialog
    @Override
    @SuppressWarnings("deprecation")
    public AbstractFindUsagesDialog getFindUsagesDialog(boolean isSingleFile, boolean toShowInNewTab, boolean mustOpenInNewTab) {
        return new CommonFindUsagesDialog(myElement,
            getProject(),
            getFindUsagesOptions(),
            toShowInNewTab,
            mustOpenInNewTab,
            isSingleFile,
            this) {
            @Override
            public void configureLabelComponent(SimpleColoredComponent coloredComponent) {
                coloredComponent.append(myElement instanceof PsiDirectory ? "Package " : "Module ");
                coloredComponent.append(myElement.getName(), SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
            }
        };
    }

    @Override
    public Collection<PsiReference> findReferencesToHighlight(PsiElement target, SearchScope searchScope) {
        PsiElement resolvedTarget = target;
        if (resolvedTarget instanceof PyImportedModule) {
            resolvedTarget = ((PyImportedModule) resolvedTarget).resolve();
        }
        if (resolvedTarget instanceof PyFile && PyNames.INIT_DOT_PY.equals(((PyFile) resolvedTarget).getName())) {
            List<PsiReference> result = new ArrayList<>(super.findReferencesToHighlight(resolvedTarget, searchScope));
            PsiElement targetDir = PyUtil.turnInitIntoDir(resolvedTarget);
            if (targetDir != null) {
                result.addAll(ReferencesSearch.search(targetDir, searchScope, false).findAll());
            }
            return result;
        }
        return super.findReferencesToHighlight(resolvedTarget, searchScope);
    }
}
