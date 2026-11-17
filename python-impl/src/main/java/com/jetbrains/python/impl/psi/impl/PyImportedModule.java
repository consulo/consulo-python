// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.impl.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.impl.psi.types.PyImportedModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;


public class PyImportedModule extends LightElement implements PyTypedElement {
    private final @Nullable PyImportElement myImportElement;
    private final PyFile myContainingFile;
    private final QualifiedName myImportedPrefix;

    /**
     * @param importElement  parental import element, may be {@code null} if we're resolving {@code module} part in {@code from module import ...} statement
     * @param containingFile file to be used as anchor e.g. to determine relative import position
     * @param importedPrefix qualified name to resolve
     * @see ResolveImportUtil
     */
    public PyImportedModule(@Nullable PyImportElement importElement, PyFile containingFile, QualifiedName importedPrefix) {
        super(containingFile.getManager(), PythonLanguage.getInstance());
        myImportElement = importElement;
        myContainingFile = containingFile;
        myImportedPrefix = importedPrefix;
    }

    @Override
    public PyFile getContainingFile() {
        return myContainingFile;
    }

    public QualifiedName getImportedPrefix() {
        return myImportedPrefix;
    }

    @Override
    public String getText() {
        return "import " + myImportedPrefix;
    }

    @Override
    public void accept(PsiElementVisitor visitor) {
        super.accept(visitor);
    }

    @Override
    public PsiElement copy() {
        return new PyImportedModule(myImportElement, myContainingFile, myImportedPrefix);
    }

    @Override
    public String toString() {
        return "PyImportedModule:" + myImportedPrefix;
    }

    @Override
    public PsiElement getNavigationElement() {
        if (myImportElement != null) {
            PsiElement element = resolve(myImportElement, myImportedPrefix);
            if (element != null) {
                return element;
            }
        }
        return super.getNavigationElement();
    }

    @Override
    public boolean isValid() {
        return (myImportElement == null || myImportElement.isValid()) && myContainingFile.isValid();
    }

    public @Nullable PyImportElement getImportElement() {
        return myImportElement;
    }

    /**
     * @deprecated use {@link #multiResolve()} instead
     */
    @Deprecated
    public PsiElement resolve() {
        return multiResolve().stream().findFirst().map(res -> res.getElement()).orElse(null);
    }

    public List<RatedResolveResult> multiResolve() {
        List<RatedResolveResult> results;
        if (myImportElement != null) {
            results = ResolveImportUtil.multiResolveImportElement(myImportElement, myImportedPrefix);
        }
        else {
            ResolveResultList resList = new ResolveResultList();
            ResolveImportUtil.multiResolveModuleInRoots(myImportedPrefix, myContainingFile)
                .forEach(res -> resList.poke(res, RatedResolveResult.RATE_NORMAL));
            results = resList;
        }
        return ContainerUtil.map(results, this::tryReplaceDirWithPackage);
    }

    private RatedResolveResult tryReplaceDirWithPackage(RatedResolveResult el) {
        PsiElement element = el.getElement();
        return element instanceof PsiDirectory ? el.replace(PyUtil.getPackageElement((PsiDirectory)element, this)) : el;
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        return new PyImportedModuleType(this);
    }

    private static @Nullable PsiElement resolve(PyImportElement importElement, QualifiedName prefix) {
        PsiElement resolved = ResolveImportUtil.resolveImportElement(importElement, prefix);
        PsiElement packageInit = PyUtil.turnDirIntoInit(resolved);
        return packageInit != null ? packageInit : resolved;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PyImportedModule module = (PyImportedModule)o;
        return Objects.equals(myImportElement, module.myImportElement) &&
               Objects.equals(myContainingFile, module.myContainingFile) &&
               Objects.equals(myImportedPrefix, module.myImportedPrefix);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myImportElement, myContainingFile, myImportedPrefix);
    }
}
