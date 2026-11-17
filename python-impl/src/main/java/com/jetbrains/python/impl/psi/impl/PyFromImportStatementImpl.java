// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import consulo.language.ast.ASTNode;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.resolve.DelegatingScopeProcessor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.ast.IElementType;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyElementTypes;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.ast.impl.ValueHolder;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementType;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.impl.psi.resolve.ResolveImportUtil;
import com.jetbrains.python.psi.stubs.PyFromImportStatementStub;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;

public class PyFromImportStatementImpl extends PyBaseElementImpl<PyFromImportStatementStub>
    implements PyFromImportStatement /* TODO consulo: PsiListLikeElement missing */ {
    private volatile @Nullable ValueHolder<QualifiedName> myImportSourceQName;

    public PyFromImportStatementImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyFromImportStatementImpl(PyFromImportStatementStub stub) {
        this(stub, PyStubElementTypes.FROM_IMPORT_STATEMENT);
    }

    public PyFromImportStatementImpl(PyFromImportStatementStub stub, IStubElementType nodeType) {
        super(stub, nodeType);
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyFromImportStatement(this);
    }

    @Override
    public void subtreeChanged() {
        super.subtreeChanged();
        myImportSourceQName = null;
    }

    @Override
    public @Nullable QualifiedName getImportSourceQName() {
        ValueHolder<QualifiedName> result = myImportSourceQName;
        if (result == null) {
            myImportSourceQName = result = new ValueHolder<>(doGetImportSourceQName());
        }
        return result.value;
    }

    private @Nullable QualifiedName doGetImportSourceQName() {
        PyFromImportStatementStub stub = getStub();
        if (stub != null) {
            QualifiedName qName = stub.getImportSourceQName();
            if (qName != null && qName.getComponentCount() == 0) {  // relative import only: from .. import the_name
                return null;
            }
            return qName;
        }

        return PyFromImportStatement.super.getImportSourceQName();
    }

    @Override
    public PyImportElement[] getImportElements() {
        return getImportElements(PyElementTypes.IMPORT_ELEMENT, PyTokenTypes.IMPORT_KEYWORD);
    }

    protected final PyImportElement[] getImportElements(
        IElementType importElementType,
        PyElementType importKeywordToken) {
        PyFromImportStatementStub stub = getStub();
        if (stub != null) {
            return stub.getChildrenByType(importElementType, count -> new PyImportElement[count]);
        }
        List<PyImportElement> result = new ArrayList<>();
        ASTNode importKeyword = getNode().findChildByType(importKeywordToken);
        if (importKeyword != null) {
            for (ASTNode node = importKeyword.getTreeNext(); node != null; node = node.getTreeNext()) {
                if (node.getElementType() == importElementType) {
                    result.add((PyImportElement)node.getPsi());
                }
            }
        }
        return result.toArray(new PyImportElement[0]);
    }

    @Override
    public int getRelativeLevel() {
        PyFromImportStatementStub stub = getStub();
        if (stub != null) {
            return stub.getRelativeLevel();
        }

        return PyFromImportStatement.super.getRelativeLevel();
    }

    @Override
    public boolean processDeclarations(PsiScopeProcessor processor,
            ResolveState state,
            PsiElement lastParent,
            PsiElement place) {
        // import is per-file
        if (place.getContainingFile() != getContainingFile()) {
            return true;
        }
        if (isStarImport()) {
            List<PsiElement> targets = ResolveImportUtil.resolveFromImportStatementSource(this, getImportSourceQName());
            for (PsiElement target : targets) {
                PsiElement importedFile = PyUtil.turnDirIntoInit(target);
                if (importedFile != null) {
                    PsiScopeProcessor starImportableNamesProcessor = new DelegatingScopeProcessor(processor) {
                        @Override
                        public boolean execute(PsiElement element, ResolveState state) {
                            if (element instanceof PyElement && importedFile instanceof PyFile &&
                                    !PyUtil.isStarImportableFrom(StringUtil.notNullize(((PyElement)element).getName()), (PyFile)importedFile)) {
                                return true;
                            }
                            return super.execute(element, state);
                        }
                    };
                    if (!importedFile.processDeclarations(starImportableNamesProcessor, state, null, place)) {
                        return false;
                    }
                }
            }
        }
        else {
            PyImportElement[] importElements = getImportElements();
            for (PyImportElement element : importElements) {
                if (!processor.execute(element, state)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public ASTNode addInternal(ASTNode first, ASTNode last, ASTNode anchor, Boolean before) {
        boolean addingNewName = first == last &&
                first.getElementType() == PyElementTypes.IMPORT_ELEMENT &&
                (anchor == null || anchor.getElementType() == PyElementTypes.IMPORT_ELEMENT);
        if (!addingNewName) {
            return super.addInternal(first, last, anchor, before);
        }

        if (anchor == null) {
            PyImportElement[] elements = getImportElements();
            if (elements.length != 0) {
                if (before) {
                    anchor = elements[elements.length - 1].getNode();
                    before = false;
                }
                else {
                    anchor = elements[0].getNode();
                    before = true;
                }
            }
        }

        // In an incomplete from import statement there is a special sentinel empty PyImportElement at the end
        if (anchor != null && anchor.getTextLength() == 0) {
            getNode().replaceChild(anchor, first);
            return first;
        }
        else {
            ASTNode result = super.addInternal(first, last, anchor, before);
            if (anchor != null && anchor.getElementType() == PyElementTypes.IMPORT_ELEMENT &&
                    result.getElementType() == PyElementTypes.IMPORT_ELEMENT) {
                ASTNode comma = PyElementGenerator.getInstance(getProject()).createComma();
                super.addInternal(comma, comma, before ? result : anchor, false);
            }
            return result;
        }
    }

    @Override
    public void deleteChildInternal(ASTNode child) {
        if (ArrayUtil.contains(child.getPsi(), getImportElements())) {
            PyPsiUtils.deleteAdjacentCommaWithWhitespaces(this, child.getPsi());
        }
        super.deleteChildInternal(child);
    }

    @Override
    public @Nullable PyStarImportElement getStarImportElement() {
        return getStubOrPsiChild(PyStubElementTypes.STAR_IMPORT_ELEMENT);
    }

    @Override
    public @Nullable PsiFileSystemItem resolveImportSource() {
        return FluentIterable.from(resolveImportSourceCandidates()).filter(PsiFileSystemItem.class).first().orNull();
    }

    @Override
    public List<PsiElement> resolveImportSourceCandidates() {
        QualifiedName qName = getImportSourceQName();
        if (qName == null) {
            int level = getRelativeLevel();
            if (level > 0) {
                PsiDirectory upper = ResolveImportUtil.stepBackFrom(getContainingFile().getOriginalFile(), level);
                return ContainerUtil.createMaybeSingletonList(upper);
            }
        }
        return ResolveImportUtil.resolveFromImportStatementSource(this, qName);
    }

    @Override
    public Iterable<PyElement> iterateNames() {
        PyElement resolved = as(resolveImplicitSubModule(), PyElement.class);
        return resolved != null ? ImmutableList.of(resolved) : Collections.emptyList();
    }

    @Override
    public List<RatedResolveResult> multiResolveName(String name) {
        QualifiedName importSourceQName = getImportSourceQName();
        if (importSourceQName != null && importSourceQName.endsWith(name)) {
            PsiElement element = resolveImplicitSubModule();
            if (element != null) {
                return ResolveResultList.to(element);
            }
        }
        return Collections.emptyList();
    }

    /**
     * The statement 'from pkg1.m1 import ...' makes 'm1' available as a local name in the package 'pkg1'.
     * <p>
     * http://stackoverflow.com/questions/6048786/from-module-import-in-init-py-makes-module-name-visible
     */
    private @Nullable PsiElement resolveImplicitSubModule() {
        QualifiedName importSourceQName = getImportSourceQName();
        if (importSourceQName != null) {
            String name = importSourceQName.getLastComponent();
            PsiFile file = getContainingFile();
            if (name != null && PyUtil.isPackage(file)) {
                PsiElement resolvedImportSource = PyUtil.turnInitIntoDir(resolveImportSource());
                if (resolvedImportSource != null && resolvedImportSource.getParent() == file.getContainingDirectory()) {
                    return resolvedImportSource;
                }
            }
        }
        return null;
    }

    public List<? extends PsiElement> getComponents() {
        return Arrays.asList(getImportElements());
    }
}
