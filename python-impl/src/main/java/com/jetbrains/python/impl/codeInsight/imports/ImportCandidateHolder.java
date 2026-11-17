// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.impl.codeInsight.completion.PyCompletionUtilsKt;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.SmartPointerManager;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.QualifiedName;
import consulo.logging.Logger;
import consulo.util.lang.Comparing;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/**
 * An immutable holder of information for one auto-import candidate.
 * <p/>
 * There can be do different flavors of such candidates:
 * <ul>
 *   <li>Candidates based on existing imports in module. In this case {@link #getImportElement()} must return not {@code null}.</li>
 *   <li>Candidates not yet imported. In this case {@link #getPath()} must return not {@code null}.</li>
 * </ul>
 * <p/>
 */
public class ImportCandidateHolder implements Comparable<ImportCandidateHolder> {
    private static final Logger LOG = Logger.getInstance(ImportCandidateHolder.class);

    private final SmartPsiElementPointer<PsiNamedElement> myImportable;
    private final @Nullable SmartPsiElementPointer<PyImportElement> myImportElement;
    private final SmartPsiElementPointer<PsiFileSystemItem> myFile;
    private final @Nullable QualifiedName myPath;
    private final String myImportableName;
    private final @Nullable String myAsName;
    private final @Nullable String myQualifiedReferenceText;
    private final int myRelevance;

    /**
     * Creates new instance.
     *
     * @param importable    an element that could be imported either from import element or from file.
     * @param file          the file which is the source of the importable (module for symbols, containing directory for modules and packages)
     * @param importElement an existing import element that can be a source for the importable.
     * @param path          import path for the file, as a qualified name (a.b.c)
     *                      For top-level imported symbols it's <em>qualified name of containing module</em> (or package for __init__.py).
     *                      For modules and packages it should be <em>qualified name of their parental package</em>
     *                      (empty for modules and packages located at source roots).
     */
    public ImportCandidateHolder(PsiNamedElement importable, PsiFileSystemItem file,
                                 @Nullable PyImportElement importElement, @Nullable QualifiedName path,
                                 @Nullable String asName, @Nullable String qualifiedReferenceText) {
        if (importElement == null && path == null) {
            throw new IllegalArgumentException("Either an import path or an existing import should be provided for " + importable);
        }
        SmartPointerManager pointerManager = SmartPointerManager.getInstance(importable.getProject());
        myFile = pointerManager.createSmartPsiElementPointer(file);
        myImportable = pointerManager.createSmartPsiElementPointer(importable);
        myImportableName = PyUtil.getElementNameWithoutExtension(importable);
        assert myImportableName != null;
        myImportElement = importElement != null ? pointerManager.createSmartPsiElementPointer(importElement) : null;
        myPath = path;
        myAsName = asName;
        myQualifiedReferenceText = qualifiedReferenceText;
        myRelevance = PyCompletionUtilsKt.computeCompletionWeight(importable, myImportableName, myPath, null, false);
        LOG.debug("Computed relevance for import item ", myImportableName, ": ", myRelevance);
    }

    public ImportCandidateHolder(PsiNamedElement importable, PsiFileSystemItem file,
                                 @Nullable PyImportElement importElement, @Nullable QualifiedName path) {
        this(importable, file, importElement, path, null, null);
    }

    public @Nullable PsiNamedElement getImportable() {
        return myImportable.getElement();
    }

    public String getImportableName() {
        return myImportableName;
    }

    public @Nullable PyImportElement getImportElement() {
        return myImportElement != null ? myImportElement.getElement() : null;
    }

    public @Nullable PsiFileSystemItem getFile() {
        return myFile.getElement();
    }

    public @Nullable QualifiedName getPath() {
        return myPath;
    }

    public @Nullable String getQualifiedReferenceText() {
        return myQualifiedReferenceText;
    }

    /**
     * Helper method that builds an import path, handling all these "import foo", "import foo as bar", "from bar import foo", etc.
     * Either importPath or importSource must be not null.
     *
     * @param name       what is ultimately imported.
     * @param importPath known path to import the name.
     * @param source     known ImportElement to import the name; its 'as' clause is used if present.
     * @return a properly qualified name.
     */
    public static String getQualifiedName(String name,
                                          @Nullable QualifiedName importPath,
                                          @Nullable PyImportElement source) {
        final StringBuilder sb = new StringBuilder();
        if (source != null) {
            final PsiElement parent = source.getParent();
            if (parent instanceof PyFromImportStatement) {
                sb.append(name);
            }
            else {
                sb.append(source.getVisibleName()).append(".").append(name);
            }
        }
        else {
            if (importPath != null && importPath.getComponentCount() > 0) {
                sb.append(importPath).append(".");
            }
            sb.append(name);
        }
        return sb.toString();
    }

    public String getPresentableText() {
        PyImportElement importElement = getImportElement();
        // For regular imports with qualifiedReferenceText, the text already contains the full qualified path
        if (myQualifiedReferenceText != null && importElement != null && !(importElement.getParent() instanceof PyFromImportStatement)) {
            return myQualifiedReferenceText;
        }
        final String name = myQualifiedReferenceText != null ? myQualifiedReferenceText : getImportableName();
        final QualifiedName path = myQualifiedReferenceText != null ? null : myPath;
        final StringBuilder sb = new StringBuilder(getQualifiedName(name, path, importElement));
        PsiElement parent = null;
        if (importElement != null) {
            parent = importElement.getParent();
        }
        if (parent instanceof PyFromImportStatement fromImportStatement) {
            sb.append(" from ");
            sb.append(StringUtil.repeat(".", fromImportStatement.getRelativeLevel()));
            final PyReferenceExpression source = fromImportStatement.getImportSource();
            if (source != null) {
                sb.append(source.asQualifiedName());
            }
        }
        else if (importElement == null && myQualifiedReferenceText != null && myPath != null) {
            sb.append(" from ").append(myPath);
        }
        if (myAsName != null) {
            sb.append(" as ").append(myAsName);
        }
        return sb.toString();
    }

    @Override
    public int compareTo(ImportCandidateHolder other) {
        if (myImportElement != null && other.myImportElement == null) {
            return -1;
        }
        if (myImportElement == null && other.myImportElement != null) {
            return 1;
        }
        if (myAsName != null && other.myAsName == null) {
            return -1;
        }
        if (myAsName == null && other.myAsName != null) {
            return 1;
        }

        int comparedRelevance = Comparator
            .comparing(ImportCandidateHolder::getRelevance).reversed()
            .compare(this, other);
        if (comparedRelevance != 0) {
            return comparedRelevance;
        }

        QualifiedName myLocalPath = getPath();
        QualifiedName otherPath = other.getPath();
        if (myImportElement != null) {
            // both are not null here
            PyImportElement myElement = myImportElement.getElement();
            PyImportElement otherElement = other.myImportElement.getElement();
            if (myElement != null && otherElement != null) {
                myLocalPath = myElement.getImportedQName();
                otherPath = otherElement.getImportedQName();
            }
        }
        return Comparing.compare(myLocalPath, otherPath);
    }

    public int getRelevance() {
        return myRelevance;
    }

    public @Nullable String getAsName() {
        return myAsName;
    }
}
