// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyImportStatementBase;
import consulo.project.Project;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PyCodeFragmentWithHiddenImports extends PyExpressionCodeFragmentImpl {
    private List<PyImportStatementBase> myPseudoImports = new ArrayList<>();

    public PyCodeFragmentWithHiddenImports(Project project, String name, CharSequence text, boolean isPhysical) {
        super(project, name, text, isPhysical);
    }

    public List<PyImportStatementBase> getPseudoImports() {
        return myPseudoImports;
    }

    public void addImports(Collection<? extends PyImportStatementBase> pseudoImports) {
        myPseudoImports.addAll(pseudoImports);
        clearCaches();
        // TODO consulo: PsiManagerEx.beforeChange(boolean) is internal; PsiManager.dropResolveCaches() is used instead
        getManager().dropResolveCaches(); // drops resolve caches
    }

    public void addImportsFromStrings(Collection<String> importStatements) {
        PyElementGenerator generator = PyElementGenerator.getInstance(getProject());
        LanguageLevel langLevel = LanguageLevel.forElement(this);
        List<PyImportStatementBase> imports = new ArrayList<>(importStatements.size());
        for (String it : importStatements) {
            imports.add(generator.createFromText(langLevel, PyImportStatementBase.class, it));
        }
        addImports(imports);
    }

    @Override
    protected PyCodeFragmentWithHiddenImports clone() {
        PyCodeFragmentWithHiddenImports clone = (PyCodeFragmentWithHiddenImports)super.clone();
        // Consulo: Object#clone() copies the field reference, so the clone gets its own list (as in JB, where the clone's list is refilled)
        clone.myPseudoImports = new ArrayList<>(myPseudoImports);
        return clone;
    }
}
