// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.docstrings;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.inspections.PyInspectionMessages;
import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.psi.PsiReferenceEx;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.document.util.TextRange;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPolyVariantReferenceBase;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.psi.util.QualifiedName;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A reference to a Python object referenced from a Sphinx Python-domain cross-reference role
 * (e.g. {@code :py:class:`socket.socket`} or {@code :meth:`~queue.Queue.get`}) or a Python-domain directive
 * signature (e.g. {@code .. py:class:: Foo}).
 * <p>
 * Found inside a docstring or a line comment. One reference is created per dotted component of the target so that
 * renaming any symbol in the path (module, class or member) updates the markup, mirroring {@link DocStringTypeReference}.
 *
 * @see <a href="https://www.sphinx-doc.org/en/master/usage/domains/python.html#cross-referencing-python-objects">Cross-referencing Python objects</a>
 * @see <a href="https://www.sphinx-doc.org/en/master/usage/domains/python.html#python-signatures">Python signatures</a>
 * @see SphinxReferences
 */
final class PySphinxReference extends PsiPolyVariantReferenceBase<PsiElement> implements PsiReferenceEx {
    /**
     * The range of the target from its first component through this component. A move rewrites it.
     */
    private final TextRange myPrefixRange;
    private final QualifiedName myQualifiedName;
    private final boolean mySoft;

    /**
     * The reference to the first component of a dotted target, or {@code null} when the target has one component.
     * A dotted target whose head does not resolve comes from another project, so it must not produce a warning.
     */
    @Nullable PySphinxReference head = null;

    PySphinxReference(PsiElement element,
                      TextRange rangeInElement,
                      TextRange prefixRange,
                      QualifiedName qualifiedName,
                      boolean soft) {
        super(element, rangeInElement);
        myPrefixRange = prefixRange;
        myQualifiedName = qualifiedName;
        mySoft = soft;
    }

    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        return ResolveCache.getInstance(getElement().getProject()).resolveWithCaching(this, Resolver.INSTANCE, true, incompleteCode);
    }

    private ResolveResult[] resolveInner() {
        PsiElement host = getElement();
        PsiFile file = host.getContainingFile();
        if (file == null) {
            return ResolveResult.EMPTY_ARRAY;
        }
        TypeEvalContext context = TypeEvalContext.codeAnalysis(host.getProject(), file);

        Set<PsiElement> resolved = new LinkedHashSet<>();
        ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(host);
        if (scopeOwner != null) {
            resolved.addAll(PyResolveUtil.resolveQualifiedNameInScope(myQualifiedName, scopeOwner, context));
        }
        if (resolved.isEmpty()) {
            PsiElement fullyQualified = PyResolveUtil.resolveFullyQualifiedName(myQualifiedName, host, context);
            if (fullyQualified != null) {
                resolved.add(fullyQualified);
            }
        }

        if (resolved.isEmpty()) {
            return ResolveResult.EMPTY_ARRAY;
        }
        ResolveResultList results = new ResolveResultList();
        for (PsiElement target : resolved) {
            results.poke(target, RatedResolveResult.RATE_NORMAL);
        }
        return results.toArray(ResolveResult.EMPTY_ARRAY);
    }

    @Override
    public boolean isSoft() {
        return mySoft;
    }

    @Override
    public PsiElement handleElementRename(String newElementName) {
        // A module rename carries a ".py" or a ".pyi" suffix that must not appear in the qualified name.
        String name = StringUtil.trimEnd(StringUtil.trimEnd(newElementName, PyNames.DOT_PY), PyNames.DOT_PYI);
        return super.handleElementRename(name);
    }

    /**
     * Rewrites the target path when the referenced object moves to another module. Only the components up to this one
     * change, so a move of the module in {@code :py:class:`pkg.mod.Foo`} keeps the {@code Foo} tail.
     */
    @Override
    public @Nullable PsiElement bindToElement(PsiElement element) {
        if (element.equals(resolve())) {
            return getElement();
        }
        QualifiedName newName = canonicalName(element);
        if (newName == null) {
            return null;
        }
        // JB: ElementManipulators.handleContentChange(element, range, text)
        return ElementManipulators.getNotNullManipulator(getElement()).handleContentChange(getElement(), myPrefixRange, newName.toString());
    }

    /**
     * Returns the path through which {@code target} is imported. A class that {@code pkg/_impl.py} defines and {@code pkg/__init__.py}
     * re-exports gets {@code pkg.Foo}, not {@code pkg._impl.Foo}. A class member keeps its path inside the top-level declaration.
     */
    private @Nullable QualifiedName canonicalName(PsiElement target) {
        if (target instanceof PyFile) {
            return QualifiedNameFinder.findCanonicalImportPath(target, getElement());
        }
        if (!(target instanceof PyQualifiedNameOwner)) {
            return null;
        }
        Deque<String> path = new ArrayDeque<>();
        PyQualifiedNameOwner declaration = (PyQualifiedNameOwner)target;
        while (true) {
            String declarationName = declaration.getName();
            if (declarationName == null) {
                return null;
            }
            path.addFirst(declarationName);
            ScopeOwner owner = ScopeUtil.getScopeOwner(declaration);
            if (owner instanceof PyFile) {
                break;
            }
            // A local of a function has no import path.
            if (!(owner instanceof PyClass ownerClass)) {
                return null;
            }
            declaration = ownerClass;
        }
        QualifiedName modulePath = QualifiedNameFinder.findCanonicalImportPath(declaration, getElement());
        if (modulePath == null) {
            return null;
        }
        return modulePath.append(QualifiedName.fromComponents(path));
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedHighlightSeverity(@Nullable TypeEvalContext context) {
        if (mySoft) {
            return null;
        }
        // Sphinx resolves a dotted target through its own inventory, which can hold objects this project does not see.
        // Warn only when the head of the path resolves, as PyCharm does for a qualified expression.
        PySphinxReference head = this.head;
        if (head == null) {
            return HighlightSeverity.WARNING;
        }
        return head.multiResolve(false).length == 0 ? null : HighlightSeverity.WARNING;
    }

    // The bundle template marks the name with backticks. `problemMessage` turns them into the quotes that the
    // Problems view shows, which `PyPsiBundle.message` would leave in place.
    @Override
    public String getUnresolvedDescription() {
        return PyInspectionMessages.problemMessage(params -> PyLocalize.inspUnresolvedRefsUnresolvedReference(params[0]), getValue())
            .description();
    }

    @Override
    public Object[] getVariants() {
        return new Object[0];
    }

    private static final class Resolver implements ResolveCache.PolyVariantResolver<PySphinxReference> {
        private static final Resolver INSTANCE = new Resolver();

        @Override
        public ResolveResult[] resolve(PySphinxReference ref, boolean incompleteCode) {
            return ref.resolveInner();
        }
    }
}
