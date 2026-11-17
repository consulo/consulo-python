// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.move;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.impl.PyImportStatementNavigator;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.fileTemplate.FileTemplateUtil;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiUtilCore;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.python.impl.localize.PyLocalize;
import consulo.usage.UsageInfo;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.util.QualifiedName;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Mikhail Golubev
 */
public final class PyMoveRefactoringUtil {
    private static final Logger LOG = Logger.getInstance(PyMoveRefactoringUtil.class);

    private PyMoveRefactoringUtil() {
    }

    /**
     * Checks that given file has importable name.
     *
     * @param anchor arbitrary PSI element to determine project, module, etc.
     * @param file   virtual file of the module to check
     * @see QualifiedNameFinder#findShortestImportableName(PsiElement, VirtualFile)
     */
    public static void checkValidImportableFile(PsiElement anchor, VirtualFile file) {
        QualifiedName qName = QualifiedNameFinder.findShortestImportableQName(anchor, file);
        if (!PyPsiRefactoringUtil.isValidQualifiedName(qName)) {
            throw new IncorrectOperationException(PyLocalize.refactoringMoveErrorCannotUseModuleName(file.getName()));
        }
    }

    /**
     * Returns name of the symbol suitable for displaying inside dialogs.
     * Functions will have parentheses after their name, names of another named
     * elements are returned as is.
     * <p>
     * If it's not possible to get the qualified name of the element, plain
     * {@link PsiNamedElement#getName()} will be called instead.
     * <p>
     * If {@link PsiNamedElement#getName()} returns {@code null} or emtpy string,
     * empty string is returned.
     *
     * @param element named PSI element
     * @return element name as described
     */
    public static String getPresentableName(PsiNamedElement element) {
        String name = null;
        if (element instanceof PyQualifiedNameOwner qualifiedNameOwner) {
            // Will return null for a local function
            name = qualifiedNameOwner.getQualifiedName();
        }
        if (StringUtil.isEmpty(name)) {
            name = element.getName();
        }
        if (StringUtil.isNotEmpty(name)) {
            return element instanceof PyFunction ? name + "()" : name;
        }
        return "";
    }

    /**
     * Returns anchor PSI element for {@link PsiElement#addBefore(PsiElement, PsiElement)}.
     * <p>
     * If there are any usages at file's level returns the top-level parent element for the first of them,
     * otherwise return {@code null} which means that the element can be safely inserted at the end of the file.
     *
     * @param usages      usages of the original element
     * @param destination file where original/generated element is to be moved
     * @return anchor element as described
     */
    public static @Nullable PsiElement findLowestPossibleTopLevelInsertionPosition(List<UsageInfo> usages, PsiFile destination) {
        return findFirstTopLevelUsageInFile(usages, destination)
            .map(element -> PyPsiUtils.getParentRightBefore(element, element.getContainingFile()))
            .orElse(null);
    }

    private static Optional<PsiElement> findFirstTopLevelUsageInFile(List<UsageInfo> usages, PsiFile destination) {
        return usages.stream()
            .map(UsageInfo::getElement)
            .filter(Objects::nonNull)
            .filter(element -> ScopeUtil.getScopeOwner(element) == destination)
            .filter(element -> PyImportStatementNavigator.getImportStatementByElement(element) == null)
            .min(PsiUtilCore::compareElementsByPosition);
    }

    public static void createInitPyInHierarchy(PsiDirectory created, PsiDirectory ancestor) {
        if (created.equals(ancestor)) {
            createInitPy(created);
            return;
        }
        PsiDirectory current = created;
        do {
            createInitPy(current);
            current = current.getParent();
        }
        while (current != null && !current.equals(ancestor));
    }

    private static void createInitPy(PsiDirectory directory) {
        FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance(directory.getProject());
        FileTemplate template = fileTemplateManager.getInternalTemplate("Python Script");
        if (directory.findFile(PyNames.INIT_DOT_PY) != null) {
            return;
        }

        try {
            FileTemplateUtil.createFromTemplate(template, PyNames.INIT_DOT_PY, fileTemplateManager.getDefaultProperties(), directory);
        }
        catch (Exception e) {
            LOG.error(e);
        }
    }
}
