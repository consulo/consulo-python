// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import org.jspecify.annotations.Nullable;
import consulo.localize.LocalizeValue;
import consulo.ui.image.Image;
import com.jetbrains.python.psi.impl.PyPsiUtils;

import com.google.common.collect.Lists;
import consulo.language.ast.ASTNode;
import consulo.navigation.ItemPresentation;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.impl.PyStubElementTypes;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyStarImportElementStub;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.TypeEvalContext;

import java.util.stream.StreamSupport;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;

public class PyStarImportElementImpl extends PyBaseElementImpl<PyStarImportElementStub> implements PyStarImportElement {
    public PyStarImportElementImpl(ASTNode astNode) {
        super(astNode);
    }

    public PyStarImportElementImpl(PyStarImportElementStub stub) {
        super(stub, PyStubElementTypes.STAR_IMPORT_ELEMENT);
    }

    @Override
    public Iterable<PyElement> iterateNames() {
        if (getParent() instanceof PyFromImportStatement fromImportStatement) {
            return fromImportStatement.resolveImportSourceCandidates().stream()
                .distinct()
                .map(PyUtil::turnDirIntoInit)
                .filter(PyFile.class::isInstance)
                .map(PyFile.class::cast)
                .flatMap(file -> StreamSupport.stream(file.iterateNames().spliterator(), false)
                    .filter(e -> {
                        String name = e.getName();
                        return name != null && PyUtil.isStarImportableFrom(name, file);
                    }))
                .toList();
        }
        return Collections.emptyList();
    }

    @Override
    public List<RatedResolveResult> multiResolveName(String name) {
        return PyUtil.getParameterizedCachedValue(this, name, this::calculateMultiResolveName);
    }

    private List<RatedResolveResult> calculateMultiResolveName(String name) {
        PsiElement parent = getParentByStub();
        if (parent instanceof PyFromImportStatement fromImportStatement) {
            List<PsiElement> importedFiles = fromImportStatement.resolveImportSourceCandidates();
            for (PsiElement importedFile : new HashSet<>(importedFiles)) { // resolver gives lots of duplicates
                PyFile sourceFile = as(PyUtil.turnDirIntoInit(importedFile), PyFile.class);
                if (sourceFile != null && PyUtil.isStarImportableFrom(name, sourceFile)) {
                    PyModuleType moduleType = new PyModuleType(sourceFile);
                    TypeEvalContext context = TypeEvalContext.codeInsightFallback(sourceFile.getProject());
                    List<? extends RatedResolveResult> results = moduleType.resolveMember(name, null, AccessDirection.READ,
                                                                                                PyResolveContext.defaultContext(context));
                    if (!ContainerUtil.isEmpty(results)) {
                        return Lists.newArrayList(results);
                    }
                }
            }
        }
        return Collections.emptyList();
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {

            private String getName() {
                PyFromImportStatement elt = PsiTreeUtil.getParentOfType(PyStarImportElementImpl.this, PyFromImportStatement.class);
                if (elt != null) { // always? who knows :)
                    PyReferenceExpression imp_src = elt.getImportSource();
                    if (imp_src != null) {
                        return PyPsiUtils.toPath(imp_src);
                    }
                }
                return "<?>";
            }

            @Override
            public LocalizeValue getPresentableText() {
                return LocalizeValue.of(getName());
            }

            @Override
            public String getLocationString() {
                return "| " + "from " + getName() + " import *";
            }

            @Override
            public @Nullable Image getIcon() {
                return null;
            }
        };
    }

    @Override
    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyStarImportElement(this);
    }
}
