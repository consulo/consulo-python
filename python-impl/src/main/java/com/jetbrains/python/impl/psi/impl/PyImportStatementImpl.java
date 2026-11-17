// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.impl.PyPsiUtils;

import com.google.common.collect.ImmutableList;
import consulo.language.ast.ASTNode;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.ast.TokenSet;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ArrayUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.impl.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyImportStatementStub;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;


// TODO consulo: com.intellij.psi.PsiListLikeElement missing
public class PyImportStatementImpl extends PyBaseElementImpl<PyImportStatementStub> implements PyImportStatement {
    public PyImportStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyImportStatementImpl(PyImportStatementStub stub) {
        this(stub, PyStubElementTypes.IMPORT_STATEMENT);
    }

    public PyImportStatementImpl(PyImportStatementStub stub, IStubElementType nodeType) {
        super(stub, nodeType);
    }

    @Override
    public PyImportElement[] getImportElements() {
        PyImportStatementStub stub = getStub();
        if (stub != null) {
            return stub.getChildrenByType(PyElementTypes.IMPORT_ELEMENT, count -> new PyImportElement[count]);
        }
        return childrenToPsi(TokenSet.create(PyElementTypes.IMPORT_ELEMENT), new PyImportElement[0]);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyImportStatement(this);
    }

    @Override
    public void deleteChildInternal(ASTNode child) {
        if (ArrayUtil.contains(child.getPsi(), getImportElements())) {
            PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
        }
        super.deleteChildInternal(child);
    }

    @Override
    public Iterable<PyElement> iterateNames() {
        PyElement resolved = as(resolveImplicitSubModule(), PyElement.class);
        return resolved != null ? ImmutableList.of(resolved) : Collections.emptyList();
    }

    @Override
    public List<RatedResolveResult> multiResolveName(String name) {
        PyImportElement[] elements = getImportElements();
        if (elements.length == 1) {
            PyImportElement element = elements[0];
            QualifiedName importedQName = element.getImportedQName();
            if (importedQName != null && importedQName.getComponentCount() > 1 && name.equals(importedQName.getLastComponent())) {
                return ResolveResultList.to(resolveImplicitSubModule());
            }
        }
        return Collections.emptyList();
    }

    /**
     * The statement 'import pkg1.m1' makes 'm1' available as a local name in the package 'pkg1'.
     *
     * http://stackoverflow.com/questions/6048786/from-module-import-in-init-py-makes-module-name-visible
     */
    private @Nullable PsiElement resolveImplicitSubModule() {
        PyImportElement[] elements = getImportElements();
        if (elements.length == 1) {
            PyImportElement element = elements[0];
            QualifiedName importedQName = element.getImportedQName();
            PsiFile file = element.getContainingFile();
            if (file != null) {
                if (importedQName != null && importedQName.getComponentCount() > 1 && PyUtil.isPackage(file)) {
                    QualifiedName packageQName = importedQName.removeLastComponent();
                    PsiElement resolvedImport = PyUtil.turnDirIntoInit(ResolveImportUtil.resolveImportElement(element, packageQName));
                    if (resolvedImport == file) {
                        return element.resolve();
                    }
                }
            }
        }
        return null;
    }

    public List<? extends PsiElement> getComponents() {
        return Arrays.asList(getImportElements());
    }
}
