// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.python.requirements.parser.psi.impl;

import consulo.language.ast.ASTNode;
import consulo.language.impl.psi.ASTWrapperPsiElement;
import consulo.language.psi.ContributedReferenceHost;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ReferenceProvidersRegistry;

/**
 * Base class for Requirements PSI elements that host references contributed via the
 * {@code psi.referenceContributor} extension point (see {@code RequirementsReferenceContributor}).
 *
 * <p>{@link ASTWrapperPsiElement#getReferences()} returns an empty array unless the element opts
 * into the reference-contributor pipeline by implementing {@link ContributedReferenceHost} and
 * routing {@code getReferences()} through {@link ReferenceProvidersRegistry}. Without this, the
 * contributor's providers are registered but never invoked, so Quick Doc and Ctrl-Click on package
 * names do nothing. Wired to the {@code name_req} and {@code package_name} rules via the grammar's
 * {@code mixin} attribute so regeneration preserves it.
 */
public abstract class RequirementsReferenceHost extends ASTWrapperPsiElement implements ContributedReferenceHost {
    public RequirementsReferenceHost(ASTNode node) {
        super(node);
    }

    @Override
    public PsiReference[] getReferences() {
        return ReferenceProvidersRegistry.getReferencesFromProviders(this);
    }
}
