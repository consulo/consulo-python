// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring.classes;

import consulo.fileTemplate.FileTemplate;
import consulo.fileTemplate.FileTemplateManager;
import consulo.language.inject.InjectedLanguageManager;
import consulo.logging.Logger;
import consulo.document.Document;
import consulo.project.Project;
import consulo.module.content.ProjectRootManager;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.document.util.TextRange;
import consulo.util.io.FileUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileSystem;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.ResolveResult;
import consulo.language.impl.psi.LightElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ArrayUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.impl.codeInsight.imports.PyImportOptimizer;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * @author Dennis.Ushakov
 */
public final class PyClassRefactoringUtil {
    private static final Logger LOG = Logger.getInstance(PyClassRefactoringUtil.class.getName());
    private static final Key<PsiNamedElement> ENCODED_IMPORT = Key.create("PyEncodedImport");
    private static final Key<Boolean> ENCODED_USE_FROM_IMPORT = Key.create("PyEncodedUseFromImport");
    private static final Key<String> ENCODED_IMPORT_AS = Key.create("PyEncodedImportAs");
    private static final Key<List<PyReferenceExpression>> INJECTION_REFERENCES = Key.create("PyInjectionReferences");
    private static final Key<Set<FutureFeature>> ENCODED_FROM_FUTURE_IMPORTS = Key.create("PyFromFutureImports");


    private PyClassRefactoringUtil() {
    }


    /**
     * Copies class field declarations to some other place
     *
     * @param assignmentStatements       list of class fields
     * @param dequalifyIfDeclaredInClass If not null method will check if field declared in this class.
     *                                   If declared -- qualifier will be removed.
     *                                   For example: MyClass.Foo will become Foo it this param is MyClass.
     * @return new (copied) fields
     */
    public static List<PyAssignmentStatement> copyFieldDeclarationToStatement(Collection<? extends PyAssignmentStatement> assignmentStatements,
                                                                                       PyStatementList superClassStatement,
                                                                                       @Nullable PyClass dequalifyIfDeclaredInClass) {
        List<PyAssignmentStatement> declarations = new ArrayList<>(assignmentStatements.size());

        for (PyAssignmentStatement pyAssignmentStatement : assignmentStatements) {
            PyElement value = pyAssignmentStatement.getAssignedValue();
            PyAssignmentStatement newDeclaration = (PyAssignmentStatement)pyAssignmentStatement.copy();

            if (value instanceof PyReferenceExpression ref && dequalifyIfDeclaredInClass != null) {
                String newValue = getNewValueToAssign(ref, dequalifyIfDeclaredInClass);

                setNewAssigneeValue(newDeclaration, newValue);
            }

            declarations.add(PyPsiRefactoringUtil.addElementToStatementList(newDeclaration, superClassStatement));
            PyPsiUtils.removeRedundantPass(superClassStatement);
        }
        return declarations;
    }

    /**
     * Sets new value to assignment statement.
     *
     * @param assignmentStatement statement to change
     * @param newValue            new value
     */
    private static void setNewAssigneeValue(PyAssignmentStatement assignmentStatement, String newValue) {
        PyExpression oldValue = assignmentStatement.getAssignedValue();
        PyExpression newExpression =
            PyElementGenerator.getInstance(assignmentStatement.getProject())
                .createExpressionFromText(LanguageLevel.forElement(assignmentStatement), newValue);
        if (oldValue != null) {
            oldValue.replace(newExpression);
        }
        else {
            assignmentStatement.add(newExpression);
        }
    }

    /**
     * Checks if current value declared in provided class and removes class qualifier if true
     *
     * @param currentValue               current value
     * @param dequalifyIfDeclaredInClass class to check
     * @return value as string
     */
    private static String getNewValueToAssign(PyReferenceExpression currentValue,
                                                       PyClass dequalifyIfDeclaredInClass) {
        PyExpression qualifier = currentValue.getQualifier();
        if ((qualifier instanceof PyReferenceExpression ref) && ref.getReference().isReferenceTo(dequalifyIfDeclaredInClass)) {
            String name = currentValue.getName();
            return ((name != null) ? name : currentValue.getText());
        }
        return currentValue.getText();
    }

    /**
     * Adds methods to class.
     *
     * @param destination where to add methods
     * @param methods     methods
     * @param skipIfExist do not add anything if method already exists
     * @return newly added methods or existing one (if skipIfExists is true and method already exists)
     */
    public static List<PyFunction> addMethods(PyClass destination,
                                                       boolean skipIfExist,
                                                       PyFunction ... methods) {

        PyStatementList destStatementList = destination.getStatementList();
        List<PyFunction> result = new ArrayList<>(methods.length);

        for (PyFunction method : methods) {

            PyFunction existingMethod = destination.findMethodByName(method.getName(), false, null);
            if ((existingMethod != null) && skipIfExist) {
                result.add(existingMethod);
                continue; //We skip adding if class already has this method.
            }

            result.add(insertMethodInProperPlace(destStatementList, method));
        }

        PyPsiUtils.removeRedundantPass(destStatementList);
        return result;
    }

    /**
     * Adds init methods before all other methods (but after class vars and docs).
     * Adds all other methods to the bottom
     *
     * @param destStatementList where to add methods
     * @param method            method to add
     * @return newlty added method
     */
    private static PyFunction insertMethodInProperPlace(
        PyStatementList destStatementList,
        PyFunction method) {
        boolean methodIsInit = PyNames.INIT.equals(method.getName());
        if (!methodIsInit) {
            //Not init method could be inserted in the bottom
            return (PyFunction)destStatementList.add(method);
        }

        //We should find appropriate place for init
        for (PsiElement element : destStatementList.getStatements()) {
            boolean elementComment = element instanceof PyExpressionStatement;
            boolean elementClassField = element instanceof PyAssignmentStatement;

            if (!(elementComment || elementClassField)) {
                return (PyFunction)destStatementList.addBefore(method, element);
            }
        }
        return (PyFunction)destStatementList.add(method);
    }


    /**
     * Restores references saved by {@link #rememberNamedReferences(PsiElement, String...)}.
     *
     * @param element newly created element to restore references
     * @see #rememberNamedReferences(PsiElement, String...)
     */
    public static void restoreNamedReferences(PsiElement element) {
        restoreNamedReferences(element, null);
    }

    public static void restoreNamedReferences(PsiElement newElement, @Nullable PsiElement oldElement) {
        restoreNamedReferences(newElement, oldElement, PsiElement.EMPTY_ARRAY);
    }

    public static void restoreNamedReferences(PsiElement newElement,
                                              @Nullable PsiElement oldElement,
                                              PsiElement [] otherMovedElements) {
        Set<FutureFeature> fromFutureImports = newElement.getCopyableUserData(ENCODED_FROM_FUTURE_IMPORTS);
        newElement.putCopyableUserData(ENCODED_FROM_FUTURE_IMPORTS, null);
        PsiFile destFile = newElement.getContainingFile();
        if (fromFutureImports != null & destFile != null) {
            for (FutureFeature futureFeature : fromFutureImports) {
                AddImportHelper.addOrUpdateFromImportStatement(destFile, PyNames.FUTURE_MODULE, futureFeature.toString(), null,
                                                               AddImportHelper.ImportPriority.FUTURE, null);
            }
        }

        newElement.acceptChildren(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyReferenceExpression(PyReferenceExpression node) {
                super.visitPyReferenceExpression(node);
                restoreReference(node, node, otherMovedElements);
            }

            @Override
            public void visitPyStringLiteralExpression(PyStringLiteralExpression node) {
                super.visitPyStringLiteralExpression(node);
                if (oldElement != null) {
                    for (PsiReference ref : node.getReferences()) {
                        if (ref.isReferenceTo(oldElement)) {
                            ref.bindToElement(newElement);
                        }
                    }
                }
                restoreReference(node, node, otherMovedElements);
            }

            @Override
            public void visitComment(PsiComment comment) {
                super.visitComment(comment);
                if (comment instanceof PsiLanguageInjectionHost) {
                    restoreReference(comment, comment, otherMovedElements);
                }
            }
        });
    }


    private static void restoreReference(PsiElement sourceNode,
                                         PsiElement targetNode,
                                         PsiElement [] otherMovedElements) {
        try {
            if (sourceNode instanceof PyReferenceExpression ref) {
                doRestoreReference(ref, targetNode, otherMovedElements);
            }
            else if (sourceNode instanceof PsiLanguageInjectionHost) {
                var injectionReferences = sourceNode.getCopyableUserData(INJECTION_REFERENCES);
                if (injectionReferences != null) {
                    injectionReferences.forEach(expression -> doRestoreReference(expression, targetNode, otherMovedElements));
                }
            }
        }
        finally {
            sourceNode.putCopyableUserData(INJECTION_REFERENCES, null);
        }
    }

    private static void doRestoreReference(PyReferenceExpression sourceNode,
                                           PsiElement targetNode,
                                           PsiElement [] otherMovedElements) {
        try {
            PsiNamedElement target = sourceNode.getCopyableUserData(ENCODED_IMPORT);
            String asName = sourceNode.getCopyableUserData(ENCODED_IMPORT_AS);
            Boolean useFromImport = sourceNode.getCopyableUserData(ENCODED_USE_FROM_IMPORT);
            if (target == null) {
                return;
            }
            if (target instanceof PsiDirectory) {
                target = (PsiNamedElement)PyUtil.getPackageElement((PsiDirectory)target, sourceNode);
            }
            if (target instanceof PyFunction f) {
                PyClass c = f.getContainingClass();
                if (c != null && c.multiFindInitOrNew(false, null).contains(f)) {
                    target = c;
                }
            }
            if (PsiTreeUtil.isAncestor(targetNode.getContainingFile(), target, false)) {
                return;
            }
            if (ArrayUtil.contains(target, otherMovedElements)) {
                return;
            }
            if (target instanceof PyFile || target instanceof PsiDirectory) {
                boolean preferFromImport = useFromImport != null ? useFromImport : true;
                boolean isUsedAsQualifier = sourceNode.getParent() instanceof PyQualifiedExpression &&
                                            ((PyQualifiedExpression)sourceNode.getParent()).getQualifier() == sourceNode;
                boolean shouldQualifyReference = target instanceof PyFile &&
                                                 !PyCodeInsightSettings.getInstance().PREFER_FROM_IMPORT &&
                                                 useFromImport != null && useFromImport &&
                                                 asName == null &&
                                                 !isUsedAsQualifier;
                if (shouldQualifyReference) {
                    preferFromImport = false;
                }
                PyPsiRefactoringUtil.insertImport(targetNode, target, asName, preferFromImport);
                if (shouldQualifyReference) {
                    QualifiedName canonicalPath = QualifiedNameFinder.findCanonicalImportPath(target, targetNode);
                    if (canonicalPath != null && sourceNode.isValid()) {
                        String qualifiedReference = canonicalPath.toString();
                        PyElementGenerator generator = PyElementGenerator.getInstance(sourceNode.getProject());
                        PyExpression newExpr = generator.createExpressionFromText(LanguageLevel.forElement(sourceNode), qualifiedReference);
                        sourceNode.replace(newExpr);
                    }
                }
            }
            else {
                PyPsiRefactoringUtil.insertImport(targetNode, target, asName, true);
            }
        }
        finally {
            sourceNode.putCopyableUserData(ENCODED_IMPORT, null);
            sourceNode.putCopyableUserData(ENCODED_IMPORT_AS, null);
            sourceNode.putCopyableUserData(ENCODED_USE_FROM_IMPORT, null);
        }
    }

    /**
     * Searches for references inside some element (like {@link PyAssignmentStatement}, {@link PyFunction} etc
     * and stored them.
     * After that you can add element to some new parent. Newly created element then should be processed via {@link #restoreNamedReferences(PsiElement)}
     * and all references would be restored.
     *
     * @param element     element to store references for
     * @param namesToSkip if reference inside of element has one of this names, it will not be saved.
     */
    public static void rememberNamedReferences(PsiElement element, String ... namesToSkip) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile instanceof PyFile file) {
            Set<FutureFeature> fromFutureImports = collectFromFutureImports(file);
            element.putCopyableUserData(ENCODED_FROM_FUTURE_IMPORTS, fromFutureImports);
        }

        element.accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyReferenceExpression(PyReferenceExpression node) {
                super.visitPyReferenceExpression(node);
                if (PsiTreeUtil.getParentOfType(node, PyImportStatementBase.class) != null) {
                    return;
                }
                PyImportedNameDefiner importElement = getImportElement(node);
                if (importElement != null && PsiTreeUtil.isAncestor(element, importElement, false)) {
                    return;
                }
                if (!ArrayUtil.contains(node.getText(), namesToSkip)) { //Do not remember name if it should be skipped
                    rememberReference(node, element);
                }
            }

            @Override
            public void visitComment(PsiComment comment) {
                super.visitComment(comment);
                if (comment instanceof PsiLanguageInjectionHost host) {
                    rememberInjectionReferences(host, element, namesToSkip);
                }
            }

            @Override
            public void visitPyStringLiteralExpression(PyStringLiteralExpression expression) {
                super.visitPyStringLiteralExpression(expression);
                rememberInjectionReferences(expression, element, namesToSkip);
            }
        });
    }

    private static void rememberInjectionReferences(PsiLanguageInjectionHost host,
                                                    PsiElement element,
                                                    String ... namesToSkip) {
        List<Pair<PsiElement, TextRange>> files = InjectedLanguageManager.getInstance(host.getProject()).getInjectedPsiFiles(host);
        if (files == null) {
            return;
        }
        for (Pair<PsiElement, TextRange> pair : files) {
            pair.getFirst().accept(
                new PyRecursiveElementVisitor() {
                    @Override
                    public void visitPyReferenceExpression(PyReferenceExpression expression) {
                        super.visitPyReferenceExpression(expression);
                        if (!ArrayUtil.contains(expression.getText(), namesToSkip)) {
                            rememberReference(expression, element);
                            rememberReferenceInInjectionHost(expression, host);
                        }
                    }
                });
        }
    }

    private static void rememberReferenceInInjectionHost(PyReferenceExpression expression,
                                                         PsiLanguageInjectionHost host) {
        var encodedImports = host.getCopyableUserData(INJECTION_REFERENCES);
        var rememberedReferences = encodedImports == null ? new ArrayList<PyReferenceExpression>() : encodedImports;
        rememberedReferences.add(expression);
        host.putCopyableUserData(INJECTION_REFERENCES, rememberedReferences);
    }

    private static Set<FutureFeature> collectFromFutureImports(PyFile file) {
        EnumSet<FutureFeature> result = EnumSet.noneOf(FutureFeature.class);
        for (FutureFeature feature : FutureFeature.values()) {
            if (file.hasImportFromFuture(feature)) {
                result.add(feature);
            }
        }
        return result;
    }

    private static void rememberReference(PyReferenceExpression node, PsiElement element) {
        // We will remember reference in deepest node (except for references to PyImportedModules, as we need references to modules, not to
        // their packages)
        PyExpression qualifier = node.getQualifier();
        if (qualifier != null && !(resolveExpression(qualifier) instanceof PyImportedModule)) {
            return;
        }
        List<PsiElement> allResolveResults = multiResolveExpression(node);
        PsiElement target = ContainerUtil.getFirstItem(allResolveResults);
        if (target instanceof PsiNamedElement && !PsiTreeUtil.isAncestor(element, target, false)) {
            PyImportedNameDefiner importElement = getImportElement(node);
            if (!PyUtil.inSameFile(element, target) && importElement == null && !(target instanceof PsiFileSystemItem)) {
                return;
            }
            if (target instanceof PyTargetExpression targetExpr && PyNames.ALL.equals(targetExpr.getName())) {
                for (PsiElement result : allResolveResults) {
                    if (result instanceof PyImportElement pyImport) {
                        QualifiedName importedQName = pyImport.getImportedQName();
                        if (importedQName != null) {
                            target = new DynamicNamedElement(target.getContainingFile(), importedQName.toString());
                            break;
                        }
                    }
                }
            }
            node.putCopyableUserData(ENCODED_IMPORT, (PsiNamedElement)target);
            if (importElement instanceof PyImportElement pyImport) {
                node.putCopyableUserData(ENCODED_IMPORT_AS, pyImport.getAsName());
            }
            node.putCopyableUserData(ENCODED_USE_FROM_IMPORT, qualifier == null);
        }
    }

    private static @Nullable PyImportedNameDefiner getImportElement(PyReferenceExpression expr) {
        for (ResolveResult result : expr.getReference().multiResolve(false)) {
            PsiElement e = result.getElement();
            if (e instanceof PyImportElement pyImport) {
                return pyImport;
            }
            if (e instanceof PyStarImportElement pyStarImport) {
                return pyStarImport;
            }
        }
        return null;
    }

    private static @Nullable PsiElement resolveExpression(PyExpression expr) {
        if (expr instanceof PyReferenceExpression ref) {
            return ref.getReference().resolve();
        }
        return null;
    }

    private static List<PsiElement> multiResolveExpression(PyReferenceExpression expr) {
        return ContainerUtil.mapNotNull(expr.getReference().multiResolve(false), result -> result.getElement());
    }

    /**
     * Forces the use of 'import as' when restoring references (i.e. if there are name clashes)
     *
     * @param node   with encoded import
     * @param asName new alias for import
     */
    public static void forceAsName(PyReferenceExpression node, String asName) {
        if (node.getCopyableUserData(ENCODED_IMPORT) == null) {
            LOG.warn("As name is forced on the referenceExpression, that has no encoded import. Forcing it will likely be ignored.");
        }
        node.putCopyableUserData(ENCODED_IMPORT_AS, asName);
    }

    public static void transferEncodedImports(PyReferenceExpression source, PyReferenceExpression target) {
        target.putCopyableUserData(ENCODED_IMPORT, source.getCopyableUserData(ENCODED_IMPORT));
        target.putCopyableUserData(ENCODED_IMPORT_AS, source.getCopyableUserData(ENCODED_IMPORT_AS));
        target.putCopyableUserData(ENCODED_USE_FROM_IMPORT, source.getCopyableUserData(ENCODED_USE_FROM_IMPORT));

        source.putCopyableUserData(ENCODED_IMPORT, null);
        source.putCopyableUserData(ENCODED_IMPORT_AS, null);
        source.putCopyableUserData(ENCODED_USE_FROM_IMPORT, null);
    }

    public static boolean hasEncodedTarget(PyReferenceExpression node) {
        return node.getCopyableUserData(ENCODED_IMPORT) != null;
    }

    /**
     * Updates the import statement if the given PSI element <em>has the same name</em> as one of the import elements of that statement.
     * It means that you should be careful if you actually want to update the source part of a "from import" statement, because in cases
     * like {@code from foo import foo} this method may do not what you expect.
     *
     * @param importStatement parent import statement that contains reference to given element
     * @param element         PSI element reference to which should be updated
     * @return whether import statement was actually updated
     */
    public static boolean updateUnqualifiedImportOfElement(PyImportStatementBase importStatement, PsiNamedElement element) {
        String name = PyPsiRefactoringUtil.getOriginalName(element);
        if (name != null) {
            PyImportElement importElement = null;
            for (PyImportElement e : importStatement.getImportElements()) {
                if (name.equals(PyPsiRefactoringUtil.getOriginalName(e))) {
                    importElement = e;
                }
            }
            if (importElement != null) {
                PsiFile file = importStatement.getContainingFile();
                PsiFile newFile = element.getContainingFile();
                if (newFile == file || PyPsiRefactoringUtil.insertImport(importStatement, element, importElement.getAsName(), true)) {
                    if (importStatement.getImportElements().length == 1) {
                        boolean isInjected =
                            InjectedLanguageManager.getInstance(importElement.getProject()).isInjectedFragment(importElement.getContainingFile());
                        if (!isInjected) {
                            importStatement.delete();
                        }
                        else {
                            deleteImportStatementFromInjected(importStatement);
                        }
                    }
                    else {
                        importElement.delete();
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private static void deleteImportStatementFromInjected(PyImportStatementBase importStatement) {
        PsiElement sibling = importStatement.getPrevSibling();
        importStatement.delete();
        if (sibling instanceof PsiWhiteSpace) {
            sibling.delete();
        }
    }


    /**
     * Optimizes imports resorting them and removing unneeded
     *
     * @param file file to optimize imports
     */
    public static void optimizeImports(PsiFile file) {
        PyImportOptimizer.onlyRemoveUnused().processFile(file).run();
    }


    public static PsiFile placeFile(Project project, String path, String filename, boolean isNamespace)
        throws IOException {
        return placeFile(project, path, filename, null, isNamespace);
    }

    /**
     * Places a file at the end of given path, creating intermediate dirs and inits.
     *
     * @return the placed file
     */
    public static PsiFile placeFile(Project project,
                                    String path,
                                    String filename,
                                    @Nullable String content,
                                    boolean isNamespace) throws IOException {
        PsiDirectory psiDir = createDirectories(project, path, isNamespace);
        LOG.assertTrue(psiDir != null);
        PsiFile psiFile = psiDir.findFile(filename);
        if (psiFile == null) {
            psiFile = psiDir.createFile(filename);
            if (content != null) {
                PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
                Document document = manager.getDocument(psiFile);
                if (document != null) {
                    document.setText(content);
                    manager.commitDocument(document);
                }
            }
        }
        return psiFile;
    }

    /**
     * Create all intermediate dirs with inits from one of roots up to target dir.
     *
     * @param target a full path to target dir
     * @return deepest child directory, or null if target is not in roots or process fails at some point.
     */
    private static @Nullable PsiDirectory createDirectories(Project project, String target, boolean isNamespace)
        throws IOException {
        String relativePath = null;
        VirtualFile closestRoot = null;

        // NOTE: we don't canonicalize target; must be ok in reasonable cases, and is far easier in unit test mode
        target = FileUtil.toSystemIndependentName(target);
        ProjectRootManager projectRootManager = ProjectRootManager.getInstance(project);
        List<VirtualFile> allRoots = new ArrayList<>();
        ContainerUtil.addAll(allRoots, projectRootManager.getContentRoots());
        ContainerUtil.addAll(allRoots, projectRootManager.getContentSourceRoots());
        // Check deepest roots first
        allRoots.sort(Comparator.comparingInt((VirtualFile vf) -> vf.getPath().length()).reversed());
        for (VirtualFile file : allRoots) {
            String rootPath = file.getPath();
            if (target.startsWith(rootPath)) {
                relativePath = target.substring(rootPath.length());
                closestRoot = file;
                break;
            }
        }
        if (closestRoot == null) {
            throw new IOException("Can't find '" + target + "' among roots");
        }
        VirtualFileSystem lfs = StandardFileSystems.local();
        PsiManager psiManager = PsiManager.getInstance(project);
        String[] dirs = relativePath.split("/");
        int i = 0;
        if (dirs[0].isEmpty()) {
            i = 1;
        }
        VirtualFile resultDir = closestRoot;
        while (i < dirs.length) {
            VirtualFile subdir = resultDir.findChild(dirs[i]);
            if (subdir != null) {
                if (!subdir.isDirectory()) {
                    throw new IOException("Expected resultDir, but got non-resultDir: " + subdir.getPath());
                }
            }
            else {
                subdir = resultDir.createChildDirectory(lfs, dirs[i]);
            }
            if (subdir.findChild(PyNames.INIT_DOT_PY) == null && !isNamespace) {
                subdir.createChildData(lfs, PyNames.INIT_DOT_PY);
            }
            /*
            // here we could add an __all__ clause to the __init__.py.
            // * there's no point to do so; we import the class directly;
            // * we can't do this consistently since __init__.py may already exist and be nontrivial.
            if (i == dirs.length - 1) {
                PsiFile init_file = psiManager.findFile(initVFile);
                LOG.assertTrue(init_file != null);
                PyElementGenerator gen = PyElementGenerator.getInstance(project);
                PyStatement statement = gen.createFromText(LanguageLevel.getDefault(), PyStatement.class, PyNames.ALL + " = [\"" + lastName + "\"]");
                init_file.add(statement);
            }
           */
            resultDir = subdir;
            i += 1;
        }
        return psiManager.findDirectory(resultDir);
    }

    public static PyFile getOrCreateFile(String path, Project project, boolean isNamespace) {
        VirtualFile vfile = StandardFileSystems.local().findFileByPath(new File(path).getAbsolutePath());
        PsiFile psi;
        if (vfile == null) {
            File file = new File(path);
            try {
                VirtualFile baseDir = project.getBaseDir();
                FileTemplateManager fileTemplateManager = FileTemplateManager.getInstance(project);
                FileTemplate template = fileTemplateManager.getInternalTemplate("Python Script");
                Properties properties = fileTemplateManager.getDefaultProperties();
                properties.setProperty("NAME", FileUtil.getNameWithoutExtension(file.getName()));
                String content = template.getText(properties);
                psi = PyClassRefactoringUtil.placeFile(project,
                                                       StringUtil.notNullize(
                                                         file.getParent(),
                                                         baseDir != null ? baseDir
                                                           .getPath() : "."
                                                       ),
                                                       file.getName(),
                                                       content, isNamespace
                );
            }
            catch (IOException e) {
                throw new IncorrectOperationException(String.format("Cannot create file '%s'", path), e);
            }
        }
        else {
            psi = PsiManager.getInstance(project).findFile(vfile);
        }
        if (!(psi instanceof PyFile pyFile)) {
            throw new IncorrectOperationException(PyLocalize.refactoringMoveModuleMembersErrorCannotPlaceElementsIntoNonpythonFile().get());
        }
        return pyFile;
    }

    /**
     * Transfer necessary imports for references in the signature of one function to another.
     */
    public static void transplantImportsFromSignature(PyFunction sourceFunction, PyFunction targetFunction) {
        Map<String, PyReferenceExpression> unresolvedNames = new HashMap<>();

        targetFunction.accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyReferenceExpression(PyReferenceExpression referenceExpression) {
                super.visitPyReferenceExpression(referenceExpression);
                if (!referenceExpression.isQualified() && referenceExpression.getReference().multiResolve(false).length == 0) {
                    unresolvedNames.put(referenceExpression.getName(), referenceExpression);
                }
            }

            @Override
            public void visitPyStatementList(PyStatementList pruned) {
            }
        });

        sourceFunction.accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyReferenceExpression(PyReferenceExpression referenceExpression) {
                super.visitPyReferenceExpression(referenceExpression);
                if (!referenceExpression.isQualified()) {
                    PyReferenceExpression unresolvedReference = unresolvedNames.get(referenceExpression.getName());
                    if (unresolvedReference != null) {
                        rememberNamedReferences(referenceExpression);
                        restoreReference(referenceExpression, unresolvedReference, PsiElement.EMPTY_ARRAY);
                    }
                }
            }

            @Override
            public void visitPyStatementList(PyStatementList pruned) {
            }
        });
    }

    private static final class DynamicNamedElement extends LightElement implements PsiNamedElement {
        private final PsiFile myFile;
        private final String myName;

        private DynamicNamedElement(PsiFile file, String name) {
            super(file.getManager(), file.getLanguage());
            myName = name;
            myFile = file;
        }

        @Override
        public String toString() {
            return "DynamicNamedElement(file='" + getContainingFile().getName() + "', name='" + getName() + "')";
        }

        @Override
        public PsiFile getContainingFile() {
            return myFile;
        }

        @Override
        public PsiElement setName(String name) throws IncorrectOperationException {
            return null;
        }

        @Override
        public String getName() {
            return myName;
        }
    }
}
