// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.jetbrains.python.impl.codeInsight.PyCodeInsightCounters;
import com.jetbrains.python.impl.codeInsight.PyCodeInsightCounters.Counter;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionCodeFragment;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyInstantTypeProvider;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeParameterType;
import com.jetbrains.python.psi.types.PyVariance;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.psi.types.TypeEvalContextCache;
import com.jetbrains.python.psi.types.engine.PyTypeEngine;
import com.jetbrains.python.psi.types.engine.PyTypeEngineProvider;
import com.jetbrains.python.pyi.PyiLanguageDialect;
import consulo.application.util.RecursionManager;
import consulo.application.util.registry.Registry;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ModuleUtilCore;
import consulo.language.util.ProcessingContext;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.module.content.ProjectFileIndex;
import consulo.module.content.layer.orderEntry.ModuleExtensionWithSdkOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.util.collection.ArrayUtil;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.HashingStrategy;
import consulo.util.lang.Pair;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;

public class TypeEvalContextImpl extends TypeEvalContext {
    protected static final Logger logger = Logger.getInstance(TypeEvalContextImpl.class);

    /**
     * Context constraints (see {@link TypeEvalConstraints}
     */
    private final TypeEvalConstraints constraints;

    private @Nullable List<String> myTrace;
    private String myTraceIndent = "";

    private final ThreadLocal<ProcessingContext> myProcessingContext = ThreadLocal.withInitial(ProcessingContext::new);

    private final Map<Module, Optional<PyTypeEngine>> myModuleTypeEngines = new ConcurrentHashMap<>();

    private final @Nullable PyTypeEngine typeEngine;

    protected final Map<PyTypedElement, PyType> myEvaluated = getConcurrentMapForCachingTypes();
    protected final Map<PyCallable, PyType> myEvaluatedReturn = getConcurrentMapForCachingTypes();
    protected final ConcurrentMap<Pair<Object, Object>, PyType> contextTypeCache = getConcurrentMapForCachingTypes();
    protected final Map<PyTypeParameterType, PyVariance> myVarianceCache = getConcurrentMapForCaching();
    protected final Map<SubstitutionsIdentifier, PyTypeChecker.GenericSubstitutions> mySubstitutionsCache = getConcurrentMapForCaching();

    TypeEvalContextImpl(TypeEvalConstraints constraints) {
        this.constraints = constraints;
        PsiFile origin = constraints.myOrigin;
        if (origin == null || isNotebookExternalTypeEngineDisabled(origin)) {
            typeEngine = null;
        }
        else {
            typeEngine = getOrCreateTypeEngine(origin.getOriginalFile());
        }
        PyCodeInsightCounters.inc(Counter.CONTEXTS_CONSTRUCTED);
    }

    TypeEvalContextImpl(boolean allowDataFlow,
                        boolean allowStubToAST,
                        boolean allowCallContext,
                        boolean isExternal,
                        @Nullable PsiFile origin) {
        this(new TypeEvalConstraints(allowDataFlow, allowStubToAST, allowCallContext, isExternal, origin));
    }

    /**
     * @return context constraints (see {@link TypeEvalConstraints}
     */
    public TypeEvalConstraints getConstraints() {
        return constraints;
    }

    public @Nullable PyTypeEngine getTypeEngine() {
        return typeEngine;
    }

    private @Nullable PyTypeEngine getTypeEngine(PyTypedElement element) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) {
            return null;
        }
        if (isNotebookExternalTypeEngineDisabled(containingFile)) {
            return null;
        }
        return getOrCreateTypeEngine(containingFile);
    }

    private @Nullable PyTypeEngine getOrCreateTypeEngine(PsiFile file) {
        Module module = file.getUserData(ModuleUtilCore.KEY_MODULE);
        if (module == null) {
            module = ModuleUtilCore.findModuleForFile(file.getOriginalFile());
        }
        if (module != null) {
            return moduleTypeEngine(module);
        }
        return findTypeEngineWithoutModule(file.getOriginalFile());
    }

    private @Nullable PyTypeEngine moduleTypeEngine(Module module) {
        return myModuleTypeEngines.computeIfAbsent(module, it -> Optional.ofNullable(PyTypeEngineProvider.createTypeEngineFor(it)))
            .orElse(null);
    }

    /**
     * The number of entries in each cache of this context.
     */
    public Map<String, Integer> cacheSizes() {
        return Map.of(
            "types", myEvaluated.size(),
            "returnTypes", myEvaluatedReturn.size(),
            "contextTypes", contextTypeCache.size(),
            "substitutions", mySubstitutionsCache.size(),
            "variances", myVarianceCache.size()
        );
    }

    public void clearCaches() {
        myEvaluated.clear();
        myEvaluatedReturn.clear();
        contextTypeCache.clear();
        mySubstitutionsCache.clear();
        myVarianceCache.clear();
    }

    @Override
    public String toString() {
        return "TypeEvalContext(" + constraints.myAllowDataFlow + ", " + constraints.myAllowStubToAST + ", " + constraints.myOrigin + ")";
    }

    @Override
    public boolean allowDataFlow(PsiElement element) {
        return constraints.myAllowDataFlow && !inPyiFile(element) || inOrigin(element);
    }

    @Override
    public boolean allowReturnTypes(PsiElement element) {
        return constraints.myAllowDataFlow && !inPyiFile(element) || inOrigin(element);
    }

    @Override
    public boolean allowCallContext(PsiElement element) {
        return constraints.myAllowCallContext && !inPyiFile(element) && inOrigin(element);
    }

    @Override
    public boolean maySwitchToAST(PsiElement element) {
        return constraints.myAllowStubToAST && !inPyiFile(element) || inOrigin(element);
    }

    private boolean isNotebookExternalTypeEngineDisabled(PsiFile origin) {
        VirtualFile realFile = origin.getOriginalFile().getVirtualFile();
        if (realFile == null) {
            return false;
        }
        return "ipynb".equalsIgnoreCase(realFile.getExtension()) &&
            !Registry.is("python.lsp.type.engine.notebooks", false);
    }

    private @Nullable PyTypeEngine findTypeEngineWithoutModule(PsiFile origin) {
        VirtualFile virtualFile = origin.getVirtualFile();
        if (virtualFile == null) {
            return null;
        }
        List<Module> modules = new ArrayList<>();
        for (OrderEntry entry : ProjectFileIndex.getInstance(origin.getProject()).getOrderEntriesForFile(virtualFile)) {
            // The interpreter library of a module carries the roots of its interpreter, as its SDK does.
            // TODO consulo: com.intellij.python.sdk.backend isIgnoredInterpreterEntry()/isInterpreterLibraryCarrier() missing - JB:
            // .filter { !it.isIgnoredInterpreterEntry() && (it is JdkOrderEntry || it.isInterpreterLibraryCarrier()) }
            if (entry instanceof ModuleExtensionWithSdkOrderEntry) {
                Module ownerModule = entry.getOwnerModule();
                if (!modules.contains(ownerModule)) {
                    modules.add(ownerModule);
                }
            }
        }
        for (Module module : modules) {
            PyTypeEngine engine = moduleTypeEngine(module);
            if (engine != null) {
                return engine;
            }
        }
        return null;
    }

    @Override
    public boolean isExternal() {
        return constraints.myIsExternal;
    }

    @Override
    public TypeEvalContext withTracing() {
        if (myTrace == null) {
            myTrace = new ArrayList<>();
        }
        return this;
    }

    @Override
    public void trace(String message, @Nullable Object... args) {
        if (myTrace != null) {
            myTrace.add(myTraceIndent + String.format(message, args));
        }
    }

    @Override
    public void traceIndent() {
        if (myTrace != null) {
            myTraceIndent += "  ";
        }
    }

    @Override
    public void traceUnindent() {
        if (myTrace != null && myTraceIndent.length() >= 2) {
            myTraceIndent = myTraceIndent.substring(0, myTraceIndent.length() - 2);
        }
    }

    @Override
    public String printTrace() {
        return String.join("\n", Objects.requireNonNull(myTrace));
    }

    @Override
    public boolean tracing() {
        return myTrace != null;
    }

    @Override
    public void traceWithIndent(String message, Runnable block) {
        trace(message);
        traceIndent();
        try {
            block.run();
        }
        finally {
            traceUnindent();
        }
    }

    @Override
    public <R> @Nullable R assumeType(PyTypedElement element, @Nullable PyType type, Function<@Nullable TypeEvalContext, R> func) {
        if (!Registry.is("python.use.better.control.flow.type.inference", true)) {
            return func.apply(this);
        }
        // PY-89956: if this exact (element, type) assumption is already in effect, don't nest another context.
        if (this instanceof AssumptionContext assumptionContext && assumptionContext.isAlreadyAssumed(element, type)) {
            return func.apply(this);
        }
        // Bound assumption nesting: pathological def-use/operator chains (esp. in library code) can nest
        // hundreds of levels, overflowing the stack / exhausting the heap. Past the cap fall back to the
        // un-narrowed context — less precise narrowing, still sound.
        int currentDepth = this instanceof AssumptionContext assumptionContext ? assumptionContext.getAssumptionDepth() : 0;
        if (currentDepth >= Registry.intValue("python.control.flow.assumption.max.depth", 8)) {
            return func.apply(this);
        }
        PyCodeInsightCounters.inc(Counter.ASSUME_TYPE_CALLS);
        AssumptionContext context = new AssumptionContext(this, element, type);
        try {
            return func.apply(context);
        }
        finally {
            element.getManager().dropResolveCaches();
        }
    }

    @Override
    public boolean hasAssumptions() {
        return this instanceof AssumptionContext;
    }

    @Override
    public @Nullable PyType getKnownType(PyTypedElement element) {
        if (element instanceof PyInstantTypeProvider) {
            return element.getType(this, KeyImpl.INSTANCE);
        }
        PyType type = myEvaluated.get(element);
        if (type != null) {
            assertValid(type, element);
        }
        return type;
    }

    @Override
    public @Nullable PyType getKnownReturnType(PyCallable callable) {
        PyType type = myEvaluatedReturn.get(callable);
        if (type != null) {
            assertValid(type, callable);
        }
        return type;
    }

    public void putSubstitutions(SubstitutionsIdentifier si, PyTypeChecker.GenericSubstitutions substitutions) {
        mySubstitutionsCache.putIfAbsent(si, substitutions);
    }

    public void removeSubstitutions(SubstitutionsIdentifier si) {
        mySubstitutionsCache.remove(si);
    }

    public PyTypeChecker.@Nullable GenericSubstitutions getKnownSubstitutions(SubstitutionsIdentifier si) {
        return mySubstitutionsCache.get(si);
    }

    private TypeEvalContext getLibraryContext(Project project) {
        // code completion will always have a new PsiFile, use the original file instead
        PsiFile origin = constraints.myOrigin != null ? constraints.myOrigin.getOriginalFile() : null;
        TypeEvalConstraints constraints = new TypeEvalConstraints(
            this.constraints.myAllowDataFlow,
            this.constraints.myAllowStubToAST,
            this.constraints.myAllowCallContext,
            this.constraints.myIsExternal,
            origin
        );
        return project.getInstance(TypeEvalContextCache.class)
            .getLibraryContext(new LibraryTypeEvalContext(constraints));
    }

    /**
     * If true the element's type will be calculated and stored in the long-life context bounded to the PyLibraryModificationTracker.
     */
    protected boolean canDelegateToLibraryContext(PyTypedElement element) {
        return Registry.is("python.use.separated.libraries.type.cache", true) && isLibraryElement(element);
    }

    @Override
    public @Nullable PyType getType(PyTypedElement element) {
        PyCodeInsightCounters.inc(Counter.GET_TYPE_CALLS);
        if (canDelegateToLibraryContext(element)) {
            TypeEvalContext context = getLibraryContext(element.getProject());
            return context.getType(element);
        }

        PyType knownType = getKnownType(element);
        if (knownType != null) {
            PyCodeInsightCounters.inc(Counter.GET_TYPE_CACHE_HITS);
            return knownType == PyNullType.INSTANCE ? PyAnyType.getUnknown() : knownType;
        }

        PyType result = RecursionManager.doPreventingRecursion(Pair.create(element, this), false, () -> {
            PyCodeInsightCounters.inc(Counter.GET_TYPE_EVALUATIONS);
            PyTypeEngine engine = getTypeEngine(element);
            if (engine != null && !engine.isSupportedForResolve(element)) {
                engine = null;
            }
            PyType type;
            if (engine == null) {
                type = evaluateWithBuiltInEngine(element);
            }
            else if (engine.isReady()) {
                // TODO consulo: PyTypeEvaluationAggregatesCollector (FUS statistics) missing - JB wraps this block in
                // PyTypeEvaluationAggregatesCollector.recordHybridTypeEngineTime(engine) { ... }
                boolean isUserInitiated = constraints.myAllowStubToAST && constraints.myAllowDataFlow;
                var resolvedType = engine.resolveType(element, isUserInitiated);
                // resolvedType is null when the engine could not evaluate at all, in contrast to Ref(null) which means the type is legitimately null
                type = resolvedType == null ? evaluateWithBuiltInEngine(element) : resolvedType.get();
            }
            else {
                // The engine answers after its server starts, and the start drops this context. Do not cache the gap.
                return PyAnyType.getUnknown();
            }

            assertValid(type, element);
            PyAnyType.validate(type, element);
            return publish(myEvaluated, element, type != null ? type : PyAnyType.getUnknown());
        });
        return result != null ? result : PyAnyType.getUnknown();
    }

    private @Nullable PyType evaluateWithBuiltInEngine(PyTypedElement element) {
        // TODO consulo: PyTypeEvaluationAggregatesCollector (FUS statistics) missing - JB wraps this call in
        // PyTypeEvaluationAggregatesCollector.recordPyCharmTypeEngineTime { ... }
        return element.getType(this, KeyImpl.INSTANCE);
    }

    @Override
    public @Nullable PyType getReturnType(PyCallable callable) {
        if (canDelegateToLibraryContext(callable)) {
            TypeEvalContext context = getLibraryContext(callable.getProject());
            return context.getReturnType(callable);
        }

        PyType knownReturnType = getKnownReturnType(callable);
        if (knownReturnType != null) {
            return knownReturnType instanceof PyNullType ? PyAnyType.getUnknown() : knownReturnType;
        }
        PyType result = RecursionManager.doPreventingRecursion(Pair.create(callable, this), false, () -> {
            PyType type = callable.getReturnType(this, KeyImpl.INSTANCE);
            assertValid(type, callable);
            PyAnyType.validate(type, callable);
            return publish(myEvaluatedReturn, callable, type);
        });
        return result != null ? result : PyAnyType.getUnknown();
    }

    /**
     * Stores {@code type} for {@code key} unless another thread stored a type first, and returns the stored type.
     * All threads that share this context then see the same type for {@code key}.
     */
    private static <K> @Nullable PyType publish(Map<K, PyType> cache, K key, @Nullable PyType type) {
        PyType stored = cache.putIfAbsent(key, type != null ? type : PyNullType.INSTANCE);
        if (stored == null) {
            return type;
        }
        return stored == PyNullType.INSTANCE ? PyAnyType.getUnknown() : stored;
    }

    /**
     * Normally, each {@code PyTypeProvider} is supposed to perform all the necessary analysis independently
     * and hence should completely isolate its state. However, on rare occasions when several type providers have to
     * recursively call each other, it might be necessary to preserve some state for subsequent calls to the same provider with
     * the same instance of {@link TypeEvalContext}. Each {@link TypeEvalContext} instance contains an associated thread-local
     * {@link ProcessingContext} that can be used for such caching. Should be used with discretion.
     *
     * @return a thread-local instance of {@link ProcessingContext} bound to this {@link TypeEvalContext} instance
     */
    @Override
    public ProcessingContext getProcessingContext() {
        return myProcessingContext.get();
    }

    @Override
    public @Nullable PsiFile getOrigin() {
        return constraints.myOrigin;
    }

    @Override
    public boolean getUsesExternalTypeEngine() {
        return typeEngine != null;
    }

    @Override
    public Map<Pair<Object, Object>, @Nullable PyType> getContextTypeCache() {
        return contextTypeCache;
    }

    @Override
    public Map<PyTypeParameterType, PyVariance> getVarianceCache() {
        return myVarianceCache;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || getClass() != other.getClass()) {
            return false;
        }

        TypeEvalContextImpl context = (TypeEvalContextImpl) other;

        return this.constraints.equals(context.constraints);
    }

    @Override
    public int hashCode() {
        return constraints.hashCode();
    }

    private boolean inOrigin(PsiElement element) {
        return isSameVirtualFile(constraints.myOrigin, element.getContainingFile()) ||
            isSameVirtualFile(constraints.myOrigin, getContextFile(element));
    }

    private static final class PyNullType implements PyType {
        static final PyNullType INSTANCE = new PyNullType();

        private PyNullType() {
        }

        @Override
        public List<? extends RatedResolveResult> resolveMember(String name,
                                                                @Nullable PyExpression location,
                                                                AccessDirection direction,
                                                                PyResolveContext resolveContext) {
            return Collections.emptyList();
        }

        @Override
        public Object[] getCompletionVariants(@Nullable String completionPrefix, PsiElement location, ProcessingContext context) {
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }

        @Override
        public String getName() {
            return "null";
        }

        @Override
        public boolean isBuiltin() {
            return false;
        }

        @Override
        public void assertValid(@Nullable String message) {
        }
    }

    /**
     * Use a temporary context to avoid that during computations, intermediate type results pollute the parent context's cache.
     * A temporary context is stacked on top of the parent context and will first check its own cache and then delegate to the parent
     * context's cache. New results will be stored in the temporary context's cache but eventually will be dropped when the temporary
     * context is disposed.
     */
    public static class TemporaryContext extends TypeEvalContextImpl {
        protected final TypeEvalContextImpl myParent;
        private final boolean keepUncapturedTypeParameters;

        public TemporaryContext(TypeEvalContextImpl myParent) {
            this(myParent, false);
        }

        public TemporaryContext(TypeEvalContextImpl myParent, boolean keepUncapturedTypeParameters) {
            super(myParent.constraints);
            this.myParent = myParent;
            this.keepUncapturedTypeParameters = keepUncapturedTypeParameters;
        }

        public TypeEvalContextImpl getMyParent() {
            return myParent;
        }

        public boolean getKeepUncapturedTypeParameters() {
            return keepUncapturedTypeParameters;
        }

        @Override
        public @Nullable PyType getKnownType(PyTypedElement element) {
            PyType type = super.getKnownType(element);
            return type != null ? type : myParent.getKnownType(element);
        }

        @Override
        public @Nullable PyType getKnownReturnType(PyCallable callable) {
            PyType type = super.getKnownReturnType(callable);
            return type != null ? type : myParent.getKnownReturnType(callable);
        }

        @Override
        public PyTypeChecker.@Nullable GenericSubstitutions getKnownSubstitutions(SubstitutionsIdentifier si) {
            PyTypeChecker.GenericSubstitutions substitutions = super.getKnownSubstitutions(si);
            return substitutions != null ? substitutions : myParent.getKnownSubstitutions(si);
        }
    }

    public static class AssumptionContext extends TemporaryContext {
        private final Map<List<Object>, PyType> myInstructionCache = new ConcurrentHashMap<>();

        private final AssumptionContext topAssumption;

        private final PyTypedElement assumedElement;
        private final @Nullable PyType assumedType;

        /**
         * 1 for the outermost assumption, incremented for each nested one. Used to bound assumption nesting.
         */
        private final int assumptionDepth;

        public AssumptionContext(TypeEvalContextImpl myParent, PyTypedElement element, @Nullable PyType type) {
            super(myParent);
            topAssumption = myParent instanceof AssumptionContext parentAssumption ? parentAssumption.topAssumption : this;
            assumedElement = element;
            assumedType = type;
            assumptionDepth = myParent instanceof AssumptionContext parentAssumption ? parentAssumption.assumptionDepth + 1 : 1;

            PyAnyType.validate(type, element);
            myEvaluated.put(element, type != null ? type : PyNullType.INSTANCE);
        }

        public Map<List<Object>, PyType> getMyInstructionCache() {
            return myInstructionCache;
        }

        public int getAssumptionDepth() {
            return assumptionDepth;
        }

        /**
         * Whether the same {@code (element, type)} assumption is already in effect somewhere in this assumption chain.
         */
        public boolean isAlreadyAssumed(PyTypedElement element, @Nullable PyType type) {
            TypeEvalContextImpl current = this;
            while (current instanceof AssumptionContext assumption) {
                if (assumption.assumedElement == element && Objects.equals(assumption.assumedType, type)) {
                    return true;
                }
                current = assumption.myParent;
            }
            return false;
        }

        @Override
        public @Nullable PyType getKnownType(PyTypedElement element) {
            TypeEvalContext current = this;
            while (current instanceof AssumptionContext assumption) {
                PyType knownType = assumption.getOwnKnownType(element);
                if (knownType != null) {
                    return knownType;
                }
                current = assumption.myParent;
            }
            return current.getKnownType(element);
        }

        // Helper to call super.getKnownType without recursion loops
        private @Nullable PyType getOwnKnownType(PyTypedElement element) {
            return super.getKnownType(element);
        }

        @Override
        public @Nullable PyType getKnownReturnType(PyCallable callable) {
            TypeEvalContextImpl current = this;
            while (current instanceof AssumptionContext assumption) {
                PyType knownType = assumption.getOwnKnownReturnType(callable);
                if (knownType != null) {
                    return knownType;
                }
                current = assumption.myParent;
            }
            return current.getKnownReturnType(callable);
        }

        private @Nullable PyType getOwnKnownReturnType(PyCallable callable) {
            return super.getKnownReturnType(callable);
        }

        @Override
        public void trace(String message, @Nullable Object... args) {
            topAssumption.myParent.trace(message, args);
        }

        @Override
        public void traceIndent() {
            topAssumption.myParent.traceIndent();
        }

        @Override
        public void traceUnindent() {
            topAssumption.myParent.traceUnindent();
        }

        @Override
        public boolean equals(Object other) {
            // Otherwise, it can be equal to other AssumptionContext with same constraints
            return this == other;
        }

        @Override
        public int hashCode() {
            return super.hashCode();
        }

        public @Nullable PyType getKnownTypeForInstruction(PyExpression anchor, PyType deducedType, int num) {
            return topAssumption.myInstructionCache.get(List.of(anchor, deducedType, num));
        }

        public void setKnownTypeForInstruction(PyExpression anchor, PyType deducedType, int num, PyType type) {
            topAssumption.myInstructionCache.put(List.of(anchor, deducedType, num), type);
        }

        @Override
        public Map<Pair<Object, Object>, @Nullable PyType> getContextTypeCache() {
            return topAssumption.myParent.getContextTypeCache();
        }
    }

    private static class LibraryTypeEvalContext extends TypeEvalContextImpl {
        LibraryTypeEvalContext(TypeEvalConstraints constraints) {
            super(constraints);
        }

        @Override
        protected boolean canDelegateToLibraryContext(PyTypedElement element) {
            // It's already the library-context.
            return false;
        }
    }

    public static class OptimizedTypeEvalContext extends TypeEvalContextImpl {
        private volatile @Nullable TypeEvalContext codeInsightFallback;

        public OptimizedTypeEvalContext(boolean allowDataFlow, boolean allowStubToAST, boolean allowCallContext, @Nullable PsiFile origin) {
            super(allowDataFlow, allowStubToAST, allowCallContext, false, origin);
        }

        public boolean shouldSwitchToFallbackContext(PsiElement element) {
            PsiFile file = element.getContainingFile();
            if (file instanceof PyExpressionCodeFragment) {
                PsiElement context = file.getContext();
                if (context != null) {
                    file = context.getContainingFile();
                }
            }
            TypeEvalConstraints constraints = getConstraints();
            return constraints.myOrigin != null && !isSameVirtualFile(
                file,
                constraints.myOrigin
            ) && (file instanceof PyFile) && !constraints.myAllowDataFlow && !constraints.myAllowStubToAST && !constraints.myAllowCallContext;
        }

        public @Nullable TypeEvalContext getFallbackContext(Project project) {
            if (codeInsightFallback == null) {
                codeInsightFallback = codeInsightFallback(project);
            }
            return codeInsightFallback;
        }

        @Override
        public @Nullable PyType getKnownType(PyTypedElement element) {
            if (shouldSwitchToFallbackContext(element)) {
                return Objects.requireNonNull(getFallbackContext(element.getProject())).getKnownType(element);
            }
            return super.getKnownType(element);
        }

        @Override
        public @Nullable PyType getKnownReturnType(PyCallable callable) {
            if (shouldSwitchToFallbackContext(callable)) {
                return Objects.requireNonNull(getFallbackContext(callable.getProject())).getKnownReturnType(callable);
            }
            return super.getKnownReturnType(callable);
        }

        @Override
        public @Nullable PyType getType(PyTypedElement element) {
            if (shouldSwitchToFallbackContext(element)) {
                return Objects.requireNonNull(getFallbackContext(element.getProject())).getType(element);
            }
            return super.getType(element);
        }

        @Override
        public @Nullable PyType getReturnType(PyCallable callable) {
            if (shouldSwitchToFallbackContext(callable)) {
                return Objects.requireNonNull(getFallbackContext(callable.getProject())).getReturnType(callable);
            }
            return super.getReturnType(callable);
        }
    }

    private static <T> ConcurrentMap<T, PyType> getConcurrentMapForCachingTypes() {
        return getConcurrentMapForCaching();
    }

    @SuppressWarnings("deprecation")
    private static <T, V> ConcurrentMap<T, V> getConcurrentMapForCaching() {
        // In the current implementation, this value is only used to initialize the map and is basically ignored
        // Just in case, set it to a reasonable value
        // `Runtime.availableProcessors` shouldn't be called here, as that is a potentially expensive operation
        int concurrencyLevel = 4;
        if (Registry.is("python.typing.weak.keys.type.eval.context", true)) {
            return ContainerUtil.createConcurrentWeakKeySoftValueMap(
                10,
                0.75f,
                concurrencyLevel,
                HashingStrategy.canonical()
            );
        }
        else if (Registry.is("python.typing.soft.keys.type.eval.context", false)) {
            return ContainerUtil.createConcurrentSoftKeySoftValueMap(10, 0.75f, concurrencyLevel, HashingStrategy.canonical());
        }
        else {
            return ContainerUtil.createConcurrentSoftValueMap();
        }
    }

    private static boolean isLibraryElement(PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        VirtualFile vFile = containingFile != null ? containingFile.getOriginalFile().getVirtualFile() : null;
        return vFile != null && ("pyi".equals(vFile.getExtension()) || ProjectFileIndex.getInstance(element.getProject()).isInLibrary(vFile));
    }

    private static void assertValid(@Nullable PyType result, PyTypedElement element) {
        if (result != null) {
            result.assertValid(element.toString());
        }
    }

    private static boolean isSameVirtualFile(@Nullable PsiFile file1, @Nullable PsiFile file2) {
        if (file1 == null) {
            return false;
        }
        if (file2 == null) {
            return false;
        }
        return Objects.equals(file1.getViewProvider().getVirtualFile(), file2.getViewProvider().getVirtualFile());
    }

    private static boolean inPyiFile(PsiElement element) {
        PsiFile containingFile = element.getContainingFile();
        if (containingFile == null) {
            return false;
        }
        if (isPyiFile(containingFile)) {
            return true;
        }
        PsiFile contextFile = getContextFile(element);
        return contextFile != null && isPyiFile(contextFile);
    }

    private static @Nullable PsiFile getContextFile(PsiElement element) {
        PsiFile file = element.getContainingFile();
        if (file == null) {
            return null;
        }
        PsiElement context = file.getContext();
        if (context == null) {
            return file;
        }
        return getContextFile(context);
    }

    private static boolean isPyiFile(PsiFile file) {
        return file.getLanguage() == PyiLanguageDialect.getInstance();
    }
}
