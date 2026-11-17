// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.references;

import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.util.lang.Pair;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.psi.FutureFeature;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.Property;
import com.jetbrains.python.psi.PyAssignmentExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyComprehensionElement;
import com.jetbrains.python.psi.PyComprehensionForComponent;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyGlobalStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportedNameDefiner;
import com.jetbrains.python.psi.PyListCompExpression;
import com.jetbrains.python.psi.PyNonlocalStatement;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyQualifiedElement;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.PyTupleExpression;
import com.jetbrains.python.psi.PyTypeParameter;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PropertyBunch;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.impl.psi.resolve.CompletionVariantsProcessor;
import com.jetbrains.python.impl.psi.resolve.ImplicitResolveResult;
import com.jetbrains.python.impl.psi.resolve.ImportedResolveResult;
import com.jetbrains.python.psi.resolve.PyOverridingReferenceResolveProvider;
import com.jetbrains.python.psi.resolve.PyReferenceResolveProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.impl.psi.resolve.PyResolveProcessor;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.impl.refactoring.PyDefUseUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class PyReferenceImpl extends PyReferenceBase {

    public PyReferenceImpl(PyQualifiedExpression element, PyResolveContext context) {
        super(element, context);
    }

    // sorts and modifies results of resolveInner

    private static ResolveResultList resolveToLatestDefs(List<Instruction> instructions,
                                                                  PsiElement element,
                                                                  String name,
                                                                  TypeEvalContext context) {
        ResolveResultList ret = new ResolveResultList();
        for (Instruction instruction : instructions) {
            PsiElement definition = instruction.getElement();
            // TODO: This check may slow down resolving, but it is the current solution to the comprehension scopes problem
            if (isInnerComprehension(element, definition)) {
                continue;
            }
            if (definition instanceof PyImportedNameDefiner definer && !(definition instanceof PsiNamedElement)) {
                List<RatedResolveResult> resolvedResults = definer.multiResolveName(name);
                for (RatedResolveResult result : resolvedResults) {
                    ret.add(new ImportedResolveResult(result.getElement(), result.getRate(), definer));
                }
                if (resolvedResults.isEmpty()) {
                    ret.add(new ImportedResolveResult(null, RatedResolveResult.RATE_NORMAL, definer));
                }
                else {
                    // TODO this kind of resolve contract is quite stupid
                    ret.poke(definer, RatedResolveResult.RATE_LOW);
                }
            }
            else {
                ret.poke(definition, getRate(definition, context));
            }
        }

        ResolveResultList results = new ResolveResultList();
        for (RatedResolveResult r : ret) {
            PsiElement e = r.getElement();
            if (e == element ||
                element instanceof PyTargetExpression && e != null && PyUtil.inSameFile(element, e) && PyPsiUtils.isBefore(element, e)) {
                continue;
            }
            results.add(changePropertyMethodToSameNameGetter(r, name, element));
        }
        return results;
    }

    private static boolean isInnerComprehension(PsiElement referenceElement, PsiElement definition) {
        if (definition != null && definition.getParent() instanceof PyAssignmentExpression) {
            return false;
        }

        PyComprehensionElement definitionComprehension = PsiTreeUtil.getParentOfType(definition, PyComprehensionElement.class);
        if (definitionComprehension != null && PyUtil.isOwnScopeComprehension(definitionComprehension)) {
            PyComprehensionElement elementComprehension = PsiTreeUtil.getParentOfType(referenceElement, PyComprehensionElement.class);
            if (elementComprehension == null || !PsiTreeUtil.isAncestor(definitionComprehension, elementComprehension, false)) {
                return true;
            }
        }
        return false;
    }

    private static RatedResolveResult changePropertyMethodToSameNameGetter(RatedResolveResult resolveResult,
                                                                                    String name,
                                                                                    PsiElement referenceElement) {
        PyCallExpression propertyCall = PsiTreeUtil.getParentOfType(referenceElement, PyCallExpression.class);
        if (propertyCall != null) {
            // Avoid converting function to property getter when the reference we are resolving sits inside a property(...) call (e.g., property(__getX))
            // In such context we actually need the bare function, not the property
            PyCallExpression propertyCallSite = PropertyBunch.findPropertyCallSite(propertyCall);
            if (propertyCallSite != null) {
                return resolveResult;
            }
        }

        PsiElement element = resolveResult.getElement();
        if (element instanceof PyFunction) {
            Property property = ((PyFunction)element).getProperty();
            if (property != null) {
                PyCallable getter = property.getGetter().valueOrNull();
                PyCallable setter = property.getSetter().valueOrNull();
                PyCallable deleter = property.getDeleter().valueOrNull();

                if (getter != null && name.equals(getter.getName()) &&
                    (setter == null || name.equals(setter.getName())) &&
                    (deleter == null || name.equals(deleter.getName()))) {
                    return resolveResult.replace(getter);
                }
            }
        }

        return resolveResult;
    }

    private boolean isInOwnScopeComprehension(@Nullable PsiElement uexpr) {
        if (uexpr == null || !myContext.getTypeEvalContext().maySwitchToAST(uexpr)) {
            return false;
        }
        PyComprehensionElement comprehensionElement = PsiTreeUtil.getParentOfType(uexpr, PyComprehensionElement.class);
        return comprehensionElement != null && PyUtil.isOwnScopeComprehension(comprehensionElement);
    }

    /**
     * Does actual resolution of resolve().
     *
     * @return resolution result.
     * @see #resolve()
     */
    @Override
    protected List<RatedResolveResult> resolveInner() {
        ResolveResultList overriddenResult = resolveByOverridingReferenceResolveProviders();
        if (!overriddenResult.isEmpty()) {
            return overriddenResult;
        }

        String referencedName = myElement.getReferencedName();
        if (referencedName == null) {
            return Collections.emptyList();
        }

        if (myElement instanceof PyTargetExpression && PsiTreeUtil.getParentOfType(myElement, PyComprehensionElement.class) != null) {
            return ResolveResultList.to(myElement);
        }

        // Use real context here to enable correct completion and resolve in case of PyExpressionCodeFragment
        PsiElement realContext = PyPsiUtils.getRealContext(myElement);

        if (!myContext.getTypeEvalContext().maySwitchToAST(realContext) && realContext instanceof PyFile) {
            return ((PyFile)realContext).multiResolveName(referencedName);
        }

        // PY-89956 fast path: for a plain local variable, resolve straight to its control-flow
        // reaching definitions instead of first collecting *all* same-name definitions of the scope
        TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
        ScopeOwner owner = ScopeUtil.getScopeOwner(realContext);
        if (typeEvalContext.maySwitchToAST(realContext) && owner != null && !(owner instanceof PyClass)) {
            Scope scope = ControlFlowCache.getScope(owner);
            if (!scope.getNamedElements(referencedName, false).isEmpty() &&
                !scope.isGlobal(referencedName) &&
                !scope.isNonlocal(referencedName)) {
                List<Instruction> defs =
                    PyDefUseUtil.getLatestDefs(owner, referencedName, realContext, false, true, typeEvalContext).defs();
                if (!defs.isEmpty() && ContainerUtil.and(defs, i -> i.getElement() instanceof PyTargetExpression)) {
                    ResolveResultList latest = resolveToLatestDefs(defs, realContext, referencedName, typeEvalContext);
                    if (!latest.isEmpty()) {
                        return latest;
                    }
                }
            }
        }

        // here we have an unqualified expr. it may be defined:
        // ...in current file
        PyResolveProcessor processor = new PyResolveProcessor(referencedName);

        PsiElement roof = findResolveRoof(referencedName, realContext);
        PyResolveUtil.scopeCrawlUp(processor, myElement, referencedName, roof);

        return getResultsFromProcessor(referencedName, processor, realContext, roof);
    }

    protected final List<RatedResolveResult> getResultsFromProcessor(String referencedName,
                                                                     PyResolveProcessor processor,
                                                                     @Nullable PsiElement referenceAnchor,
                                                                     @Nullable PsiElement resolveRoof) {
        boolean unreachableLocalDeclaration = false;
        Supplier<ScopeOwner> resolveInParentScope = null;
        ResolveResultList resultList = new ResolveResultList();
        ScopeOwner referenceOwner = ScopeUtil.getScopeOwner(referenceAnchor);
        TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
        ScopeOwner resolvedOwner = processor.getOwner();

        Collection<PsiElement> resolvedElements = processor.getElements();
        if (resolvedOwner != null &&
            !processor.isTypeParameterScope() &&
            !resolvedElements.isEmpty() &&
            !ControlFlowCache.getScope(resolvedOwner).isGlobal(referencedName)) {
            if (resolvedOwner == referenceOwner && referenceAnchor != null) {
                List<Instruction> instructions = getLatestDefinitions(referencedName, resolvedOwner, referenceAnchor);
                // TODO: Use the results from the processor as a cache for resolving to latest defs
                ResolveResultList latestDefs = resolveToLatestDefs(instructions, referenceAnchor, referencedName, typeEvalContext);
                if (!latestDefs.isEmpty()) {
                    return latestDefs.stream()
                        .flatMap(r -> {
                            if (r.getClass() != RatedResolveResult.class || !(r.getElement() instanceof PyFunction pyFunction)) {
                                return Stream.of(r);
                            }
                            List<PyFunction> overloads = PyiUtil.getOverloads(pyFunction, typeEvalContext);
                            if (overloads.isEmpty()) {
                                return Stream.of(r);
                            }
                            return Stream.concat(
                                Stream.ofNullable(PyiUtil.isOverload(pyFunction, typeEvalContext) ? null : r),
                                overloads.stream()
                                    .map(overload -> new RatedResolveResult(getRate(overload, typeEvalContext), overload))
                            );
                        })
                        .toList();
                }
                else if (resolvedOwner instanceof PyClass && !(referenceAnchor instanceof PyTargetExpression)) {
                    resolveInParentScope = () -> PyResolveUtil.parentScopeForUnresolvedClassLevelName((PyClass)resolvedOwner, referencedName);
                }
                else if (instructions.isEmpty() && ContainerUtil.and(resolvedElements, this::isInOwnScopeComprehension)) {
                    resolveInParentScope = () -> ScopeUtil.getScopeOwner(resolvedOwner);
                }
                else {
                    unreachableLocalDeclaration = true;
                }
            }
            else if (referenceOwner != null) {
                if (!allowsForwardOutgoingReferencesInClass(getElement())) {
                    PyClass outermostNestedClass = outermostNestedClass(referenceOwner, resolvedOwner);

                    if (outermostNestedClass != null) {
                        List<Instruction> instructions =
                            PyDefUseUtil.getLatestDefs(resolvedOwner, referencedName, outermostNestedClass, false, true, typeEvalContext).defs();

                        return resolveToLatestDefs(instructions, outermostNestedClass, referencedName, typeEvalContext);
                    }
                }

                Scope referenceScope = ControlFlowCache.getScope(referenceOwner);
                if (referenceScope.containsDeclaration(referencedName)) {
                    unreachableLocalDeclaration = true;
                }
            }
        }

        // TODO: Try resolve to latest defs for outer scopes starting from the last element in CFG (=> no need for a special rate for globals)

        if (!unreachableLocalDeclaration) {
            if (resolveInParentScope != null) {
                processor = new PyResolveProcessor(referencedName);
                ScopeOwner parentScope = resolveInParentScope.get();
                if (parentScope != null) {
                    PyResolveUtil.scopeCrawlUp(processor, parentScope, referencedName, resolveRoof);
                }
            }

            for (Map.Entry<PsiElement, PyImportedNameDefiner> entry : processor.getResults().entrySet()) {
                PsiElement resolved = entry.getKey();
                PyImportedNameDefiner definer = entry.getValue();
                if (resolved != null) {
                    if (typeEvalContext.maySwitchToAST(resolved) && isInnerComprehension(referenceAnchor, resolved)) {
                        continue;
                    }
                    if (definer == null) {
                        resultList.poke(resolved, getRate(resolved, typeEvalContext));
                    }
                    else {
                        resultList.poke(definer, getRate(definer, typeEvalContext));
                        resultList.add(new ImportedResolveResult(resolved, getRate(resolved, typeEvalContext), definer));
                    }
                }
                else if (definer != null) {
                    resultList.add(new ImportedResolveResult(null, RatedResolveResult.RATE_LOW, definer));
                }
            }

            if (!resultList.isEmpty()) {
                return resultList;
            }
        }

        return resolveByReferenceResolveProviders();
    }

    protected List<Instruction> getLatestDefinitions(String referencedName,
                                                              ScopeOwner resolvedOwner,
                                                              PsiElement referenceAnchor) {
        return PyDefUseUtil.getLatestDefs(resolvedOwner, referencedName, referenceAnchor, false, true, myContext.getTypeEvalContext()).defs();
    }

    private static boolean allowsForwardOutgoingReferencesInClass(PyQualifiedExpression element) {
        return ContainerUtil.exists(PyReferenceResolveProvider.EP_NAME.getExtensionList(),
                                    provider -> provider.allowsForwardOutgoingReferencesInClass(element));
    }

    private static @Nullable PyClass outermostNestedClass(ScopeOwner referenceOwner, ScopeOwner resolvedOwner) {
        PyClass current = PyUtil.as(referenceOwner, PyClass.class);
        ScopeOwner outer = ScopeUtil.getScopeOwner(current);

        while (outer != resolvedOwner) {
            current = PyUtil.as(outer, PyClass.class);
            if (current == null) {
                return null;
            }
            outer = ScopeUtil.getScopeOwner(outer);
        }

        return current;
    }

    private ResolveResultList resolveByOverridingReferenceResolveProviders() {
        ResolveResultList results = new ResolveResultList();
        TypeEvalContext context = myContext.getTypeEvalContext();

        PyReferenceResolveProvider.EP_NAME.getExtensionList().stream()
            .filter(PyOverridingReferenceResolveProvider.class::isInstance)
            .map(provider -> provider.resolveName(getElement(), context))
            .forEach(results::addAll);

        return results;
    }

    private ResolveResultList resolveByReferenceResolveProviders() {
        ResolveResultList results = new ResolveResultList();
        TypeEvalContext context = myContext.getTypeEvalContext();
        for (PyReferenceResolveProvider provider : PyReferenceResolveProvider.EP_NAME.getExtensionList()) {
            if (provider instanceof PyOverridingReferenceResolveProvider) {
                continue;
            }
            results.addAll(provider.resolveName(getElement(), context));
        }
        return results;
    }

    private PsiElement findResolveRoof(String referencedName, PsiElement realContext) {
        if (PyUtil.isClassPrivateName(referencedName)) {
            // a class-private name; limited by either class or this file
            PsiElement one = myElement;
            do {
                one = ScopeUtil.getScopeOwner(one);
            }
            while (one instanceof PyFunction);
            if (one instanceof PyClass && !PsiTreeUtil.isAncestor(((PyClass)one).getSuperClassExpressionList(), myElement, false)) {
                return one;
            }
        }

        if (myElement instanceof PyTargetExpression) {
            ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(myElement, ScopeOwner.class);
            if (scopeOwner != null) {
                Scope scope = ControlFlowCache.getScope(scopeOwner);
                if (scope.isNonlocal(referencedName)) {
                    ScopeOwner nonlocalOwner = ScopeUtil.getDeclarationScopeOwner(myElement, referencedName);
                    if (nonlocalOwner != null) {
                        boolean isGlobal = nonlocalOwner instanceof PyFile ||
                                           ControlFlowCache.getScope(nonlocalOwner).getGlobals().contains(referencedName);
                        if (!isGlobal) {
                            return nonlocalOwner;
                        }
                    }
                }
                if (!scope.getGlobals().contains(referencedName)) {
                    return scopeOwner;
                }
            }
        }
        return realContext.getContainingFile();
    }

    public static int getRate(@Nullable PsiElement elt, TypeEvalContext context) {
        int rate;
        if (elt instanceof PyTargetExpression && context.maySwitchToAST(elt)) {
            PsiElement parent = elt.getParent();
            if (parent instanceof PyGlobalStatement || parent instanceof PyNonlocalStatement) {
                rate = RatedResolveResult.RATE_LOW;
            }
            else {
                rate = RatedResolveResult.RATE_NORMAL;
            }
        }
        else if (elt instanceof PyImportedNameDefiner ||
                 elt instanceof PyReferenceExpression) {
            rate = RatedResolveResult.RATE_LOW;
        }
        else if (elt != null && !PyiUtil.isInsideStub(elt) && PyiUtil.isOverload(elt, context)) {
            rate = RatedResolveResult.RATE_PY_FILE_OVERLOAD;
        }
        else {
            rate = RatedResolveResult.RATE_NORMAL;
        }
        return rate;
    }

    @Override
    public String getCanonicalText() {
        return getRangeInElement().substring(getElement().getText());
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
        if (element instanceof PsiFileSystemItem) {
            // may be import via alias, so don't check if names match, do simple resolve check instead
            PsiElement resolveResult = resolve();
            if (resolveResult instanceof PyImportedModule) {
                resolveResult = resolveResult.getNavigationElement();
            }
            if (element instanceof PsiDirectory) {
                if (resolveResult instanceof PyFile file) {
                    if (PyUtil.isPackage(file) && file.getContainingDirectory() == element) {
                        return true;
                    }
                }
                else if (resolveResult instanceof PsiDirectory directory) {
                    if (PyUtil.isPackage(directory, null) && directory == element) {
                        return true;
                    }
                }
            }
            return resolveResult == element;
        }
        if (element instanceof PsiNamedElement) {
            String elementName = ((PsiNamedElement)element).getName();
            if ((Objects.equals(myElement.getReferencedName(), elementName) || PyNames.INIT.equals(elementName))) {
                if (!haveQualifiers(element)) {
                    ScopeOwner ourScopeOwner = ScopeUtil.getScopeOwner(getElement());
                    ScopeOwner theirScopeOwner = ScopeUtil.getScopeOwner(element);
                    if (element instanceof PyParameter || element instanceof PyTargetExpression) {
                        // Check if the reference is in the same or inner scope of the element scope, not shadowed by an intermediate declaration
                        if (resolvesToSameLocal(element, elementName, ourScopeOwner, theirScopeOwner)) {
                            return true;
                        }
                    }

                    List<PsiElement> resolveResults = Arrays.stream(multiResolve(false))
                        .filter(r -> !(r instanceof ImplicitResolveResult))
                        .map(r -> r.getElement())
                        .toList();

                    for (PsiElement resolveResult : resolveResults) {
                        if (resolveResult == element) {
                            return true;
                        }

                        if (!haveQualifiers(element) && ourScopeOwner != null && theirScopeOwner != null) {
                            if (resolvesToSameGlobal(element, elementName, ourScopeOwner, theirScopeOwner, resolveResult)) {
                                return true;
                            }
                        }

                        if (resolvesToWrapper(element, resolveResult)) {
                            return true;
                        }
                    }
                }
                if (element instanceof PyExpression expr) {
                    if (PyUtil.isClassAttribute(myElement) && (PyUtil.isClassAttribute(expr) || PyUtil.isInstanceAttribute(expr))) {
                        PyClass c1 = PsiTreeUtil.getParentOfType(element, PyClass.class);
                        PyClass c2 = PsiTreeUtil.getParentOfType(myElement, PyClass.class);
                        TypeEvalContext context = myContext.getTypeEvalContext();
                        if (c1 != null && c2 != null && (c1.isSubclass(c2, context) || c2.isSubclass(c1, context))) {
                            return true;
                        }
                    }
                }
            }
        }

        return PyReferenceCustomTargetChecker.Companion.isReferenceTo(this, element);
    }

    private boolean resolvesToSameLocal(PsiElement element, String elementName, ScopeOwner ourScopeOwner, ScopeOwner theirScopeOwner) {
        PsiElement ourContainer = findContainer(getElement());
        PsiElement theirContainer = findContainer(element);
        if (ourContainer != null) {
            if (ourContainer == theirContainer && ourScopeOwner == theirScopeOwner) {
                return true;
            }
            if (PsiTreeUtil.isAncestor(theirContainer, ourContainer, true)) {
                if (ourContainer instanceof PyComprehensionElement && containsDeclaration((PyComprehensionElement)ourContainer, elementName)) {
                    return false;
                }

                ScopeOwner owner = ourScopeOwner;
                while (owner != theirScopeOwner && owner != null) {
                    if (ControlFlowCache.getScope(owner).containsDeclaration(elementName)) {
                        return false;
                    }
                    owner = ScopeUtil.getScopeOwner(owner);
                }

                return true;
            }
        }
        return false;
    }

    private static @Nullable PsiElement findContainer(PsiElement element) {
        PyElement parent = PsiTreeUtil.getParentOfType(element, ScopeOwner.class, PyComprehensionElement.class);
        if (parent instanceof PyListCompExpression && LanguageLevel.forElement(element).isPython2()) {
            return findContainer(parent);
        }
        return parent;
    }

    private static boolean containsDeclaration(PyComprehensionElement comprehensionElement, String variableName) {
        for (PyComprehensionForComponent forComponent : comprehensionElement.getForComponents()) {
            PyExpression iteratorVariable = forComponent.getIteratorVariable();

            if (iteratorVariable instanceof PyTupleExpression) {
                for (PyExpression variable : (PyTupleExpression)iteratorVariable) {
                    if (variable instanceof PyTargetExpression && variableName.equals(variable.getName())) {
                        return true;
                    }
                }
            }
            else if (iteratorVariable instanceof PyTargetExpression && variableName.equals(iteratorVariable.getName())) {
                return true;
            }
        }

        return false;
    }

    private boolean resolvesToSameGlobal(PsiElement element,
                                         @Nullable String elementName,
                                         ScopeOwner ourScopeOwner,
                                         ScopeOwner theirScopeOwner,
                                         @Nullable PsiElement resolveResult) {
        // Handle situations when there is no top-level declaration for globals and transitive resolve doesn't help
        PsiFile ourFile = getElement().getContainingFile();
        PsiFile theirFile = element.getContainingFile();
        if (ourFile == theirFile) {
            boolean ourIsGlobal = ControlFlowCache.getScope(ourScopeOwner).isGlobal(elementName);
            boolean theirIsGlobal = ControlFlowCache.getScope(theirScopeOwner).isGlobal(elementName);
            if (ourIsGlobal && theirIsGlobal) {
                return true;
            }
        }

        var resolvedScopeOwner = ScopeUtil.getScopeOwner(resolveResult);
        if (resolvedScopeOwner != null && resolveResult.getContainingFile() == theirFile) {
            if (ControlFlowCache.getScope(resolvedScopeOwner).isGlobal(elementName) &&
                ControlFlowCache.getScope(theirScopeOwner).isGlobal(elementName)) {
                return true;
            }
        }

        if (resolvedScopeOwner == ourFile && ControlFlowCache.getScope(theirScopeOwner).isGlobal(elementName)) {
            return true;
        }
        return false;
    }

    protected boolean resolvesToWrapper(PsiElement element, PsiElement resolveResult) {
        if (element instanceof PyFunction &&
            ((PyFunction)element).getContainingClass() != null &&
            resolveResult instanceof PyTargetExpression) {
            PyExpression assignedValue = ((PyTargetExpression)resolveResult).findAssignedValue();
            if (assignedValue instanceof PyCallExpression call) {
                Pair<String, PyFunction> functionPair = PyCallExpressionHelper.interpretAsModifierWrappingCall(call);
                if (functionPair != null && functionPair.second == element) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean haveQualifiers(PsiElement element) {
        if (myElement.isQualified()) {
            return true;
        }
        if (element instanceof PyQualifiedExpression && ((PyQualifiedExpression)element).isQualified()) {
            return true;
        }
        return false;
    }

    @Override
    public Object[] getVariants() {
        List<LookupElement> ret = new ArrayList<>();

        // Use real context here to enable correct completion and resolve in case of PyExpressionCodeFragment!!!
        PyQualifiedElement element = CompletionUtilCore.getOriginalOrSelf(myElement);

        PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(element);
        LanguageLevel languageLevel = LanguageLevel.forElement(myElement);
        CompletionVariantsProcessor processor = new CompletionVariantsProcessor(element, e -> {
            if (builtinCache.isBuiltin(e)) {
                if (e instanceof PyImportElement) {
                    return false;
                }

                String name = e instanceof PyElement pyElement ? pyElement.getName() : null;
                if (PyUtil.getInitialUnderscores(name) == 1) {
                    return false;
                }

                if (languageLevel.isPython2() && PyNames.PRINT.equals(name) &&
                    myElement.getContainingFile() instanceof PyFile pyFile && !pyFile.hasImportFromFuture(FutureFeature.PRINT_FUNCTION)) {
                    return false;
                }
            }
            else if (e instanceof PyTypeParameter) {
                // The control flow does not define a type parameter.
                return true;
            }
            else if (ScopeUtil.getScopeOwner(e) == ScopeUtil.getScopeOwner(element)) {
                return PyDefUseUtil.isDefinedBefore(e, element);
            }
            return true;
        }, null);

        PyResolveUtil.scopeCrawlUp(processor, element, null, null);

        // This method is probably called for completion, so use appropriate context here
        // in a call, include function's arg names
        TypeEvalContext context = TypeEvalContext.codeCompletion(element.getProject(), element.getContainingFile());
        KeywordArgumentCompletionUtil.collectFunctionArgNames(element, ret, context, true);
        // include builtin names
        PyFile builtinsFile = builtinCache.getBuiltinsFile();
        if (builtinsFile != null) {
            PyResolveUtil.scopeCrawlUp(processor, builtinsFile, null, null);
        }

        if (PyUtil.getInitialUnderscores(element.getName()) >= 2) {
            // if we're a normal module, add module's attrs
            if (PyPsiUtils.getRealContext(element).getContainingFile() instanceof PyFile) {
                for (String name : PyModuleType.getPossibleInstanceMembers()) {
                    ret.add(
                        LookupElementBuilder.create(name).withIcon(PlatformIconGroup.nodesField()));
                }
            }

            // if we're inside method, add implicit __class__
            if (!languageLevel.isPython2()) {
                Optional
                    .ofNullable(PsiTreeUtil.getParentOfType(myElement, PyFunction.class))
                    .map(PyFunction::getContainingClass)
                    .ifPresent(pyClass -> ret.add(LookupElementBuilder.create(PyNames.__CLASS__)));
            }
        }

        ret.addAll(getOriginalElements(processor));
        return ret.toArray();
    }

    /**
     * Throws away fake elements used for completion internally.
     */
    protected List<LookupElement> getOriginalElements(CompletionVariantsProcessor processor) {
        List<LookupElement> ret = new ArrayList<>();
        for (LookupElement item : processor.getResultList()) {
            PsiElement e = item.getPsiElement();
            if (e != null) {
                PsiElement original = CompletionUtilCore.getOriginalElement(e);
                if (original == null) {
                    continue;
                }
            }
            ret.add(item);
        }
        return ret;
    }

    @Override
    public @Nullable HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
        if (isBuiltInConstant()) {
            return null;
        }

        HighlightSeverity severity = super.getUnresolvedHighlightSeverity(context);
        if (severity == null) {
            return null;
        }

        boolean isInsideDocString = DocStringUtil.getDocstringInjectionHost(myElement.getContainingFile()) != null;
        return isInsideDocString ? HighlightSeverity.WARNING : severity;
    }

    private boolean isBuiltInConstant() {
        // TODO: generalize
        String name = myElement.getReferencedName();
        return PyNames.NONE.equals(name) || "True".equals(name) || "False".equals(name) || PyNames.DEBUG.equals(name);
    }

    @Override
    public PyQualifiedExpression getElement() {
        return (PyQualifiedExpression)super.getElement();
    }
}
