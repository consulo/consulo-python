// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.intentions;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.CodeInsightUtilCore;
import consulo.language.editor.intention.IntentionMetaData;
import consulo.language.editor.template.TemplateBuilder;
import consulo.language.editor.template.TemplateBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.IncorrectOperationException;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

/**
 * Helps to specify type by assertion
 *
 * @author ktisha
 */
@ExtensionImpl
@IntentionMetaData(ignoreId = "python.TypeAssertionIntention", categories = "Python", fileExtensions = "py")
public final class TypeAssertionIntention extends PyBaseIntentionAction {
    @Override
    public LocalizeValue getText() {
        return PyLocalize.intnInsertAssertion();
    }

    public boolean isAvailable(Project project, Editor editor, PsiFile file) {
        if (!(file instanceof PyFile)) {
            return false;
        }

        PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
        PyExpression problemElement = PsiTreeUtil.getParentOfType(elementAt, PyReferenceExpression.class);
        if (problemElement == null) {
            return false;
        }
        if (problemElement.getParent() instanceof PyWithItem) {
            return false;
        }
        PyExpression qualifier = ((PyQualifiedExpression) problemElement).getQualifier();
        if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) {
            problemElement = qualifier;
        }
        PsiReference reference = problemElement.getReference();
        if (problemElement.getParent() instanceof PyCallExpression ||
            PsiTreeUtil.getParentOfType(problemElement, PyComprehensionElement.class) != null ||
            PsiTreeUtil.getParentOfType(problemElement, PyLambdaExpression.class) != null ||
            PsiTreeUtil.getParentOfType(problemElement, PyGeneratorExpression.class) != null ||
            (reference != null && reference.resolve() == null)) {
            return false;
        }
        PyType type = TypeEvalContext.codeAnalysis(file.getProject(), file).getType(problemElement);
        return isUnknown(type);
    }

    @Override
    public void doInvoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
        PsiElement elementAt = PyUtil.findNonWhitespaceAtOffset(file, editor.getCaretModel().getOffset());
        PyQualifiedExpression problemElement = PsiTreeUtil.getParentOfType(elementAt, PyReferenceExpression.class);
        if (problemElement != null) {
            PyElementGenerator elementGenerator = PyElementGenerator.getInstance(project);

            String name = problemElement.getText();
            PyExpression qualifier = problemElement.getQualifier();
            if (qualifier != null && !qualifier.getText().equals(PyNames.CANONICAL_SELF)) {
                String referencedName = problemElement.getReferencedName();
                if (referencedName == null || PyNames.GETITEM.equals(referencedName)) {
                    name = qualifier.getText();
                }
            }

            String text = "assert isinstance(" + name + ", )";
            PyAssertStatement assertStatement =
                elementGenerator.createFromText(LanguageLevel.forElement(problemElement), PyAssertStatement.class, text);

            PsiElement parentStatement = PsiTreeUtil.getParentOfType(problemElement, PyStatement.class);
            if (parentStatement == null) {
                return;
            }
            PsiElement parent = parentStatement.getParent();
            PsiElement element;
            if (parentStatement instanceof PyAssignmentStatement && ((PyAssignmentStatement) parentStatement).getTargets()[0] == problemElement) {
                element = parent.addAfter(assertStatement, parentStatement);
            }
            else {
                PyStatementList statementList = PsiTreeUtil.getParentOfType(parentStatement, PyStatementList.class);
                Document document = editor.getDocument();

                if (statementList != null) {
                    PsiElement statementListParent = PsiTreeUtil.getParentOfType(statementList, PyStatement.class);
                    if (statementListParent != null && document.getLineNumber(statementList.getTextOffset()) == document.getLineNumber(
                        statementListParent.getTextOffset())) {
                        String substring =
                            TextRange.create(statementListParent.getTextRange().getStartOffset(), statementList.getTextOffset())
                                .substring(document.getText());
                        PyStatement foo = elementGenerator.createFromText(
                            LanguageLevel.forElement(problemElement),
                            PyStatement.class,
                            substring + "\n\t" +
                                text + "\n\t" + statementList.getText()
                        );

                        statementListParent = statementListParent.replace(foo);
                        statementList = PsiTreeUtil.findChildOfType(statementListParent, PyStatementList.class);
                        assert statementList != null;
                        element = statementList.getStatements()[0];
                    }
                    else {
                        element = parent.addBefore(assertStatement, parentStatement);
                    }
                }
                else {
                    element = parent.addBefore(assertStatement, parentStatement);
                }
            }

            int textOffSet = element.getTextOffset();
            editor.getCaretModel().moveToOffset(textOffSet);

            element = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(element);
            TemplateBuilder builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(element);
            builder.replaceRange(TextRange.create(text.length() - 1, text.length() - 1), PyNames.OBJECT);
            builder.run(editor, true);
        }
    }
}