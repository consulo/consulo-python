// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.search.PySearchUtilBase;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.impl.psi.stubs.PyModuleNameIndex;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.PossiblyDumbAware;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.ide.navigation.GotoClassOrTypeContributor;
import consulo.language.Language;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.search.FindSymbolParameters;
import consulo.language.psi.stub.DumbModeAccessType;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.stub.IdFilter;
import consulo.language.psi.stub.StubIndex;
import consulo.navigation.NavigationItem;
import consulo.project.Project;
import consulo.project.content.scope.ProjectAwareSearchScope;
import org.jspecify.annotations.Nullable;

import java.util.Collections;

@ExtensionImpl
public class PyGotoClassContributor implements GotoClassOrTypeContributor, PossiblyDumbAware {
    @Override
    public void processNames(Processor<String> processor, SearchScope scope, @Nullable IdFilter filter) {
        DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
            if (!StubIndex.getInstance().processAllKeys(PyClassNameIndex.KEY, processor, (ProjectAwareSearchScope) scope, filter)) {
                return;
            }
            FileBasedIndex.getInstance().processAllKeys(PyModuleNameIndex.NAME, processor, scope, filter);
        });
    }

    @Override
    public void processElementsWithName(String name,
                                        Processor<NavigationItem> processor,
                                        FindSymbolParameters parameters) {
        Project project = parameters.getProject();
        GlobalSearchScope scope = PySearchUtilBase.excludeSdkTestScope((GlobalSearchScope) parameters.getSearchScope());
        IdFilter filter = parameters.getIdFilter();
        PsiManager psiManager = PsiManager.getInstance(project);
        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
            if (!StubIndex.getInstance().processElements(PyClassNameIndex.KEY, name, project, scope, filter, PyClass.class, processor)) {
                return;
            }
            FileBasedIndex.getInstance().getFilesWithKey(PyModuleNameIndex.NAME, Collections.singleton(name), file -> {
                PsiFile psiFile = psiManager.findFile(file);
                return !(psiFile instanceof PyFile) || processor.process(psiFile);
            }, scope);
        });
    }

    @Override
    public @Nullable String getQualifiedName(NavigationItem item) {
        return item instanceof PyQualifiedNameOwner qNameOwner ? qNameOwner.getQualifiedName() : null;
    }

    @Override
    public @Nullable String getQualifiedNameSeparator() {
        return ".";
    }

    // TODO consulo: GotoClassContributor#getElementLanguage() missing
    public @Nullable Language getElementLanguage() {
        return PythonLanguage.getInstance();
    }

    @Override
    public boolean isDumbAware() {
        return true;
    }
}
