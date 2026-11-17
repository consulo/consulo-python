// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.unresolvedReference;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.inspections.quickfix.AddFieldQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.AddFunctionQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.AddMethodQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.CreateClassQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.UnresolvedRefCreateFunctionQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.UnresolvedRefTrueFalseQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.UnresolvedReferenceAddParameterQuickFix;
import com.jetbrains.python.impl.inspections.quickfix.UnresolvedReferenceAddSelfQuickFix;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionNavigator;
import com.jetbrains.python.impl.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.inject.InjectedLanguageManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Java facade of the top-level functions of JB {@code PyUnresolvedReferenceQuickFixes.kt}.
 */
public final class PyUnresolvedReferenceQuickFixesKt {
    private PyUnresolvedReferenceQuickFixesKt() {
    }

    /**
     * {@code UnresolvedRefTrueFalseQuickFix} for {@code true} / {@code false} typos.
     */
    public static @Nullable LocalQuickFix getTrueFalseQuickFix(String refText) {
        return refText.equals("true") || refText.equals("false") ? new UnresolvedRefTrueFalseQuickFix(refText) : null;
    }

    /**
     * {@code UnresolvedRefCreateFunctionQuickFix} for unresolved unqualified calls like {@code foo()}.
     */
    static @Nullable LocalQuickFix getCreateFunctionQuickFix(PyReferenceExpression expr) {
        PyCallExpression callExpression = PyCallExpressionNavigator.getPyCallExpressionByCallee(expr);
        if (callExpression == null) {
            return null;
        }
        PyExpression callee = callExpression.getCallee();
        boolean isUnqualifiedCallee = !(callee instanceof PyQualifiedExpression qualifiedCallee) || qualifiedCallee.getQualifier() == null;
        return isUnqualifiedCallee ? new UnresolvedRefCreateFunctionQuickFix(expr) : null;
    }

    /**
     * Offer adding a parameter to the enclosing function when an unknown name is used inside its body.
     */
    public static @Nullable LocalQuickFix getAddParameterQuickFix(@Nullable String refName, @Nullable PyReferenceExpression expr) {
        if (PsiTreeUtil.getParentOfType(expr, PyFunction.class) == null) {
            return null;
        }
        boolean isInsideDecoratorOrAnnotationOrImport =
            PsiTreeUtil.getParentOfType(expr, PyDecorator.class) != null ||
                PsiTreeUtil.getParentOfType(expr, PyAnnotation.class) != null ||
                PsiTreeUtil.getParentOfType(expr, PyImportStatement.class) != null;
        if (isInsideDecoratorOrAnnotationOrImport) {
            return null;
        }
        return new UnresolvedReferenceAddParameterQuickFix(refName);
    }

    /**
     * Offer {@code self.<name>} / {@code cls.<name>} rewrites when the unresolved name actually exists as an
     * instance attribute, property, or method on the enclosing class. Walks instance attrs,
     * {@code @property} class-attrs, and methods to decide.
     */
    static List<LocalQuickFix> getAddSelfFixes(TypeEvalContext typeEvalContext, @Nullable PyElement node, PyReferenceExpression expr) {
        PyClass containedClass = PsiTreeUtil.getParentOfType(node, PyClass.class);
        if (containedClass == null) {
            return Collections.emptyList();
        }
        PyFunction function = PsiTreeUtil.getParentOfType(node, PyFunction.class);
        if (function == null) {
            return Collections.emptyList();
        }
        PyParameter[] parameters = function.getParameterList().getParameters();
        if (parameters.length == 0) {
            return Collections.emptyList();
        }
        String qualifier = parameters[0].getText();
        boolean isClassMethod = false;
        PyDecoratorList decoratorList = function.getDecoratorList();
        if (decoratorList != null) {
            for (PyDecorator decorator : decoratorList.getDecorators()) {
                PyExpression callee = decorator.getCallee();
                if (PyNames.CLASSMETHOD.equals(callee != null ? callee.getText() : null)) {
                    isClassMethod = true;
                    break;
                }
            }
        }

        List<LocalQuickFix> result = new ArrayList<>();
        for (PyTargetExpression target : containedClass.getInstanceAttributes()) {
            if (!isClassMethod && Objects.equals(node != null ? node.getName() : null, target.getName())) {
                result.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
            }
        }
        for (PyStatement statement : containedClass.getStatementList().getStatements()) {
            if (!(statement instanceof PyAssignmentStatement assignmentStatement)) {
                continue;
            }
            PyExpression lhs = assignmentStatement.getLeftHandSideExpression();
            if (lhs == null) {
                continue;
            }
            if (!lhs.getText().equals(expr.getText())) {
                continue;
            }
            if (!(assignmentStatement.getAssignedValue() instanceof PyCallExpression assignedValue)) {
                continue;
            }
            PyType type = typeEvalContext.getType(assignedValue);
            if (type instanceof PyClassTypeImpl && assignedValue.isCalleeText(PyNames.PROPERTY)) {
                result.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
            }
        }
        for (PyFunction method : containedClass.getMethods()) {
            if (expr.getText().equals(method.getName())) {
                result.add(new UnresolvedReferenceAddSelfQuickFix(expr, qualifier));
            }
        }
        return result;
    }

    /**
     * {@code CreateClassQuickFix} when {@code refText} looks like a class name (CamelCase, not all-caps).
     */
    static @Nullable LocalQuickFix getCreateClassFix(TypeEvalContext typeEvalContext, String refText, @Nullable PsiElement element) {
        if (refText.length() <= 2 || !Character.isUpperCase(refText.charAt(0)) || refText.toUpperCase().equals(refText)) {
            return null;
        }
        if (!(element instanceof PyQualifiedExpression qualifiedExpression)) {
            return null;
        }

        PyExpression qualifier = qualifiedExpression.getQualifier();
        if (qualifier == null) {
            PyFromImportStatement fromImport = PsiTreeUtil.getParentOfType(element, PyFromImportStatement.class);
            if (fromImport != null) {
                qualifier = fromImport.getImportSource();
            }
        }

        PsiFile destination;
        if (qualifier != null) {
            if (!(typeEvalContext.getType(qualifier) instanceof PyModuleType type)) {
                return null;
            }
            destination = type.getModule();
        }
        else {
            PsiElement injectionHost = InjectedLanguageManager.getInstance(element.getProject()).getInjectionHost(element);
            destination = ObjectUtil.chooseNotNull(injectionHost, element).getContainingFile();
        }
        return new CreateClassQuickFix(refText, destination);
    }

    /**
     * Offer {@code AddField} / {@code AddMethod} / {@code AddFunction} / {@code CreateClass} fixes derived from the qualifier
     * type — what we add depends on whether the qualifier resolves to a user class, a module, or
     * something else.
     */
    static List<LocalQuickFix> getCreateMemberFromUsageFixes(TypeEvalContext typeEvalContext,
                                                            PyType type,
                                                            PsiReference reference,
                                                            String refText) {
        List<LocalQuickFix> result = new ArrayList<>();
        PsiElement element = reference.getElement();
        if (type instanceof PyClassType classType) {
            PyClass cls = classType.getPyClass();
            if (!PyBuiltinCache.getInstance(element).isBuiltin(cls)) {
                if (element.getParent() instanceof PyCallExpression) {
                    result.add(new AddMethodQuickFix(refText, cls.getName(), true));
                }
                else if (!(reference instanceof PyOperatorReference)) {
                    result.add(new AddFieldQuickFix(refText, "None", classType.getName(), true));
                }
            }
        }
        else if (type instanceof PyModuleType moduleType) {
            boolean isQualifiedRefInsideImport = element instanceof PyReferenceExpression referenceExpression &&
                referenceExpression.isQualified() &&
                PsiTreeUtil.getParentOfType(element, PyImportStatementBase.class) != null;
            if (!isQualifiedRefInsideImport) {
                PsiFile file = moduleType.getModule();
                LocalQuickFix createClassQuickFix = getCreateClassFix(typeEvalContext, refText, element);
                if (createClassQuickFix != null) {
                    result.add(createClassQuickFix);
                }
                else {
                    result.add(new AddFunctionQuickFix(refText, file.getName()));
                }
            }
        }
        return result;
    }
}
