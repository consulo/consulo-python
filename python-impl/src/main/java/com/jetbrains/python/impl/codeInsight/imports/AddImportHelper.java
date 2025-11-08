/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.impl.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.formatter.PyBlock;
import com.jetbrains.python.impl.formatter.PyCodeStyleSettings;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.content.bundle.Sdk;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.jetbrains.python.impl.psi.PyUtil.as;
import static com.jetbrains.python.impl.psi.PyUtil.sure;

/**
 * Does the actual job of adding an import statement into a file.
 *
 * @author dcheryasov
 * @since 2009-04-24
 */
public class AddImportHelper {
    private static final Logger LOG = Logger.getInstance(AddImportHelper.class);

    // normal imports go first, then "from" imports
    private static final Comparator<PyImportStatementBase> IMPORT_TYPE_COMPARATOR = (import1, import2) -> {
        int firstIsFromImport = import1 instanceof PyFromImportStatement ? 1 : 0;
        int secondIsFromImport = import2 instanceof PyFromImportStatement ? 1 : 0;
        return firstIsFromImport - secondIsFromImport;
    };

    private static final Comparator<PyImportStatementBase> IMPORT_NAMES_COMPARATOR =
        (import1, import2) -> ContainerUtil.compareLexicographically(getSortNames(import1), getSortNames(import2));

    @Nonnull
    private static List<String> getSortNames(@Nonnull PyImportStatementBase importStatement) {
        List<String> result = new ArrayList<>();
        PyFromImportStatement fromImport = as(importStatement, PyFromImportStatement.class);
        if (fromImport != null) {
            // because of that relative imports go to the end of an import block
            result.add(StringUtil.repeatSymbol('.', fromImport.getRelativeLevel()));
            QualifiedName source = fromImport.getImportSourceQName();
            result.add(Objects.toString(source, ""));
            if (fromImport.isStarImport()) {
                result.add("*");
            }
        }
        else {
            // fake relative level
            result.add("");
        }

        for (PyImportElement importElement : importStatement.getImportElements()) {
            QualifiedName qualifiedName = importElement.getImportedQName();
            result.add(Objects.toString(qualifiedName, ""));
            result.add(StringUtil.notNullize(importElement.getAsName()));
        }
        return result;
    }

    /**
     * Creates and return comparator for import statements that compares them according to the rules specified in the code style settings.
     * It's intended to be used for imports that have the same import priority in order to sort them within the corresponding group.
     *
     * @see ImportPriority
     */
    @Nonnull
    public static Comparator<PyImportStatementBase> getSameGroupImportsComparator(@Nonnull Project project) {
        PyCodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project).getCustomSettings(PyCodeStyleSettings.class);
        if (settings.OPTIMIZE_IMPORTS_SORT_BY_TYPE_FIRST) {
            return IMPORT_TYPE_COMPARATOR.thenComparing(IMPORT_NAMES_COMPARATOR);
        }
        else {
            return IMPORT_NAMES_COMPARATOR.thenComparing(IMPORT_TYPE_COMPARATOR);
        }
    }

    public enum ImportPriority {
        FUTURE,
        BUILTIN,
        THIRD_PARTY,
        PROJECT
    }

    private static final ImportPriority UNRESOLVED_SYMBOL_PRIORITY = ImportPriority.THIRD_PARTY;

    private AddImportHelper() {
    }

    @RequiredWriteAction
    public static void addLocalImportStatement(@Nonnull PsiElement element, @Nonnull String name) {
        PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(element);

        PsiElement anchor = getLocalInsertPosition(element);
        PsiElement parentElement = sure(anchor).getParent();
        if (parentElement != null) {
            parentElement.addBefore(generator.createImportStatement(languageLevel, name, null), anchor);
        }
    }

    @RequiredWriteAction
    public static void addLocalFromImportStatement(@Nonnull PsiElement element, @Nonnull String qualifier, @Nonnull String name) {
        PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(element);

        PsiElement anchor = getLocalInsertPosition(element);
        PsiElement parentElement = sure(anchor).getParent();
        if (parentElement != null) {
            parentElement.addBefore(generator.createFromImportStatement(languageLevel, qualifier, name, null), anchor);
        }
    }

    @Nullable
    public static PsiElement getLocalInsertPosition(@Nonnull PsiElement anchor) {
        return PsiTreeUtil.getParentOfType(anchor, PyStatement.class, false);
    }

    @Nullable
    @RequiredReadAction
    public static PsiElement getFileInsertPosition(PsiFile file) {
        return getInsertPosition(file, null, null);
    }

    @Nullable
    @RequiredReadAction
    private static PsiElement getInsertPosition(
        @Nonnull PsiElement insertParent,
        @Nullable PyImportStatementBase newImport,
        @Nullable ImportPriority priority
    ) {
        PsiElement feeler = insertParent.getFirstChild();
        if (feeler == null) {
            return null;
        }
        // skip initial comments and whitespace and try to get just below the last import stmt
        boolean skippedOverImports = false;
        boolean skippedOverDoc = false;
        PsiElement seeker = feeler;
        boolean isInjected = InjectedLanguageManager.getInstance(feeler.getProject()).isInjectedFragment(feeler.getContainingFile());
        PyImportStatementBase importAbove = null, importBelow = null;
        do {
            if (feeler instanceof PyImportStatementBase && !isInjected) {
                PyImportStatementBase existingImport = (PyImportStatementBase) feeler;
                if (priority != null && newImport != null) {
                    if (shouldInsertBefore(newImport, existingImport, priority)) {
                        importBelow = existingImport;
                        break;
                    }
                    else {
                        importAbove = existingImport;
                    }
                }
                seeker = feeler;
                feeler = feeler.getNextSibling();
                skippedOverImports = true;
            }
            else if (PyUtil.instanceOf(feeler, PsiWhiteSpace.class, PsiComment.class)) {
                seeker = feeler;
                feeler = feeler.getNextSibling();
            }
            // maybe we arrived at the doc comment stmt; skip over it, too
            else if (!skippedOverImports && !skippedOverDoc && insertParent instanceof PyFile) {
                // this gives the literal; its parent is the expr seeker may have encountered
                PsiElement docElem = DocStringUtil.findDocStringExpression((PyElement) insertParent);
                if (docElem != null && docElem.getParent() == feeler) {
                    feeler = feeler.getNextSibling();
                    seeker = feeler; // skip over doc even if there's nothing below it
                    skippedOverDoc = true;
                }
                else {
                    break; // not a doc comment, stop on it
                }
            }
            else {
                break; // some other statement, stop
            }
        }
        while (feeler != null);

        ImportPriority priorityAbove = importAbove != null ? getImportPriority(importAbove) : null;
        ImportPriority priorityBelow = importBelow != null ? getImportPriority(importBelow) : null;
        if (newImport != null && (priorityAbove == null || priorityAbove.compareTo(priority) < 0)) {
            newImport.putCopyableUserData(PyBlock.IMPORT_GROUP_BEGIN, true);
        }
        if (priorityBelow != null) {
            // actually not necessary because existing import with higher priority (i.e. lower import group)
            // probably should have IMPORT_GROUP_BEGIN flag already, but we add it anyway just for safety
            if (priorityBelow.compareTo(priority) > 0) {
                importBelow.putCopyableUserData(PyBlock.IMPORT_GROUP_BEGIN, true);
            }
            else if (priorityBelow == priority) {
                importBelow.putCopyableUserData(PyBlock.IMPORT_GROUP_BEGIN, null);
            }
        }
        return seeker;
    }

    @RequiredReadAction
    private static boolean shouldInsertBefore(
        @Nullable PyImportStatementBase newImport,
        @Nonnull PyImportStatementBase existingImport,
        @Nonnull ImportPriority priority
    ) {
        ImportPriority existingImportPriority = getImportPriority(existingImport);
        int byPriority = priority.compareTo(existingImportPriority);
        if (byPriority != 0) {
            return byPriority < 0;
        }
        if (newImport == null) {
            return false;
        }
        return getSameGroupImportsComparator(existingImport.getProject()).compare(newImport, existingImport) < 0;
    }

    @Nonnull
    @RequiredReadAction
    public static ImportPriority getImportPriority(@Nonnull PyImportStatementBase importStatement) {
        PsiElement resolved;
        if (importStatement instanceof PyFromImportStatement fromImportStatement) {
            if (fromImportStatement.isFromFuture()) {
                return ImportPriority.FUTURE;
            }
            if (fromImportStatement.getRelativeLevel() > 0) {
                return ImportPriority.PROJECT;
            }
            resolved = fromImportStatement.resolveImportSource();
        }
        else {
            PyImportElement firstImportElement = ArrayUtil.getFirstElement(importStatement.getImportElements());
            if (firstImportElement == null) {
                return UNRESOLVED_SYMBOL_PRIORITY;
            }
            resolved = firstImportElement.resolve();
        }
        if (resolved == null) {
            return UNRESOLVED_SYMBOL_PRIORITY;
        }

        PsiFileSystemItem resolvedFileOrDir;
        if (resolved instanceof PsiDirectory directory) {
            resolvedFileOrDir = directory;
        }
        // resolved symbol may be PsiPackage in Jython
        else if (resolved instanceof PsiDirectoryContainer directoryContainer) {
            resolvedFileOrDir = ArrayUtil.getFirstElement(directoryContainer.getDirectories());
        }
        else {
            resolvedFileOrDir = resolved.getContainingFile();
        }

        if (resolvedFileOrDir == null) {
            return UNRESOLVED_SYMBOL_PRIORITY;
        }

        return getImportPriority(importStatement, resolvedFileOrDir);
    }

    @Nonnull
    @RequiredReadAction
    public static ImportPriority getImportPriority(@Nonnull PsiElement importLocation, @Nonnull PsiFileSystemItem toImport) {
        VirtualFile vFile = toImport.getVirtualFile();
        if (vFile == null) {
            return UNRESOLVED_SYMBOL_PRIORITY;
        }
        ProjectRootManager projectRootManager = ProjectRootManager.getInstance(toImport.getProject());
        ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
        if (fileIndex.isInContent(vFile) && !fileIndex.isInLibraryClasses(vFile)) {
            return ImportPriority.PROJECT;
        }
        Module module = importLocation.getModule();
        Sdk pythonSdk = module != null ? PythonSdkType.findPythonSdk(module) : null;

        return PythonSdkType.isStdLib(vFile, pythonSdk) ? ImportPriority.BUILTIN : ImportPriority.THIRD_PARTY;
    }

    /**
     * Adds an import statement, if it doesn't exist yet, presumably below all other initial imports in the file.
     *
     * @param file   where to operate
     * @param name   which to import (qualified is OK)
     * @param asName optional name for 'as' clause
     * @param anchor place where the imported name was used. It will be used to determine proper block where new import should be inserted,
     *               e.g. inside conditional block or try/except statement. Also if anchor is another import statement, new import statement
     *               will be inserted right after it.
     * @return whether import statement was actually added
     */
    @RequiredWriteAction
    public static boolean addImportStatement(
        @Nonnull PsiFile file,
        @Nonnull String name,
        @Nullable String asName,
        @Nullable ImportPriority priority,
        @Nullable PsiElement anchor
    ) {
        if (!(file instanceof PyFile pyFile)) {
            return false;
        }
        List<PyImportElement> existingImports = pyFile.getImportTargets();
        for (PyImportElement element : existingImports) {
            QualifiedName qName = element.getImportedQName();
            if (qName != null && name.equals(qName.toString())) {
                if ((asName != null && asName.equals(element.getAsName())) || (asName == null && element.getAsName() == null)) {
                    return false;
                }
            }
        }

        PyElementGenerator generator = PyElementGenerator.getInstance(file.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(file);
        PyImportStatement importNodeToInsert = generator.createImportStatement(languageLevel, name, asName);
        PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(anchor, PyImportStatementBase.class, false);
        PsiElement insertParent =
            importStatement != null && importStatement.getContainingFile() == file ? importStatement.getParent() : file;
        try {
            if (anchor instanceof PyImportStatementBase) {
                insertParent.addAfter(importNodeToInsert, anchor);
            }
            else {
                insertParent.addBefore(importNodeToInsert, getInsertPosition(insertParent, importNodeToInsert, priority));
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
        return true;
    }

    /**
     * Adds a new {@link PyFromImportStatement} statement within other top-level imports or as specified by anchor.
     *
     * @param file   where to operate
     * @param from   import source (reference after {@code from} keyword)
     * @param name   imported name (identifier after {@code import} keyword)
     * @param asName optional alias (identifier after {@code as} keyword)
     * @param anchor place where the imported name was used. It will be used to determine proper block where new import should be inserted,
     *               e.g. inside conditional block or try/except statement. Also if anchor is another import statement, new import statement
     *               will be inserted right after it.
     * @see #addOrUpdateFromImportStatement
     */
    @RequiredWriteAction
    public static void addFromImportStatement(
        @Nonnull PsiFile file,
        @Nonnull String from,
        @Nonnull String name,
        @Nullable String asName,
        @Nullable ImportPriority priority,
        @Nullable PsiElement anchor
    ) {
        PyElementGenerator generator = PyElementGenerator.getInstance(file.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(file);
        PyFromImportStatement newImport = generator.createFromImportStatement(languageLevel, from, name, asName);
        addFromImportStatement(file, newImport, priority, anchor);
    }

    /**
     * Adds a new {@link PyFromImportStatement} statement within other top-level imports or as specified by anchor.
     *
     * @param file      where to operate
     * @param newImport new "from import" statement to insert. It may be generated, because it won't be used for resolving anyway.
     *                  You might want to use overloaded version of this method to generate such statement automatically.
     * @param anchor    place where the imported name was used. It will be used to determine proper block where new import should be inserted,
     *                  e.g. inside conditional block or try/except statement. Also if anchor is another import statement, new import statement
     *                  will be inserted right after it.
     * @see #addFromImportStatement(PsiFile, String, String, String, ImportPriority, PsiElement)
     * @see #addFromImportStatement
     */
    @RequiredWriteAction
    public static void addFromImportStatement(
        @Nonnull PsiFile file,
        @Nonnull PyFromImportStatement newImport,
        @Nullable ImportPriority priority,
        @Nullable PsiElement anchor
    ) {
        try {
            PyImportStatementBase parentImport = PsiTreeUtil.getParentOfType(anchor, PyImportStatementBase.class, false);
            PsiElement insertParent;
            if (parentImport != null && parentImport.getContainingFile() == file) {
                insertParent = parentImport.getParent();
            }
            else {
                insertParent = file;
            }
            if (InjectedLanguageManager.getInstance(file.getProject()).isInjectedFragment(file)) {
                PsiElement element = insertParent.addBefore(newImport, getInsertPosition(insertParent, newImport, priority));
                PsiElement whitespace = element.getNextSibling();
                if (!(whitespace instanceof PsiWhiteSpace)) {
                    whitespace = PsiParserFacade.SERVICE.getInstance(file.getProject()).createWhiteSpaceFromText("  >>> ");
                }
                insertParent.addBefore(whitespace, element);
            }
            else if (anchor instanceof PyImportStatementBase) {
                insertParent.addAfter(newImport, anchor);
            }
            else {
                insertParent.addBefore(newImport, getInsertPosition(insertParent, newImport, priority));
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    /**
     * Adds new {@link PyFromImportStatement} in file or append {@link PyImportElement} to
     * existing from import statement.
     *
     * @param file     module where import will be added
     * @param from     import source (reference after {@code from} keyword)
     * @param name     imported name (identifier after {@code import} keyword)
     * @param asName   optional alias (identifier after {@code as} keyword)
     * @param priority optional import priority used to sort imports
     * @param anchor   place where the imported name was used. It will be used to determine proper block where new import should be inserted,
     *                 e.g. inside conditional block or try/except statement. Also if anchor is another import statement, new import statement
     *                 will be inserted right after it.
     * @return whether import was actually added
     * @see #addFromImportStatement
     */
    @RequiredWriteAction
    public static boolean addOrUpdateFromImportStatement(
        @Nonnull PsiFile file,
        @Nonnull String from,
        @Nonnull String name,
        @Nullable String asName,
        @Nullable ImportPriority priority,
        @Nullable PsiElement anchor
    ) {
        List<PyFromImportStatement> existingImports = ((PyFile) file).getFromImports();
        for (PyFromImportStatement existingImport : existingImports) {
            if (existingImport.isStarImport()) {
                continue;
            }
            QualifiedName qName = existingImport.getImportSourceQName();
            if (qName != null && qName.toString().equals(from) && existingImport.getRelativeLevel() == 0) {
                for (PyImportElement el : existingImport.getImportElements()) {
                    QualifiedName importedQName = el.getImportedQName();
                    if (importedQName != null
                        && StringUtil.equals(name, importedQName.toString())
                        && StringUtil.equals(asName, el.getAsName())) {
                        return false;
                    }
                }
                PyElementGenerator generator = PyElementGenerator.getInstance(file.getProject());
                PyImportElement importElement = generator.createImportElement(LanguageLevel.forElement(file), name);
                existingImport.add(importElement);
                return false;
            }
        }
        addFromImportStatement(file, from, name, asName, priority, anchor);
        return true;
    }

    /**
     * Adds either {@link PyFromImportStatement} or {@link PyImportStatement}
     * to specified target depending on user preferences and whether it's possible to import element via "from" form of import
     * (e.g. consider top level module).
     *
     * @param target  element import is pointing to
     * @param file    file where import will be inserted
     * @param element used to determine where to insert import
     * @see PyCodeInsightSettings#PREFER_FROM_IMPORT
     * @see #addImportStatement
     * @see #addOrUpdateFromImportStatement
     */
    @RequiredWriteAction
    public static void addImport(PsiNamedElement target, PsiFile file, PyElement element) {
        boolean useQualified = !PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT;
        PsiFileSystemItem toImport = target instanceof PsiFileSystemItem fsItem ? fsItem.getParent() : target.getContainingFile();
        if (toImport == null) {
            return;
        }
        ImportPriority priority = getImportPriority(file, toImport);
        QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(target, element);
        if (qName == null) {
            return;
        }
        String path = qName.toString();
        if (target instanceof PsiFileSystemItem && qName.getComponentCount() == 1) {
            addImportStatement(file, path, null, priority, element);
        }
        else {
            QualifiedName toImportQName = QualifiedNameFinder.findCanonicalImportPath(toImport, element);
            if (toImportQName == null) {
                return;
            }
            if (useQualified) {
                addImportStatement(file, path, null, priority, element);
                PyElementGenerator elementGenerator = PyElementGenerator.getInstance(file.getProject());
                String targetName = PyUtil.getElementNameWithoutExtension(target);
                element.replace(elementGenerator.createExpressionFromText(
                    LanguageLevel.forElement(target),
                    toImportQName + "." + targetName
                ));
            }
            else {
                String name = target.getName();
                if (name != null) {
                    addOrUpdateFromImportStatement(file, toImportQName.toString(), name, null, priority, element);
                }
            }
        }
    }
}
