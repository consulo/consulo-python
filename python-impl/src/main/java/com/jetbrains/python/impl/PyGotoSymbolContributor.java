// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.impl.psi.search.PySearchUtilBase;
import com.jetbrains.python.impl.psi.stubs.PyClassAttributesIndex;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.impl.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.impl.psi.stubs.PyModuleNameIndex;
import com.jetbrains.python.impl.psi.stubs.PyTypeAliasNameIndex;
import com.jetbrains.python.impl.psi.stubs.PyVariableNameIndex;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.PossiblyDumbAware;
import consulo.application.util.function.Processor;
import consulo.content.scope.SearchScope;
import consulo.ide.navigation.GotoSymbolContributor;
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

/**
 * JB implements {@code GotoClassContributor} here and registers it as {@code gotoSymbolContributor};
 * Consulo uses {@link GotoSymbolContributor}.
 */
@ExtensionImpl
public class PyGotoSymbolContributor implements GotoSymbolContributor, PossiblyDumbAware {
    @Override
    public void processNames(Processor<String> processor, SearchScope searchScope, @Nullable IdFilter filter) {
        ProjectAwareSearchScope scope = (ProjectAwareSearchScope) searchScope;
        FileBasedIndex fileIndex = FileBasedIndex.getInstance();
        StubIndex stubIndex = StubIndex.getInstance();
        DumbModeAccessType.RAW_INDEX_DATA_ACCEPTABLE.ignoreDumbMode(() -> {
            if (!fileIndex.processAllKeys(PyModuleNameIndex.NAME, processor, searchScope, filter)) {
                return;
            }
            if (!stubIndex.processAllKeys(PyClassNameIndex.KEY, processor, scope, filter)) {
                return;
            }
            if (!stubIndex.processAllKeys(PyFunctionNameIndex.KEY, processor, scope, filter)) {
                return;
            }
            if (!stubIndex.processAllKeys(PyVariableNameIndex.KEY, processor, scope, filter)) {
                return;
            }
            if (!stubIndex.processAllKeys(PyClassAttributesIndex.KEY, processor, scope, filter)) {
                return;
            }
            if (!stubIndex.processAllKeys(PyTypeAliasNameIndex.KEY, processor, scope, filter)) {
                return;
            }
        });
    }

    @Override
    public void processElementsWithName(String name,
                                        Processor<NavigationItem> processor,
                                        FindSymbolParameters parameters) {
        Project project = parameters.getProject();
        GlobalSearchScope scope = PySearchUtilBase.excludeSdkTestScope((GlobalSearchScope) parameters.getSearchScope());
        IdFilter filter = parameters.getIdFilter();
        FileBasedIndex fileIndex = FileBasedIndex.getInstance();
        StubIndex stubIndex = StubIndex.getInstance();
        PsiManager psiManager = PsiManager.getInstance(project);

        DumbModeAccessType.RELIABLE_DATA_ONLY.ignoreDumbMode(() -> {
            if (!fileIndex.getFilesWithKey(PyModuleNameIndex.NAME, Collections.singleton(name), file -> {
                PsiFile psiFile = psiManager.findFile(file);
                return !(psiFile instanceof PyFile) || processor.process(psiFile);
            }, scope)) {
                return;
            }
            if (!stubIndex.processElements(PyClassNameIndex.KEY, name, project, scope, filter, PyClass.class, processor)) {
                return;
            }
            if (!stubIndex.processElements(PyFunctionNameIndex.KEY, name, project, scope, filter, PyFunction.class, processor)) {
                return;
            }
            if (!stubIndex.processElements(PyVariableNameIndex.KEY, name, project, scope, filter, PyTargetExpression.class, processor)) {
                return;
            }
            if (!stubIndex.processElements(PyTypeAliasNameIndex.KEY, name, project, scope, filter, PyTypeAliasStatement.class, processor)) {
                return;
            }
            PyClassAttributesIndex.findClassAndInstanceAttributes(name, project, scope).forEach(processor::process);
        });
    }

    // TODO consulo: GotoSymbolContributor has no qualified name support (JB GotoClassContributor#getQualifiedName)
    public @Nullable String getQualifiedName(NavigationItem item) {
        if (item instanceof PyQualifiedNameOwner) {
            return ((PyQualifiedNameOwner) item).getQualifiedName();
        }
        return null;
    }

    // TODO consulo: GotoSymbolContributor has no qualified name support (JB GotoClassContributor#getQualifiedNameSeparator)
    public String getQualifiedNameSeparator() {
        return ".";
    }

    @Override
    public boolean isDumbAware() {
        return true;
    }
}
