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
package com.jetbrains.python.impl.psi.impl.references;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.controlflow.ControlFlowCache;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.Scope;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyImportedModule;
import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.impl.psi.resolve.ImplicitResolveResult;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.search.PyProjectScopeBuilder;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndexInsensitive;
import com.jetbrains.python.impl.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.impl.psi.stubs.PyInstanceAttributeIndex;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.impl.psi.types.PyStructuralType;
import com.jetbrains.python.impl.psi.types.PyTypeChecker;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.language.editor.completion.AutoCompletionPolicy;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.psi.*;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ProcessingContext;
import consulo.logging.Logger;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import consulo.project.content.scope.ProjectScopes;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.*;

/**
 * @author yole
 */
public class PyQualifiedReference extends PyReferenceImpl {
    private static final Logger LOG = Logger.getInstance(PyQualifiedReference.class);

    public PyQualifiedReference(PyQualifiedExpression element, PyResolveContext context) {
        super(element, context);
    }

    @Nonnull
    @Override
    @RequiredReadAction
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
        if (PyUtil.isClassPrivateName(referencedName) && qualifierType instanceof PyClassType classType) {
            if (isOtherClassQualifying(qualifier, classType)) {
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

        // look for assignment of this attribute in containing function
        if (qualifier instanceof PyQualifiedExpression qualifiedExpr && ret.isEmpty()) {
            if (addAssignedAttributes(ret, referencedName, qualifiedExpr)) {
                return ret;
            }
        }

        if ((PyTypeChecker.isUnknown(qualifierType)
            || (qualifierType instanceof PyStructuralType structuralType && structuralType.isInferredFromUsages()))
            && myContext.allowImplicits() && canQualifyAnImplicitName(qualifier)) {
            addImplicitResolveResults(referencedName, ret);
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
            PyClass ourClass = qualifierType.getPyClass();
            PsiElement theirClass = CompletionUtilCore.getOriginalOrSelf(match.get(match.size() - 1));
            if (ourClass != theirClass) {
                return true;
            }
        }
        return false;
    }

    private void addImplicitResolveResults(String referencedName, ResolveResultList ret) {
        Project project = myElement.getProject();
        GlobalSearchScope scope = PyProjectScopeBuilder.excludeSdkTestsScope(project);
        Collection functions = PyFunctionNameIndex.find(referencedName, project, scope);
        List<QualifiedName> imports;
        if (myElement.getContainingFile() instanceof PyFile containingFile) {
            imports = collectImports(containingFile);
        }
        else {
            imports = Collections.emptyList();
        }
        for (Object function : functions) {
            if (!(function instanceof PyFunction pyFunction)) {
                break;
            }
            if (pyFunction.getContainingClass() != null) {
                ret.add(new ImplicitResolveResult(pyFunction, getImplicitResultRate(pyFunction, imports)));
            }
        }

        Collection attributes = PyInstanceAttributeIndex.find(referencedName, project, scope);
        for (Object attribute : attributes) {
            if (!(attribute instanceof PyTargetExpression targetExpr)) {
                break;
            }
            ret.add(new ImplicitResolveResult(targetExpr, getImplicitResultRate(targetExpr, imports)));
        }
    }

    private static List<QualifiedName> collectImports(PyFile containingFile) {
        List<QualifiedName> imports = new ArrayList<>();
        for (PyFromImportStatement anImport : containingFile.getFromImports()) {
            QualifiedName source = anImport.getImportSourceQName();
            if (source != null) {
                imports.add(source);
            }
        }
        for (PyImportElement importElement : containingFile.getImportTargets()) {
            QualifiedName qName = importElement.getImportedQName();
            if (qName != null) {
                imports.add(qName.removeLastComponent());
            }
        }
        return imports;
    }

    private int getImplicitResultRate(PyElement target, List<QualifiedName> imports) {
        int rate = RatedResolveResult.RATE_LOW;
        if (target.getContainingFile() == myElement.getContainingFile()) {
            rate += 200;
        }
        else {
            VirtualFile vFile = target.getContainingFile().getVirtualFile();
            if (vFile != null) {
                if (ProjectScopes.getProjectScope(myElement.getProject()).contains(vFile)) {
                    rate += 80;
                }
                QualifiedName qName = QualifiedNameFinder.findShortestImportableQName(myElement, vFile);
                if (qName != null && imports.contains(qName)) {
                    rate += 70;
                }
            }
        }
        if (myElement.getParent() instanceof PyCallExpression) {
            if (target instanceof PyFunction) {
                rate += 50;
            }
        }
        else if (!(target instanceof PyFunction)) {
            rate += 50;
        }
        return rate;
    }

    @RequiredReadAction
    private static boolean canQualifyAnImplicitName(@Nonnull PyExpression qualifier) {
        if (qualifier instanceof PyCallExpression call) {
            PyExpression callee = call.getCallee();
            if (callee instanceof PyReferenceExpression calleeRef && PyNames.SUPER.equals(callee.getName())) {
                PsiElement target = calleeRef.getReference().resolve();
                if (target != null && PyBuiltinCache.getInstance(call).isBuiltin(target)) {
                    return false; // super() of unresolved type
                }
            }
        }
        return true;
    }

    private static boolean addAssignedAttributes(ResolveResultList ret, String referencedName, @Nonnull PyQualifiedExpression qualifier) {
        QualifiedName qName = qualifier.asQualifiedName();
        if (qName == null) {
            return false;
        }
        for (PyExpression ex : collectAssignedAttributes(qName, qualifier)) {
            if (referencedName.equals(ex.getName())) {
                ret.poke(ex, RatedResolveResult.RATE_NORMAL);
                return true;
            }
        }
        return false;
    }

    @RequiredReadAction
    private void addDocReference(ResolveResultList ret, PyExpression qualifier, PyType qualifierType) {
        PsiElement docString = null;
        if (qualifierType instanceof PyClassType classType) {
            docString = classType.getPyClass().getDocStringExpression();
        }
        else if (qualifierType instanceof PyModuleType moduleType) {
            docString = moduleType.getModule().getDocStringExpression();
        }
        else if (qualifier instanceof PyReferenceExpression refExpr
            && refExpr.getReference(myContext).resolve() instanceof PyDocStringOwner docStringOwner) {
            docString = docStringOwner.getDocStringExpression();
        }
        ret.poke(docString, RatedResolveResult.RATE_HIGH);
    }

    @Nonnull
    @Override
    @RequiredReadAction
    public Object[] getVariants() {
        PyExpression qualifier = myElement.getQualifier();
        if (qualifier != null) {
            qualifier = CompletionUtilCore.getOriginalOrSelf(qualifier);
        }
        if (qualifier == null) {
            return EMPTY_ARRAY;
        }
        PyQualifiedExpression element = CompletionUtilCore.getOriginalOrSelf(myElement);

        PyType qualifierType = TypeEvalContext.codeCompletion(element.getProject(), element.getContainingFile()).getType(qualifier);
        ProcessingContext ctx = new ProcessingContext();
        Set<String> namesAlready = new HashSet<>();
        ctx.put(PyType.CTX_NAMES, namesAlready);
        Collection<Object> variants = new ArrayList<>();
        if (qualifierType != null) {
            Collections.addAll(variants, getVariantFromHasAttr(qualifier));
            if (qualifierType instanceof PyStructuralType structuralType && structuralType.isInferredFromUsages()) {
                PyClassType guessedType = guessClassTypeByName();
                if (guessedType != null) {
                    Collections.addAll(variants, getTypeCompletionVariants(myElement, guessedType));
                }
            }
            if (qualifier instanceof PyQualifiedExpression qualifierExpr) {
                QualifiedName qualifiedName = qualifierExpr.asQualifiedName();
                if (qualifiedName == null) {
                    return variants.toArray();
                }
                Collection<PyExpression> attrs = collectAssignedAttributes(qualifiedName, qualifierExpr);
                for (PyExpression ex : attrs) {
                    String name = ex.getName();
                    if (name != null && name.endsWith(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED)) {
                        continue;
                    }
                    if (ex instanceof PsiNamedElement && qualifierType instanceof PyClassType && name != null) {
                        variants.add(LookupElementBuilder.createWithSmartPointer(name, ex)
                            .withTypeText(qualifierType.getName())
                            .withIcon(PlatformIconGroup.nodesField()));
                    }
                    if (ex instanceof PyReferenceExpression refExpr) {
                        namesAlready.add(refExpr.getReferencedName());
                    }
                    else if (ex instanceof PyTargetExpression targetExpr) {
                        namesAlready.add(targetExpr.getName());
                    }
                }
                Collections.addAll(variants, qualifierType.getCompletionVariants(element.getName(), element, ctx));
                return variants.toArray();
            }
            else {
                return qualifierType.getCompletionVariants(element.getName(), element, ctx);
            }
        }
        else {
            PyClassType guessedType = guessClassTypeByName();
            if (guessedType != null) {
                Collections.addAll(variants, getTypeCompletionVariants(myElement, guessedType));
            }
            if (qualifier instanceof PyReferenceExpression) {
                Collections.addAll(variants, collectSeenMembers(qualifier.getText()));
            }
            return variants.toArray();
        }
    }

    @RequiredReadAction
    private Object[] getVariantFromHasAttr(PyExpression qualifier) {
        Collection<Object> variants = new ArrayList<>();
        PyIfStatement ifStatement = PsiTreeUtil.getParentOfType(myElement, PyIfStatement.class);
        while (ifStatement != null) {
            if (ifStatement.getIfPart().getCondition() instanceof PyCallExpression call
                && call.isCalleeText(PyNames.HAS_ATTR)
                && call.getArguments().length > 1
                && call.getArguments()[0].getText().equals(qualifier.getText())) {
                PyStringLiteralExpression string = call.getArgument(1, PyStringLiteralExpression.class);
                if (string != null && StringUtil.isJavaIdentifier(string.getStringValue())) {
                    variants.add(string.getStringValue());
                }
            }
            ifStatement = PsiTreeUtil.getParentOfType(ifStatement, PyIfStatement.class);
        }
        return variants.toArray();
    }

    @Nullable
    @RequiredReadAction
    private PyClassType guessClassTypeByName() {
        if (myElement.getQualifier() instanceof PyReferenceExpression qualifier) {
            String className = qualifier.getReferencedName();
            if (className != null) {
                Collection<PyClass> classes = PyClassNameIndexInsensitive.find(className, getElement().getProject());
                classes = filterByImports(classes, myElement.getContainingFile());
                if (classes.size() == 1) {
                    return new PyClassTypeImpl(classes.iterator().next(), false);
                }
            }
        }
        return null;
    }

    @RequiredReadAction
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
                PsiElement exportedClass = ((PyFile) containingFile).getElementNamed(pyClass.getName());
                if (exportedClass == pyClass) {
                    result.add(pyClass);
                }
            }
        }
        return result;
    }

    private Object[] collectSeenMembers(final String text) {
        final Set<String> members = new HashSet<>();
        myElement.getContainingFile().accept(new PyRecursiveElementVisitor() {
            @Override
            @RequiredReadAction
            public void visitPyReferenceExpression(PyReferenceExpression node) {
                super.visitPyReferenceExpression(node);
                visitPyQualifiedExpression(node);
            }

            @Override
            @RequiredReadAction
            public void visitPyTargetExpression(PyTargetExpression node) {
                super.visitPyTargetExpression(node);
                visitPyQualifiedExpression(node);
            }

            @RequiredReadAction
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
        for (String member : members) {
            results.add(AutoCompletionPolicy.NEVER_AUTOCOMPLETE.applyPolicy(LookupElementBuilder.create(member)));
        }
        return ArrayUtil.toObjectArray(results);
    }

    @Nonnull
    public static Collection<PyExpression> collectAssignedAttributes(@Nonnull QualifiedName qualifierQName, @Nonnull PsiElement anchor) {
        Set<String> names = new HashSet<>();
        List<PyExpression> results = new ArrayList<>();
        for (ScopeOwner owner = ScopeUtil.getScopeOwner(anchor); owner != null; owner = ScopeUtil.getScopeOwner(owner)) {
            Scope scope = ControlFlowCache.getScope(owner);
            for (PyTargetExpression target : scope.getTargetExpressions()) {
                QualifiedName targetQName = target.asQualifiedName();
                if (targetQName != null
                    && targetQName.getComponentCount() == qualifierQName.getComponentCount() + 1
                    && targetQName.matchesPrefix(qualifierQName)) {
                    String name = target.getName();
                    if (!names.contains(name)) {
                        names.add(name);
                        results.add(target);
                    }
                }
            }
        }
        return results;
    }

    @Override
    @RequiredReadAction
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
            if (containingFile instanceof StubBasedPsiElement stubBasedPsiElement) {
                assert stubBasedPsiElement.getStub() == null : "Stub origin for type eval context in isReferenceTo()";
            }
            TypeEvalContext context = TypeEvalContext.codeAnalysis(containingFile.getProject(), containingFile);
            resolveContext = resolveContext.withTypeEvalContext(context);
        }
        if (element instanceof PyFunction function
            && Objects.equals(referencedName, function.getName())
            && function.getContainingClass() != null
            && !PyNames.INIT.equals(referencedName)) {
            PyExpression qualifier = myElement.getQualifier();
            if (qualifier != null) {
                PyType qualifierType = resolveContext.getTypeEvalContext().getType(qualifier);
                if (qualifierType == null
                    || (qualifierType instanceof PyStructuralType structuralType && structuralType.isInferredFromUsages())) {
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

    @Nonnull
    protected PyQualifiedReference copyWithResolveContext(PyResolveContext context) {
        return new PyQualifiedReference(myElement, context);
    }

    @RequiredReadAction
    private boolean isResolvedToResult(PsiElement element, PsiElement resolveResult) {
        if (resolveResult instanceof PyImportedModule) {
            resolveResult = resolveResult.getNavigationElement();
        }
        if (element instanceof PsiDirectory
            && resolveResult instanceof PyFile file
            && PyNames.INIT_DOT_PY.equals(file.getName()) && file.getContainingDirectory() == element) {
            return true;
        }
        if (resolveResult == element) {
            return true;
        }
        if (resolveResult instanceof PyTargetExpression targetExpr
            && PyUtil.isAttribute(targetExpr)
            && element instanceof PyTargetExpression elemTargetExpr
            && PyUtil.isAttribute(elemTargetExpr)
            && Objects.equals(targetExpr.getReferencedName(), elemTargetExpr.getReferencedName())) {

            PyClass aClass = PsiTreeUtil.getParentOfType(resolveResult, PyClass.class);
            PyClass bClass = PsiTreeUtil.getParentOfType(element, PyClass.class);

            if (aClass != null && bClass != null && bClass.isSubclass(aClass, myContext.getTypeEvalContext())) {
                return true;
            }
        }

        return resolvesToWrapper(element, resolveResult);
    }

    @SuppressWarnings("SimplifiableIfStatement")
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
