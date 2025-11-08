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
package com.jetbrains.python.impl.psi.impl;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.PyElementTypes;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.inspections.PythonVisitorFilter;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.impl.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.impl.psi.resolve.PyResolveProcessor;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.resolve.VariantsProcessor;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.stubs.PyFileStub;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.application.util.RecursionManager;
import consulo.ide.impl.idea.openapi.vfs.VfsUtilCore;
import consulo.language.Language;
import consulo.language.file.FileViewProvider;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.impl.psi.PsiFileBase;
import consulo.language.psi.*;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.stub.IndexingDataKeys;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.module.content.ProjectFileIndex;
import consulo.navigation.ItemPresentation;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import consulo.util.collection.ContainerUtil;
import consulo.util.dataholder.Key;
import consulo.util.io.FileUtil;
import consulo.util.lang.ref.SoftReference;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;

public class PyFileImpl extends PsiFileBase implements PyFile, PyExpression {
    protected PyType myType;

    //private volatile Boolean myAbsoluteImportEnabled;
    private final Map<FutureFeature, Boolean> myFutureFeatures;
    private List<String> myDunderAll;
    private boolean myDunderAllCalculated;
    private volatile SoftReference<ExportedNameCache> myExportedNameCache = new SoftReference<>(null);
    private final PsiModificationTracker myModificationTracker;

    private class ExportedNameCache {
        private final List<String> myNameDefinerNegativeCache = new ArrayList<>();
        private long myNameDefinerOOCBModCount = -1;
        private final long myModificationStamp;
        private final Map<String, List<PsiNamedElement>> myNamedElements = Maps.newHashMap();
        private final List<PyImportedNameDefiner> myImportedNameDefiners = Lists.newArrayList();

        @RequiredReadAction
        private ExportedNameCache(long modificationStamp) {
            myModificationStamp = modificationStamp;

            processDeclarations(
                PyPsiUtils.collectAllStubChildren(PyFileImpl.this, getStub()),
                element -> {
                    if (element instanceof PsiNamedElement namedElement && !(namedElement instanceof PyKeywordArgument)) {
                        String name = namedElement.getName();
                        if (!myNamedElements.containsKey(name)) {
                            myNamedElements.put(name, new ArrayList<>());
                        }
                        List<PsiNamedElement> elements = myNamedElements.get(name);
                        elements.add(namedElement);
                    }
                    if (element instanceof PyImportedNameDefiner importedNameDefiner) {
                        myImportedNameDefiners.add(importedNameDefiner);
                    }
                    if (element instanceof PyFromImportStatement fromImportStatement) {
                        PyStarImportElement starImportElement = fromImportStatement.getStarImportElement();
                        if (starImportElement != null) {
                            myImportedNameDefiners.add(starImportElement);
                        }
                        else {
                            Collections.addAll(myImportedNameDefiners, fromImportStatement.getImportElements());
                        }
                    }
                    else if (element instanceof PyImportStatement importStatement) {
                        Collections.addAll(myImportedNameDefiners, importStatement.getImportElements());
                    }
                    return true;
                }
            );
            for (List<PsiNamedElement> elements : myNamedElements.values()) {
                Collections.reverse(elements);
            }
            Collections.reverse(myImportedNameDefiners);
        }

        private boolean processDeclarations(@Nonnull List<PsiElement> elements, @Nonnull Predicate<PsiElement> processor) {
            for (PsiElement child : elements) {
                if (!processor.test(child)) {
                    return false;
                }
                if (child instanceof PyExceptPart part
                    && !processDeclarations(PyPsiUtils.collectAllStubChildren(part, part.getStub()), processor)) {
                    return false;
                }
            }
            return true;
        }

        @Nonnull
        private List<RatedResolveResult> multiResolve(@Nonnull String name) {
            synchronized (myNameDefinerNegativeCache) {
                long modCount = myModificationTracker.getOutOfCodeBlockModificationCount();
                if (modCount != myNameDefinerOOCBModCount) {
                    myNameDefinerNegativeCache.clear();
                    myNameDefinerOOCBModCount = modCount;
                }
                else if (myNameDefinerNegativeCache.contains(name)) {
                    return Collections.emptyList();
                }
            }

            PyResolveProcessor processor = new PyResolveProcessor(name);
            boolean stopped = false;
            if (myNamedElements.containsKey(name)) {
                for (PsiNamedElement element : myNamedElements.get(name)) {
                    if (!processor.execute(element, ResolveState.initial())) {
                        stopped = true;
                        break;
                    }
                }
            }
            if (!stopped) {
                for (PyImportedNameDefiner definer : myImportedNameDefiners) {
                    if (!processor.execute(definer, ResolveState.initial())) {
                        break;
                    }
                }
            }
            Map<PsiElement, PyImportedNameDefiner> results = processor.getResults();
            if (!results.isEmpty()) {
                ResolveResultList resultList = new ResolveResultList();
                TypeEvalContext typeEvalContext = TypeEvalContext.codeInsightFallback(getProject());
                for (Map.Entry<PsiElement, PyImportedNameDefiner> entry : results.entrySet()) {
                    PsiElement element = entry.getKey();
                    PyImportedNameDefiner definer = entry.getValue();
                    if (element != null) {
                        int elementRate = PyReferenceImpl.getRate(element, typeEvalContext);
                        if (definer != null) {
                            resultList.add(new ImportedResolveResult(element, elementRate, definer));
                        }
                        else {
                            resultList.poke(element, elementRate);
                        }
                    }
                }
                return resultList;
            }

            synchronized (myNameDefinerNegativeCache) {
                myNameDefinerNegativeCache.add(name);
            }
            return Collections.emptyList();
        }

        public long getModificationStamp() {
            return myModificationStamp;
        }
    }

    public PyFileImpl(FileViewProvider viewProvider) {
        this(viewProvider, PythonLanguage.getInstance());
    }

    public PyFileImpl(FileViewProvider viewProvider, Language language) {
        super(viewProvider, language);
        myFutureFeatures = new HashMap<>();
        myModificationTracker = PsiModificationTracker.SERVICE.getInstance(getProject());
    }

    @Override
    @RequiredReadAction
    public String toString() {
        return "PyFile:" + getName();
    }

    @Override
    @RequiredReadAction
    public PyFunction findTopLevelFunction(String name) {
        return findByName(name, getTopLevelFunctions());
    }

    @Override
    @RequiredReadAction
    public PyClass findTopLevelClass(String name) {
        return findByName(name, getTopLevelClasses());
    }

    @Override
    @RequiredReadAction
    public PyTargetExpression findTopLevelAttribute(String name) {
        return findByName(name, getTopLevelAttributes());
    }

    @Nullable
    @RequiredReadAction
    private static <T extends PsiNamedElement> T findByName(String name, List<T> namedElements) {
        for (T namedElement : namedElements) {
            if (name.equals(namedElement.getName())) {
                return namedElement;
            }
        }
        return null;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public LanguageLevel getLanguageLevel() {
        if (myOriginalFile != null) {
            return ((PyFileImpl) myOriginalFile).getLanguageLevel();
        }
        VirtualFile virtualFile = getVirtualFile();

        if (virtualFile == null) {
            virtualFile = getUserData(IndexingDataKeys.VIRTUAL_FILE);
        }
        if (virtualFile == null) {
            virtualFile = getViewProvider().getVirtualFile();
        }
        return PyUtil.getLanguageLevelForVirtualFile(getProject(), virtualFile);
    }

    @Override
    public void accept(@Nonnull PsiElementVisitor visitor) {
        if (isAcceptedFor(visitor.getClass())) {
            if (visitor instanceof PyElementVisitor elemVisitor) {
                elemVisitor.visitPyFile(this);
            }
            else {
                super.accept(visitor);
            }
        }
    }

    public boolean isAcceptedFor(@Nonnull Class visitorClass) {
        for (Language lang : getViewProvider().getLanguages()) {
            List<PythonVisitorFilter> filters = PythonVisitorFilter.forLanguage(lang);
            for (PythonVisitorFilter filter : filters) {
                if (!filter.isSupported(visitorClass, this)) {
                    return false;
                }
            }
        }
        return true;
    }

    private final Key<Set<PyFile>> PROCESSED_FILES = Key.create("PyFileImpl.processDeclarations.processedFiles");

    @Override
    public boolean processDeclarations(
        @Nonnull final PsiScopeProcessor processor,
        @Nonnull ResolveState resolveState,
        PsiElement lastParent,
        @Nonnull PsiElement place
    ) {
        List<String> dunderAll = getDunderAll();
        final List<String> remainingDunderAll = dunderAll == null ? null : new ArrayList<>(dunderAll);
        PsiScopeProcessor wrapper = new PsiScopeProcessor() {
            @Override
            public boolean execute(@Nonnull PsiElement element, @Nonnull ResolveState state) {
                if (!processor.execute(element, state)) {
                    return false;
                }
                if (remainingDunderAll != null && element instanceof PyElement pyElement) {
                    remainingDunderAll.remove(pyElement.getName());
                }
                return true;
            }

            @Override
            public <T> T getHint(@Nonnull Key<T> hintKey) {
                return processor.getHint(hintKey);
            }

            @Override
            public void handleEvent(@Nonnull Event event, @Nullable Object associated) {
                processor.handleEvent(event, associated);
            }
        };

        Set<PyFile> pyFiles = resolveState.get(PROCESSED_FILES);
        if (pyFiles == null) {
            pyFiles = new HashSet<>();
            resolveState = resolveState.put(PROCESSED_FILES, pyFiles);
        }
        if (pyFiles.contains(this)) {
            return true;
        }
        pyFiles.add(this);
        for (PyClass c : getTopLevelClasses()) {
            if (c == lastParent) {
                continue;
            }
            if (!wrapper.execute(c, resolveState)) {
                return false;
            }
        }
        for (PyFunction f : getTopLevelFunctions()) {
            if (f == lastParent) {
                continue;
            }
            if (!wrapper.execute(f, resolveState)) {
                return false;
            }
        }
        for (PyTargetExpression e : getTopLevelAttributes()) {
            if (e == lastParent) {
                continue;
            }
            if (!wrapper.execute(e, resolveState)) {
                return false;
            }
        }

        for (PyImportElement e : getImportTargets()) {
            if (e == lastParent) {
                continue;
            }
            if (!wrapper.execute(e, resolveState)) {
                return false;
            }
        }

        for (PyFromImportStatement e : getFromImports()) {
            if (e == lastParent) {
                continue;
            }
            if (!e.processDeclarations(wrapper, resolveState, null, this)) {
                return false;
            }
        }

        if (remainingDunderAll != null) {
            for (String s : remainingDunderAll) {
                if (!PyNames.isIdentifier(s)) {
                    continue;
                }
                if (!processor.execute(new LightNamedElement(myManager, PythonLanguage.getInstance(), s), resolveState)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    @RequiredReadAction
    public List<PyStatement> getStatements() {
        List<PyStatement> stmts = new ArrayList<>();
        for (PsiElement child : getChildren()) {
            if (child instanceof PyStatement statement) {
                stmts.add(statement);
            }
        }
        return stmts;
    }

    @Override
    @RequiredReadAction
    public List<PyClass> getTopLevelClasses() {
        return PyPsiUtils.collectStubChildren(this, this.getStub(), PyElementTypes.CLASS_DECLARATION, PyClass.class);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public List<PyFunction> getTopLevelFunctions() {
        return PyPsiUtils.collectStubChildren(this, this.getStub(), PyElementTypes.FUNCTION_DECLARATION, PyFunction.class);
    }

    @Override
    @RequiredReadAction
    public List<PyTargetExpression> getTopLevelAttributes() {
        return PyPsiUtils.collectStubChildren(this, this.getStub(), PyElementTypes.TARGET_EXPRESSION, PyTargetExpression.class);
    }

    @Nullable
    @Override
    @RequiredReadAction
    public PsiElement findExportedName(String name) {
        List<RatedResolveResult> results = multiResolveName(name);
        List<PsiElement> elements = Lists.newArrayList();
        for (RatedResolveResult result : results) {
            PsiElement element = result.getElement();
            ImportedResolveResult importedResult = PyUtil.as(result, ImportedResolveResult.class);
            if (importedResult != null) {
                PyImportedNameDefiner definer = importedResult.getDefiner();
                if (definer != null) {
                    elements.add(definer);
                }
            }
            else if (element != null && element.getContainingFile() == this) {
                elements.add(element);
            }
        }
        PsiElement element = elements.isEmpty() ? null : elements.get(elements.size() - 1);
        if (element != null && !element.isValid()) {
            throw new PsiInvalidElementAccessException(element);
        }
        return element;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public List<RatedResolveResult> multiResolveName(@Nonnull String name) {
        List<RatedResolveResult> results =
            RecursionManager.doPreventingRecursion(this, false, () -> getExportedNameCache().multiResolve(name));
        if (results != null && !results.isEmpty()) {
            return results;
        }
        List<String> allNames = getDunderAll();
        if (allNames != null && allNames.contains(name)) {
            PsiElement allElement = findExportedName(PyNames.ALL);
            ResolveResultList allFallbackResults = new ResolveResultList();
            allFallbackResults.poke(allElement, RatedResolveResult.RATE_LOW);
            return allFallbackResults;
        }
        return Collections.emptyList();
    }

    @RequiredReadAction
    private ExportedNameCache getExportedNameCache() {
        ExportedNameCache cache;
        cache = myExportedNameCache != null ? myExportedNameCache.get() : null;
        long modificationStamp = getModificationStamp();
        if (myExportedNameCache != null && cache != null && modificationStamp != cache.getModificationStamp()) {
            myExportedNameCache.clear();
            cache = null;
        }
        if (cache == null) {
            cache = new ExportedNameCache(modificationStamp);
            myExportedNameCache = new SoftReference<>(cache);
        }
        return cache;
    }

    @Nullable
    @Override
    @RequiredReadAction
    public PsiElement getElementNamed(String name) {
        List<RatedResolveResult> results = multiResolveName(name);
        List<PsiElement> elements = PyUtil.filterTopPriorityResults(results.toArray(new ResolveResult[results.size()]));
        PsiElement element = elements.isEmpty() ? null : elements.get(elements.size() - 1);
        if (element != null) {
            if (!element.isValid()) {
                throw new PsiInvalidElementAccessException(element);
            }
            return element;
        }
        return null;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public Iterable<PyElement> iterateNames() {
        final List<PyElement> result = new ArrayList<>();
        VariantsProcessor processor = new VariantsProcessor(this) {
            @Override
            protected void addElement(String name, PsiElement element) {
                if (PyUtil.turnDirIntoInit(element) instanceof PyElement pyElement) {
                    result.add(pyElement);
                }
            }
        };
        processor.setAllowedNames(getDunderAll());
        processDeclarations(processor, ResolveState.initial(), null, this);
        return result;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public List<PyImportElement> getImportTargets() {
        List<PyImportElement> ret = new ArrayList<>();
        List<PyImportStatement> imports =
            PyPsiUtils.collectStubChildren(this, this.getStub(), PyElementTypes.IMPORT_STATEMENT, PyImportStatement.class);
        for (PyImportStatement one : imports) {
            ContainerUtil.addAll(ret, one.getImportElements());
        }
        return ret;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public List<PyFromImportStatement> getFromImports() {
        return PyPsiUtils.collectStubChildren(this, getStub(), PyElementTypes.FROM_IMPORT_STATEMENT, PyFromImportStatement.class);
    }

    @Override
    @RequiredReadAction
    public List<String> getDunderAll() {
        if (getStub() instanceof PyFileStub fileStub) {
            return fileStub.getDunderAll();
        }
        if (!myDunderAllCalculated) {
            List<String> dunderAll = calculateDunderAll();
            myDunderAll = dunderAll == null ? null : Collections.unmodifiableList(dunderAll);
            myDunderAllCalculated = true;
        }
        return myDunderAll;
    }

    @Nullable
    public List<String> calculateDunderAll() {
        DunderAllBuilder builder = new DunderAllBuilder();
        accept(builder);
        return builder.result();
    }

    private static class DunderAllBuilder extends PyRecursiveElementVisitor {
        private List<String> myResult = null;
        private boolean myDynamic = false;
        private boolean myFoundDunderAll = false;

        // hashlib builds __all__ by concatenating multiple lists of strings, and we want to understand this
        private final Map<String, List<String>> myDunderLike = new HashMap<>();

        @Override
        @RequiredReadAction
        public void visitPyFile(PyFile node) {
            if (node.getText().contains(PyNames.ALL)) {
                super.visitPyFile(node);
            }
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            if (PyNames.ALL.equals(node.getName())) {
                myFoundDunderAll = true;
                if (node.findAssignedValue() instanceof PyBinaryExpression binaryExpression) {
                    if (binaryExpression.isOperator("+")) {
                        List<String> lhs = getStringListFromValue(binaryExpression.getLeftExpression());
                        List<String> rhs = getStringListFromValue(binaryExpression.getRightExpression());
                        if (lhs != null && rhs != null) {
                            myResult = new ArrayList<>(lhs);
                            myResult.addAll(rhs);
                        }
                    }
                }
                else {
                    myResult = PyUtil.getStringListFromTargetExpression(node);
                }
            }
            if (!myFoundDunderAll) {
                List<String> names = PyUtil.getStringListFromTargetExpression(node);
                if (names != null) {
                    myDunderLike.put(node.getName(), names);
                }
            }
        }

        @Nullable
        private List<String> getStringListFromValue(PyExpression expression) {
            if (expression instanceof PyReferenceExpression refExpr && !refExpr.isQualified()) {
                return myDunderLike.get(refExpr.getReferencedName());
            }
            return PyUtil.strListValue(expression);
        }

        @Override
        public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
            if (PyNames.ALL.equals(node.getTarget().getName())) {
                myDynamic = true;
            }
        }

        @Override
        @RequiredReadAction
        public void visitPyCallExpression(PyCallExpression node) {
            if (node.getCallee() instanceof PyQualifiedExpression qualifiedExpr) {
                PyExpression qualifier = qualifiedExpr.getQualifier();
                if (qualifier != null && PyNames.ALL.equals(qualifier.getText())) {
                    // TODO handle append and extend with constant arguments here
                    myDynamic = true;
                }
            }
        }

        @Nullable
        List<String> result() {
            return myDynamic ? null : myResult;
        }
    }

    @Nullable
    public static List<String> getStringListFromTargetExpression(String name, List<PyTargetExpression> attrs) {
        for (PyTargetExpression attr : attrs) {
            if (name.equals(attr.getName())) {
                return PyUtil.getStringListFromTargetExpression(attr);
            }
        }
        return null;
    }

    @Override
    @RequiredReadAction
    public boolean hasImportFromFuture(FutureFeature feature) {
        if (getStub() instanceof PyFileStub fileStub) {
            return fileStub.getFutureFeatures().get(feature.ordinal());
        }
        Boolean enabled = myFutureFeatures.get(feature);
        if (enabled == null) {
            enabled = calculateImportFromFuture(feature);
            myFutureFeatures.put(feature, enabled);
            // NOTE: ^^^ not synchronized. if two threads will try to modify this, both can only be expected to set the same value.
        }
        return enabled;
    }

    @Override
    @RequiredReadAction
    public String getDeprecationMessage() {
        if (getStub() instanceof PyFileStub fileStub) {
            return fileStub.getDeprecationMessage();
        }
        return extractDeprecationMessage();
    }

    @Override
    @RequiredReadAction
    public List<PyImportStatementBase> getImportBlock() {
        List<PyImportStatementBase> result = new ArrayList<>();
        PsiElement firstChild = getFirstChild();
        PyImportStatementBase firstImport;
        if (firstChild instanceof PyImportStatementBase importStmtBase) {
            firstImport = importStmtBase;
        }
        else {
            firstImport = PsiTreeUtil.getNextSiblingOfType(firstChild, PyImportStatementBase.class);
        }
        if (firstImport != null) {
            result.add(firstImport);
            PsiElement nextImport = PyPsiUtils.getNextNonCommentSibling(firstImport, true);
            while (nextImport instanceof PyImportStatementBase importStmtBase) {
                result.add(importStmtBase);
                nextImport = PyPsiUtils.getNextNonCommentSibling(importStmtBase, true);
            }
        }
        return result;
    }

    @RequiredReadAction
    public String extractDeprecationMessage() {
        if (canHaveDeprecationMessage(getText())) {
            return PyFunctionImpl.extractDeprecationMessage(getStatements());
        }
        else {
            return null;
        }
    }

    private static boolean canHaveDeprecationMessage(String text) {
        return text.contains(PyNames.DEPRECATION_WARNING) || text.contains(PyNames.PENDING_DEPRECATION_WARNING);
    }

    @RequiredReadAction
    public boolean calculateImportFromFuture(FutureFeature feature) {
        if (getText().contains(feature.toString())) {
            List<PyFromImportStatement> fromImports = getFromImports();
            for (PyFromImportStatement fromImport : fromImports) {
                if (fromImport.isFromFuture()) {
                    PyImportElement[] pyImportElements = fromImport.getImportElements();
                    for (PyImportElement element : pyImportElements) {
                        QualifiedName qName = element.getImportedQName();
                        if (qName != null && qName.matches(feature.toString())) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }


    @Override
    public PyType getType(@Nonnull TypeEvalContext context, @Nonnull TypeEvalContext.Key key) {
        if (myType == null) {
            myType = new PyModuleType(this);
        }
        return myType;
    }

    @Nullable
    @Override
    public String getDocStringValue() {
        return DocStringUtil.getDocStringValue(this);
    }

    @Nullable
    @Override
    public StructuredDocString getStructuredDocString() {
        return DocStringUtil.getStructuredDocString(this);
    }

    @Nullable
    @Override
    public PyStringLiteralExpression getDocStringExpression() {
        return DocStringUtil.findDocStringExpression(this);
    }

    @Override
    @RequiredReadAction
    public void subtreeChanged() {
        super.subtreeChanged();
        ControlFlowCache.clear(this);
        myDunderAllCalculated = false;
        myFutureFeatures.clear(); // probably no need to synchronize
        myExportedNameCache.clear();
    }

    @Override
    @RequiredWriteAction
    public void delete() throws IncorrectOperationException {
        String path = getVirtualFile().getPath();
        super.delete();
        PyUtil.deletePycFiles(path);
    }

    @Override
    @RequiredWriteAction
    public PsiElement setName(@Nonnull String name) throws IncorrectOperationException {
        String path = getVirtualFile().getPath();
        PsiElement newElement = super.setName(name);
        PyUtil.deletePycFiles(path);
        return newElement;
    }

    private static class ArrayListThreadLocal extends ThreadLocal<List<String>> {
        @Override
        protected List<String> initialValue() {
            return new ArrayList<>();
        }
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            @RequiredReadAction
            public String getPresentableText() {
                return getModuleName(PyFileImpl.this);
            }

            @Override
            @RequiredReadAction
            public String getLocationString() {
                String name = getLocationName();
                return name != null ? "(" + name + ")" : null;
            }

            @Override
            @RequiredReadAction
            public Image getIcon() {
                if (PyUtil.isPackage(PyFileImpl.this)) {
                    return PlatformIconGroup.modulesSourceroot();
                }
                return IconDescriptorUpdaters.getIcon(PyFileImpl.this, 0);
            }

            @Nonnull
            @RequiredReadAction
            private String getModuleName(@Nonnull PyFile file) {
                if (PyUtil.isPackage(file)) {
                    PsiDirectory dir = file.getContainingDirectory();
                    if (dir != null) {
                        return dir.getName();
                    }
                }
                return FileUtil.getNameWithoutExtension(file.getName());
            }

            @Nullable
            @RequiredReadAction
            private String getLocationName() {
                QualifiedName name = QualifiedNameFinder.findShortestImportableQName(PyFileImpl.this);
                if (name != null) {
                    QualifiedName prefix = name.removeTail(1);
                    if (prefix.getComponentCount() > 0) {
                        return prefix.toString();
                    }
                }
                String relativePath = getRelativeContainerPath();
                if (relativePath != null) {
                    return relativePath;
                }
                PsiDirectory psiDirectory = getParent();
                if (psiDirectory != null) {
                    return psiDirectory.getVirtualFile().getPresentableUrl();
                }
                return null;
            }

            @Nullable
            @RequiredReadAction
            private String getRelativeContainerPath() {
                PsiDirectory psiDirectory = getParent();
                if (psiDirectory != null) {
                    VirtualFile virtualFile = getVirtualFile();
                    if (virtualFile != null) {
                        VirtualFile root = ProjectFileIndex.SERVICE.getInstance(getProject()).getContentRootForFile(virtualFile);
                        if (root != null) {
                            VirtualFile parent = virtualFile.getParent();
                            VirtualFile rootParent = root.getParent();
                            if (rootParent != null && parent != null) {
                                return VfsUtilCore.getRelativePath(parent, rootParent, File.separatorChar);
                            }
                        }
                    }
                }
                return null;
            }
        };
    }
}
