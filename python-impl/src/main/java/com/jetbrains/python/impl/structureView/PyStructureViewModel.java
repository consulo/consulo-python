// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.structureView;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import consulo.codeEditor.Editor;
import consulo.fileEditor.structureView.StructureViewModel;
import consulo.fileEditor.structureView.StructureViewTreeElement;
import consulo.fileEditor.structureView.tree.Filter;
import consulo.fileEditor.structureView.tree.Sorter;
import consulo.language.editor.structureView.StructureViewModelBase;
import consulo.language.psi.PsiFile;
import org.jspecify.annotations.Nullable;

public class PyStructureViewModel extends StructureViewModelBase implements StructureViewModel.ElementInfoProvider,
                                                                                        StructureViewModel.ExpandInfoProvider {
    public PyStructureViewModel(PsiFile psiFile, @Nullable Editor editor) {
        this(psiFile, editor, new PyStructureViewElement((PyElement) psiFile));
        withSorters(Sorter.ALPHA_SORTER);
        withSuitableClasses(PyFunction.class, PyClass.class, PyTypeAliasStatement.class);
    }

    public PyStructureViewModel(PsiFile file, @Nullable Editor editor, StructureViewTreeElement element) {
        super(file, editor, element);
    }

    @Override
    public boolean isAlwaysShowsPlus(StructureViewTreeElement element) {
        Object value = element.getValue();
        return value instanceof PyFile || value instanceof PyClass;
    }

    @Override
    public boolean isAlwaysLeaf(StructureViewTreeElement element) {
        Object value = element.getValue();
        return value instanceof PyTargetExpression || value instanceof PyTypeAliasStatement;
    }

    @Override
    public boolean shouldEnterElement(Object element) {
        return element instanceof PyClass;
    }

    @Override
    public Filter[] getFilters() {
        return new Filter[] {
            new PyInheritedMembersFilter(),
            new PyFieldsFilter(),
        };
    }

    @Override
    public boolean isAutoExpand(StructureViewTreeElement element) {
        return false;
    }

    @Override
    public boolean isSmartExpand() {
        return false;
    }

    @Override
    public int getMinimumAutoExpandDepth() {
        return 1;
    }
}
