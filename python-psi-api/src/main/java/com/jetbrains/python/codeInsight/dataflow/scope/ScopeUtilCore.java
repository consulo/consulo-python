package com.jetbrains.python.codeInsight.dataflow.scope;

import com.jetbrains.python.ast.PyAstAnnotation;
import com.jetbrains.python.ast.PyAstClass;
import com.jetbrains.python.ast.PyAstDecorator;
import com.jetbrains.python.ast.PyAstDecoratorList;
import com.jetbrains.python.ast.PyAstElement;
import com.jetbrains.python.ast.PyAstExpression;
import com.jetbrains.python.ast.PyAstExpressionCodeFragment;
import com.jetbrains.python.ast.PyAstFunction;
import com.jetbrains.python.ast.PyAstLambdaExpression;
import com.jetbrains.python.ast.PyAstNamedParameter;
import com.jetbrains.python.ast.PyAstTypeParameterList;
import com.jetbrains.python.ast.controlFlow.AstScopeOwner;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.application.util.ParameterizedCachedValue;
import consulo.application.util.ParameterizedCachedValueProvider;
import consulo.language.impl.psi.stub.StubBasedPsiElementBase;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiModificationTracker;
import consulo.language.psi.StubBasedPsiElement;
import consulo.language.psi.stub.StubElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public final class ScopeUtilCore {
    private ScopeUtilCore() {
    }

    /**
     * Return the scope owner for the element.
     * <p>
     * Scope owner is not always the first ScopeOwner parent of the element. Some elements are resolved in outer scopes.
     * <p>
     * This method does not access AST if underlying PSI is stub based.
     */
    @RequiredReadAction
    public static @Nullable AstScopeOwner getScopeOwner(@Nullable PsiElement element) {
        if (element == null) {
            return null;
        }
        if (element instanceof PyAstExpressionCodeFragment) {
            // A fragment holds the text of an annotation or a type comment that a stub keeps. As in calculateScopeOwner, these are
            // resolved outside of the function, so a name such as `type` in `type: type` does not resolve to the parameter itself.
            PsiElement context = element.getContext();
            if (context instanceof PyAstNamedParameter) {
                AstScopeOwner function = getScopeOwner(context);
                return function instanceof PyAstFunction astFunction && astFunction.getTypeParameterList() == null
                    ? getScopeOwner(function)
                    : function;
            }
            else if (context instanceof PyAstFunction astFunction) {
                return astFunction.getTypeParameterList() == null ? getScopeOwner(astFunction) : astFunction;
            }
            else {
                return context instanceof AstScopeOwner scopeOwner ? scopeOwner : getScopeOwner(context);
            }
        }
        // TODO consulo: StubBuildCachedValuesManager.getCachedValueStubBuildOptimized missing
        return CachedValuesManager.getManager(element.getProject())
            .getParameterizedCachedValue(element, GET_SCOPE_OWNER_KEY, GET_SCOPE_OWNER_PROVIDER, false, element);
    }

    private static final Key<ParameterizedCachedValue<AstScopeOwner, PsiElement>> GET_SCOPE_OWNER_KEY = Key.create("python.scopeOwner");

    private static final ParameterizedCachedValueProvider<AstScopeOwner, PsiElement> GET_SCOPE_OWNER_PROVIDER =
        element -> CachedValueProvider.Result.create(calculateScopeOwner(element), PsiModificationTracker.MODIFICATION_COUNT);

    @RequiredReadAction
    private static @Nullable AstScopeOwner calculateScopeOwner(@Nullable PsiElement element) {
        if (element instanceof StubBasedPsiElement<?> stubBasedPsiElement) {
            StubElement<?> stub = stubBasedPsiElement.getStub();
            if (stub != null) {
                AstScopeOwner firstOwner = stub.getParentStubOfType(AstScopeOwner.class);
                AstScopeOwner nextOwner;
                if (firstOwner != null && !(firstOwner instanceof PsiFile)) {
                    StubElement<?> firstOwnerStub = Objects.requireNonNull(((StubBasedPsiElementBase<?>)firstOwner).getGreenStub());
                    nextOwner = firstOwnerStub.getParentStubOfType(AstScopeOwner.class);
                }
                else {
                    nextOwner = null;
                }
                if (stub.getParentStubOfType(PyAstDecoratorList.class) != null) {
                    return nextOwner;
                }
                return firstOwner;
            }
        }
        AstScopeOwner firstOwner = PsiTreeUtil.getParentOfType(element, AstScopeOwner.class);
        if (firstOwner == null) {
            return null;
        }
        AstScopeOwner nextOwner = PsiTreeUtil.getParentOfType(firstOwner, AstScopeOwner.class);
        // References in decorator expressions are resolved outside of the function (if the lambda is not inside the decorator)
        PyAstElement decoratorAncestor = PsiTreeUtil.getParentOfType(element, PyAstDecorator.class, false);
        if (decoratorAncestor != null && !PsiTreeUtil.isAncestor(decoratorAncestor, firstOwner, true)) {
            return nextOwner;
        }
        /*
         * References in default values are resolved outside of the function (if the lambda is not inside the default value).
         * Annotations of parameters are resolved outside of the function if the function doesn't have type parameters list
         */
        PyAstNamedParameter parameterAncestor = PsiTreeUtil.getParentOfType(element, PyAstNamedParameter.class, false);
        if (parameterAncestor != null && !PsiTreeUtil.isAncestor(parameterAncestor, firstOwner, true)) {
            PyAstExpression defaultValue = parameterAncestor.getDefaultValue();
            PyAstAnnotation annotation = parameterAncestor.getAnnotation();
            if (firstOwner instanceof PyAstFunction function) {
                PyAstTypeParameterList typeParameterList = function.getTypeParameterList();
                if ((typeParameterList == null && PsiTreeUtil.isAncestor(annotation, Objects.requireNonNull(element), false))
                    || (PsiTreeUtil.isAncestor(defaultValue, Objects.requireNonNull(element), false))) {
                    return nextOwner;
                }
            }
            else if (firstOwner instanceof PyAstLambdaExpression
                && PsiTreeUtil.isAncestor(defaultValue, Objects.requireNonNull(element), false)) {
                return nextOwner;
            }
        }
        // Superclasses are resolved outside of the class if the class doesn't have type parameters list
        PyAstClass containingClass = PsiTreeUtil.getParentOfType(element, PyAstClass.class);
        if (containingClass != null && PsiTreeUtil.isAncestor(
            containingClass.getSuperClassExpressionList(),
            Objects.requireNonNull(element),
            false
        ) && containingClass.getTypeParameterList() == null) {
            return nextOwner;
        }
        // Function return annotations and type comments are resolved outside of the function if the function doesn't have type parameters list
        if (firstOwner instanceof PyAstFunction function) {
            PyAstTypeParameterList typeParameterList = function.getTypeParameterList();
            if ((typeParameterList == null && PsiTreeUtil.isAncestor(function.getAnnotation(), Objects.requireNonNull(element), false)
                || PsiTreeUtil.isAncestor(function.getTypeComment(), Objects.requireNonNull(element), false))) {
                return nextOwner;
            }
        }
        return firstOwner;
    }
}
