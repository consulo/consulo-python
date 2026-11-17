// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.psi.impl.references.PyReferenceBase;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.editor.TargetElementUtilExtender;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Finds the target element for user actions that get it from {@link consulo.language.editor.TargetElementUtil},
 * e.g. quick documentation, find usages, and rename.
 * These actions accept the element name, so this evaluator resolves the reference with a user-initiated context.
 * <p>
 * Go to declaration gets its targets from {@link com.jetbrains.python.impl.psi.impl.PyGotoDeclarationHandler} first.
 * That handler also uses a user-initiated context.
 */
@ExtensionImpl
public final class PyTargetElementEvaluator implements TargetElementUtilExtender {
    // Consulo: TargetElementUtilExtender is not language-scoped (JB registers the evaluator for language="Python"),
    // so each method applies only to Python elements.

    @Override
    public boolean includeSelfInGotoImplementation(PsiElement element) {
        return element.getLanguage() != PythonLanguage.getInstance();
    }

    /**
     * A keyword argument that maps to a parameter is a usage of that parameter, so it is not a declaration.
     * A keyword argument that maps to no parameter declares a name, e.g. a key of {@code dict(a=1)}.
     */
    // TODO consulo: TargetElementEvaluatorEx2.isAcceptableNamedParent missing
    public boolean isAcceptableNamedParent(PsiElement parent) {
        if (parent instanceof PyKeywordArgument keywordArgument &&
            keywordArgument.getReference() instanceof PsiPolyVariantReference reference) {
            return reference.multiResolve(false).length == 0;
        }
        return true;
    }

    @Override
    public @Nullable PsiElement getReferenceOrReferencedElement(PsiReference ref, Set<String> flags) {
        if (!flags.contains(ELEMENT_NAME_ACCEPTED)) {
            return null;
        }

        PsiElement element = ref.getElement();
        if (element.getLanguage() != PythonLanguage.getInstance()) {
            return null;
        }
        // PsiFile.findReferenceAt gives a reference with a code analysis context, so get the reference again.
        // A provided reference, e.g. a BaseReference, does not belong to the element, so keep it.
        PyResolveContext resolveContext =
            PyResolveContext.defaultContext(TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile()));
        PsiReference userInitiatedRef =
            ref instanceof PyReferenceBase && element instanceof PyReferenceOwner owner ? owner.getReference(resolveContext) : ref;

        PsiElement result = PyResolveUtil.resolveDeclaration(userInitiatedRef, resolveContext);
        Set<PsiElement> visited = new HashSet<>();
        visited.add(result);
        while (result instanceof PyReferenceExpression || result instanceof PyTargetExpression) {
            PsiElement nextResult = PyResolveUtil.resolveDeclaration(((PyReferenceOwner) result).getReference(resolveContext), resolveContext);
            if (nextResult != null && !visited.contains(nextResult) &&
                PsiTreeUtil.getParentOfType(element, ScopeOwner.class) == PsiTreeUtil.getParentOfType(result, ScopeOwner.class) &&
                (nextResult instanceof PyReferenceExpression || nextResult instanceof PyTargetExpression || nextResult instanceof PyParameter)) {
                visited.add(nextResult);
                result = nextResult;
            }
            else {
                break;
            }
        }
        return result;
    }
}
