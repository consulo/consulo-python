// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.docstrings;

import com.google.common.collect.Lists;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.impl.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyImportedModuleType;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.psi.ElementManipulators;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPolyVariantReferenceBase;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * User : catherine
 */
public class DocStringTypeReference extends PsiPolyVariantReferenceBase<PsiElement> {
    private @Nullable PyType myType;
    private final TextRange myFullRange;
    private final @Nullable PyImportElement myImportElement;

    public DocStringTypeReference(PsiElement element, TextRange range, TextRange fullRange, @Nullable PyType type,
                                  @Nullable PyImportElement importElement) {
        super(element, range);
        myFullRange = fullRange;
        myType = type;
        myImportElement = importElement;
    }

    @Override
    @RequiredWriteAction
    public @Nullable PsiElement bindToElement(PsiElement element) throws IncorrectOperationException {
        if (element.equals(resolve())) {
            return element;
        }
        if (myElement instanceof PyStringLiteralExpression e && element instanceof PyClass cls) {
            QualifiedName qname = QualifiedNameFinder.findCanonicalImportPath(cls, element);
            if (qname != null) {
                qname = qname.append(cls.getName());
                myType = new PyClassTypeImpl(cls, false);
                return ElementManipulators.getManipulator(e).handleContentChange(e, myFullRange, qname.toString());
            }
        }
        return null;
    }

    @Override
    public boolean isSoft() {
        return false;
    }

    @Override
    @RequiredWriteAction
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        newElementName = StringUtil.trimEnd(newElementName, PyNames.DOT_PY);
        return super.handleElementRename(newElementName);
    }

    @Override
    @RequiredReadAction
    public boolean isReferenceTo(PsiElement element) {
        if (myType instanceof PyImportedModuleType) {
            return element.equals(PyUtil.turnInitIntoDir(resolve()));
        }
        return super.isReferenceTo(element);
    }

    @Override
    @RequiredReadAction
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        PsiElement result = null;
        final ResolveResultList results = new ResolveResultList();
        if (myType instanceof PyClassType) {
            result = ((PyClassType) myType).getPyClass();
        }
        else if (myType instanceof PyImportedModuleType) {
            result = ((PyImportedModuleType) myType).getImportedModule().resolve();
        }
        else if (myType instanceof PyModuleType) {
            result = ((PyModuleType) myType).getModule();
        }
        if (result != null) {
            if (myImportElement != null) {
                results.add(new ImportedResolveResult(result, RatedResolveResult.RATE_NORMAL, myImportElement));
            }
            else {
                results.poke(result, RatedResolveResult.RATE_NORMAL);
            }
        }
        return results.toArray(ResolveResult.EMPTY_ARRAY);
    }

    @Override
    @RequiredReadAction
    public Object[] getVariants() {
        // see PyDocstringCompletionContributor
        return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @RequiredReadAction
    public List<Object> collectTypeVariants() {
        final PsiFile file = myElement.getContainingFile();
        final List<Object> variants =
            Lists.newArrayList(PyNames.TYPE_STR, PyNames.TYPE_INT, "basestring", "bool", "buffer", "bytearray", "complex", "dict",
                "tuple", "enumerate", "file", "float", "frozenset", "list", PyNames.TYPE_LONG, "set", "object");
        if (file instanceof PyFile) {
            variants.addAll(((PyFile) file).getTopLevelClasses());
            final List<PyFromImportStatement> fromImports = ((PyFile) file).getFromImports();
            for (PyFromImportStatement fromImportStatement : fromImports) {
                final PyImportElement[] elements = fromImportStatement.getImportElements();
                for (PyImportElement element : elements) {
                    final PyReferenceExpression referenceExpression = element.getImportReferenceExpression();
                    if (referenceExpression == null) {
                        continue;
                    }
                    final PyType type = TypeEvalContext.userInitiated(file.getProject(), CompletionUtilCore
                        .getOriginalOrSelf(file)).getType(referenceExpression);
                    if (type instanceof PyClassType) {
                        variants.add(((PyClassType) type).getPyClass());
                    }
                }
            }
        }
        return variants;
    }
}
