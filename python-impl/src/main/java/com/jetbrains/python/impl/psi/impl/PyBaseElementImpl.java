// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.ast.PyAstElementVisitor;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyReferenceOwner;
import com.jetbrains.python.psi.PyTypeCommentOwner;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.stubs.PyTypeCommentOwnerStub;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.impl.psi.stub.StubBasedPsiElementBase;
import consulo.language.psi.OuterLanguageElement;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiMultiReference;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ReferenceRange;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.stub.IStubElementType;
import consulo.language.psi.stub.StubElement;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class PyBaseElementImpl<T extends StubElement> extends StubBasedPsiElementBase<T> implements PyElement {
    public PyBaseElementImpl(T stub, IStubElementType nodeType) {
        super(stub, nodeType);
    }

    public PyBaseElementImpl(ASTNode node) {
        super(node);
    }

    @Override
    @RequiredReadAction
    public PythonLanguage getLanguage() {
        return (PythonLanguage) PythonFileType.INSTANCE.getLanguage();
    }

    @Override
    public String toString() {
        String className = getClass().getName();
        int pos = className.lastIndexOf('.');
        if (pos >= 0) {
            className = className.substring(pos + 1);
        }
        className = StringUtil.trimEnd(className, "Impl");
        return className;
    }

    @Override
    public void accept(PsiElementVisitor visitor) {
        PyUtil.verboseOnly(() -> PyPsiUtils.assertValid(this));
        if (visitor instanceof PyElementVisitor pyVisitor) {
            acceptPyVisitor(pyVisitor);
        }
        else if (visitor instanceof PyAstElementVisitor pyVisitor) {
            acceptPyVisitor(pyVisitor);
        }
        else {
            super.accept(visitor);
        }
    }

    protected void acceptPyVisitor(PyElementVisitor pyVisitor) {
        pyVisitor.visitPyElement(this);
    }

    /**
     * Overrides the findReferenceAt() logic in order to provide a resolve context with origin file for returned references.
     * The findReferenceAt() is usually invoked from UI operations, and it helps to be able to do deeper analysis in the
     * current file for such operations.
     *
     * @param offset the offset to find the reference at
     * @return the reference or null.
     */
    @Override
    @RequiredReadAction
    public PsiReference findReferenceAt(int offset) {
        return findReferenceAt(this, offset);
    }

    @RequiredReadAction
    protected static <T extends StubBasedPsiElement<? extends PyTypeCommentOwnerStub> & PyTypeCommentOwner>
    @Nullable String getTypeCommentAnnotationFromStubOrPsi(T elem) {
        PyTypeCommentOwnerStub stub = elem.getStub();
        if (stub != null) {
            return stub.getTypeComment();
        }
        PsiComment comment = elem.getTypeComment();
        if (comment != null) {
            return PyTypingTypeProvider.getTypeCommentValue(comment.getText());
        }
        return null;
    }

    @Override
    public <E extends PsiElement> @Nullable E getStubOrPsiParentOfType(Class<E> parentClass) {
        return super.getStubOrPsiParentOfType(parentClass);
    }

    @RequiredReadAction
    static @Nullable PsiReference findReferenceAt(PsiElement target, int offset) {
        PsiElement element = target.findElementAt(offset);
        if (element == null || element instanceof OuterLanguageElement) {
            return null;
        }
        offset = target.getTextRange().getStartOffset() + offset - element.getTextRange().getStartOffset();

        List<PsiReference> referencesList = new ArrayList<>();
        PsiFile file = element.getContainingFile();
        TypeEvalContext context =
            file != null ? TypeEvalContext.codeAnalysis(file.getProject(), file) : TypeEvalContext.codeInsightFallback(element.getProject());
        PyResolveContext resolveContext = PyResolveContext.implicitContext(context);
        while (element != null) {
            addReferences(offset, element, referencesList, resolveContext);
            offset = element.getStartOffsetInParent() + offset;
            if (element instanceof PsiFile) {
                break;
            }
            element = element.getParent();
        }

        if (referencesList.isEmpty()) {
            return null;
        }
        if (referencesList.size() == 1) {
            return referencesList.get(0);
        }
        return new PsiMultiReference(referencesList.toArray(PsiReference.EMPTY_ARRAY),
            referencesList.get(referencesList.size() - 1).getElement());
    }

    @RequiredReadAction
    private static void addReferences(int offset,
                                      PsiElement element,
                                      Collection<PsiReference> outReferences,
                                      PyResolveContext resolveContext) {
        PsiReference[] references;
        if (element instanceof PyReferenceOwner owner) {
            PsiPolyVariantReference reference = owner.getReference(resolveContext);
            references = new PsiReference[]{reference};
        }
        else {
            references = element.getReferences();
        }
        for (PsiReference reference : references) {
            for (TextRange range : ReferenceRange.getRanges(reference)) {
                assert range != null : reference;
                if (range.containsOffset(offset)) {
                    outReferences.add(reference);
                }
            }
        }
    }
}
