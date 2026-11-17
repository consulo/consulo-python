// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.references;

import consulo.language.ast.ASTNode;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.document.util.TextRange;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.resolve.ResolveCache;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.IncorrectOperationException;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.psi.PsiReferenceEx;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyQualifiedElement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.ImplicitResolveResult;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import org.jspecify.annotations.Nullable;

import java.util.List;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

public abstract class PyReferenceBase implements PsiReferenceEx, PsiPolyVariantReference {
    // it is *not* final so that it can be changed in debug time. if set to false, caching is off
    private static final boolean USE_CACHE = true;

    protected final PyQualifiedElement myElement;
    protected final PyResolveContext myContext;

    PyReferenceBase(PyQualifiedElement element, PyResolveContext context) {
        myElement = element;
        myContext = context;
    }

    @Override
    public TextRange getRangeInElement() {
        ASTNode nameElement = myElement.getNameElement();
        return nameElement != null ? nameElement.getPsi().getTextRangeInParent() : TextRange.from(0, myElement.getTextLength());
    }

    @Override
    public PyQualifiedElement getElement() {
        return myElement;
    }

    /**
     * Resolves reference to the most obvious point.
     * Imported module names: to module file (or directory for a qualifier).
     * Other identifiers: to most recent definition before this reference.
     * This implementation is cached.
     *
     * @see #resolveInner().
     */
    @Override
    public @Nullable PsiElement resolve() {
        ResolveResult[] results = multiResolve(false);
        return results.length >= 1 && !(results[0] instanceof ImplicitResolveResult) ? results[0].getElement() : null;
    }

    /**
     * Resolves reference to possible referred elements.
     * First element is always what resolve() would return.
     * Imported module names: to module file, or {directory, '__init__.py}' for a qualifier.
     * todo Local identifiers: a list of definitions in the most recent compound statement
     * (e.g. {@code if X: a = 1; else: a = 2} has two definitions of {@code a}.).
     * todo Identifiers not found locally: similar definitions in imported files and builtins.
     *
     * @see PsiPolyVariantReference#multiResolve(boolean)
     */
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        if (USE_CACHE) {
            ResolveCache cache = ResolveCache.getInstance(getElement().getProject());
            boolean actuallyIncomplete = incompleteCode || myContext.getTypeEvalContext().hasAssumptions();
            return cache.resolveWithCaching(this, CachingResolver.INSTANCE, true, actuallyIncomplete);
        }
        else {
            return multiResolveInner();
        }
    }

    protected ResolveResult[] multiResolveInner() {
        String referencedName = myElement.getReferencedName();
        if (referencedName == null) {
            return ResolveResult.EMPTY_ARRAY;
        }

        List<RatedResolveResult> targets = resolveInner();
        if (targets.isEmpty()) {
            return ResolveResult.EMPTY_ARRAY;
        }

        return RatedResolveResult.sorted(targets).toArray(ResolveResult.EMPTY_ARRAY);
    }

    abstract List<RatedResolveResult> resolveInner();

    @Override
    public String getCanonicalText() {
        return getRangeInElement().substring(getElement().getText());
    }

    @Override
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        ASTNode nameElement = myElement.getNameElement();
        // there is a case where the file has already been renamed when this function is invoked
        //  so we can't resolve `myElement` to determine if it's actually a PyFile
        if (newElementName.endsWith(PyNames.DOT_PY)) {
            newElementName = StringUtil.trimEnd(newElementName, PyNames.DOT_PY);
        }
        else if (newElementName.endsWith(PyNames.DOT_PYI)) {
            newElementName = StringUtil.trimEnd(newElementName, PyNames.DOT_PYI);
        }
        if (nameElement != null && PyNames.isIdentifier(newElementName)) {
            ASTNode newNameElement = PyUtil.createNewName(myElement, newElementName);
            myElement.getNode().replaceChild(nameElement, newNameElement);
        }
        return myElement;
    }

    @Override
    public @Nullable PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
        return null;
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
        return PyReferenceCustomTargetChecker.Companion.isReferenceTo(this, element);
    }

    @Override
    public Object[] getVariants() {
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Override
    public boolean isSoft() {
        return false;
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
        PyExpression qualifier = myElement.getQualifier();
        if (qualifier == null) {
            return HighlightSeverity.ERROR;
        }
        if (!isUnknown(context.getType(qualifier))) {
            return HighlightSeverity.WARNING;
        }
        return null;
    }

    @Override
    public @Nullable String getUnresolvedDescription() {
        return null;
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PyReferenceBase that = (PyReferenceBase)o;

        if (!myElement.equals(that.myElement)) {
            return false;
        }
        if (!myContext.equals(that.myContext)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return myElement.hashCode();
    }

    private static class CachingResolver implements ResolveCache.PolyVariantResolver<PyReferenceBase> {
        public static final CachingResolver INSTANCE = new CachingResolver();

        @Override
        public ResolveResult[] resolve(PyReferenceBase ref, boolean incompleteCode) {
            return ref.multiResolveInner();
        }
    }
}
