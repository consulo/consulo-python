// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.stubs;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.PyElement;
import consulo.application.progress.ProgressManager;
import consulo.application.util.matcher.PrefixMatcher;
import consulo.component.ProcessCanceledException;
import consulo.language.editor.completion.CamelHumpMatcher;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiManager;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.util.QualifiedName;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ref.SimpleReference;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public final class PyQualifiedNameCompletionMatcher {
    private static final Logger LOG = Logger.getInstance(PyQualifiedNameCompletionMatcher.class);

    private PyQualifiedNameCompletionMatcher() {
    }

    public static void processMatchingExportedNames(QualifiedName qualifiedNamePattern,
                                                    PsiFile currentFile,
                                                    GlobalSearchScope scope,
                                                    Predicate<? super ExportedName> processor) {
        if (qualifiedNamePattern.getComponentCount() < 2) {
            return;
        }
        QualifiedNameMatcher matcher = new QualifiedNameMatcher(qualifiedNamePattern);
        StubIndex stubIndex = StubIndex.getInstance();
        Project project = Objects.requireNonNull(scope.getProject());
        PsiManager psiManager = PsiManager.getInstance(project);

        GlobalSearchScope moduleMatchingScope = scope.intersectWith(new ModuleQualifiedNameMatchingScope(matcher, project));
        Set<QualifiedName> alreadySuggestedAttributes = new HashSet<>();
        IndexLookupStats stats = new IndexLookupStats();
        try {
            List<String> matchingAttributeNames = new ArrayList<>();
            stubIndex.processAllKeys(PyExportedModuleAttributeIndex.KEY, attributeName -> {
                ProgressManager.checkCanceled();
                stats.scannedKeys++;
                if (!matcher.attributeMatches(attributeName)) {
                    return true;
                }
                stats.matchingKeys++;
                matchingAttributeNames.add(attributeName);
                return true;
            }, moduleMatchingScope, null);

            for (String attributeName : matchingAttributeNames) {
                stubIndex.processElements(PyExportedModuleAttributeIndex.KEY,
                    attributeName, project, moduleMatchingScope, null, PyElement.class, element -> {
                        ProgressManager.checkCanceled();
                        VirtualFile vFile = element.getContainingFile().getVirtualFile();
                        QualifiedName moduleQualifiedName = findQualifiedNameInClosestRoot(vFile, project);
                        assert moduleQualifiedName != null : vFile;
                        QualifiedName canonicalImportPath = findCanonicalImportPath(element, moduleQualifiedName, currentFile);
                        QualifiedName importPath;
                        if (canonicalImportPath != null && matcher.qualifierMatches(canonicalImportPath)) {
                            importPath = canonicalImportPath;
                        }
                        else {
                            importPath = moduleQualifiedName;
                        }
                        if (ContainerUtil.exists(importPath.getComponents(), c -> c.startsWith("_")) && !psiManager.isInProject(element)) {
                            return true;
                        }
                        QualifiedName attributeQualifiedName = importPath.append(attributeName);
                        if (alreadySuggestedAttributes.add(attributeQualifiedName)) {
                            if (!processor.test(new ExportedName(attributeQualifiedName, element))) {
                                return false;
                            }
                        }
                        return true;
                    });
            }
        }
        catch (ProcessCanceledException e) {
            stats.cancelled = true;
            throw e;
        }
        finally {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Index lookup stats for '" + qualifiedNamePattern + "':\n" +
                    "Scanned keys: " + stats.scannedKeys + "\n" +
                    "Matched keys: " + stats.matchingKeys + "\n" +
                    (stats.cancelled ? "Cancelled in " : "Completed in ") + stats.getDuration() + " ms");
            }
        }
    }

    private static class IndexLookupStats {
        long startNanoTime = System.nanoTime();
        int scannedKeys;
        int matchingKeys;
        boolean cancelled;

        long getDuration() {
            // TODO consulo: TimeoutUtil.getDurationMillis missing
            return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanoTime);
        }
    }

    private static @Nullable QualifiedName findCanonicalImportPath(PyElement element,
                                                                   QualifiedName moduleQualifiedName,
                                                                   PsiFile currentFile) {
        QualifiedName canonicalImportPath;
        if (moduleQualifiedName.getComponentCount() != 1) {
            canonicalImportPath = QualifiedNameFinder.findCanonicalImportPath(element, currentFile);
        }
        else {
            canonicalImportPath = QualifiedNameFinder.canonizeQualifiedName(element, moduleQualifiedName, currentFile);
        }
        return canonicalImportPath;
    }

    public static final class ExportedName {
        private final QualifiedName myQualifiedName;
        private final PyElement myElement;

        private ExportedName(QualifiedName qualifiedName, PyElement element) {
            myQualifiedName = qualifiedName;
            myElement = element;
        }

        public QualifiedName getQualifiedName() {
            return myQualifiedName;
        }

        public PyElement getElement() {
            return myElement;
        }
    }

    public static final class QualifiedNameMatcher extends PrefixMatcher {
        private final PrefixMatcher myQualifierFirstComponentMatcher;
        private final PrefixMatcher myQualifierRemainderMatcher;
        private final PrefixMatcher myLastComponentMatcher;

        public QualifiedNameMatcher(QualifiedName qualifiedName) {
            super(qualifiedName.toString());
            if (qualifiedName.getComponentCount() < 2) {
                throw new IllegalArgumentException("Qualified name should have at least two components, but was '" + qualifiedName + "'");
            }
            myLastComponentMatcher = new CamelHumpMatcher(qualifiedName.getLastComponent(), false);
            QualifiedName qualifier = qualifiedName.removeLastComponent();
            myQualifierFirstComponentMatcher = new CamelHumpMatcher(qualifier.getFirstComponent(), false);
            myQualifierRemainderMatcher = new CamelHumpMatcher(qualifier.removeHead(1).toString(), false);
        }

        @Override
        public boolean prefixMatches(String name) {
            QualifiedName qualifiedName = QualifiedName.fromDottedString(name);
            if (qualifiedName.getComponentCount() == 0) {
                return false;
            }
            if (!attributeMatches(qualifiedName.getLastComponent())) {
                return false;
            }
            if (!qualifierMatches(qualifiedName.removeLastComponent())) {
                return false;
            }
            return true;
        }

        private boolean attributeMatches(@Nullable String attribute) {
            return myLastComponentMatcher.isStartMatch(attribute);
        }

        private boolean qualifierMatches(QualifiedName qualifier) {
            String firstComponent = Objects.requireNonNullElse(qualifier.getFirstComponent(), "");
            if (!myQualifierFirstComponentMatcher.isStartMatch(firstComponent)) {
                return false;
            }
            String remainder = qualifier.getComponentCount() == 0 ? "" : qualifier.removeHead(1).toString();
            if (!myQualifierRemainderMatcher.prefixMatches(remainder)) {
                return false;
            }
            return true;
        }

        @Override
        public PrefixMatcher cloneWithPrefix(String prefix) {
            return new QualifiedNameMatcher(QualifiedName.fromDottedString(prefix));
        }
    }

    private static class ModuleQualifiedNameMatchingScope extends QualifiedNameFinder.QualifiedNameBasedScope {
        private final QualifiedNameMatcher myQualifiedNameMatcher;

        ModuleQualifiedNameMatchingScope(QualifiedNameMatcher qualifiedNameMatcher, Project project) {
            super(project);
            myQualifiedNameMatcher = qualifiedNameMatcher;
        }

        @Override
        protected boolean containsQualifiedNameInRoot(VirtualFile root, QualifiedName qName) {
            return ContainerUtil.all(qName.getComponents(), PyNames::isIdentifier) && myQualifiedNameMatcher.qualifierMatches(qName);
        }
    }

    private static @Nullable QualifiedName findQualifiedNameInClosestRoot(VirtualFile file, Project project) {
        // TODO Come up with a better API for exposing these internals
        SimpleReference<QualifiedName> result = SimpleReference.create();
        //noinspection ResultOfMethodCallIgnored
        new QualifiedNameFinder.QualifiedNameBasedScope(project) {
            @Override
            protected boolean containsQualifiedNameInRoot(VirtualFile root, QualifiedName qName) {
                result.set(qName);
                return true;
            }
        }.contains(file);
        return result.get();
    }
}
