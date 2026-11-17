// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.quickfix;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.PythonTemplateRunner;
import com.jetbrains.python.impl.PythonUiService;
import com.jetbrains.python.impl.psi.impl.ParamHelper;
import com.jetbrains.python.impl.psi.impl.PyFunctionBuilder;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.refactoring.PyPsiRefactoringUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyDecoratorList;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyPrefixExpression;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredWriteAction;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.FileModificationService;
import consulo.language.editor.inspection.LocalQuickFix;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import static com.jetbrains.python.impl.psi.PyUtil.sure;

/**
 * Adds a method foo to class X if X.foo() is unresolved.
 */
public class AddMethodQuickFix implements LocalQuickFix {

    private final String myClassName;
    private final boolean myReplaceUsage;
    private final String myIdentifier;

    public AddMethodQuickFix(String identifier, String className,
            boolean replaceUsage) {
        myIdentifier = identifier;
        myClassName = className;
        myReplaceUsage = replaceUsage;
    }

    @Override
    public LocalizeValue getName() {
        return PyLocalize.qfixAddMethodToClass(myIdentifier, myClassName);
    }

    @Override
    @RequiredWriteAction
    public void applyFix(Project project, ProblemDescriptor descriptor) {
        try {
            // there can be no name clash, else the name would have resolved, and it hasn't.
            PsiElement problemElement = descriptor.getPsiElement();
            PyClassType type = getClassType(problemElement);
            if (type == null) {
                return;
            }
            PyClass cls = type.getPyClass();
            boolean callByClass = type.isDefinition();
            PyStatementList clsStmtList = cls.getStatementList();
            sure(FileModificationService.getInstance().preparePsiElementForWrite(clsStmtList));
            // try to at least match parameter count
            // TODO: get parameter style from code style
            PyFunctionBuilder builder = new PyFunctionBuilder(myIdentifier, cls);
            PsiElement pe = problemElement.getParent();
            String decoratorName = null; // set to non-null to add a decorator
            PyExpression[] args = PyExpression.EMPTY_ARRAY;
            if (pe instanceof PyCallExpression callExpression) {
                PyArgumentList arglist = callExpression.getArgumentList();
                if (arglist == null) {
                    return;
                }
                args = arglist.getArguments();
                if (callExpression.getParent() instanceof PyPrefixExpression prefixExpression &&
                        prefixExpression.getOperator() == PyTokenTypes.AWAIT_KEYWORD) {
                    builder.makeAsync();
                }
            }
            boolean madeInstance = false;
            if (callByClass) {
                if (args.length > 0) {
                    TypeEvalContext context = TypeEvalContext.userInitiated(cls.getProject(), cls.getContainingFile());
                    PyType firstArgType = context.getType(args[0]);
                    if (firstArgType instanceof PyClassType && ((PyClassType)firstArgType).getPyClass().isSubclass(cls, context)) {
                        // class, first arg ok: instance method
                        builder.parameter("self"); // NOTE: might use a name other than 'self', according to code style.
                        madeInstance = true;
                    }
                }
                if (!madeInstance) { // class, first arg absent or of different type: classmethod
                    builder.parameter("cls"); // NOTE: might use a name other than 'cls', according to code style.
                    decoratorName = PyNames.CLASSMETHOD;
                }
            }
            else { // instance method
                builder.parameter("self"); // NOTE: might use a name other than 'self', according to code style.
            }
            boolean skipFirst = callByClass && madeInstance; // ClassFoo.meth(foo_instance)
            for (PyExpression arg : args) {
                if (skipFirst) {
                    skipFirst = false;
                    continue;
                }
                if (arg instanceof PyKeywordArgument) { // foo(bar) -> def foo(self, bar_1)
                    builder.parameter(((PyKeywordArgument)arg).getKeyword());
                }
                else if (arg instanceof PyReferenceExpression refex) {
                    builder.parameter(refex.getReferencedName());
                }
                else { // use a boring name
                    builder.parameter("param");
                }
            }
            PyFunction method = builder.buildFunction();
            if (decoratorName != null) {
                PyElementGenerator generator = PyElementGenerator.getInstance(project);
                PyDecoratorList decoratorList = generator
                    .createFromText(LanguageLevel.getDefault(), PyDecoratorList.class, "@" + decoratorName + "\ndef foo(): pass", new int[]{0, 0});
                method.addBefore(decoratorList, method.getFirstChild()); // in the very beginning
            }

            method = (PyFunction)PyPsiRefactoringUtil.addElementToStatementList(method, clsStmtList, PyNames.INIT.equals(method.getName()));
            if (myReplaceUsage) {
                showTemplateBuilder(method);
            }
        }
        catch (IncorrectOperationException ignored) {
            // we failed. tell about this
            PythonUiService.getInstance().showBalloonError(project, PyLocalize.qfixFailedToAddMethod().get());
        }
    }

    private static PyClassType getClassType(PsiElement problemElement) {
        if ((problemElement instanceof PyQualifiedExpression)) {
            PyExpression qualifier = ((PyQualifiedExpression)problemElement).getQualifier();
            if (qualifier == null) {
                return null;
            }
            PyType type = TypeEvalContext.userInitiated(problemElement.getProject(), problemElement.getContainingFile()).getType(qualifier);
            return type instanceof PyClassType ? (PyClassType)type : null;
        }
        PyClass pyClass = PsiTreeUtil.getParentOfType(problemElement, PyClass.class);
        return pyClass != null ? new PyClassTypeImpl(pyClass, false) : null;
    }

    private static void showTemplateBuilder(PyFunction method) {
        method = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(method);
        PsiFile file = method.getContainingFile();
        if (file == null) {
            return;
        }
        TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(method);
        ParamHelper.walkDownParamArray(
            method.getParameterList().getParameters(),
            new ParamHelper.ParamVisitor() {
                @Override
                public void visitNamedParameter(PyNamedParameter param, boolean first, boolean last) {
                    builder.replaceElement(param, param.getName());
                }
            }
        );

        PyStatementList statementList = method.getStatementList();
        builder.replaceElement(statementList, PyNames.PASS);
        PythonTemplateRunner.runTemplate(file, builder);
    }
}
