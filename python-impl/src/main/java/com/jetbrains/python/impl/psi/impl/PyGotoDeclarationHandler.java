// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.impl.PyUserInitiatedResolvableReference;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.language.editor.navigation.GotoDeclarationHandlerBase;
import consulo.language.psi.FileContextUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * {@code GotoDeclarationAction} uses {@link PsiElement#findReferenceAt}.
 * This method knows nothing about execution context and {@link PyBaseElementImpl} injects loose {@link TypeEvalContext}.
 * While regular methods are indexed, {@link com.jetbrains.python.codeInsight.PyCustomMember} are not.
 * As result, "go to declaration" failed to resolve reference pointing to another files which leads to bugs like PY-18089.
 * <p>
 * <p>
 * "Go to declaration" is always user-initiated action, so we resolve it manually using best context
 * <p>
 * Other user actions, e.g. quick documentation, find usages, and rename, get the target element from
 * {@code com.jetbrains.python.codeInsight.PyTargetElementEvaluator}. That evaluator also uses a user-initiated context.
 *
 * @author Ilya.Kazakevich
 */
@ExtensionImpl
public class PyGotoDeclarationHandler extends GotoDeclarationHandlerBase {
    @Override
    public @Nullable PsiElement getGotoDeclarationTarget(@Nullable PsiElement sourceElement, @Nullable Editor editor) {
        PsiElement[] targets = getGotoDeclarationTargets(sourceElement, -1, editor);
        return targets != null && targets.length > 0 ? targets[0] : null;
    }

    @Override
    public PsiElement @Nullable [] getGotoDeclarationTargets(@Nullable PsiElement sourceElement, int offset, @Nullable Editor editor) {
        if (sourceElement == null) {
            return null;
        }
        PyResolveContext context =
            PyResolveContext.defaultContext(TypeEvalContext.userInitiated(sourceElement.getProject(), sourceElement.getContainingFile()));

        PsiElement parent = sourceElement.getParent();
        PyReferenceOwner referenceOwner = sourceElement instanceof PyReferenceOwner sourceOwner
            ? sourceOwner
            : parent instanceof PyReferenceOwner parentOwner ? parentOwner : null;
        if (referenceOwner != null) {
            PsiFile contextFile = FileContextUtil.getContextFile(sourceElement);
            PsiElement navigationSource = contextFile != null ? contextFile : sourceElement;
            Map<PsiFile, List<PsiElement>> grouped = new LinkedHashMap<>();
            for (PsiElement it : PyResolveUtil.multiResolveDeclaration(referenceOwner.getReference(context), context)) {
                PsiElement target = PyiUtil.getNavigationTarget(it, navigationSource);
                if (target != referenceOwner) {
                    grouped.computeIfAbsent(target.getContainingFile(), k -> new ArrayList<>()).add(target);
                }
            }
            List<PsiElement> results = new ArrayList<>();
            for (Map.Entry<PsiFile, List<PsiElement>> entry : grouped.entrySet()) {
                PsiFile containingFile = entry.getKey();
                List<PsiElement> declarations = entry.getValue();
                if (!Objects.equals(containingFile, sourceElement.getContainingFile())) {
                    results.addAll(declarations.subList(0, Math.min(1, declarations.size())));
                }
                // if it's a qualified expression, then it could be a union, so go to the different declarations
                //  otherwise go to the most recent assignment
                else if (referenceOwner instanceof PyQualifiedExpression qualifiedExpression && qualifiedExpression.isQualified()) {
                    results.addAll(declarations);
                }
                else {
                    results.addAll(declarations.subList(Math.max(0, declarations.size() - 1), declarations.size()));
                }
            }
            if (!results.isEmpty()) {
                return results.toArray(PsiElement.EMPTY_ARRAY);
            }
        }

        // If element is not ref owner, it still may have provided references, lets find some
        PsiElement[] provided = findProvidedReferenceAndResolve(sourceElement);
        if (provided != null) {
            return provided;
        }
        return parent != null ? findProvidedReferenceAndResolve(parent) : null;
    }

    private static PsiElement @Nullable [] findProvidedReferenceAndResolve(PsiElement element) {
        for (PsiReference ref : element.getReferences()) {
            if (ref instanceof PyUserInitiatedResolvableReference userInitiatedReference) {
                PsiElement resolved = userInitiatedReference.userInitiatedResolve();
                if (resolved != null && resolved != element) {
                    return new PsiElement[]{resolved};
                }
            }
        }
        return null;
    }
}
