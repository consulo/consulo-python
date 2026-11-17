// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.psi.PyFileElementType;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder.QualifiedNameBasedScope;
import com.jetbrains.python.impl.psi.search.PySearchUtilBase;
import com.jetbrains.python.impl.psi.stubs.PyExportedModuleAttributeIndex;
import com.jetbrains.python.impl.psi.stubs.PyModuleNameIndex;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiFileType;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.application.progress.ProgressManager;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.registry.Registry;
import consulo.document.Document;
import consulo.language.Language;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResult;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.*;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.pattern.StandardPatterns;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.FileBasedIndex;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Predicate;

/**
 * Adds completion variants for Python classes, functions and variables.
 */
@ExtensionImpl(id = "pyClassNameCompletionContributor")
public class PyClassNameCompletionContributor extends CompletionContributor implements DumbAware {
    // See https://plugins.jetbrains.com/plugin/18465-sputnik
    private static final boolean TRACING_WITH_SPUTNIK_ENABLED = false;
    private static final Logger LOG = Logger.getInstance(PyClassNameCompletionContributor.class);

    // See PY-73964, IJPL-265
    private static final boolean RECURSIVE_INDEX_ACCESS_ALLOWED = false;

    public PyClassNameCompletionContributor() {
        if (TRACING_WITH_SPUTNIK_ENABLED) {
            System.out.println("\u0001hr('Importable names completion')");
        }
    }

    @Override
    @RequiredReadAction
    public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
        if (result.getPrefixMatcher().getPrefix().isEmpty()) {
            result.restartCompletionOnPrefixChange(StandardPatterns.string().longerThan(0));
            return;
        }
        result.restartCompletionWhenNothingMatches();
        Set<CompletionResult> remainingResults = result.runRemainingContributors(parameters, true);
        if (parameters.isExtendedCompletion() || remainingResults.isEmpty() || containsOnlyElementUnderTheCaret(remainingResults, parameters)) {
            fillCompletionVariantsImpl(parameters, result);
        }
    }

    @RequiredReadAction
    private void fillCompletionVariantsImpl(CompletionParameters parameters, CompletionResultSet result) {
        boolean isExtendedCompletion = parameters.isExtendedCompletion();
        if (!PyCodeInsightSettings.getInstance().INCLUDE_IMPORTABLE_NAMES_IN_BASIC_COMPLETION && !isExtendedCompletion) {
            return;
        }
        PsiFile originalFile = parameters.getOriginalFile();
        PsiElement position = parameters.getPosition();
        PyReferenceExpression refExpr = position.getParent() instanceof PyReferenceExpression r ? r : null;
        PyTargetExpression targetExpr = position.getParent() instanceof PyTargetExpression t ? t : null;
        PyStringElement stringElem = position instanceof PyStringElement s ? s : null;
        PsiElement originalPosition = parameters.getOriginalPosition();
        // In cases like `fo<caret>o = 42` the target expression gets split by the trailing space at the end of DUMMY_IDENTIFIER as
        // `foIntellijIdeaRulezzz o = 42`, so in the copied file we're still completing inside a standalone reference expression.
        boolean originallyInsideTarget = originalPosition != null && originalPosition.getParent() instanceof PyTargetExpression;
        boolean insideUnqualifiedReference = refExpr != null && !refExpr.isQualified() && !originallyInsideTarget;
        boolean insidePattern = targetExpr != null && position.getParent().getParent() instanceof PyCapturePattern;
        boolean insideStringLiteralInExtendedCompletion = stringElem != null && isExtendedCompletion;
        if (!(insideUnqualifiedReference || insidePattern || insideStringLiteralInExtendedCompletion)) {
            return;
        }

        // Directly inside the class body scope, it's rarely needed to have expression statements
        if (refExpr != null && (isDirectlyInsideClassBody(refExpr) ||
            isInsideErrorElement(refExpr) ||
            isInsideImportElement(refExpr))) {
            return;
        }

        if (insideStringLiteralInExtendedCompletion) {
            String prefix = stringElem.getText()
                .substring(stringElem.getContentRange().getStartOffset(), parameters.getOffset() - stringElem.getTextRange().getStartOffset());
            if (prefix.contains(".")) {
                return;
            }
        }

        Project project = originalFile.getProject();
        TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(project, originalFile);
        int maxVariants = Registry.intValue("ide.completion.variant.limit");
        Counters counters = new Counters();
        StubIndex stubIndex = StubIndex.getInstance();
        FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
        // TODO consulo: TimeoutUtil.run(ThrowableRunnable, LongConsumer) missing
        long start = System.currentTimeMillis();
        {
            Set<QualifiedName> alreadySuggested = new HashSet<>();

            // Suggest importable modules and packages
            GlobalSearchScope modulePackageScope = createScope(originalFile, false);
            forEachModulePackageNameFromIndex(modulePackageScope, modulePackageName -> {
                ProgressManager.checkCanceled();
                counters.scannedNames++;
                if (!result.getPrefixMatcher().isStartMatch(modulePackageName)) {
                    return true;
                }
                // TODO consulo: FileBasedIndex.getContainingFilesIterator missing
                for (VirtualFile vFile : fileBasedIndex.getContainingFiles(PyModuleNameIndex.NAME, modulePackageName, modulePackageScope)) {
                    ProgressManager.checkCanceled();
                    // For ordinary packages (non-namespace) PyModuleNameIndex returns the corresponding __init__.py
                    PyFile psiFile = originalFile.getManager().findFile(vFile) instanceof PyFile f ? f : null;
                    if (psiFile == null) {
                        return true;
                    }
                    if (!PyUtil.isImportable(originalFile, psiFile)) {
                        return true;
                    }
                    QualifiedName fqn = getFullyQualifiedName(psiFile);
                    if (!isApplicableInInsertionContext(psiFile, fqn, position, typeEvalContext)) {
                        counters.notApplicableInContextNames++;
                        return true;
                    }
                    if (alreadySuggested.add(fqn)) {
                        result.addElement(createLookupElementForImportableName(modulePackageName, psiFile, originalFile, position, typeEvalContext));
                        counters.totalVariants++;
                        if (counters.totalVariants >= maxVariants) {
                            return false;
                        }
                    }
                }
                return true;
            });

            // Suggest top-level importable names
            GlobalSearchScope topLevelNameScope = createScope(originalFile, true);
            forEachPublicNameFromIndex(topLevelNameScope, elementName -> {
                ProgressManager.checkCanceled();
                counters.scannedNames++;
                if (!result.getPrefixMatcher().isStartMatch(elementName)) {
                    return true;
                }
                stubIndex.processElements(PyExportedModuleAttributeIndex.KEY, elementName, project, topLevelNameScope,
                    PyElement.class, exported -> {
                        ProgressManager.checkCanceled();
                        String name = exported.getName();
                        if (name == null) {
                            return true;
                        }
                        QualifiedName fqn = getFullyQualifiedName(exported);
                        if (!isApplicableInInsertionContext(exported, fqn, position, typeEvalContext)) {
                            counters.notApplicableInContextNames++;
                            return true;
                        }
                        if (alreadySuggested.add(fqn)) {
                            if (isPrivateDefinition(fqn, exported, originalFile)) {
                                counters.privateNames++;
                                return true;
                            }
                            result.addElement(createLookupElementForImportableName(name, exported, originalFile, position, typeEvalContext));
                            counters.totalVariants++;
                            if (counters.totalVariants >= maxVariants) {
                                return false;
                            }
                        }
                        return true;
                    });
                return true;
            });
        }
        long duration = System.currentTimeMillis() - start;
        LOG.debug(counters + " computed for prefix '" + result.getPrefixMatcher().getPrefix() + "' in " + duration + " ms");
        if (TRACING_WITH_SPUTNIK_ENABLED) {
            System.out.println("\u0001h('Importable names completion','" + (duration / 10) * 10 + "')");
            System.out.println("\u0001Hi(" + duration + ")");
        }
    }

    private LookupElement createLookupElementForImportableName(
        String name,
        PyElement importable,
        PsiFile originalFile,
        PsiElement position,
        TypeEvalContext typeEvalContext
    ) {
        LookupElementBuilder builder = LookupElementBuilder
            .createWithSmartPointer(name, importable)
            .withIcon(IconDescriptorUpdaters.getIcon(importable, 0))
            .withExpensiveRenderer(new LookupElementRenderer<LookupElement>() {
                @Override
                @RequiredReadAction
                public void renderElement(LookupElement element, LookupElementPresentation presentation) {
                    presentation.setItemText(element.getLookupString());
                    presentation.setIcon(IconDescriptorUpdaters.getIcon(importable, 0));
                    QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(importable, originalFile);
                    if (importPath == null) {
                        return;
                    }
                    if (importable instanceof PsiFileSystemItem) {
                        presentation.setTypeText(importPath.removeLastComponent().toString());
                    }
                    else {
                        presentation.setTypeText(importPath.toString());
                    }
                }
            })
            .withInsertHandler(getInsertHandler(importable, position, typeEvalContext));
        return PrioritizedLookupElement.withPriority(builder, PythonCompletionWeigher.NOT_IMPORTED_MODULE_WEIGHT);
    }

    private InsertHandler<LookupElement> getInsertHandler(
        PyElement exported,
        PsiElement position,
        TypeEvalContext typeEvalContext
    ) {
        if (position.getParent() instanceof PyStringLiteralExpression) {
            if (isInsideAllInInitPy(position)) {
                return InsertHandlers.importingInsertHandler;
            }
            return InsertHandlers.stringLiteralInsertHandler;
        }
        else if (PyParameterizedTypeInsertHandler.isCompletingParameterizedType(exported, position, typeEvalContext)) {
            return InsertHandlers.genericTypeInsertHandler;
        }
        else if (exported instanceof PyFunction && !(position.getParent().getParent() instanceof PyDecorator)) {
            return InsertHandlers.functionInsertHandler;
        }
        return InsertHandlers.importingInsertHandler;
    }

    private boolean containsOnlyElementUnderTheCaret(
        Set<CompletionResult> remainingResults,
        CompletionParameters parameters
    ) {
        PsiElement position = parameters.getOriginalPosition();
        if (remainingResults.size() == 1 && position != null) {
            CompletionResult lookup = remainingResults.iterator().next();
            return lookup.getLookupElement().getLookupString().equals(position.getText());
        }
        return false;
    }

    private void forEachPublicNameFromIndex(GlobalSearchScope scope, Predicate<String> processor) {
        StubIndex stubIndex = StubIndex.getInstance();
        if (!RECURSIVE_INDEX_ACCESS_ALLOWED) {
            Project project = Objects.requireNonNull(scope.getProject());
            CachedValuesManager manager = CachedValuesManager.getManager(project);

            Collection<String> cachedAllNames = manager.getCachedValue(project, () -> {
                StubIndex index = StubIndex.getInstance();
                Collection<String> keys = index.getAllKeys(PyExportedModuleAttributeIndex.KEY, project);
                consulo.component.util.ModificationTracker modificationTracker =
                    index.getPerFileElementTypeModificationTracker(PyFileElementType.INSTANCE);
                return CachedValueProvider.Result.<Collection<String>>create(keys, modificationTracker);
            });

            for (String name : cachedAllNames) {
                if (!processor.test(name)) {
                    return;
                }
            }
        }
        else {
            stubIndex.processAllKeys(PyExportedModuleAttributeIndex.KEY, processor, scope, null);
        }
    }

    private void forEachModulePackageNameFromIndex(GlobalSearchScope scope, Predicate<String> processor) {
        for (String name : PyModuleNameIndex.getAllKeys(Objects.requireNonNull(scope.getProject()))) {
            if (!processor.test(name)) {
                return;
            }
        }
    }

    private boolean isApplicableInInsertionContext(
        PyElement definition,
        QualifiedName fqn,
        PsiElement position,
        TypeEvalContext context
    ) {
        if (PyTypingTypeProvider.isInsideTypeHint(position, context)) {
            // Not all names from typing.py are defined as classes
            return (definition instanceof PyClass ||
                definition instanceof PsiFileSystemItem ||
                isSuitableTypeAlias(definition, context) ||
                ArrayUtil.contains(fqn.getFirstComponent(), "typing", "typing_extensions"));
        }
        if (PsiTreeUtil.getParentOfType(position, PyPattern.class) != null) {
            return definition instanceof PyClass || definition instanceof PsiFileSystemItem;
        }
        return true;
    }

    private boolean isSuitableTypeAlias(PyElement element, TypeEvalContext context) {
        if (element instanceof PyTargetExpression && element.getParent() instanceof PyAssignmentStatement assignment) {
            if (PyTypingTypeProvider.isExplicitTypeAlias(assignment, context)) {
                return true;
            }
        }
        return element instanceof PyTypeAliasStatement;
    }

    private boolean isInsideErrorElement(PyReferenceExpression referenceExpression) {
        return PsiTreeUtil.getParentOfType(referenceExpression, PsiErrorElement.class) != null;
    }

    private boolean isInsideImportElement(PyReferenceExpression referenceExpression) {
        return PsiTreeUtil.getParentOfType(referenceExpression, PyImportStatementBase.class) != null;
    }

    private boolean isDirectlyInsideClassBody(PyReferenceExpression referenceExpression) {
        return referenceExpression.getParent() instanceof PyExpressionStatement &&
            ScopeUtil.getScopeOwner(referenceExpression.getParent()) instanceof PyClass;
    }

    private QualifiedName getFullyQualifiedName(PyElement exported) {
        if (exported instanceof PyFile file) {
            QualifiedName shortest = QualifiedNameFinder.findShortestImportableQName(file);
            return shortest != null ? shortest : QualifiedName.fromDottedString(FileUtil.getNameWithoutExtension(file.getName()));
        }
        String name = exported.getName();
        String shortName = name != null ? name : "";
        String qualifiedName = exported instanceof PyQualifiedNameOwner owner ? owner.getQualifiedName() : null;
        return QualifiedName.fromDottedString(qualifiedName != null ? qualifiedName : shortName);
    }

    private boolean isPrivateDefinition(QualifiedName fqn, PyElement exported, @Nullable PsiFile originalFile) {
        if (containsPrivateComponents(fqn)) {
            QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(exported, originalFile);
            return importPath != null && containsPrivateComponents(importPath);
        }
        return false;
    }

    private static boolean containsPrivateComponents(QualifiedName fqn) {
        for (String component : fqn.getComponents()) {
            if (component.startsWith("_")) {
                return true;
            }
        }
        return false;
    }

    private GlobalSearchScope createScope(PsiFile originalFile, boolean forTopLevelNames) {
        class HavingLegalImportPathScope extends QualifiedNameBasedScope {
            HavingLegalImportPathScope(Project project) {
                super(project);
            }

            @Override
            protected boolean containsQualifiedNameInRoot(VirtualFile root, QualifiedName qName) {
                for (String component : qName.getComponents()) {
                    if (!PyNames.isIdentifier(component)) {
                        return false;
                    }
                }
                return !qName.equals(QualifiedName.fromComponents("__future__"));
            }
        }

        Project project = originalFile.getProject();
        GlobalSearchScope pyiStubsScope =
            GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.everythingScope(project), PyiFileType.INSTANCE);
        GlobalSearchScope common = PySearchUtilBase.defaultSuggestionScope(originalFile)
            .intersectWith(GlobalSearchScope.notScope(pyiStubsScope))
            .intersectWith(GlobalSearchScope.notScope(GlobalSearchScope.fileScope(originalFile)))
            .intersectWith(new HavingLegalImportPathScope(project));
        if (forTopLevelNames) {
            // Some types in typing.py are defined as functions, causing inserting them with parentheses. It's better to rely on typing.pyi.
            return common
                .intersectWith(GlobalSearchScope.notScope(fileScope("typing", originalFile, false)))
                .uniteWith(fileScope("typing", originalFile, true));
        }
        return common;
    }

    private GlobalSearchScope fileScope(String fqn, PsiFile anchor, boolean pyiStub) {
        PyQualifiedNameResolveContext context = pyiStub
            ? com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil.fromFoothold(anchor)
            : com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil.fromFoothold(anchor).copyWithoutStubs();
        List<VirtualFile> files = new ArrayList<>();
        for (PsiElement element : com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil.resolveQualifiedName(
            QualifiedName.fromDottedString(fqn), context)) {
            if (element instanceof PsiFile file) {
                files.add(file.getVirtualFile());
            }
        }
        if (files.isEmpty()) {
            return GlobalSearchScope.EMPTY_SCOPE;
        }
        return GlobalSearchScope.filesWithLibrariesScope(anchor.getProject(), files);
    }

    private boolean isInsideAllInInitPy(PsiElement position) {
        PsiFile originalFile = position.getContainingFile();
        if (originalFile == null) {
            return false;
        }

        if (!PyNames.INIT_DOT_PY.equals(originalFile.getName())) {
            return false;
        }

        PyAssignmentStatement assignment = PsiTreeUtil.getParentOfType(position, PyAssignmentStatement.class);
        if (assignment == null) {
            return false;
        }

        PyExpression[] targets = assignment.getTargets();
        if (targets.length != 1) {
            return false;
        }

        PyExpression target = targets[0];
        if (!(target instanceof PyTargetExpression)) {
            return false;
        }

        return PyNames.ALL.equals(target.getName());
    }

    private static final class Counters {
        int scannedNames = 0;
        int privateNames = 0;
        int notApplicableInContextNames = 0;
        int totalVariants = 0;

        @Override
        public String toString() {
            return "Counters(scannedNames=" + scannedNames +
                ", privateNames=" + privateNames +
                ", notApplicableInContextNames=" + notApplicableInContextNames +
                ", totalVariants=" + totalVariants + ")";
        }
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    public static final class InsertHandlers {
        private InsertHandlers() {
        }

        static final InsertHandler<LookupElement> importingInsertHandler =
            (context, item) -> addImportForLookupElement(context, item, context.getTailOffset() - 1);

        static final InsertHandler<LookupElement> functionInsertHandler = new PyFunctionInsertHandler() {
            @Override
            @RequiredReadAction
            public void handleInsert(InsertionContext context, LookupElement item) {
                int tailOffset = context.getTailOffset() - 1;
                super.handleInsert(context, item);  // adds parentheses, modifies tail offset
                context.commitDocument();
                addImportForLookupElement(context, item, tailOffset);
            }
        };

        static final InsertHandler<LookupElement> genericTypeInsertHandler = (context, item) -> {
            int tailOffset = context.getTailOffset() - 1;
            PyParameterizedTypeInsertHandler.INSTANCE.handleInsert(context, item);
            context.commitDocument();
            addImportForLookupElement(context, item, tailOffset);
        };

        static final InsertHandler<LookupElement> stringLiteralInsertHandler = (context, item) -> {
            PsiElement element = item.getPsiElement();
            if (element == null) {
                return;
            }
            if (element instanceof PyQualifiedNameOwner owner) {
                insertStringLiteralPrefix(owner.getQualifiedName(), owner.getName(), context);
            }
            else {
                QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(element, null);
                if (importPath != null) {
                    insertStringLiteralPrefix(importPath.toString(), String.valueOf(importPath.getLastComponent()), context);
                }
            }
        };

        private static void insertStringLiteralPrefix(@Nullable String qualifiedName, @Nullable String name, InsertionContext context) {
            if (qualifiedName != null && name != null) {
                String qualifiedNamePrefix = qualifiedName.substring(0, Math.max(0, qualifiedName.length() - name.length()));
                context.getDocument().insertString(context.getStartOffset(), qualifiedNamePrefix);
            }
        }

        @RequiredReadAction
        public static void addImportForLookupElement(InsertionContext context, LookupElement item, int tailOffset) {
            PsiDocumentManager manager = PsiDocumentManager.getInstance(context.getProject());
            Document document = manager.getDocument(context.getFile());
            if (document != null) {
                manager.commitDocument(document);
            }
            PsiReference ref = context.getFile().findReferenceAt(tailOffset);
            if (ref == null || ref.resolve() == item.getPsiElement()) {
                // no import statement needed
                return;
            }
            WriteCommandAction.writeCommandAction(context.getFile()).run(() -> {
                PsiElement psiElement = item.getPsiElement();
                if (psiElement instanceof PsiNamedElement namedElement) {
                    AddImportHelper.addImport(namedElement, context.getFile(), (PyElement) ref.getElement());
                }
            });
        }
    }
}
