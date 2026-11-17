// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.override;

import com.google.common.collect.Lists;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.externalService.statistic.FeatureUsageTracker;
import consulo.language.editor.util.ProductivityFeatureNames;
import consulo.ide.impl.idea.ide.util.MemberChooser;
import consulo.application.ApplicationManager;
import consulo.language.editor.WriteCommandAction;
import consulo.codeEditor.Editor;
import consulo.codeEditor.ScrollType;
import consulo.project.Project;
import consulo.ui.ex.awt.DialogWrapper;
import consulo.util.lang.StringUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.util.PsiTreeUtil;
import com.jetbrains.python.PyNames;
import consulo.python.impl.localize.PyLocalize;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyAnnotation;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySingleStarParameter;
import com.jetbrains.python.psi.PySlashParameter;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.impl.pyi.PyiUtil;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.impl.refactoring.classes.PyClassRefactoringUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;

public final class PyOverrideImplementUtil {

    public static @Nullable PyClass getContextClass(Editor editor, PsiFile file) {
        int offset = editor.getCaretModel().getOffset();
        PsiElement element = file.findElementAt(offset);
        if (element == null) {
            // are we in whitespace after last class? PY-440
            PsiElement lastChild = file.getLastChild();
            if (lastChild != null &&
                offset >= lastChild.getTextRange().getStartOffset() &&
                offset <= lastChild.getTextRange().getEndOffset()) {
                element = lastChild;
            }
        }
        PyClass pyClass = PsiTreeUtil.getParentOfType(element, PyClass.class, false);
        if (pyClass == null && element instanceof PsiWhiteSpace && element.getPrevSibling() instanceof PyClass) {
            return (PyClass)element.getPrevSibling();
        }
        return pyClass;
    }

    public static void chooseAndOverrideMethods(Project project,
                                                Editor editor,
                                                PyClass cls,
                                                TypeEvalContext context) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT);

        PyPsiUtils.assertValid(cls);
        ApplicationManager.getApplication().assertReadAccessAllowed();

        chooseAndOverrideOrImplementMethods(project, editor, cls, PyPsiRefactoringUtil.getAllSuperMethods(cls, context), false);
    }

    public static void chooseAndImplementMethods(Project project,
                                                 Editor editor,
                                                 PyClass cls,
                                                 TypeEvalContext context) {
        chooseAndImplementMethods(project, editor, cls, PyPsiRefactoringUtil.getAllSuperAbstractMethods(cls, context));
    }

    public static void chooseAndImplementMethods(Project project,
                                                 Editor editor,
                                                 PyClass cls,
                                                 Collection<PyFunction> methods) {
        FeatureUsageTracker.getInstance().triggerFeatureUsed(ProductivityFeatureNames.CODEASSISTS_OVERRIDE_IMPLEMENT);

        PyPsiUtils.assertValid(cls);
        ApplicationManager.getApplication().assertReadAccessAllowed();

        chooseAndOverrideOrImplementMethods(project, editor, cls, methods, true);
    }

    private static void chooseAndOverrideOrImplementMethods(Project project,
                                                            Editor editor,
                                                            PyClass cls,
                                                            Collection<PyFunction> methods,
                                                            boolean implement) {
        List<PyMethodMember> elements = new ArrayList<>();
        for (PyFunction method : methods) {
            String name = method.getName();
            if (name == null || PyUtil.isClassPrivateName(name)) {
                continue;
            }
            if (cls.findMethodByName(name, false, null) == null) {
                PyMethodMember member = new PyMethodMember(method);
                elements.add(member);
            }
        }
        if (elements.isEmpty()) {
            return;
        }

        MemberChooser<PyMethodMember> chooser = new MemberChooser<>(elements.toArray(new PyMethodMember[0]), false, true, project);
        chooser.setTitle(implement ? PyLocalize.codeInsightSelectMethodsToImplement().get()
                                   : PyLocalize.codeInsightSelectMethodsToOverride().get());
        chooser.setCopyJavadocVisible(false);
        chooser.show();
        if (chooser.getExitCode() != DialogWrapper.OK_EXIT_CODE) {
            return;
        }
        overrideMethods(editor, cls, chooser.getSelectedElements(), implement);
    }

    public static void overrideMethods(Editor editor, PyClass pyClass, List<PyMethodMember> membersToOverride,
                                       boolean implement) {
        if (membersToOverride == null) {
            return;
        }
        WriteCommandAction
            .writeCommandAction(pyClass.getContainingFile())
            .run(() -> write(pyClass, membersToOverride, editor, implement));
    }

    private static void write(PyClass pyClass, List<PyMethodMember> newMembers, Editor editor, boolean implement) {
        PyStatementList statementList = pyClass.getStatementList();
        int offset = editor.getCaretModel().getOffset();
        PsiElement anchor = null;
        for (PyStatement statement : statementList.getStatements()) {
            if (statement.getTextRange().getStartOffset() < offset ||
                (statement instanceof PyExpressionStatement &&
                 ((PyExpressionStatement)statement).getExpression() instanceof PyStringLiteralExpression)) {
                anchor = statement;
            }
        }

        PyFunction element = null;
        for (PyMethodMember newMember : Lists.reverse(newMembers)) {
            element = writeMember(pyClass, (PyFunction)newMember.getPsiElement(), anchor, implement);
        }

        PyPsiUtils.removeRedundantPass(statementList);
        if (element != null) {
            PyStatementList targetStatementList = element.getStatementList();
            int start = targetStatementList.getTextRange().getStartOffset();
            editor.getCaretModel().moveToOffset(start);
            editor.getScrollingModel().scrollToCaret(ScrollType.RELATIVE);
            editor.getSelectionModel().setSelection(start, element.getTextRange().getEndOffset());
        }
    }

    private static @Nullable PyFunction writeMember(PyClass cls,
                                                    PyFunction baseFunction,
                                                    @Nullable PsiElement anchor,
                                                    boolean implement) {
        PyStatementList statementList = cls.getStatementList();
        TypeEvalContext context = TypeEvalContext.userInitiated(cls.getProject(), cls.getContainingFile());

        PyFunctionBuilder builder = buildOverriddenFunction(cls, baseFunction, implement);
        // A plain PSI insertion into a class body without statements gives an incorrect indent to an async method.
        PyFunction function = statementList.getStatements().length == 0
                                    ? (PyFunction)PyPsiRefactoringUtil.addElementToStatementList(builder.buildFunction(), statementList, true)
                                    : builder.addFunctionAfter(statementList, anchor);
        PyClassRefactoringUtil.transplantImportsFromSignature(baseFunction, function);

        PyiUtil
            .getOverloads(baseFunction, context)
            .forEach(
                baseOverload -> {
                    PyFunction overload = (PyFunction)statementList.addBefore(baseOverload, function);
                    PyClassRefactoringUtil.transplantImportsFromSignature(baseOverload, overload);
                }
            );

        return CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(function);
    }

    private static PyFunctionBuilder buildOverriddenFunction(PyClass pyClass,
                                                             PyFunction baseFunction,
                                                             boolean implement) {
        String functionName = baseFunction.getName();
        boolean overridingNew = PyNames.NEW.equals(functionName);
        assert functionName != null;
        PyFunctionBuilder pyFunctionBuilder = new PyFunctionBuilder(functionName, baseFunction);
        PyDecoratorList decorators = baseFunction.getDecoratorList();
        boolean baseMethodIsStatic = false;
        boolean isProperty = false;
        if (decorators != null) {
            if (decorators.findDecorator(PyNames.CLASSMETHOD) != null) {
                pyFunctionBuilder.decorate(PyNames.CLASSMETHOD);
            }
            else if (decorators.findDecorator(PyNames.STATICMETHOD) != null) {
                baseMethodIsStatic = true;
                pyFunctionBuilder.decorate(PyNames.STATICMETHOD);
            }
            else if (decorators.findDecorator(PyNames.PROPERTY) != null ||
                decorators.findDecorator(PyNames.ABSTRACTPROPERTY) != null) {
                isProperty = true;
                pyFunctionBuilder.decorate(PyNames.PROPERTY);
            }
        }
        PyAnnotation anno = baseFunction.getAnnotation();
        boolean copyAnnotations = PyPsiRefactoringUtil.shouldCopyAnnotations(baseFunction, pyClass.getContainingFile());
        if (anno != null && copyAnnotations && anno.getValue() != null) {
            pyFunctionBuilder.returnType(anno.getValue().getText());
        }
        if (baseFunction.isAsync()) {
            pyFunctionBuilder.makeAsync();
        }
        TypeEvalContext context = TypeEvalContext.userInitiated(baseFunction.getProject(), baseFunction.getContainingFile());
        List<PyCallableParameter> baseParams = baseFunction.getParameters(context);
        for (PyCallableParameter parameter : baseParams) {
            PyParameter psi = parameter.getParameter();
            PyNamedParameter namedParameter = PyUtil.as(psi, PyNamedParameter.class);

            if (namedParameter != null) {
                StringBuilder parameterBuilder = new StringBuilder();
                parameterBuilder.append(ParamHelper.getNameInSignature(namedParameter));
                PyAnnotation annotation = namedParameter.getAnnotation();
                if (annotation != null && copyAnnotations) {
                    parameterBuilder.append(annotation.getText());
                }
                PyExpression defaultValue = namedParameter.getDefaultValue();
                if (defaultValue != null) {
                    parameterBuilder.append("=");
                    parameterBuilder.append(defaultValue.getText());
                }
                pyFunctionBuilder.parameter(parameterBuilder.toString());
            }
            else if (psi != null) {
                pyFunctionBuilder.parameter(psi.getText());
            }
        }

        PyClass baseClass = baseFunction.getContainingClass();
        assert baseClass != null;
        StringBuilder statementBody = new StringBuilder();

        boolean hadStar = false;
        List<String> parameters = new ArrayList<>();
        for (PyCallableParameter parameter : baseParams) {
            PyParameter psi = parameter.getParameter();
            PyNamedParameter namedParameter = PyUtil.as(psi, PyNamedParameter.class);

            if (namedParameter != null) {
                String repr = namedParameter.getRepr(false);
                parameters.add(hadStar && !namedParameter.isKeywordContainer() ? namedParameter.getName() + "=" + repr : repr);
                if (namedParameter.isPositionalContainer()) {
                    hadStar = true;
                }
            }
            else if (psi instanceof PySingleStarParameter) {
                hadStar = true;
            }
            else if (psi != null && !(psi instanceof PySlashParameter)) {
                parameters.add(psi.getText());
            }
        }

        if (implement ||
            baseFunction.onlyRaisesNotImplementedError() ||
            PyKnownDecoratorUtil.hasAbstractDecorator(baseFunction, context)) {
            statementBody.append(PyNames.PASS);
        }
        else {
            if (!PyNames.INIT.equals(functionName) && !isNoneType(context.getReturnType(baseFunction)) || overridingNew || isProperty) {

                statementBody.append("return ");
            }
            if (baseFunction.isAsync()) {
                statementBody.append(PyNames.AWAIT);
                statementBody.append(" ");
            }
            if (baseClass.isNewStyleClass(context)) {
                statementBody.append(PyNames.SUPER);
                statementBody.append("(");
                LanguageLevel langLevel = ((PyFile)pyClass.getContainingFile()).getLanguageLevel();
                if (langLevel.isPython2()) {
                    String baseFirstName = !baseParams.isEmpty() ? baseParams.get(0).getName() : null;
                    String firstName = baseFirstName != null ? baseFirstName : PyNames.CANONICAL_SELF;
                    PyClass outerClass = PsiTreeUtil.getParentOfType(pyClass, PyClass.class, true, PyFunction.class);
                    String className = pyClass.getName();
                    List<String> nameResult = Lists.newArrayList(className);
                    while (outerClass != null) {
                        nameResult.add(0, outerClass.getName());
                        outerClass = PsiTreeUtil.getParentOfType(outerClass, PyClass.class, true, PyFunction.class);
                    }

                    StringUtil.join(nameResult, ".", statementBody);
                    statementBody.append(", ").append(firstName);
                }
                statementBody.append(").").append(functionName);

                if (!parameters.isEmpty() && !(baseMethodIsStatic || overridingNew)) {
                    parameters.remove(0);
                }
            }
            else {
                statementBody.append(getReferenceText(pyClass, baseClass)).append(".").append(functionName);
            }
            if (!isProperty) {
                statementBody.append("(");
                StringUtil.join(parameters, ", ", statementBody);
                statementBody.append(")");
            }
        }

        pyFunctionBuilder.statement(statementBody.toString());
        return pyFunctionBuilder;
    }

    // TODO find a better place for this logic
    private static String getReferenceText(PyClass fromClass, PyClass toClass) {
        PyExpression[] superClassExpressions = fromClass.getSuperClassExpressions();
        for (PyExpression expression : superClassExpressions) {
            if (expression instanceof PyReferenceExpression) {
                PsiElement target = ((PyReferenceExpression)expression).getReference().resolve();
                if (target == toClass) {
                    return expression.getText();
                }
            }
        }
        return toClass.getName();
    }
}
