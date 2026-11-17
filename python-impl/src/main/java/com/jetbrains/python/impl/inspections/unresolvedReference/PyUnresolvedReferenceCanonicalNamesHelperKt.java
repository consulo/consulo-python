// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.unresolvedReference;

import com.jetbrains.python.impl.documentation.docstrings.DocStringParameterReference;
import com.jetbrains.python.impl.psi.impl.references.PyOperatorReference;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.types.PyImportedModuleType;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyCallable;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyFunctionType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ContainerUtil;
import consulo.util.collection.SmartList;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Java facade of JB {@code PyUnresolvedReferenceCanonicalNamesHelper.kt}.
 */
public final class PyUnresolvedReferenceCanonicalNamesHelperKt {
    private PyUnresolvedReferenceCanonicalNamesHelperKt() {
    }

    /**
     * Return the canonical qualified names for a reference (even for an unresolved one).
     */
    static List<QualifiedName> getCanonicalNames(PsiReference reference, TypeEvalContext context) {
        PsiElement element = reference.getElement();
        List<QualifiedName> result = new SmartList<>();
        if (reference instanceof PyOperatorReference operatorReference && element instanceof PyQualifiedExpression qualifiedExpression) {
            PyExpression receiver = operatorReference.getReceiver(null);
            if (receiver == null) {
                return result;
            }
            if (!(context.getType(receiver) instanceof PyClassType type)) {
                return result;
            }
            ContainerUtil.addIfNotNull(result, extractAttributeQNameFromClassType(qualifiedExpression.getReferencedName(), type));
        }
        else if (element instanceof PyReferenceExpression referenceExpression) {
            String exprName = referenceExpression.getName();
            if (exprName == null) {
                return result;
            }
            PyExpression qualifier = referenceExpression.getQualifier();
            if (qualifier != null) {
                appendQualifiedNamesFromQualifierType(result, context.getType(qualifier), referenceExpression, exprName);
            }
            else {
                appendQualifiedNamesForUnqualifiedReference(result, referenceExpression, exprName);
            }
        }
        else if (reference instanceof DocStringParameterReference) {
            ContainerUtil.addIfNotNull(result, QualifiedName.fromDottedString(reference.getCanonicalText()));
        }
        return result;
    }

    private static void appendQualifiedNamesFromQualifierType(List<QualifiedName> result,
                                                              @Nullable PyType qualifierType,
                                                              PyReferenceExpression element,
                                                              String exprName) {
        for (PyType type : PyTypeUtil.toStream(qualifierType).toList()) {
            QualifiedName name = qualifiedNameForType(type, element, exprName);
            if (name != null) {
                result.add(name);
            }
        }
    }

    private static @Nullable QualifiedName qualifiedNameForType(@Nullable PyType type, PyReferenceExpression element, String exprName) {
        if (type instanceof PyClassType classType) {
            return extractAttributeQNameFromClassType(exprName, classType);
        }
        else if (type instanceof PyModuleType moduleType) {
            QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(moduleType.getModule(), element);
            return path != null ? path.append(exprName) : null;
        }
        else if (type instanceof PyImportedModuleType importedModuleType) {
            PsiElement resolved = importedModuleType.getImportedModule().resolve();
            if (resolved == null) {
                return null;
            }
            QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(resolved, element);
            return path != null ? path.append(exprName) : null;
        }
        else if (type instanceof PyFunctionType functionType) {
            PyCallable callable = functionType.getCallable();
            String callableName = callable.getName();
            if (callableName == null) {
                return null;
            }
            QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(callable, element);
            return path != null ? path.append(QualifiedName.fromComponents(callableName, exprName)) : null;
        }
        return null;
    }

    private static void appendQualifiedNamesForUnqualifiedReference(List<QualifiedName> result,
                                                                    PyReferenceExpression element,
                                                                    String exprName) {
        PsiElement parent = element.getParent();
        if (parent instanceof PyImportElement) {
            PyImportStatementBase importStmt = PsiTreeUtil.getParentOfType(parent, PyImportStatementBase.class);
            if (importStmt instanceof PyImportStatement) {
                ContainerUtil.addIfNotNull(result, QualifiedName.fromComponents(exprName));
            }
            else if (importStmt instanceof PyFromImportStatement fromImportStatement) {
                PsiElement resolved = fromImportStatement.resolveImportSource();
                if (resolved == null) {
                    return;
                }
                QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(resolved, element);
                if (path == null) {
                    return;
                }
                ContainerUtil.addIfNotNull(result, path.append(exprName));
            }
        }
        else {
            QualifiedName path = QualifiedNameFinder.findCanonicalImportPath(element, element);
            if (path == null) {
                return;
            }
            ContainerUtil.addIfNotNull(result, path.append(exprName));
        }
    }

    private static @Nullable QualifiedName extractAttributeQNameFromClassType(@Nullable String exprName, PyClassType type) {
        String name = type.getClassQName();
        if (name == null) {
            return null;
        }
        return QualifiedName.fromDottedString(name).append(exprName);
    }
}
