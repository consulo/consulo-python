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
package com.jetbrains.python.impl.codeInsight.intentions;

import static com.jetbrains.python.impl.psi.PyUtil.as;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import com.google.common.base.Function;
import com.jetbrains.python.impl.psi.PyUtil;
import consulo.language.ast.ASTNode;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.module.content.ProjectRootManager;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiPolyVariantReference;
import consulo.language.psi.PsiReference;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.documentation.doctest.PyDocstringFile;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;

/**
 * Common part for type specifying intentions.
 *
 * @author ktisha
 */
public abstract class TypeIntention extends PyBaseIntentionAction {
    public boolean isAvailable(@Nonnull Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PyFile) || file instanceof PyDocstringFile) {
            return false;
        }
        updateText(false);

        PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
        if (elementAt == null) {
            return false;
        }
        if (isAvailableForParameter(project, elementAt)) {
            return true;
        }
        if (isAvailableForReturn(elementAt)) {
            updateText(true);
            return true;
        }
        return false;
    }

    private boolean isAvailableForParameter(Project project, PsiElement elementAt) {
        PyExpression problemElement = getProblemElement(elementAt);
        if (problemElement == null) {
            return false;
        }
        if (PsiTreeUtil.getParentOfType(problemElement, PyLambdaExpression.class) != null) {
            return false;
        }
        PsiReference reference = problemElement.getReference();
        if (reference instanceof PsiPolyVariantReference) {
            ResolveResult[] results = ((PsiPolyVariantReference) reference).multiResolve(false);
            if (results.length != 1) {
                return false;
            }
        }
        VirtualFile virtualFile = problemElement.getContainingFile().getVirtualFile();
        if (virtualFile != null) {
            if (ProjectRootManager.getInstance(project).getFileIndex().isInLibraryClasses(virtualFile)) {
                return false;
            }
        }
        PsiElement resolved = reference != null ? reference.resolve() : null;
        PyParameter parameter = getParameter(problemElement, resolved);

        return parameter != null && !isParamTypeDefined(parameter);
    }

    @Nullable
    public static PyExpression getProblemElement(@Nullable PsiElement elementAt) {
        PyExpression problemElement = PsiTreeUtil.getParentOfType(elementAt, PyNamedParameter.class, PyReferenceExpression.class);
        if (problemElement == null) {
            return null;
        }
        if (problemElement instanceof PyQualifiedExpression) {
            PyExpression qualifier = ((PyQualifiedExpression) problemElement).getQualifier();
            if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) {
                problemElement = qualifier;
            }
        }
        return problemElement;
    }

    protected abstract void updateText(boolean isReturn);

    protected boolean isParamTypeDefined(PyParameter parameter) {
        return false;
    }

    @Nullable
    protected static PyNamedParameter getParameter(PyExpression problemElement, PsiElement resolved) {
        PyNamedParameter parameter = as(problemElement, PyNamedParameter.class);
        if (resolved instanceof PyNamedParameter) {
            parameter = (PyNamedParameter) resolved;
        }
        return parameter == null || parameter.isSelf() ? null : parameter;
    }

    private boolean isAvailableForReturn(@Nonnull PsiElement elementAt) {
        return resolvesToFunction(elementAt, new Function<PyFunction, Boolean>() {
            @Override
            public Boolean apply(PyFunction input) {
                return !isReturnTypeDefined(input);
            }
        });
    }

    static boolean resolvesToFunction(@Nonnull PsiElement elementAt, Function<PyFunction, Boolean> isAvailableForFunction) {
        PyFunction parentFunction = PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
        if (parentFunction != null) {
            ASTNode nameNode = parentFunction.getNameNode();
            if (nameNode != null) {
                PsiElement prev = elementAt.getContainingFile().findElementAt(elementAt.getTextOffset() - 1);
                if (nameNode.getPsi() == elementAt || nameNode.getPsi() == prev) {
                    return isAvailableForFunction.apply(parentFunction);
                }
            }
        }

        PyCallExpression callExpression = getCallExpression(elementAt);
        if (callExpression == null) {
            return false;
        }
        PyExpression callee = callExpression.getCallee();
        if (callee == null) {
            return false;
        }
        PsiReference reference = callee.getReference();
        if (reference instanceof PsiPolyVariantReference) {
            ResolveResult[] results = ((PsiPolyVariantReference) reference).multiResolve(false);
            for (int i = 0; i < results.length; i++) {
                if (results[i].getElement() instanceof PyFunction) {
                    PsiElement result = results[i].getElement();
                    PsiFile psiFile = result.getContainingFile();
                    if (psiFile == null) {
                        return false;
                    }
                    VirtualFile virtualFile = psiFile.getVirtualFile();
                    if (virtualFile != null) {
                        if (ProjectRootManager.getInstance(psiFile.getProject()).getFileIndex().isInLibraryClasses(virtualFile)) {
                            return false;
                        }
                    }
                    return isAvailableForFunction.apply((PyFunction) result);
                }
            }
        }
        return false;
    }

    protected boolean isReturnTypeDefined(@Nonnull PyFunction function) {
        return false;
    }

    @Nullable
    static PyCallExpression getCallExpression(PsiElement elementAt) {
        PyExpression problemElement = getProblemElement(elementAt);
        if (problemElement != null) {
            PsiReference reference = problemElement.getReference();
            PsiElement resolved = reference != null ? reference.resolve() : null;
            if (resolved instanceof PyTargetExpression) {
                PyExpression assignedValue = ((PyTargetExpression) resolved).findAssignedValue();
                if (assignedValue instanceof PyCallExpression) {
                    return (PyCallExpression) assignedValue;
                }
            }
        }

        PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(elementAt, PyAssignmentStatement.class);
        if (assignmentStatement != null) {
            PyExpression assignedValue = assignmentStatement.getAssignedValue();
            if (assignedValue instanceof PyCallExpression) {
                return (PyCallExpression) assignedValue;
            }
        }
        return PsiTreeUtil.getParentOfType(elementAt, PyCallExpression.class, false);
    }

    @Nullable
    static PyCallable getCallable(PsiElement elementAt) {
        PyCallExpression callExpression = getCallExpression(elementAt);

        if (callExpression != null && elementAt != null) {
            PyCallable callable = callExpression.resolveCalleeFunction(getResolveContext(elementAt));
            return callable == null ? PsiTreeUtil.getParentOfType(elementAt, PyFunction.class) : callable;
        }
        return PsiTreeUtil.getParentOfType(elementAt, PyFunction.class);
    }

    protected static PyResolveContext getResolveContext(@Nonnull PsiElement origin) {
        return PyResolveContext.defaultContext()
            .withTypeEvalContext(TypeEvalContext.codeAnalysis(origin.getProject(), origin.getContainingFile()));
    }
}