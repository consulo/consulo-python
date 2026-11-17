// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.imports;

import com.jetbrains.python.PythonCodeStyleService;
import com.jetbrains.python.ast.impl.PyUtilCore;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.documentation.doctest.PyDoctestFile;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCodeFragmentWithHiddenImports;
import com.jetbrains.python.impl.psi.resolve.MainPythonSdkKt;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.pyi.PyiFile;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.impl.sdk.skeleton.PySkeletonUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.application.util.registry.Registry;
import consulo.content.bundle.Sdk;
import consulo.ide.impl.idea.util.containers.ContainerUtil;
import consulo.language.codeStyle.CodeStyleManager;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.OuterLanguageElement;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDirectoryContainer;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiParserFacade;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.ProjectRootManager;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Does the actual job of adding an import statement into a file.
 */
public final class AddImportHelper {
    private static final Logger LOG = Logger.getInstance(AddImportHelper.class);

    private AddImportHelper() {
    }

    // normal imports go first, then "from" imports
    private static final Comparator<PyImportStatementBase> IMPORT_TYPE_COMPARATOR = (import1, import2) -> {
        int firstIsFromImport = import1 instanceof PyFromImportStatement ? 1 : 0;
        int secondIsFromImport = import2 instanceof PyFromImportStatement ? 1 : 0;
        return firstIsFromImport - secondIsFromImport;
    };

    private static Comparator<PyImportStatementBase> getImportStatementComparator(PsiFile settingsAnchor) {
        return (import1, import2) -> {
            Comparator<String> stringComparator = getImportTextComparator(settingsAnchor);
            return ContainerUtil.compareLexicographically(
                getSortNames(import1),
                getSortNames(import2),
                Comparator.nullsFirst(stringComparator)
            );
        };
    }

    public static Comparator<String> getImportTextComparator(PsiFile settingsAnchor) {
        return PythonCodeStyleService.getInstance().isOptimizeImportsCaseSensitiveOrder(settingsAnchor)
            ? String.CASE_INSENSITIVE_ORDER
            : Comparator.naturalOrder();
    }

    private static List<String> getSortNames(PyImportStatementBase importStatement) {
        List<String> result = new ArrayList<>();
        if (importStatement instanceof PyFromImportStatement fromImport) {
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
            String asName = importElement.getAsName();
            result.add(asName != null ? asName : "");
        }
        return result;
    }

    /**
     * Creates and return comparator for import statements that compares them according to the rules specified in the code style settings.
     * It's intended to be used for imports that have the same import priority in order to sort them within the corresponding group.
     *
     * @param settingsAnchor file to use as an anchor to detect settings of Optimize Imports
     * @see ImportPriority
     */
    public static Comparator<PyImportStatementBase> getSameGroupImportsComparator(PsiFile settingsAnchor) {
        if (PythonCodeStyleService.getInstance().isOptimizeImportsSortedByTypeFirst(settingsAnchor)) {
            return IMPORT_TYPE_COMPARATOR.thenComparing(getImportStatementComparator(settingsAnchor));
        }
        else {
            return getImportStatementComparator(settingsAnchor).thenComparing(IMPORT_TYPE_COMPARATOR);
        }
    }

    private static final ImportPriority UNRESOLVED_SYMBOL_PRIORITY = ImportPriority.THIRD_PARTY;

    public static void addLocalImportStatement(PsiElement element, String name, @Nullable String asName) {
        PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(element);

        PsiElement anchor = getLocalInsertPosition(element);
        PsiElement parentElement = PyUtil.sure(anchor).getParent();
        if (parentElement != null) {
            parentElement.addBefore(generator.createImportStatement(languageLevel, name, asName), anchor);
        }
    }

    public static void addLocalFromImportStatement(PsiElement element,
                                                   String qualifier,
                                                   String name,
                                                   @Nullable String asName) {
        PyElementGenerator generator = PyElementGenerator.getInstance(element.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(element);

        PsiElement anchor = getLocalInsertPosition(element);
        PsiElement parentElement = PyUtil.sure(anchor).getParent();
        if (parentElement != null) {
            parentElement.addBefore(generator.createFromImportStatement(languageLevel, qualifier, name, asName), anchor);
        }
    }

    public static @Nullable PsiElement getLocalInsertPosition(PsiElement anchor) {
        return PsiTreeUtil.getParentOfType(anchor, PyStatement.class, false);
    }

    /**
     * Returns position in the file after all leading comments, docstring and import statements.
     * <p>
     * Returned PSI element is intended to be used as "anchor" parameter for {@link PsiElement#addBefore},
     * hence {@code null} means that element to be inserted will be the first in the file.
     *
     * @param file target file where some new top-level element is going to be inserted
     * @return anchor PSI element as described
     */
    public static @Nullable PsiElement getFileInsertPosition(PsiFile file) {
        return getInsertPosition(file, null, null, null);
    }

    private static @Nullable PsiElement getInsertPosition(PsiElement insertParent,
                                                          @Nullable PsiElement anchor,
                                                          @Nullable PyImportStatementBase newImport,
                                                          @Nullable ImportPriority priority) {
        PsiElement feeler = ImportLocationHelper.getInstance().getSearchStartPosition(anchor, insertParent);
        if (feeler == null) {
            return null;
        }
        // skip initial comments and whitespace and try to get just below the last import stmt
        boolean skippedOverStatements = false;
        boolean skippedOverDoc = false;
        PsiElement seeker = feeler;
        boolean isInjected = InjectedLanguageManager.getInstance(feeler.getProject()).isInjectedFragment(feeler.getContainingFile());
        PyImportStatementBase importAbove = null;
        PyImportStatementBase importBelow = null;
        do {
            if (feeler instanceof PyImportStatementBase feelerImport && !isInjected) {
                if (priority != null && newImport != null) {
                    if (shouldInsertBefore(newImport, feelerImport, priority)) {
                        importBelow = feelerImport;
                        break;
                    }
                    else {
                        importAbove = feelerImport;
                    }
                }
                seeker = feeler;
                feeler = feeler.getNextSibling();
                skippedOverStatements = true;
            }
            else if (PsiTreeUtil.instanceOf(feeler, PsiWhiteSpace.class, PsiComment.class)) {
                seeker = feeler;
                feeler = feeler.getNextSibling();
            }
            else if (PsiTreeUtil.instanceOf(feeler, OuterLanguageElement.class)) {
                if (skippedOverStatements) {
                    break;
                }
                feeler = feeler.getNextSibling();
                seeker = feeler;
            }
            else if (PyUtilCore.isAssignmentToModuleLevelDunderName(feeler)) {
                if (priority == ImportPriority.FUTURE) {
                    seeker = feeler;
                    break;
                }
                feeler = feeler.getNextSibling();
                seeker = feeler;
                skippedOverStatements = true;
            }
            else if (!skippedOverStatements && !skippedOverDoc && insertParent instanceof PyFile) {
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
            newImport.putCopyableUserData(PythonCodeStyleService.IMPORT_GROUP_BEGIN, true);
        }

        if (feeler != null) {
            PsiComment anchorComment = getTopmostBoundComment(feeler);
            if (anchorComment != null) {
                seeker = anchorComment;
            }
        }

        if (priorityBelow != null) {
            // actually not necessary because existing import with higher priority (i.e. lower import group)
            // probably should have IMPORT_GROUP_BEGIN flag already, but we add it anyway just for safety
            if (priorityBelow.compareTo(priority) > 0) {
                importBelow.putCopyableUserData(PythonCodeStyleService.IMPORT_GROUP_BEGIN, true);
            }
            else if (priorityBelow == priority) {
                importBelow.putCopyableUserData(PythonCodeStyleService.IMPORT_GROUP_BEGIN, null);
            }
        }
        return seeker;
    }

    private static @Nullable PsiComment getTopmostBoundComment(PsiElement element) {
        List<List<PsiComment>> commentBlocks = PyPsiUtils.getPrecedingCommentBlocks(element);
        if (commentBlocks.isEmpty()) {
            return null;
        }

        List<PsiComment> firstBlock = commentBlocks.get(0);
        PsiComment firstComment = firstBlock.get(0);
        if (firstComment.getPrevSibling() != null) {
            return firstComment;
        }

        PsiComment lastCommentFirstBlock = firstBlock.get(firstBlock.size() - 1);
        if (PyUtil.isNoinspectionComment(lastCommentFirstBlock)) {
            return lastCommentFirstBlock;
        }

        if (commentBlocks.size() == 1) {
            return null;
        }
        List<PsiComment> secondBlock = commentBlocks.get(1);
        return secondBlock.isEmpty() ? null : secondBlock.get(0);
    }

    private static boolean shouldInsertBefore(@Nullable PyImportStatementBase newImport,
                                              PyImportStatementBase existingImport,
                                              ImportPriority priority) {
        ImportPriority existingImportPriority = getImportPriority(existingImport);
        int byPriority = priority.compareTo(existingImportPriority);
        if (byPriority != 0) {
            return byPriority < 0;
        }
        if (newImport == null) {
            return false;
        }
        return getSameGroupImportsComparator(existingImport.getContainingFile()).compare(newImport, existingImport) < 0;
    }

    public static ImportPriority getImportPriority(PsiElement importLocation, PsiFileSystemItem toImport) {
        ImportPriorityChoice choice = getImportPriorityWithReason(importLocation, toImport);
        LOG.debug(
            String.format(
                "Import group for %s at %s is %s: %s", toImport, importLocation.getContainingFile(),
                choice.getPriority(), choice.getDescription()
            )
        );
        return choice.getPriority();
    }

    public static ImportPriority getImportPriority(PyImportStatementBase importStatement) {
        ImportPriorityChoice choice = getImportPriorityWithReason(importStatement);
        LOG.debug(String.format("Import group for '%s' is %s: %s", importStatement.getText(), choice.getPriority(), choice.getDescription()));
        return choice.getPriority();
    }

    public static ImportPriorityChoice getImportPriorityWithReason(PyImportStatementBase importStatement) {
        PsiElement resolved;
        PsiElement resolveAnchor;
        if (importStatement instanceof PyFromImportStatement fromImportStatement) {
            if (fromImportStatement.isFromFuture()) {
                return new ImportPriorityChoice(ImportPriority.FUTURE, "import from __future__");
            }
            if (fromImportStatement.getRelativeLevel() > 0) {
                return new ImportPriorityChoice(ImportPriority.PROJECT, "explicit relative import");
            }
            resolveAnchor = fromImportStatement.getImportSource();
            resolved = fromImportStatement.resolveImportSource();
        }
        else {
            PyImportElement[] importElements = importStatement.getImportElements();
            PyImportElement firstImportElement = importElements.length > 0 ? importElements[0] : null;
            if (firstImportElement == null) {
                return new ImportPriorityChoice(UNRESOLVED_SYMBOL_PRIORITY, "incomplete import statement");
            }
            resolveAnchor = firstImportElement;
            resolved = firstImportElement.resolve();
        }
        if (resolved == null) {
            return new ImportPriorityChoice(
                UNRESOLVED_SYMBOL_PRIORITY,
                resolveAnchor == null ? "incomplete import statement" : resolveAnchor.getText() + " is unresolved"
            );
        }

        PsiFileSystemItem resolvedFileOrDir;
        if (resolved instanceof PsiDirectory directory) {
            resolvedFileOrDir = directory;
        }
        else if (resolved instanceof PsiDirectoryContainer directoryContainer) {
            PsiDirectory[] directories = directoryContainer.getDirectories();
            resolvedFileOrDir = directories.length > 0 ? directories[0] : null;
        }
        else {
            resolvedFileOrDir = resolved.getContainingFile();
        }

        if (resolvedFileOrDir instanceof PyiFile pyiFile) {
            PsiElement original = PyiUtil.getOriginalElement(pyiFile);
            resolvedFileOrDir = original instanceof PsiFileSystemItem fileSystemItem ? fileSystemItem : resolvedFileOrDir;
        }

        if (resolvedFileOrDir == null) {
            return new ImportPriorityChoice(UNRESOLVED_SYMBOL_PRIORITY, resolved + " is not a file or directory");
        }

        return getImportPriorityWithReason(importStatement, resolvedFileOrDir);
    }

    public static ImportPriorityChoice getImportPriorityWithReason(PsiElement importLocation, PsiFileSystemItem toImport) {
        VirtualFile vFile = toImport.getVirtualFile();
        if (vFile == null) {
            return new ImportPriorityChoice(UNRESOLVED_SYMBOL_PRIORITY, toImport + " doesn't have an associated virtual file");
        }
        ProjectRootManager projectRootManager = ProjectRootManager.getInstance(toImport.getProject());
        ProjectFileIndex fileIndex = projectRootManager.getFileIndex();
        if (fileIndex.isInContent(vFile) && !fileIndex.isInLibraryClasses(vFile)) {
            return new ImportPriorityChoice(
                ImportPriority.PROJECT,
                vFile + " belongs to the project and not under interpreter paths"
            );
        }
        Module module = ModuleUtilCore.findModuleForPsiElement(importLocation);
        Sdk pythonSdk = module != null ? MainPythonSdkKt.pythonSdkIfReady(module) : MainPythonSdkKt.mainPythonSdk(toImport.getProject());

        if (PySkeletonUtil.isStdLib(vFile, pythonSdk)) {
            return new ImportPriorityChoice(
                ImportPriority.BUILTIN, vFile.toString() + " is either in lib but not under site-packages," +
                " or belongs to the root of skeletons," +
                " or is a .pyi stub definition for stdlib module"
            );
        }
        else {
            return new ImportPriorityChoice(
                ImportPriority.THIRD_PARTY, pythonSdk == null
                ? "SDK for " + vFile + " isn't found"
                : "Fall back value for " + vFile
            );
        }
    }

    public static boolean addImportStatement(PsiFile file,
                                             String name,
                                             @Nullable String asName,
                                             @Nullable ImportPriority priority,
                                             @Nullable PsiElement anchor) {
        return addImportStatement(file, name, asName, priority, anchor, null);
    }

    /**
     * Adds an import statement, if it doesn't exist yet, presumably below all other initial imports in the file.
     *
     * @param file         where to operate
     * @param name         which to import (qualified is OK)
     * @param asName       optional name for 'as' clause
     * @param anchor       place where the imported name was used. It will be used to determine proper block where new import should be
     *                     inserted, e.g. inside conditional block or try/except statement. Also if anchor is another import statement,
     *                     new import statement will be inserted right after it.
     * @param insertBefore import statement should be inserted right before this element. If null, the position will be chosen automatically.
     * @return whether import statement was actually added
     */
    public static boolean addImportStatement(PsiFile file,
                                             String name,
                                             @Nullable String asName,
                                             @Nullable ImportPriority priority,
                                             @Nullable PsiElement anchor,
                                             @Nullable PsiElement insertBefore) {
        if (!(file instanceof PyFile pyFile)) {
            return false;
        }
        List<PyImportElement> existingImports = pyFile.getImportTargets();
        List<PyImportStatementBase> importsAllowedToReuse = getImportsAllowedToReuse(insertBefore);
        for (PyImportElement existingImport : existingImports) {
            if (importsAllowedToReuse != null && !importsAllowedToReuse.contains(existingImport.getContainingImportStatement())) {
                continue;
            }

            String existingName = Objects.toString(existingImport.getImportedQName(), "");
            if (name.equals(existingName) && Objects.equals(asName, existingImport.getAsName())) {
                return false;
            }
        }

        PyElementGenerator generator = PyElementGenerator.getInstance(file.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(file);
        PyImportStatement importNodeToInsert = generator.createImportStatement(languageLevel, name, asName);

        if (file instanceof PyCodeFragmentWithHiddenImports fragment) {
            List<PyImportStatementBase> imports = new ArrayList<>();
            imports.add(importNodeToInsert);
            fragment.addImports(imports);
            return true;
        }

        PsiElement insertParent;
        if (insertBefore == null || insertBefore.getParent() == null) {
            PyImportStatementBase importStatement = PsiTreeUtil.getParentOfType(anchor, PyImportStatementBase.class, false);
            insertParent =
                importStatement != null && importStatement.getContainingFile() == file ? importStatement.getParent() : file;
        }
        else {
            insertParent = insertBefore.getParent();
        }
        try {
            if (anchor instanceof PyImportStatementBase) {
                insertParent.addAfter(importNodeToInsert, anchor);
            }
            else {
                PsiElement position;
                if (insertBefore == null) {
                    position = getInsertPosition(insertParent, anchor, importNodeToInsert, priority);
                }
                else {
                    position = insertBefore;
                }
                insertParent.addBefore(importNodeToInsert, position);
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
        return true;
    }

    public static void addFromImportStatement(PsiFile file,
                                              String from,
                                              String name,
                                              @Nullable String asName,
                                              @Nullable ImportPriority priority,
                                              @Nullable PsiElement anchor) {
        addFromImportStatement(file, from, name, asName, priority, anchor, null);
    }

    /**
     * Adds a new {@link PyFromImportStatement} statement within other top-level imports or as specified by anchor.
     *
     * @param file         where to operate
     * @param from         import source (reference after {@code from} keyword)
     * @param name         imported name (identifier after {@code import} keyword)
     * @param asName       optional alias (identifier after {@code as} keyword)
     * @param anchor       place where the imported name was used. It will be used to determine proper block where new import should be
     *                     inserted, e.g. inside conditional block or try/except statement. Also if anchor is another import statement,
     *                     new import statement will be inserted right after it.
     * @param insertBefore import statement should be inserted right before this element. If null, the position will be chosen automatically.
     * @see #addOrUpdateFromImportStatement
     */
    public static void addFromImportStatement(PsiFile file,
                                              String from,
                                              String name,
                                              @Nullable String asName,
                                              @Nullable ImportPriority priority,
                                              @Nullable PsiElement anchor,
                                              @Nullable PsiElement insertBefore) {
        PyElementGenerator generator = PyElementGenerator.getInstance(file.getProject());
        LanguageLevel languageLevel = LanguageLevel.forElement(file);
        PyFromImportStatement newImport = generator.createFromImportStatement(languageLevel, from, name, asName);
        addFromImportStatement(file, newImport, priority, anchor, insertBefore);
    }

    public static void addFromImportStatement(PsiFile file,
                                              PyFromImportStatement newImport,
                                              @Nullable ImportPriority priority,
                                              @Nullable PsiElement anchor) {
        addFromImportStatement(file, newImport, priority, anchor, null);
    }

    /**
     * Adds a new {@link PyFromImportStatement} statement within other top-level imports or as specified by anchor.
     *
     * @param file         where to operate
     * @param newImport    new "from import" statement to insert. It may be generated, because it won't be used for resolving anyway.
     *                     You might want to use overloaded version of this method to generate such statement automatically.
     * @param anchor       place where the imported name was used. It will be used to determine proper block where new import should be
     *                     inserted, e.g. inside conditional block or try/except statement. Also if anchor is another import statement,
     *                     new import statement will be inserted right after it.
     * @param insertBefore import statement should be inserted right before this element. If null, the position will be chosen automatically.
     * @see #addFromImportStatement
     */
    public static void addFromImportStatement(PsiFile file,
                                              PyFromImportStatement newImport,
                                              @Nullable ImportPriority priority,
                                              @Nullable PsiElement anchor,
                                              @Nullable PsiElement insertBefore) {
        if (file instanceof PyCodeFragmentWithHiddenImports fragment) {
            List<PyImportStatementBase> imports = new ArrayList<>();
            imports.add(newImport);
            fragment.addImports(imports);
            return;
        }
        try {
            PyImportStatementBase parentImport = PsiTreeUtil.getParentOfType(anchor, PyImportStatementBase.class, false);
            InjectedLanguageManager manager = InjectedLanguageManager.getInstance(file.getProject());
            PsiLanguageInjectionHost injectionHost = manager.getInjectionHost(file);
            boolean insideDoctest =
                file instanceof PyDoctestFile && injectionHost != null && DocStringUtil.getParentDefinitionDocString(injectionHost) == injectionHost;

            PsiElement insertParent;
            if (insertBefore != null && insertBefore.getParent() != null) {
                insertParent = insertBefore.getParent();
            }
            else if (parentImport != null && parentImport.getContainingFile() == file) {
                insertParent = parentImport.getParent();
            }
            else if (injectionHost != null && !insideDoctest) {
                insertParent = manager.getTopLevelFile(file);
            }
            else {
                insertParent = file;
            }

            if (insideDoctest) {
                PsiElement element = insertParent.addBefore(newImport, getInsertPosition(insertParent, anchor, newImport, priority));
                PsiElement whitespace = element.getNextSibling();
                if (!(whitespace instanceof PsiWhiteSpace)) {
                    whitespace = PsiParserFacade.getInstance(file.getProject()).createWhiteSpaceFromText("  >>> ");
                }
                insertParent.addBefore(whitespace, element);
            }
            else if (anchor instanceof PyImportStatementBase) {
                insertParent.addAfter(newImport, anchor);
            }
            else {
                PsiElement position;
                if (insertBefore == null) {
                    position = getInsertPosition(insertParent, anchor, newImport, priority);
                }
                else {
                    position = insertBefore;
                }
                insertParent.addBefore(newImport, position);
            }
        }
        catch (IncorrectOperationException e) {
            LOG.error(e);
        }
    }

    public static boolean addOrUpdateFromImportStatement(PsiFile file,
                                                         String from,
                                                         String name,
                                                         @Nullable String asName,
                                                         @Nullable ImportPriority priority,
                                                         @Nullable PsiElement anchor) {
        return addOrUpdateFromImportStatement(file, from, name, asName, priority, anchor, null);
    }

    /**
     * Adds new {@link PyFromImportStatement} in file or append {@link PyImportElement} to
     * existing from import statement.
     *
     * @param file         module where import will be added
     * @param from         import source (reference after {@code from} keyword)
     * @param name         imported name (identifier after {@code import} keyword)
     * @param asName       optional alias (identifier after {@code as} keyword)
     * @param priority     optional import priority used to sort imports
     * @param anchor       place where the imported name was used. It will be used to determine proper block where new import should be
     *                     inserted, e.g. inside conditional block or try/except statement. Also if anchor is another import statement,
     *                     new import statement will be inserted right after it.
     * @param insertBefore import statement should be inserted right before this element. However, if it aims at an insertion statement in
     *                     a group of inserts, a better insertion point belonging the group may be chosen, and it can be after the specified
     *                     node. If null, the position will be chosen automatically.
     * @return whether import was actually added
     * @see #addFromImportStatement
     */
    public static boolean addOrUpdateFromImportStatement(PsiFile file,
                                                         String from,
                                                         String name,
                                                         @Nullable String asName,
                                                         @Nullable ImportPriority priority,
                                                         @Nullable PsiElement anchor,
                                                         @Nullable PsiElement insertBefore) {
        String fromToUse = from;
        PyFile pyFile = (PyFile) file;
        List<PyFromImportStatement> existingImports = pyFile.getFromImports();

        int relativeLevel = 0;
        PyRelativeImportData importData = null;
        if (priority == ImportPriority.PROJECT) {
            importData = PyRelativeImportData.fromString(fromToUse, pyFile);
            if (importData != null) {
                relativeLevel = importData.getRelativeLevel();
            }
        }

        List<PyImportStatementBase> importsAllowedToReuse = getImportsAllowedToReuse(insertBefore);

        if (!PythonCodeStyleService.getInstance().isOptimizeImportsAlwaysSplitFromImports(file)) {
            for (PyFromImportStatement existingImport : existingImports) {
                if (importsAllowedToReuse != null && !importsAllowedToReuse.contains(existingImport)) {
                    continue;
                }
                if (existingImport.isStarImport()) {
                    continue;
                }
                String existingSource = Objects.toString(existingImport.getImportSourceQName(), "");

                boolean updateExisting = false;
                int currentRelativeLevel = existingImport.getRelativeLevel();
                if (currentRelativeLevel == 0) {
                    updateExisting = fromToUse.equals(existingSource);
                }
                else if (relativeLevel != 0) {
                    updateExisting = currentRelativeLevel == relativeLevel && existingSource.equals(importData.getRelativeLocation());
                }

                if (updateExisting) {
                    return addNameToFromImportStatement(existingImport, name, asName);
                }
            }
        }

        if (!PyUtil.hasIfNameEqualsMain(pyFile)) {
            int maxRelativeLevelInFile = pyFile.getFromImports().stream()
                .mapToInt(PyFromImportStatement::getRelativeLevel)
                .max()
                .orElse(0);

            if (maxRelativeLevelInFile > 0 && relativeLevel > 0) {
                int maxAllowedDepth = Math.max(maxRelativeLevelInFile, Registry.intValue("python.relative.import.depth", 3));
                if (maxAllowedDepth >= relativeLevel) {
                    fromToUse = importData.getLocationWithDots();
                }
            }
        }

        addFromImportStatement(file, fromToUse, name, asName, priority, anchor, insertBefore);
        return true;
    }

    private static @Nullable List<PyImportStatementBase> getImportsAllowedToReuse(@Nullable PsiElement importBlockStart) {
        if (importBlockStart == null) {
            return null;
        }

        List<PyImportStatementBase> importsAllowedToReuse = new ArrayList<>();
        PsiElement candidate = importBlockStart;
        while (candidate instanceof PyImportStatementBase importStatement) {
            importsAllowedToReuse.add(importStatement);
            candidate = PyPsiUtils.getNextNonWhitespaceSibling(candidate);
        }
        return importsAllowedToReuse;
    }

    /**
     * Adds a new imported name to an existing "from" import statement.
     *
     * @param fromImport import statement to update
     * @param name       new name to import from the same source
     * @param asName     optional alias for a name in its "as" part
     * @return whether the new name was actually added
     */
    public static boolean addNameToFromImportStatement(PyFromImportStatement fromImport,
                                                       String name,
                                                       @Nullable String asName) {
        PsiFile file = fromImport.getContainingFile();
        Comparator<String> nameComparator = getImportTextComparator(file);
        PythonCodeStyleService pyCodeStyle = PythonCodeStyleService.getInstance();
        boolean shouldSort = pyCodeStyle.isOptimizeImportsSortImports(file) && pyCodeStyle.isOptimizeImportsSortNamesInFromImports(file);

        PyImportElement followingNameElement = null;
        for (PyImportElement existingNameElement : fromImport.getImportElements()) {
            String existingName = Objects.toString(existingNameElement.getImportedQName(), "");
            if (name.equals(existingName) && Objects.equals(asName, existingNameElement.getAsName())) {
                return false;
            }
            if (shouldSort && followingNameElement == null && nameComparator.compare(existingName, name) > 0) {
                followingNameElement = existingNameElement;
            }
        }
        PyElementGenerator generator = PyElementGenerator.getInstance(fromImport.getProject());
        PyImportElement newNameElement = generator.createImportElement(LanguageLevel.forElement(fromImport), name, asName);
        // addBefore(newNameElement, null) is the same as inserting at the end
        fromImport.addBefore(newNameElement, followingNameElement);
        // May need to add parentheses, trailing comma, etc.
        CodeStyleManager.getInstance(fromImport.getProject()).reformat(fromImport);
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
    public static void addImport(PsiNamedElement target, PsiFile file, PyElement element) {
        if (target.getContainingFile() == file) {
            return;
        }
        if (PyBuiltinCache.getInstance(element).isBuiltin(target)) {
            return;
        }

        if (target instanceof PsiFileSystemItem fileSystemItem) {
            addFileSystemItemImport(fileSystemItem, file, element);
            return;
        }

        // If target is a class attribute or nested class, import the top-level containing class
        PsiNamedElement elementToImport = target;
        PsiElement parent = ScopeUtil.getScopeOwner(target);
        if (parent instanceof PyClass parentClass) {
            elementToImport = parentClass;
        }

        // Walk up to the top-level class if elementToImport is itself a nested class
        String nestedQualifierPrefix = null;
        if (elementToImport instanceof PyClass elementClass && !PyUtil.isTopLevel(elementClass)) {
            PyClass topLevel = PyNestedClassUtils.findTopLevelClass(elementClass);
            if (topLevel != null) {
                nestedQualifierPrefix = PyNestedClassUtils.buildQualifiedName(elementClass, topLevel);
                elementToImport = topLevel;
            }
        }

        String name = elementToImport.getName();
        if (name == null) {
            return;
        }

        PsiFileSystemItem toImport = elementToImport.getContainingFile();
        if (toImport == null) {
            return;
        }

        // Check if the target is accessible via an already-imported module
        if (qualifyViaExistingImport(toImport, name, nestedQualifierPrefix, file, element)) {
            return;
        }

        QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(elementToImport, element);
        if (importPath == null) {
            return;
        }

        String path = importPath.toString();
        ImportPriority priority = getImportPriority(file, toImport);

        if (!PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT) {
            addImportStatement(file, path, null, priority, element);

            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(file.getProject());
            String refText = nestedQualifierPrefix != null ? nestedQualifierPrefix : name;
            element.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(elementToImport), path + "." + refText));
        }
        else {
            addOrUpdateFromImportStatement(file, path, name, null, priority, element);
            if (nestedQualifierPrefix != null) {
                // Rewrite the qualifier reference to use the qualified nested class path, e.g. Inner -> Outer.Inner
                // The element might be a qualified expression like Inner.do, so we need to find the unresolved
                // qualifier that refers to the nested class and replace it with the full nesting chain.
                PyElement toRewrite = element;
                if (element instanceof PyReferenceExpression referenceExpression) {
                    PyExpression qualifier = referenceExpression.getQualifier();
                    if (qualifier instanceof PyReferenceExpression) {
                        toRewrite = qualifier;
                    }
                }
                PyElementGenerator elementGenerator = PyElementGenerator.getInstance(file.getProject());
                toRewrite.replace(
                    elementGenerator.createExpressionFromText(
                        LanguageLevel.forElement(elementToImport),
                        nestedQualifierPrefix
                    )
                );
            }
        }
    }

    private static void addFileSystemItemImport(PsiFileSystemItem target, PsiFile file, PyElement element) {
        PsiFileSystemItem toImport = target.getParent();
        if (toImport == null) {
            return;
        }

        QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(target, element);
        if (importPath == null) {
            return;
        }

        ImportPriority priority = getImportPriority(file, toImport);
        if (importPath.getComponentCount() == 1 || !PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT) {
            String path = importPath.toString();

            addImportStatement(file, path, null, priority, element);

            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(file.getProject());
            element.replace(elementGenerator.createExpressionFromText(LanguageLevel.forElement(target), path));
        }
        else {
            String importedName = Objects.requireNonNull(importPath.getLastComponent(), "Required value was null.");
            String importSource = importPath.removeLastComponent().toString();
            addOrUpdateFromImportStatement(file, importSource, importedName, null, priority, element);
        }
    }

    private static boolean qualifyViaExistingImport(PsiFileSystemItem toImport,
                                                    String importedName,
                                                    @Nullable String nestedQualifierPrefix,
                                                    PsiFile file,
                                                    PyElement element) {
        if (!(file instanceof PyFile pyFile)) {
            return false;
        }

        if (processFromImports(toImport, importedName, nestedQualifierPrefix, element, pyFile) ||
            processRegularImports(toImport, importedName, nestedQualifierPrefix, element, pyFile)) {
            return true;
        }

        return false;
    }

    // Check regular imports: "import pkg.src" where pkg.src resolves to the target's module
    private static boolean processRegularImports(PsiFileSystemItem toImport,
                                                 String importedName,
                                                 @Nullable String nestedQualifierPrefix,
                                                 PyElement element,
                                                 PyFile pyFile) {
        for (PyImportElement importElement : pyFile.getImportTargets()) {
            PsiElement resolved = importElement.resolve();
            if (!(PyUtil.turnDirIntoInit(resolved) instanceof PsiFile resolvedFile) || !resolvedFile.equals(toImport)) {
                continue;
            }

            QualifiedName importedQName = importElement.getImportedQName();
            if (importedQName == null) {
                continue;
            }

            String prefix = importElement.getAsName() != null
                ? importElement.getVisibleName()
                : importedQName.toString();
            if (prefix == null) {
                continue;
            }

            String qualifiedRef = nestedQualifierPrefix != null
                ? prefix + "." + nestedQualifierPrefix
                : prefix + "." + importedName;
            replaceQualifier(element, qualifiedRef);
            return true;
        }
        return false;
    }

    // Check from-imports: "from pkg import src" where src resolves to the target's module
    private static boolean processFromImports(PsiFileSystemItem toImport,
                                              String importedName,
                                              @Nullable String nestedQualifierPrefix,
                                              PyElement element,
                                              PyFile pyFile) {
        for (PyFromImportStatement fromImport : pyFile.getFromImports()) {
            if (fromImport.isStarImport()) {
                continue;
            }
            for (PyImportElement importElement : fromImport.getImportElements()) {
                PsiElement resolved = importElement.resolve();
                if (!(PyUtil.turnDirIntoInit(resolved) instanceof PsiFile resolvedFile) || !resolvedFile.equals(toImport)) {
                    continue;
                }

                String visibleName = importElement.getVisibleName();
                if (visibleName == null) {
                    continue;
                }

                String qualifiedRef = nestedQualifierPrefix != null
                    ? visibleName + "." + nestedQualifierPrefix
                    : visibleName + "." + importedName;
                replaceQualifier(element, qualifiedRef);
                return true;
            }
        }
        return false;
    }

    private static void replaceQualifier(PyElement element, String qualifiedRef) {
        PyElement toRewrite = element;
        if (element instanceof PyReferenceExpression referenceExpression) {
            PyExpression qualifier = referenceExpression.getQualifier();
            if (qualifier instanceof PyReferenceExpression) {
                toRewrite = qualifier;
            }
        }
        PyElementGenerator gen = PyElementGenerator.getInstance(element.getProject());
        toRewrite.replace(gen.createExpressionFromText(LanguageLevel.forElement(element), qualifiedRef));
    }

    public enum ImportPriority {
        FUTURE,
        BUILTIN,
        THIRD_PARTY,
        PROJECT
    }

    public static class ImportPriorityChoice {
        private final ImportPriority myPriority;
        private final String myDescription;

        public ImportPriorityChoice(ImportPriority priority, String description) {
            myPriority = priority;
            myDescription = description;
        }

        public ImportPriority getPriority() {
            return myPriority;
        }

        public String getDescription() {
            return myDescription;
        }
    }
}
