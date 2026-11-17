// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import consulo.language.psi.stub.PsiFileStub;
import consulo.language.impl.ast.FileElement;
import com.google.common.collect.Maps;
import consulo.language.impl.psi.PsiFileBase;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.ui.image.Image;
import consulo.localize.LocalizeValue;
import consulo.language.Language;
import consulo.navigation.ItemPresentation;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.module.content.ProjectFileIndex;
import consulo.util.dataholder.Key;
import consulo.application.util.RecursionManager;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.file.FileViewProvider;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiElementVisitor;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.resolve.DelegatingScopeProcessor;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.IncorrectOperationException;
import consulo.application.util.function.Processor;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyLanguageFacadeKt;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.ast.PyAstElementVisitor;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAugAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyCompoundStatement;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExceptPart;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStarImportElement;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.inspections.PythonVisitorFilter;
import com.jetbrains.python.psi.StructuredDocString;
import com.jetbrains.python.impl.psi.impl.references.PyReferenceImpl;
import com.jetbrains.python.impl.psi.impl.stubs.PyVersionSpecificStubBaseKt;
import com.jetbrains.python.impl.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.impl.psi.resolve.PyResolveProcessor;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.impl.psi.resolve.VariantsProcessor;
import com.jetbrains.python.psi.stubs.PyFileStub;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.pyi.PyiUtil;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class PyFileImpl extends PsiFileBase implements PyFile {
    protected volatile @Nullable PyType myType;

    //private volatile Boolean myAbsoluteImportEnabled;
    private final Map<FutureFeature, Boolean> myFutureFeatures;
    private volatile @Nullable List<String> myDunderAll;
    private volatile boolean myDunderAllCalculated;
    private volatile SoftReference<ExportedNameCache> myExportedNameCache = new SoftReference<>(null);
    private final PsiModificationTracker myModificationTracker;

    private final class ExportedNameCache {
        private final Set<String> myNameDefinerNegativeCache = new HashSet<>();
        private long myNameDefinerOOCBModCount = -1;
        private final long myModificationStamp;
        private final Map<String, List<PsiNamedElement>> myNamedElements = Maps.newHashMap();
        private final List<PyImportedNameDefiner> myImportedNameDefiners = new ArrayList<>();

        private ExportedNameCache(long modificationStamp) {
            myModificationStamp = modificationStamp;

            StubElement<?> stub = getStub();
            LanguageLevel languageLevel = PyLanguageFacadeKt.getEffectiveLanguageLevel(PyFileImpl.this);
            processDeclarations(PyFileImpl.this, stub, languageLevel, element -> {
                if (element instanceof PsiNamedElement namedElement &&
                        !(element instanceof PyKeywordArgument) &&
                        !(stub == null && element.getParent() instanceof PyImportElement)) {
                    myNamedElements.computeIfAbsent(namedElement.getName(), _ -> new ArrayList<>()).add(namedElement);
                }
                if (element instanceof PyImportedNameDefiner) {
                    myImportedNameDefiners.add((PyImportedNameDefiner)element);
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
            });
            TypeEvalContext typeEvalContext = TypeEvalContext.codeInsightFallback(getProject());
            for (List<PsiNamedElement> elements : myNamedElements.values()) {
                prioritizeNameRedefinitions(elements, typeEvalContext);
            }

            Collections.reverse(myImportedNameDefiners);
        }

        private static boolean processDeclarations(PsiElement element,
                @Nullable StubElement<?> stub,
                LanguageLevel languageLevel,
                Processor<? super PsiElement> processor) {
            for (PsiElement child : collectAllChildren(element, stub, languageLevel)) {
                if (!processor.process(child)) {
                    return false;
                }
                if (child instanceof PyExceptPart part) {
                    if (!processDeclarations(part, part.getStub(), languageLevel, processor)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private List<RatedResolveResult> multiResolve(String name) {
            synchronized (myNameDefinerNegativeCache) {
                long modCount = myModificationTracker.getModificationCount();
                if (modCount != myNameDefinerOOCBModCount) {
                    myNameDefinerNegativeCache.clear();
                    myNameDefinerOOCBModCount = modCount;
                }
                else {
                    if (myNameDefinerNegativeCache.contains(name)) {
                        return Collections.emptyList();
                    }
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

    protected void prioritizeNameRedefinitions(List<PsiNamedElement> definitions, TypeEvalContext typeEvalContext) {
        // Group overloads with their respective implementations
        List<List<PsiNamedElement>> grouped = StreamEx.of(definitions)
            .groupRuns((e1, e2) -> PyiUtil.isOverload(e1, typeEvalContext) && e2 instanceof PyFunction)
            .toList();
        definitions.clear();
        StreamEx.ofReversed(grouped)
            .flatCollection(Function.identity())
            .into(definitions);
    }

    public PyFileImpl(FileViewProvider viewProvider) {
        this(viewProvider, PythonLanguage.getInstance());
    }

    public PyFileImpl(FileViewProvider viewProvider, Language language) {
        super(viewProvider, language);
        myFutureFeatures = new HashMap<>();
        myModificationTracker = PsiModificationTracker.getInstance(getProject());
    }

    @Override
    public FileType getFileType() {
        return PythonFileType.INSTANCE;
    }

    @Override
    public String toString() {
        return "PyFile:" + getName();
    }

    @Override
    public PyFunction findTopLevelFunction(String name) {
        return findByName(name, getTopLevelFunctions());
    }

    @Override
    public PyClass findTopLevelClass(String name) {
        return findByName(name, getTopLevelClasses());
    }

    @Override
    public @Nullable PyTargetExpression findTopLevelAttribute(String name) {
        return findByName(name, getTopLevelAttributes());
    }

    @Override
    public List<PyTypeAliasStatement> getTypeAliasStatements() {
        return collectChildren(PyTypeAliasStatement.class);
    }

    @Override
    public @Nullable PyTypeAliasStatement findTypeAliasStatement(String name) {
        return findByName(name, getTypeAliasStatements());
    }

    private static @Nullable <T extends PsiNamedElement> T findByName(String name, List<T> namedElements) {
        for (T namedElement : namedElements) {
            if (name.equals(namedElement.getName())) {
                return namedElement;
            }
        }
        return null;
    }

    public Image getIcon(int flags) {
        return PythonFileType.INSTANCE.getIcon();
    }

    @Override
    public void accept(PsiElementVisitor visitor) {
        if (isAcceptedFor(visitor.getClass())) {
            if (visitor instanceof PyElementVisitor pyVisitor) {
                pyVisitor.visitPyFile(this);
            }
            else if (visitor instanceof PyAstElementVisitor pyVisitor) {
                pyVisitor.visitPyFile(this);
            }
            else {
                super.accept(visitor);
            }
        }
    }
    //
    //@Override
    //public PsiElement getNavigationElement() {
    //  final PsiElement element = PyiUtil.getOriginalElement(this);
    //  return element != null ? element : super.getNavigationElement();
    //}

    @Override
    public boolean isAcceptedFor(Class visitorClass) {
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
    public boolean processDeclarations(PsiScopeProcessor processor,
            ResolveState resolveState,
            PsiElement lastParent,
            PsiElement place) {
        List<String> dunderAll = getDunderAll();
        List<String> remainingDunderAll = dunderAll == null ? null : new ArrayList<>(dunderAll);
        PsiScopeProcessor wrapper = new DelegatingScopeProcessor(processor) {
            @Override
            public boolean execute(PsiElement element, ResolveState state) {
                if (!super.execute(element, state)) {
                    return false;
                }
                if (remainingDunderAll != null && element instanceof PyElement) {
                    remainingDunderAll.remove(((PyElement)element).getName());
                }
                return true;
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
    public List<PyClass> getTopLevelClasses() {
        return collectChildren(PyClass.class);
    }

    @Override
    public List<PyFunction> getTopLevelFunctions() {
        return collectChildren(PyFunction.class);
    }

    @Override
    public List<PyTargetExpression> getTopLevelAttributes() {
        return collectChildren(PyTargetExpression.class);
    }

    @Override
    public @Nullable PsiElement findExportedName(String name) {
        List<RatedResolveResult> results = multiResolveName(name);
        List<PsiElement> elements = new ArrayList<>();
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

    @Override
    public List<RatedResolveResult> multiResolveName(String name) {
        return multiResolveName(name, true);
    }

    @Override
    public List<RatedResolveResult> multiResolveName(String name, boolean exported) {
        List<RatedResolveResult> results = RecursionManager.doPreventingRecursion(this, false,
                () -> getExportedNameCache().multiResolve(name));
        if (results != null && !results.isEmpty()) {
            return results;
        }
        List<String> allNames = getDunderAll();
        if (allNames != null && !name.contains(".") && allNames.contains(name)) {
            ResolveResultList allFallbackResults = new ResolveResultList();

            PyResolveImportUtil
                .resolveModuleAt(QualifiedName.fromComponents(name), getContainingDirectory(), PyResolveImportUtil.fromFoothold(this))
                .forEach(module -> allFallbackResults.poke(module, RatedResolveResult.RATE_LOW));

            PsiElement allElement = findExportedName(PyNames.ALL);
            allFallbackResults.poke(allElement, RatedResolveResult.RATE_LOW);

            return allFallbackResults;
        }
        return Collections.emptyList();
    }

    private ExportedNameCache getExportedNameCache() {
        ExportedNameCache cache = myExportedNameCache.get();
        long modificationStamp = getModificationStamp();
        if (cache != null && modificationStamp != cache.getModificationStamp()) {
            myExportedNameCache.clear();
            cache = null;
        }
        if (cache == null) {
            cache = new ExportedNameCache(modificationStamp);
            myExportedNameCache = new SoftReference<>(cache);
        }
        return cache;
    }

    @Override
    public @Nullable PsiElement getElementNamed(String name) {
        List<RatedResolveResult> results = multiResolveName(name);
        List<PsiElement> elements = PyUtil.filterTopPriorityElements(results);
        PsiElement element = elements.isEmpty() ? null : elements.get(elements.size() - 1);
        if (element != null) {
            if (!element.isValid()) {
                throw new PsiInvalidElementAccessException(element);
            }
            return element;
        }
        return null;
    }

    @Override
    public Iterable<PyElement> iterateNames() {
        List<PyElement> result = new ArrayList<>();
        VariantsProcessor processor = new VariantsProcessor(this) {
            @Override
            protected void addElement(String name, PsiElement element) {
                element = PyUtil.turnDirIntoInit(element);
                if (element instanceof PyElement) {
                    result.add((PyElement)element);
                }
            }
        };
        processor.setAllowedNames(getDunderAll());
        processDeclarations(processor, ResolveState.initial(), null, this);
        return result;
    }

    @Override
    public List<PyImportElement> getImportTargets() {
        List<PyImportElement> ret = new ArrayList<>();
        List<PyImportStatement> imports = collectChildren(PyImportStatement.class);
        for (PyImportStatement one : imports) {
            ContainerUtil.addAll(ret, one.getImportElements());
        }
        return ret;
    }

    @Override
    public List<PyFromImportStatement> getFromImports() {
        return collectChildren(PyFromImportStatement.class);
    }

    @Override
    public @Nullable List<String> getDunderAll() {
        return withGreenStubOrAst(
            PyFileStub.class,
            stub -> stub.getDunderAll(),
            ast -> {
                if (!myDunderAllCalculated) {
                    List<String> dunderAll = calculateDunderAll();
                    myDunderAll = dunderAll == null ? null : Collections.unmodifiableList(dunderAll);
                    myDunderAllCalculated = true;
                }
                return myDunderAll;
            }
        );
    }

    public @Nullable List<String> calculateDunderAll() {
        DunderAllBuilder builder = new DunderAllBuilder();
        accept(builder);
        return builder.result();
    }

    private static class DunderAllBuilder extends PyRecursiveElementVisitor {

        private final List<String> myResult = new ArrayList<>();
        private boolean myDynamic = false;
        private boolean myFoundDunderAll = false;

        // hashlib builds __all__ by concatenating multiple lists of strings, and we want to understand this
        private final Map<String, List<String>> myDunderLike = new HashMap<>();

        @Override
        public void visitPyFile(PyFile node) {
            if (node.getText().contains(PyNames.ALL)) {
                super.visitPyFile(node);
            }
        }

        @Override
        public void visitPyTargetExpression(PyTargetExpression node) {
            if (myDynamic) {
                return;
            }

            if (PyNames.ALL.equals(node.getName())) {
                myFoundDunderAll = true;
                PyExpression value = node.findAssignedValue();
                if (value instanceof PyBinaryExpression binaryExpression) {
                    if (binaryExpression.isOperator("+")) {
                        processSubList(getStringListFromValue(binaryExpression.getLeftExpression()));
                        processSubList(getStringListFromValue(binaryExpression.getRightExpression()));
                    }
                    else {
                        myDynamic = true;
                    }
                }
                else {
                    processSubList(getStringListFromValue(value));
                }
            }

            if (!myFoundDunderAll) {
                List<String> names = getStringListFromValue(node.findAssignedValue());
                if (names != null) {
                    myDunderLike.put(node.getName(), names);
                }
            }
        }

        @Override
        public void visitPyAugAssignmentStatement(PyAugAssignmentStatement node) {
            if (myDynamic || !myFoundDunderAll) {
                return;
            }

            if (PyNames.ALL.equals(node.getTarget().getName())) {
                processSubList(getStringListFromValue(node.getValue()));
            }
        }

        @Override
        public void visitPyCallExpression(PyCallExpression node) {
            if (myDynamic || !myFoundDunderAll) {
                return;
            }

            PyExpression callee = node.getCallee();
            if (callee instanceof PyQualifiedExpression) {
                PyExpression qualifier = ((PyQualifiedExpression)callee).getQualifier();
                if (qualifier != null && PyNames.ALL.equals(qualifier.getText())) {
                    String calleeName = callee.getName();
                    if (PyNames.APPEND.equals(calleeName)) {
                        PyStringLiteralExpression argument = node.getArgument(0, PyStringLiteralExpression.class);
                        if (argument != null) {
                            myResult.add(argument.getStringValue());
                            return;
                        }
                    }
                    else if (PyNames.EXTEND.equals(calleeName)) {
                        PyExpression argument = node.getArgument(0, PyExpression.class);
                        processSubList(getStringListFromValue(argument));
                        return;
                    }

                    myDynamic = true;
                }
            }
        }

        @Override
        public void visitPyClass(PyClass node) {
        }

        @Nullable
        List<String> result() {
            return myDynamic || !myFoundDunderAll ? null : myResult;
        }

        private void processSubList(@Nullable List<String> list) {
            if (list == null) {
                myDynamic = true;
            }
            else {
                myResult.addAll(list);
            }
        }

        private @Nullable List<String> getStringListFromValue(@Nullable PyExpression expression) {
            if (expression instanceof PyReferenceExpression && !((PyReferenceExpression)expression).isQualified()) {
                return myDunderLike.get(((PyReferenceExpression)expression).getReferencedName());
            }
            return PyUtil.strListValue(expression);
        }
    }

    @Override
    public boolean hasImportFromFuture(FutureFeature feature) {
        return withGreenStubOrAst(
            PyFileStub.class,
            stub -> stub.getFutureFeatures().get(feature.ordinal()),
            ast -> {
                Boolean enabled = myFutureFeatures.get(feature);
                if (enabled == null) {
                    enabled = calculateImportFromFuture(feature);
                    myFutureFeatures.put(feature, enabled);
                    // NOTE: ^^^ not synchronized. if two threads will try to modify this, both can only be expected to set the same value.
                }
                return enabled;
            }
        );
    }

    @Override
    public String getDeprecationMessage() {
        return withGreenStubOrAst(
            PyFileStub.class,
            stub -> stub.getDeprecationMessage(),
            ast -> extractDeprecationMessage()
        );
    }

    public String extractDeprecationMessage() {
        if (canHaveDeprecationMessage(getText())) {
            return PyFunction.extractDeprecationMessage(getStatements());
        }
        else {
            return null;
        }
    }

    private static boolean canHaveDeprecationMessage(String text) {
        return text.contains(PyNames.DEPRECATION_WARNING) || text.contains(PyNames.PENDING_DEPRECATION_WARNING);
    }

    public boolean calculateImportFromFuture(FutureFeature feature) {
        for (PsiElement current = getFirstChild(); current != null; current = current.getNextSibling()) {
            if (current instanceof PsiWhiteSpace || current instanceof PsiComment) {
                continue;
            }
            boolean isModuleDocString = current instanceof PyExpressionStatement exprStatement &&
                    exprStatement.getExpression() instanceof PyStringLiteralExpression stringLiteral &&
                    stringLiteral.isDocString();
            if (isModuleDocString) {
                continue;
            }
            if (current instanceof PyFromImportStatement fromImport && fromImport.isFromFuture()) {
                for (PyImportElement importElement : fromImport.getImportElements()) {
                    QualifiedName qName = importElement.getImportedQName();
                    if (qName != null && qName.matches(feature.toString())) {
                        return true;
                    }
                }
                continue;
            }
            break;
        }
        return false;
    }

    @Override
    public @Nullable PyType getType(TypeEvalContext context, TypeEvalContext.Key key) {
        if (myType == null) {
            myType = new PyModuleType(this);
        }
        return myType;
    }

    @Override
    public @Nullable StructuredDocString getStructuredDocString() {
        return DocStringUtil.getStructuredDocString(this);
    }

    @Override
    public void clearCaches() {
        super.clearCaches();
        ControlFlowCache.clear(this);
        myDunderAllCalculated = false;
        myFutureFeatures.clear(); // probably no need to synchronize
        myExportedNameCache.clear();
    }

    @Override
    public void delete() throws IncorrectOperationException {
        String path = getVirtualFile().getPath();
        super.delete();
        PyUtil.deletePycFiles(path);
    }

    @Override
    public PsiElement setName(String name) throws IncorrectOperationException {
        String path = getVirtualFile().getPath();
        PsiElement newElement = super.setName(name);
        PyUtil.deletePycFiles(path);
        return newElement;
    }

    @Override
    public ItemPresentation getPresentation() {
        return new ItemPresentation() {
            @Override
            public LocalizeValue getPresentableText() {
                return LocalizeValue.of(getModuleName(PyFileImpl.this));
            }

            @Override
            public String getLocationString() {
                String name = getLocationName();
                return name != null ? "(" + name + ")" : null;
            }

            @Override
            public Image getIcon() {
                if (PyUtil.isPackage(PyFileImpl.this)) {
                    return PlatformIconGroup.nodesPackage();
                }
                return PyFileImpl.this.getIcon(0);
            }

            private static String getModuleName(PyFile file) {
                if (PyUtil.isPackage(file)) {
                    PsiDirectory dir = file.getContainingDirectory();
                    if (dir != null) {
                        return dir.getName();
                    }
                }
                return FileUtil.getNameWithoutExtension(file.getName());
            }

            private @Nullable String getLocationName() {
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

            private @Nullable String getRelativeContainerPath() {
                PsiDirectory psiDirectory = getParent();
                if (psiDirectory != null) {
                    VirtualFile virtualFile = getVirtualFile();
                    if (virtualFile != null) {
                        VirtualFile root = ProjectFileIndex.getInstance(getProject()).getContentRootForFile(virtualFile);
                        if (root != null) {
                            VirtualFile parent = virtualFile.getParent();
                            VirtualFile rootParent = root.getParent();
                            if (rootParent != null && parent != null) {
                                return VirtualFileUtil.getRelativePath(parent, rootParent, File.separatorChar);
                            }
                        }
                    }
                }
                return null;
            }
        };
    }

    private <T extends PyElement> List<T> collectChildren(Class<T> type) {
        @Nullable StubElement<?> stub = getGreenStub();
        LanguageLevel languageLevel = PyLanguageFacadeKt.getEffectiveLanguageLevel(this);
        List<T> result = new ArrayList<>();
        if (stub != null) {
            for (StubElement<?> child : PyVersionSpecificStubBaseKt.getChildrenStubs(stub, languageLevel)) {
                PsiElement childPsi = child.getPsi();
                if (type.isInstance(childPsi)) {
                    result.add(type.cast(childPsi));
                }
            }
        }
        else {
            acceptChildren(new PyTypeCheckedTopLevelElementVisitor(languageLevel) {
                @Override
                protected void checkAddElement(PsiElement node) {
                    if (type.isInstance(node)) {
                        result.add(type.cast(node));
                    }
                }

                @Override
                public void visitPyStatement(PyStatement node) {
                    if (PyStatement.class.isAssignableFrom(type) && !(node instanceof PyCompoundStatement)) {
                        checkAddElement(node);
                        return;
                    }
                    super.visitPyStatement(node);
                }
            });
        }
        return result;
    }

    private static List<PsiElement> collectAllChildren(PsiElement element,
            @Nullable StubElement<?> stub,
            LanguageLevel languageLevel) {
        List<PsiElement> result = new ArrayList<>();
        if (stub != null) {
            for (StubElement<?> child : PyVersionSpecificStubBaseKt.getChildrenStubs(stub, languageLevel)) {
                result.add(child.getPsi());
            }
        }
        else {
            element.acceptChildren(new PyTypeCheckedTopLevelElementVisitor(languageLevel) {
                @Override
                protected void checkAddElement(PsiElement node) {
                    result.add(node);
                }
            });
        }
        return result;
    }

    // TODO consulo: PsiFileImpl.withGreenStubOrAst(Class, Function, Function) missing; uses the green stub when available, otherwise the AST
    private <T, S extends PsiFileStub<?>> T withGreenStubOrAst(Class<S> stubClass,
                                                                Function<S, T> stubProcessor,
                                                                Function<FileElement, T> astProcessor) {
        StubElement stubElement = getGreenStub();
        if (stubClass.isInstance(stubElement)) {
            return stubProcessor.apply(stubClass.cast(stubElement));
        }
        return astProcessor.apply(calcTreeElement());
    }
}
