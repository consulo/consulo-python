// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.pyi;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyFileImpl;
import com.jetbrains.python.impl.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiFileType;
import com.jetbrains.python.pyi.PyiLanguageDialect;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.resolve.DelegatingScopeProcessor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class PyiFile extends PyFileImpl {
    public PyiFile(FileViewProvider viewProvider) {
        super(viewProvider, PyiLanguageDialect.getInstance());
    }

    @Override
    public PythonFileType getFileType() {
        return PyiFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "PyiFile:" + getName();
    }

    @Override
    public LanguageLevel getLanguageLevel() {
        return LanguageLevel.getLatest();
    }

    @Override
    public List<RatedResolveResult> multiResolveName(String name, boolean exported) {
        if (name.equals("function") && PyBuiltinCache.getInstance(this).getBuiltinsFile() == this) {
            return Collections.emptyList();
        }

        List<RatedResolveResult> baseResults = super.multiResolveName(name, exported);
        List<String> dunderAll = Objects.requireNonNullElse(getDunderAll(), Collections.emptyList());
        if (exported) {
            return baseResults.stream()
                .filter(it -> !isPrivateImport(it instanceof ImportedResolveResult importedResult ? importedResult.getDefiner() : null,
                    dunderAll))
                .collect(Collectors.toList());
        }
        else {
            return baseResults;
        }
    }

    @Override
    public boolean processDeclarations(PsiScopeProcessor processor,
                                       ResolveState resolveState,
                                       @Nullable PsiElement lastParent,
                                       PsiElement place) {
        List<String> dunderAll = Objects.requireNonNullElse(getDunderAll(), Collections.emptyList());
        boolean completingInPyiStub = PyiUtil.isInsideStub(place);
        DelegatingScopeProcessor wrapper = new DelegatingScopeProcessor(processor) {
            @Override
            public boolean execute(PsiElement element, ResolveState state) {
                // According to the typing spec, underscored names in .pyi stubs should be considered
                // "stub-only" objects (https://typing.readthedocs.io/en/latest/guides/writing_stubs.html#stub-only-objects).
                // However, they can still be re-used between .pyi stubs and imported in .py files under typing.TYPE_CHECKING.
                // Also, they can point to some inadvertently exposed de-facto API of some .py module.
                // Therefore, we do allow resolving such names, but don't suggest them among completion variants
                // to avoid polluting the lookup with internal names like "_T", "_Ts", etc.
                if (!completingInPyiStub && element instanceof PsiNamedElement namedElement && isPrivateName(namedElement.getName())) {
                    return true;
                }
                if (isPrivateImport(element, dunderAll)) {
                    return true;
                }
                return super.execute(element, state);
            }
        };
        return super.processDeclarations(wrapper, resolveState, lastParent, place);
    }

    private static boolean isPrivateName(@Nullable String name) {
        return PyUtil.getInitialUnderscores(name) == 1;
    }

    private static boolean isPrivateImport(@Nullable PsiElement element, List<String> dunderAll) {
        // See https://typing.readthedocs.io/en/latest/spec/distributing.html#import-conventions
        // and https://peps.python.org/pep-0484/#stub-files
        if (!(element instanceof PyImportElement importElement)) {
            return false;
        }
        QualifiedName importedQName = importElement.getImportedQName();
        return !Objects.equals(importElement.getAsName(), importedQName != null ? importedQName.toString() : null) &&
            !dunderAll.contains(importElement.getVisibleName());
    }

    @Override
    protected void prioritizeNameRedefinitions(List<PsiNamedElement> definitions, TypeEvalContext typeEvalContext) {
        // .pyi stubs contain only declarations. Thus, names cannot be redefined, and the original order is important.
    }
}
