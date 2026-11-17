// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.docstrings;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyElement;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiReferenceContributor;
import consulo.language.psi.PsiReferenceProvider;
import consulo.language.psi.PsiReferenceRegistrar;
import consulo.language.util.ProcessingContext;


@ExtensionImpl
public final class DocStringReferenceContributor extends PsiReferenceContributor {
    @Override
    public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
        registrar.registerReferenceProvider(DocStringTagCompletionContributor.Helper.DOCSTRING_PATTERN,
            new DocStringReferenceProvider());
        // Sphinx cross-reference roles also work in line comments (e.g. `# see :py:class:`Foo``).
        registrar.registerReferenceProvider(PlatformPatterns.psiComment(), new SphinxCommentReferenceProvider());
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private static final class SphinxCommentReferenceProvider extends PsiReferenceProvider {
        @Override
        public boolean acceptsTarget(PsiElement target) {
            return target instanceof PyElement;
        }

        @Override
        public PsiReference[] getReferencesByElement(PsiElement element, ProcessingContext context) {
            if (!(element instanceof PsiComment comment)) {
                return PsiReference.EMPTY_ARRAY;
            }
            return SphinxReferences.findReferences(comment).toArray(PsiReference.EMPTY_ARRAY);
        }
    }
}
