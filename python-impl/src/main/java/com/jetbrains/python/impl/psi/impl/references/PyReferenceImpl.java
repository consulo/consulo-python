/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.jetbrains.python.impl.psi.impl.references;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.impl.PyImportedModule;
import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.impl.psi.resolve.*;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.impl.refactoring.PyDefUseUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyReferenceResolveProvider;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.access.RequiredWriteAction;
import consulo.document.util.TextRange;
import consulo.language.ast.ASTNode;
import consulo.language.controlFlow.Instruction;
import consulo.language.editor.annotation.HighlightSeverity;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.psi.*;
import consulo.language.psi.resolve.ResolveCache;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.language.util.ProcessingContext;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author yole
 */
public class PyReferenceImpl implements PsiReferenceEx, PsiPolyVariantReference {
    protected final PyQualifiedExpression myElement;
    protected final PyResolveContext myContext;

    public PyReferenceImpl(PyQualifiedExpression element, @Nonnull PyResolveContext context) {
        myElement = element;
        myContext = context;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public TextRange getRangeInElement() {
        ASTNode nameElement = myElement.getNameElement();
        TextRange range = nameElement != null ? nameElement.getTextRange() : myElement.getNode().getTextRange();
        return range.shiftRight(-myElement.getNode().getStartOffset());
    }

    @Override
    @RequiredReadAction
    public PsiElement getElement() {
        return myElement;
    }

    /**
     * Resolves reference to the most obvious point.
     * Imported module names: to module file (or directory for a qualifier).
     * Other identifiers: to most recent definition before this reference.
     * This implementation is cached.
     *
     * @see #resolveInner().
     */
    @Nullable
    @Override
    @RequiredReadAction
    public PsiElement resolve() {
        ResolveResult[] results = multiResolve(false);
        return results.length >= 1 && !(results[0] instanceof ImplicitResolveResult) ? results[0].getElement() : null;
    }

    // it is *not* final so that it can be changed in debug time. if set to false, caching is off
    @SuppressWarnings("FieldCanBeLocal")
    private static boolean USE_CACHE = true;

    /**
     * Resolves reference to possible referred elements.
     * First element is always what resolve() would return.
     * Imported module names: to module file, or {directory, '__init__.py}' for a qualifier.
     * todo Local identifiers: a list of definitions in the most recent compound statement
     * (e.g. <code>if X: a = 1; else: a = 2</code> has two definitions of <code>a</code>.).
     * todo Identifiers not found locally: similar definitions in imported files and built-ins.
     *
     * @see PsiPolyVariantReference#multiResolve(boolean)
     */
    @Nonnull
    @Override
    @RequiredReadAction
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        if (USE_CACHE) {
            ResolveCache cache = ResolveCache.getInstance(getElement().getProject());
            return cache.resolveWithCaching(this, CachingResolver.INSTANCE, false, incompleteCode);
        }
        else {
            return multiResolveInner();
        }
    }

    // sorts and modifies results of resolveInner

    @Nonnull
    private ResolveResult[] multiResolveInner() {
        String referencedName = myElement.getReferencedName();
        if (referencedName == null) {
            return ResolveResult.EMPTY_ARRAY;
        }

        List<RatedResolveResult> targets = resolveInner();
        if (targets.size() == 0) {
            return ResolveResult.EMPTY_ARRAY;
        }

        // change class results to constructor results if there are any
        if (myElement.getParent() instanceof PyCallExpression) { // we're a call
            ListIterator<RatedResolveResult> it = targets.listIterator();
            while (it.hasNext()) {
                RatedResolveResult rrr = it.next();
                if (rrr.getElement() instanceof PyClass cls) {
                    PyFunction init = cls.findMethodByName(PyNames.INIT, false, null);
                    if (init != null) {
                        // replace
                        it.set(rrr.replace(init));
                    }
                    else { // init not found; maybe it's ancestor's
                        for (PyClass ancestor : cls.getAncestorClasses(myContext.getTypeEvalContext())) {
                            init = ancestor.findMethodByName(PyNames.INIT, false, null);
                            if (init != null) {
                                // add to results as low priority
                                it.add(new RatedResolveResult(RatedResolveResult.RATE_LOW, init));
                                break;
                            }
                        }
                    }
                }
            }
        }

        // put everything in a sorting container
        List<RatedResolveResult> ret = RatedResolveResult.sorted(targets);
        return ret.toArray(new ResolveResult[ret.size()]);
    }

    @Nonnull
    private static ResolveResultList resolveToLatestDefs(
        @Nonnull List<Instruction> instructions,
        @Nonnull PsiElement element,
        @Nonnull String name,
        @Nonnull TypeEvalContext context
    ) {
        ResolveResultList ret = new ResolveResultList();
        for (Instruction instruction : instructions) {
            PsiElement definition = instruction.getElement();
            // TODO: This check may slow down resolving, but it is the current solution to the comprehension scopes problem
            if (isInnerComprehension(element, definition)) {
                continue;
            }
            if (definition instanceof PyImportedNameDefiner definer && !(definer instanceof PsiNamedElement)) {
                List<RatedResolveResult> resolvedResults = definer.multiResolveName(name);
                for (RatedResolveResult result : resolvedResults) {
                    PsiElement resolved = result.getElement();
                    ret.add(new ImportedResolveResult(resolved, getRate(resolved, context), definer));
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
            if (e == element) {
                continue;
            }
            if (element instanceof PyTargetExpression && e != null && PyPsiUtils.isBefore(element, e)) {
                continue;
            }
            else {
                results.add(r);
            }
        }

        return results;
    }

    private static boolean isInnerComprehension(PsiElement referenceElement, PsiElement definition) {
        PyComprehensionElement definitionComprehension = PsiTreeUtil.getParentOfType(definition, PyComprehensionElement.class);
        if (definitionComprehension != null && PyUtil.isOwnScopeComprehension(definitionComprehension)) {
            PyComprehensionElement elementComprehension = PsiTreeUtil.getParentOfType(referenceElement, PyComprehensionElement.class);
            if (elementComprehension == null || !PsiTreeUtil.isAncestor(definitionComprehension, elementComprehension, false)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInOwnScopeComprehension(PsiElement uExpr) {
        PyComprehensionElement comprehensionElement = PsiTreeUtil.getParentOfType(uExpr, PyComprehensionElement.class);
        return comprehensionElement != null && PyUtil.isOwnScopeComprehension(comprehensionElement);
    }

    /**
     * Does actual resolution of resolve().
     *
     * @return resolution result.
     * @see #resolve()
     */
    @Nonnull
    protected List<RatedResolveResult> resolveInner() {
        ResolveResultList ret = new ResolveResultList();

        String referencedName = myElement.getReferencedName();
        if (referencedName == null) {
            return ret;
        }

        if (myElement instanceof PyTargetExpression) {
            if (PsiTreeUtil.getParentOfType(myElement, PyComprehensionElement.class) != null) {
                ret.poke(myElement, getRate(myElement, myContext.getTypeEvalContext()));
                return ret;
            }
        }

        // resolve implicit __class__ inside class function
        if (myElement instanceof PyReferenceExpression
            && PyNames.__CLASS__.equals(referencedName)
            && LanguageLevel.forElement(myElement).isAtLeast(LanguageLevel.PYTHON30)) {
            PyFunction containingFunction = PsiTreeUtil.getParentOfType(myElement, PyFunction.class);

            if (containingFunction != null) {
                PyClass containingClass = containingFunction.getContainingClass();

                if (containingClass != null) {
                    PyResolveProcessor processor = new PyResolveProcessor(referencedName);
                    PyResolveUtil.scopeCrawlUp(processor, myElement, referencedName, containingFunction);

                    if (processor.getElements().isEmpty()) {
                        ret.add(new RatedResolveResult(RatedResolveResult.RATE_NORMAL, containingClass));
                        return ret;
                    }
                }
            }
        }

        // here we have an unqualified expr. it may be defined:
        // ...in current file
        PyResolveProcessor processor = new PyResolveProcessor(referencedName);

        // Use real context here to enable correct completion and resolve in case of PyExpressionCodeFragment
        PsiElement realContext = PyPsiUtils.getRealContext(myElement);

        PsiElement roof = findResolveRoof(referencedName, realContext);
        PyResolveUtil.scopeCrawlUp(processor, myElement, referencedName, roof);

        List<RatedResolveResult> resultsFromProcessor = getResultsFromProcessor(referencedName, processor, realContext, roof);

        // resolve to module __doc__
        if (resultsFromProcessor.isEmpty() && referencedName.equals(PyNames.DOC)) {
            ret.addAll(Optional.ofNullable(PyBuiltinCache.getInstance(myElement).getObjectType())
                .map(type -> type.resolveMember(referencedName, myElement, AccessDirection.of(myElement), myContext))
                .orElse(Collections.emptyList()));

            return ret;
        }

        return resultsFromProcessor;
    }

    protected List<RatedResolveResult> getResultsFromProcessor(
        @Nonnull String referencedName,
        @Nonnull PyResolveProcessor processor,
        @Nullable PsiElement realContext,
        @Nullable PsiElement resolveRoof
    ) {
        boolean unreachableLocalDeclaration = false;
        boolean resolveInParentScope = false;
        ResolveResultList resultList = new ResolveResultList();
        ScopeOwner referenceOwner = ScopeUtil.getScopeOwner(realContext);
        TypeEvalContext typeEvalContext = myContext.getTypeEvalContext();
        ScopeOwner resolvedOwner = processor.getOwner();

        if (resolvedOwner != null && !processor.getResults().isEmpty()) {
            Collection<PsiElement> resolvedElements = processor.getElements();
            Scope resolvedScope = ControlFlowCache.getScope(resolvedOwner);

            if (!resolvedScope.isGlobal(referencedName)) {
                if (resolvedOwner == referenceOwner) {
                    List<Instruction> instructions =
                        PyDefUseUtil.getLatestDefs(resolvedOwner, referencedName, realContext, false, true);
                    // TODO: Use the results from the processor as a cache for resolving to latest defs
                    ResolveResultList latestDefs = resolveToLatestDefs(instructions, realContext, referencedName, typeEvalContext);
                    if (!latestDefs.isEmpty()) {
                        return latestDefs;
                    }
                    else if (resolvedOwner instanceof PyClass || instructions.isEmpty() && allInOwnScopeComprehensions(resolvedElements)) {
                        resolveInParentScope = true;
                    }
                    else {
                        unreachableLocalDeclaration = true;
                    }
                }
                else if (referenceOwner != null) {
                    Scope referenceScope = ControlFlowCache.getScope(referenceOwner);
                    if (referenceScope.containsDeclaration(referencedName)) {
                        unreachableLocalDeclaration = true;
                    }
                }
            }
        }

        // TODO: Try resolve to latest defs for outer scopes starting from the last element in CFG (=> no need for a special rate for globals)

        if (!unreachableLocalDeclaration) {
            if (resolveInParentScope) {
                processor = new PyResolveProcessor(referencedName);
                resolvedOwner = ScopeUtil.getScopeOwner(resolvedOwner);
                if (resolvedOwner != null) {
                    PyResolveUtil.scopeCrawlUp(processor, resolvedOwner, referencedName, resolveRoof);
                }
            }

            for (Map.Entry<PsiElement, PyImportedNameDefiner> entry : processor.getResults().entrySet()) {
                PsiElement resolved = entry.getKey();
                PyImportedNameDefiner definer = entry.getValue();
                if (resolved != null) {
                    if (typeEvalContext.maySwitchToAST(resolved) && isInnerComprehension(realContext, resolved)) {
                        continue;
                    }
                    if (resolved == referenceOwner && referenceOwner instanceof PyClass) {
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

    private static boolean allInOwnScopeComprehensions(@Nonnull Collection<PsiElement> elements) {
        for (PsiElement element : elements) {
            if (!isInOwnScopeComprehension(element)) {
                return false;
            }
        }
        return true;
    }

    @Nonnull
    private ResolveResultList resolveByReferenceResolveProviders() {
        ResolveResultList results = new ResolveResultList();
        myElement.getApplication().getExtensionPoint(PyReferenceResolveProvider.class)
            .forEach(provider -> results.addAll(provider.resolveName(myElement)));
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
            if (one instanceof PyClass pyClass) {
                PyArgumentList superClassExpressionList = pyClass.getSuperClassExpressionList();
                if (superClassExpressionList == null || !PsiTreeUtil.isAncestor(superClassExpressionList, myElement, false)) {
                    return one;
                }
            }
        }

        if (myElement instanceof PyTargetExpression) {
            ScopeOwner scopeOwner = PsiTreeUtil.getParentOfType(myElement, ScopeOwner.class);
            Scope scope;
            if (scopeOwner != null) {
                scope = ControlFlowCache.getScope(scopeOwner);
                String name = myElement.getName();
                if (scope.isNonlocal(name)) {
                    ScopeOwner nonLocalOwner = ScopeUtil.getDeclarationScopeOwner(myElement, referencedName);
                    if (nonLocalOwner != null && !(nonLocalOwner instanceof PyFile)) {
                        return nonLocalOwner;
                    }
                }
                if (!scope.isGlobal(name)) {
                    return scopeOwner;
                }
            }
        }
        return realContext.getContainingFile();
    }

    public static int getRate(PsiElement elt, @Nonnull TypeEvalContext context) {
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
        else if (elt instanceof PyImportedNameDefiner || elt instanceof PyReferenceExpression) {
            rate = RatedResolveResult.RATE_LOW;
        }
        else if (elt instanceof PyFile) {
            rate = RatedResolveResult.RATE_HIGH;
        }
        else {
            rate = RatedResolveResult.RATE_NORMAL;
        }
        return rate;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public String getCanonicalText() {
        return getRangeInElement().substring(getElement().getText());
    }

    @Override
    @RequiredWriteAction
    public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
        ASTNode nameElement = myElement.getNameElement();
        newElementName = StringUtil.trimEnd(newElementName, PyNames.DOT_PY);
        if (nameElement != null && PyNames.isIdentifier(newElementName)) {
            ASTNode newNameElement = PyUtil.createNewName(myElement, newElementName);
            myElement.getNode().replaceChild(nameElement, newNameElement);
        }
        return myElement;
    }

    @Nullable
    @Override
    @RequiredWriteAction
    public PsiElement bindToElement(@Nonnull PsiElement element) throws IncorrectOperationException {
        return null;
    }

    @Override
    @RequiredReadAction
    public boolean isReferenceTo(PsiElement element) {
        if (element instanceof PsiFileSystemItem) {
            // may be import via alias, so don't check if names match, do simple resolve check instead
            PsiElement resolveResult = resolve();
            if (resolveResult instanceof PyImportedModule importedModule) {
                resolveResult = importedModule.getNavigationElement();
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
        if (element instanceof PsiNamedElement namedElem) {
            String elementName = namedElem.getName();
            if ((Objects.equals(myElement.getReferencedName(), elementName) || PyNames.INIT.equals(elementName))) {
                if (!haveQualifiers(namedElem)) {
                    ScopeOwner ourScopeOwner = ScopeUtil.getScopeOwner(getElement());
                    ScopeOwner theirScopeOwner = ScopeUtil.getScopeOwner(namedElem);
                    if (namedElem instanceof PyParameter || namedElem instanceof PyTargetExpression) {
                        // Check if the reference is in the same or inner scope of the element scope, not shadowed by an intermediate declaration
                        if (resolvesToSameLocal(namedElem, elementName, ourScopeOwner, theirScopeOwner)) {
                            return true;
                        }
                    }

                    PsiElement resolveResult = resolve();
                    if (resolveResult == namedElem) {
                        return true;
                    }

                    // we shadow their name or they shadow ours (PY-6241)
                    if (resolveResult instanceof PsiNamedElement
                        && resolveResult instanceof ScopeOwner
                        && namedElem instanceof ScopeOwner
                        && theirScopeOwner == ScopeUtil.getScopeOwner(resolveResult)) {
                        return true;
                    }

                    if (!haveQualifiers(namedElem) && ourScopeOwner != null && theirScopeOwner != null) {
                        if (resolvesToSameGlobal(namedElem, elementName, ourScopeOwner, theirScopeOwner, resolveResult)) {
                            return true;
                        }
                    }

                    if (resolvesToWrapper(namedElem, resolveResult)) {
                        return true;
                    }
                }
                if (namedElem instanceof PyExpression expr
                    && PyUtil.isClassAttribute(myElement)
                    && (PyUtil.isClassAttribute(expr) || PyUtil.isInstanceAttribute(expr))) {
                    PyClass c1 = PsiTreeUtil.getParentOfType(namedElem, PyClass.class);
                    PyClass c2 = PsiTreeUtil.getParentOfType(myElement, PyClass.class);
                    TypeEvalContext context = myContext.getTypeEvalContext();
                    if (c1 != null && c2 != null && (c1.isSubclass(c2, context) || c2.isSubclass(c1, context))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @RequiredReadAction
    private boolean resolvesToSameLocal(PsiElement element, String elementName, ScopeOwner ourScopeOwner, ScopeOwner theirScopeOwner) {
        PsiElement ourContainer = findContainer(getElement());
        PsiElement theirContainer = findContainer(element);
        if (ourContainer != null) {
            if (ourContainer == theirContainer) {
                return true;
            }
            if (PsiTreeUtil.isAncestor(theirContainer, ourContainer, true)) {
                if (ourContainer instanceof PyComprehensionElement comprehensionElem
                    && containsDeclaration(comprehensionElem, elementName)) {
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

    @Nullable
    private static PsiElement findContainer(@Nonnull PsiElement element) {
        PyElement parent = PsiTreeUtil.getParentOfType(element, ScopeOwner.class, PyComprehensionElement.class);
        if (parent instanceof PyListCompExpression listCompExpr && LanguageLevel.forElement(element).isOlderThan(LanguageLevel.PYTHON30)) {
            return findContainer(listCompExpr);
        }
        return parent;
    }

    private static boolean containsDeclaration(@Nonnull PyComprehensionElement comprehensionElement, @Nonnull String variableName) {
        for (PyComprehensionForComponent forComponent : comprehensionElement.getForComponents()) {
            PyExpression iteratorVariable = forComponent.getIteratorVariable();

            if (iteratorVariable instanceof PyTupleExpression tuple) {
                for (PyExpression variable : tuple) {
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

    @RequiredReadAction
    private boolean resolvesToSameGlobal(
        PsiElement element,
        String elementName,
        ScopeOwner ourScopeOwner,
        ScopeOwner theirScopeOwner,
        PsiElement resolveResult
    ) {
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
        return ScopeUtil.getScopeOwner(resolveResult) == ourFile
            && ControlFlowCache.getScope(theirScopeOwner).isGlobal(elementName);
    }

    protected boolean resolvesToWrapper(PsiElement element, PsiElement resolveResult) {
        if (element instanceof PyFunction function
            && function.getContainingClass() != null
            && resolveResult instanceof PyTargetExpression targetExpr
            && targetExpr.findAssignedValue() instanceof PyCallExpression call) {

            Pair<String, PyFunction> functionPair = PyCallExpressionHelper.interpretAsModifierWrappingCall(call, myElement);
            if (functionPair != null && functionPair.second == element) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("RedundantIfStatement")
    private boolean haveQualifiers(PsiElement element) {
        if (myElement.isQualified()) {
            return true;
        }
        if (element instanceof PyQualifiedExpression qualifiedExpr && qualifiedExpr.isQualified()) {
            return true;
        }
        return false;
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public Object[] getVariants() {
        List<LookupElement> ret = new ArrayList<>();

        // Use real context here to enable correct completion and resolve in case of PyExpressionCodeFragment!!!
        PsiElement originalElement = CompletionUtilCore.getOriginalElement(myElement);
        PyQualifiedExpression element = originalElement instanceof PyQualifiedExpression qualifiedExpr ? qualifiedExpr : myElement;
        PsiElement realContext = PyPsiUtils.getRealContext(element);

        // include our own names
        int underscores = PyUtil.getInitialUnderscores(element.getName());
        CompletionVariantsProcessor processor = new CompletionVariantsProcessor(element);
        ScopeOwner owner = realContext instanceof ScopeOwner scopeOwner ? scopeOwner : ScopeUtil.getScopeOwner(realContext);
        if (owner != null) {
            PyResolveUtil.scopeCrawlUp(processor, owner, null, null);
        }

        // This method is probably called for completion, so use appropriate context here
        // in a call, include function's arg names
        KeywordArgumentCompletionUtil.collectFunctionArgNames(
            element,
            ret,
            TypeEvalContext.codeCompletion(
                element.getProject(),
                element.getContainingFile()
            )
        );

        // include builtin names
        PyFile builtInsFile = PyBuiltinCache.getInstance(element).getBuiltinsFile();
        if (builtInsFile != null) {
            PyResolveUtil.scopeCrawlUp(processor, builtInsFile, null, null);
        }

        if (underscores >= 2) {
            // if we're a normal module, add module's attrs
            PsiFile f = realContext.getContainingFile();
            if (f instanceof PyFile) {
                for (String name : PyModuleType.getPossibleInstanceMembers()) {
                    ret.add(LookupElementBuilder.create(name).withIcon(PlatformIconGroup.nodesField()));
                }
            }
        }

        ret.addAll(getOriginalElements(processor));
        return ret.toArray();
    }

    /**
     * Throws away fake elements used for completion internally.
     */
    protected List<LookupElement> getOriginalElements(@Nonnull CompletionVariantsProcessor processor) {
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
    @RequiredReadAction
    public boolean isSoft() {
        return false;
    }

    @Override
    public HighlightSeverity getUnresolvedHighlightSeverity(TypeEvalContext context) {
        if (isBuiltInConstant()) {
            return null;
        }
        PyExpression qualifier = myElement.getQualifier();
        if (qualifier == null) {
            return HighlightSeverity.ERROR;
        }
        if (context.getType(qualifier) != null) {
            return HighlightSeverity.WARNING;
        }
        return null;
    }

    private boolean isBuiltInConstant() {
        // TODO: generalize
        String name = myElement.getReferencedName();
        return PyNames.NONE.equals(name) || "True".equals(name) || "False".equals(name);
    }

    @Override
    @Nullable
    public String getUnresolvedDescription() {
        return null;
    }


    // our very own caching resolver

    private static class CachingResolver implements ResolveCache.PolyVariantResolver<PyReferenceImpl> {
        public static CachingResolver INSTANCE = new CachingResolver();
        private ThreadLocal<AtomicInteger> myNesting = new ThreadLocal<>() {
            @Override
            protected AtomicInteger initialValue() {
                return new AtomicInteger();
            }
        };

        private static final int MAX_NESTING_LEVEL = 30;

        @Override
        @Nonnull
        public ResolveResult[] resolve(@Nonnull PyReferenceImpl ref, boolean incompleteCode) {
            if (myNesting.get().getAndIncrement() >= MAX_NESTING_LEVEL) {
                System.out.println("Stack overflow pending");
            }
            try {
                return ref.multiResolveInner();
            }
            finally {
                myNesting.get().getAndDecrement();
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        PyReferenceImpl that = (PyReferenceImpl) o;

        return myElement.equals(that.myElement)
            && myContext.equals(that.myContext);
    }

    @Override
    public int hashCode() {
        return myElement.hashCode();
    }

    protected static Object[] getTypeCompletionVariants(PyExpression pyExpression, PyType type) {
        ProcessingContext context = new ProcessingContext();
        context.put(PyType.CTX_NAMES, new HashSet<>());
        return type.getCompletionVariants(pyExpression.getName(), pyExpression, context);
    }
}
