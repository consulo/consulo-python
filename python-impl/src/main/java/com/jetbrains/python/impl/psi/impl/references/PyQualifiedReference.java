// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl.references;

import consulo.language.editor.completion.CompletionInitializationContext;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.AutoCompletionPolicy;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.logging.Logger;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.ObjectUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.completion.PyClassNameCompletionContributor;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyRecursiveElementVisitor;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.impl.psi.impl.references.hasattr.PyHasAttrHelper;
import com.jetbrains.python.impl.psi.resolve.ImplicitResolveResult;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndexInsensitive;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.impl.psi.types.PyStructuralType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.pyi.PyiUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

public class PyQualifiedReference extends PyReferenceImpl {
    private static final Logger LOG = Logger.getInstance(PyQualifiedReference.class);

    public PyQualifiedReference(PyQualifiedExpression element, PyResolveContext context) {
        super(element, context);
    }

    @Override
    protected List<RatedResolveResult> resolveInner() {
        PyPsiUtils.assertValid(myElement);
        ResolveResultList ret = new ResolveResultList();

        String referencedName = myElement.getReferencedName();
        if (referencedName == null) {
            return ret;
        }

        PyExpression qualifier = myElement.getQualifier();
        PyPsiUtils.assertValid(qualifier);
        if (qualifier == null) {
            return ret;
        }

        // regular attributes
        PyType qualifierType = myContext.getTypeEvalContext().getType(qualifier);
        // is it a class-private name qualified by a different class?
        if (PyUtil.isClassPrivateName(referencedName) && qualifierType instanceof PyClassType) {
            if (isOtherClassQualifying(qualifier, (PyClassType)qualifierType)) {
                return Collections.emptyList();
            }
        }
        //
        if (qualifierType != null) {
            qualifierType.assertValid("qualifier: " + qualifier);
            // resolve within the type proper
            AccessDirection ctx = AccessDirection.of(myElement);
            List<? extends RatedResolveResult> membersOfQualifier = qualifierType.resolveMember(referencedName, qualifier, ctx, myContext);
            if (membersOfQualifier == null) {
                return ret; // qualifier is positive that such name cannot exist in it
            }
            ret.addAll(membersOfQualifier);
        }

        if (myContext.getTypeEvalContext().maySwitchToAST(myElement)) {
            // look for assignment of this attribute in containing function
            // See PyResolveTest#testAttributeAssignedNearby for an example
            if (qualifier instanceof PyQualifiedExpression && ret.isEmpty()) {
                if (addAssignedAttributes(ret, referencedName, (PyQualifiedExpression)qualifier)) {
                    return ret;
                }
            }
        }

        if ((PyTypeChecker.isUnknown(qualifierType, myContext.getTypeEvalContext()) ||
             (qualifierType instanceof PyStructuralType && ((PyStructuralType)qualifierType).isInferredFromUsages())) &&
            myContext.allowImplicits() && PyCallExpressionHelper.canQualifyAnImplicitName(qualifier)) {
            PyResolveUtil.addImplicitResolveResults(referencedName, ret, getElement());
        }

        // special case of __doc__
        if ("__doc__".equals(referencedName)) {
            addDocReference(ret, qualifier, qualifierType);
        }

        return ret;
    }

    private static boolean isOtherClassQualifying(PyExpression qualifier, PyClassType qualifierType) {
        List<? extends PsiElement> match = PyUtil.searchForWrappingMethod(qualifier, true);
        if (match == null) {
            return true;
        }
        if (match.size() > 1) {
            PyClass ourClass = PyiUtil.getOriginalElementOrLeaveAsIs(qualifierType.getPyClass(), PyClass.class);
            PsiElement theirClass = CompletionUtilCore.getOriginalOrSelf(match.get(match.size() - 1));
            if (ourClass != theirClass) {
                return true;
            }
        }
        return false;
    }

    private static boolean addAssignedAttributes(ResolveResultList ret,
                                                 String referencedName,
                                                 PyQualifiedExpression qualifier) {
        QualifiedName qName = qualifier.asQualifiedName();
        if (qName == null) {
            return false;
        }
        for (PyExpression ex : collectAssignedAttributes(qName, qualifier)) {
            if (referencedName.equals(ex.getName()) && !PyUtil.isInstanceAttribute(ex)) {
                ret.poke(ex, RatedResolveResult.RATE_NORMAL);
                return true;
            }
        }
        return false;
    }

    private void addDocReference(ResolveResultList ret, PyExpression qualifier, PyType qualifierType) {
        PsiElement docstring = null;
        if (qualifierType instanceof PyClassType classType) {
            PyClass qualClass = classType.getPyClass();
            docstring = qualClass.getDocStringExpression();
        }
        else if (qualifierType instanceof PyModuleType) {
            PyFile qualModule = ((PyModuleType)qualifierType).getModule();
            docstring = qualModule.getDocStringExpression();
        }
        else if (qualifier instanceof PyReferenceExpression) {
            PsiElement qual_object = ((PyReferenceExpression)qualifier).getReference(myContext).resolve();
            if (qual_object instanceof PyDocStringOwner) {
                docstring = ((PyDocStringOwner)qual_object).getDocStringExpression();
            }
        }
        ret.poke(docstring, RatedResolveResult.RATE_HIGH);
    }

    @Override
    public Object[] getVariants() {
        PyExpression qualifier = myElement.getQualifier();
        if (qualifier != null) {
            qualifier = CompletionUtilCore.getOriginalOrSelf(qualifier);
        }
        if (qualifier == null) {
            return EMPTY_ARRAY;
        }
        PyQualifiedExpression element = CompletionUtilCore.getOriginalOrSelf(getElement());

        PyType qualifierType = TypeEvalContext.codeCompletion(element.getProject(), element.getContainingFile()).getType(qualifier);
        ProcessingContext ctx = new ProcessingContext();
        Set<String> namesAlready = new HashSet<>();
        ctx.put(PyType.CTX_NAMES, namesAlready);
        Collection<Object> variants = new ArrayList<>();
        if (!isUnknown(qualifierType)) {
            if (qualifierType instanceof PyStructuralType && ((PyStructuralType)qualifierType).isInferredFromUsages()) {
                PyClassType guessedType = guessClassTypeByName();
                if (guessedType != null) {
                    Collections.addAll(variants, guessedType.getCompletionVariants(myElement.getName(), myElement, ctx));
                }
            }
            if (qualifier instanceof PyQualifiedExpression qualifierExpression) {
                QualifiedName qualifiedName = qualifierExpression.asQualifiedName();
                if (qualifiedName != null) {
                    Collection<PyTargetExpression> attrs = collectAssignedAttributes(qualifiedName, qualifier);
                    for (PyTargetExpression expression : attrs) {
                        String name = expression.getName();
                        if (name != null && name.endsWith(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED)) {
                            continue;
                        }
                        if (qualifierType instanceof PyClassType && name != null) {
                            variants.add(LookupElementBuilder.createWithSmartPointer(name, expression)
                                           .withTypeText(qualifierType.getName())
                                           .withIcon(PlatformIconGroup.nodesField()));
                            namesAlready.add(name);
                        }
                    }
                }
                Collections.addAll(variants, qualifierType.getCompletionVariants(element.getName(), element, ctx));
            }
            else {
                return qualifierType.getCompletionVariants(element.getName(), element, ctx);
            }
        }
        else {
            PyClassType guessedType = guessClassTypeByName();
            if (guessedType != null) {
                ContainerUtil.addAll(variants, ContainerUtil.map(guessedType.getCompletionVariants(myElement.getName(), myElement, ctx), entry ->
                    entry instanceof LookupElementBuilder e
                        ? e.withInsertHandler(new InsertHandler<LookupElement>() {
                            @Override
                            public void handleInsert(InsertionContext context, LookupElement item) {
                                PyClassNameCompletionContributor.InsertHandlers.addImportForLookupElement(context, item, context.getTailOffset() - 1);
                                if (e.getInsertHandler() != null) {
                                    PsiDocumentManager.getInstance(context.getProject()).doPostponedOperationsAndUnblockDocument(context.getDocument());
                                    e.handleInsert(context);
                                }
                            }
                        })
                        : element)
                );
            }
            if (qualifier instanceof PyReferenceExpression) {
                Collections.addAll(variants, collectSeenMembers(qualifier.getText(), ctx));
            }
        }

        PyHasAttrHelper.INSTANCE.getNamesFromHasAttrs(element, qualifier).stream()
            .filter(it -> namesAlready.add(it))
            .forEach(variants::add);

        return variants.toArray();
    }

    private @Nullable PyClassType guessClassTypeByName() {
        PyExpression qualifierElement = myElement.getQualifier();
        if (qualifierElement instanceof PyReferenceExpression qualifier) {
            String className = qualifier.getReferencedName();
            if (className != null) {
                Collection<PyClass> classes = PyClassNameIndexInsensitive.find(className, getElement().getProject());
                classes = filterByImports(classes, myElement.getContainingFile());
                if (classes.size() == 1) {
                    return new PyClassTypeImpl(classes.iterator().next(), true);
                }
            }
        }
        return null;
    }

    private static Collection<PyClass> filterByImports(Collection<PyClass> classes, PsiFile containingFile) {
        if (classes.size() <= 1) {
            return classes;
        }
        List<PyClass> result = new ArrayList<>();
        for (PyClass pyClass : classes) {
            if (pyClass.getContainingFile() == containingFile) {
                result.add(pyClass);
            }
            else {
                PsiElement exportedClass = ((PyFile)containingFile).getElementNamed(pyClass.getName());
                if (exportedClass == pyClass) {
                    result.add(pyClass);
                }
            }
        }
        return result;
    }

    private Object[] collectSeenMembers(String text, ProcessingContext context) {
        Set<String> members = new HashSet<>();
        myElement.getContainingFile().accept(new PyRecursiveElementVisitor() {
            @Override
            public void visitPyReferenceExpression(PyReferenceExpression node) {
                super.visitPyReferenceExpression(node);
                visitPyQualifiedExpression(node);
            }

            @Override
            public void visitPyTargetExpression(PyTargetExpression node) {
                super.visitPyTargetExpression(node);
                visitPyQualifiedExpression(node);
            }

            private void visitPyQualifiedExpression(PyQualifiedExpression node) {
                if (node != myElement) {
                    PyExpression qualifier = node.getQualifier();
                    if (qualifier != null && qualifier.getText().equals(text)) {
                        String refName = node.getReferencedName();
                        if (refName != null) {
                            members.add(refName);
                        }
                    }
                }
            }
        });
        List<LookupElement> results = new ArrayList<>(members.size());
        Set<String> namesAlready = visitedNames(context);
        for (String member : members) {
            results.add(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(LookupElementBuilder.create(member)));
            namesAlready.add(member);
        }
        return ArrayUtil.toObjectArray(results);
    }

    private static Set<String> visitedNames(ProcessingContext context) {
        Set<String> names = context.get(PyType.CTX_NAMES);
        if (names == null) {
            names = new HashSet<>();
            context.put(PyType.CTX_NAMES, names);
        }
        return names;
    }

    /**
     * Returns expressions accessible from scope of "anchor" with names that start with  provided "qualifierQName".
     * Can be used for completion.
     */
    public static Collection<PyTargetExpression> collectAssignedAttributes(QualifiedName qualifierQName,
                                                                                    PsiElement anchor) {
        Set<String> names = new HashSet<>();
        List<PyTargetExpression> results = new ArrayList<>();
        for (ScopeOwner owner = ScopeUtil.getScopeOwner(anchor); owner != null; owner = ScopeUtil.getScopeOwner(owner)) {
            Scope scope = ControlFlowCache.getScope(owner);
            for (PyTargetExpression target : scope.getTargetExpressions()) {
                QualifiedName targetQName = target.asQualifiedName();
                if (targetQName != null) {
                    if (targetQName.getComponentCount() == qualifierQName.getComponentCount() + 1 && targetQName.matchesPrefix(qualifierQName)) {
                        String name = target.getName();
                        if (!names.contains(name)) {
                            names.add(name);
                            results.add(target);
                        }
                    }
                }
            }
        }
        return results;
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
        // performance: a qualified reference can never resolve to a local variable or parameter
        if (isLocalScope(element)) {
            return false;
        }
        String referencedName = myElement.getReferencedName();
        PyResolveContext resolveContext = myContext.withoutImplicits();
        // Guess type eval context origin for switching to local dataflow and return type analysis
        if (resolveContext.getTypeEvalContext().getOrigin() == null) {
            PsiFile containingFile = myElement.getContainingFile();
            if (containingFile instanceof StubBasedPsiElement) {
                assert ((StubBasedPsiElement<?>)containingFile).getStub() == null : "Stub origin for type eval context in isReferenceTo()";
            }
            TypeEvalContext context = TypeEvalContext.codeAnalysis(containingFile.getProject(), containingFile);
            resolveContext = resolveContext.withTypeEvalContext(context);
        }
        PyElement pyElement = ObjectUtil.tryCast(element, PyElement.class);
        if (pyElement == null) {
            return false;
        }
        if (Objects.equals(referencedName, pyElement.getName()) && !PyUtil.isConstructorLikeMethod(element)) {
            PyExpression qualifier = myElement.getQualifier();
            if (qualifier != null) {
                PyType qualifierType = resolveContext.getTypeEvalContext().getType(qualifier);
                if (qualifierType == null ||
                    (qualifierType instanceof PyStructuralType && ((PyStructuralType)qualifierType).isInferredFromUsages())) {
                    return true;
                }
            }
        }
        for (ResolveResult result : copyWithResolveContext(resolveContext).multiResolve(false)) {
            LOG.assertTrue(!(result instanceof ImplicitResolveResult));
            PsiElement resolveResult = result.getElement();
            if (isResolvedToResult(element, resolveResult)) {
                return true;
            }
        }

        return false;
    }

    protected PyQualifiedReference copyWithResolveContext(PyResolveContext context) {
        return new PyQualifiedReference(getElement(), context);
    }

    private boolean isResolvedToResult(PsiElement element, PsiElement resolveResult) {
        if (resolveResult instanceof PyImportedModule) {
            resolveResult = resolveResult.getNavigationElement();
        }
        if (element instanceof PsiDirectory && resolveResult instanceof PyFile &&
            PyNames.INIT_DOT_PY.equals(((PyFile)resolveResult).getName()) && ((PyFile)resolveResult).getContainingDirectory() == element) {
            return true;
        }
        if (resolveResult == element) {
            return true;
        }
        if (resolveResult instanceof PyTargetExpression && PyUtil.isAttribute((PyTargetExpression)resolveResult) &&
            element instanceof PyTargetExpression && PyUtil.isAttribute((PyTargetExpression)element) &&
            Objects.equals(((PyTargetExpression)resolveResult).getReferencedName(), ((PyTargetExpression)element).getReferencedName())) {
            PyClass aClass = PsiTreeUtil.getParentOfType(resolveResult, PyClass.class);
            PyClass bClass = PsiTreeUtil.getParentOfType(element, PyClass.class);

            if (aClass != null && bClass != null && bClass.isSubclass(aClass, myContext.getTypeEvalContext())) {
                return true;
            }
        }

        if (resolvesToWrapper(element, resolveResult)) {
            return true;
        }
        return false;
    }

    private static boolean isLocalScope(PsiElement element) {
        if (element instanceof PyParameter) {
            return true;
        }
        if (element instanceof PyTargetExpression target) {
            return !target.isQualified() && ScopeUtil.getScopeOwner(target) instanceof PyFunction;
        }
        return false;
    }

    @Override
    public String toString() {
        return "PyQualifiedReference(" + myElement + "," + myContext + ")";
    }
}
