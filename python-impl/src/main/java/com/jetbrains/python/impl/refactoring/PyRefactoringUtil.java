// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.refactoring;

import consulo.language.editor.PsiEquivalenceUtil;
import consulo.project.Project;
import consulo.util.lang.Comparing;
import consulo.util.lang.Pair;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiComment;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiWhiteSpace;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.PsiUtilCore;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.codeInsight.PyPsiIndexUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import consulo.language.psi.PsiNamedElement;
import consulo.usage.UsageInfo;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyElementVisitor;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyParameter;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.PyStatementList;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.impl.refactoring.introduce.IntroduceValidator;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

public final class PyRefactoringUtil {
    private PyRefactoringUtil() {
    }

    public static List<PsiElement> getOccurrences(PsiElement pattern, @Nullable PsiElement context) {
        if (context == null) {
            return Collections.emptyList();
        }
        List<PsiElement> occurrences = new ArrayList<>();
        PyElementVisitor visitor = new PyElementVisitor() {
            @Override
            public void visitElement(PsiElement element) {
                if (element instanceof PyParameter) {
                    return;
                }
                if (PsiEquivalenceUtil.areElementsEquivalent(element, pattern)) {
                    occurrences.add(element);
                    return;
                }
                if (element instanceof PyStringLiteralExpression) {
                    Pair<PsiElement, TextRange> selection = pattern.getUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE);
                    if (selection != null) {
                        String substring = selection.getSecond().substring(pattern.getText());
                        PyStringLiteralExpression expr = (PyStringLiteralExpression)element;
                        String text = element.getText();
                        if (text != null && expr.getStringNodes().size() == 1) {
                            final int start = text.indexOf(substring);
                            if (start >= 0) {
                                element.putUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE,
                                                                    Pair.create(element, TextRange.from(start, substring.length())));
                                occurrences.add(element);
                                return;
                            }
                        }
                    }
                }
                element.acceptChildren(this);
            }
        };
        context.acceptChildren(visitor);
        return occurrences;
    }

    public static @Nullable PyExpression getSelectedExpression(Project project,
                                                                 PsiFile file,
                                                                 PsiElement element1,
                                                                 PsiElement element2) {
        PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
        if (parent != null && !(parent instanceof PyElement)) {
            parent = PsiTreeUtil.getParentOfType(parent, PyElement.class);
        }
        if (parent == null) {
            return null;
        }

        // Allow function call where arguments are on multiple lines
        if (parent instanceof PyArgumentList || parent instanceof PyKeywordArgument) {
            PyCallExpression callExpression = PsiTreeUtil.getParentOfType(parent, PyCallExpression.class);
            if (callExpression != null) {
                return callExpression;
            }
        }

        // If it is PyIfPart for example, parent if statement, we should deny
        if (!(parent instanceof PyExpression)) {
            return null;
        }
        // We cannot extract anything within import statements
        if (PsiTreeUtil.getParentOfType(parent, PyImportStatement.class, PyFromImportStatement.class) != null) {
            return null;
        }
        if ((element1 == PsiTreeUtil.getDeepestFirst(parent)) && (element2 == PsiTreeUtil.getDeepestLast(parent))) {
            return (PyExpression)parent;
        }

        // Check if selection breaks AST node in binary expression
        if (parent instanceof PyBinaryExpression) {
            String selection = file.getText().substring(element1.getTextOffset(), element2.getTextOffset() + element2.getTextLength());
            PyElementGenerator generator = PyElementGenerator.getInstance(project);
            LanguageLevel langLevel = LanguageLevel.forElement(element1);
            PyExpression expression = generator.createFromText(langLevel, PyAssignmentStatement.class, "z=" + selection).getAssignedValue();
            if (!(expression instanceof PyBinaryExpression) || PsiUtilCore.hasErrorElementChild(expression)) {
                return null;
            }
            String parentText = parent.getText();
            final int startOffset = element1.getTextOffset() - parent.getTextOffset() - 1;
            if (startOffset < 0) {
                return null;
            }
            final int endOffset = element2.getTextOffset() + element2.getTextLength() - parent.getTextOffset();

            String prefix = parentText.substring(0, startOffset);
            String suffix = parentText.substring(endOffset);
            TextRange textRange = TextRange.from(startOffset, endOffset - startOffset);
            PsiElement fakeExpression = generator.createExpressionFromText(langLevel, prefix + "python" + suffix);
            if (PsiUtilCore.hasErrorElementChild(fakeExpression)) {
                return null;
            }

            expression.putUserData(PyReplaceExpressionUtil.SELECTION_BREAKS_AST_NODE, Pair.create(parent, textRange));
            return expression;
        }
        return null;
    }

    public static @Nullable PsiElement findExpressionInRange(PsiFile file, int startOffset, int endOffset) {
        PsiElement element1 = file.findElementAt(startOffset);
        PsiElement element2 = file.findElementAt(endOffset - 1);
        if (element1 instanceof PsiWhiteSpace) {
            startOffset = element1.getTextRange().getEndOffset();
            element1 = file.findElementAt(startOffset);
        }
        if (element2 instanceof PsiWhiteSpace) {
            endOffset = element2.getTextRange().getStartOffset();
            element2 = file.findElementAt(endOffset - 1);
        }
        if (element1 == null || element2 == null) {
            return null;
        }
        return getSelectedExpression(file.getProject(), file, element1, element2);
    }

    public static PsiElement[] findStatementsInRange(PsiFile file, int startOffset, int endOffset) {
        ArrayList<PsiElement> array = new ArrayList<>();

        PsiElement element1 = file.findElementAt(startOffset);
        PsiElement element2 = file.findElementAt(endOffset - 1);
        PsiElement endComment = null;

        boolean startsWithWhitespace = false;
        boolean endsWithWhitespace = false;
        if (element1 instanceof PsiWhiteSpace) {
            startOffset = element1.getTextRange().getEndOffset();
            element1 = file.findElementAt(startOffset);
            startsWithWhitespace = true;
        }
        if (element2 instanceof PsiWhiteSpace) {
            element2 = PsiTreeUtil.skipWhitespacesBackward(element2);
            endsWithWhitespace = true;
        }
        while (element2 instanceof PsiComment) {
            endComment = element2;
            element2 = PsiTreeUtil.skipWhitespacesAndCommentsBackward(element2);
            endsWithWhitespace = true;
        }

        while (element1 instanceof PsiComment) {
            array.add(element1);
            element1 = PsiTreeUtil.skipWhitespacesForward(element1);
            startsWithWhitespace = true;
        }

        if (element1 == null || element2 == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        PsiElement parent = PsiTreeUtil.findCommonParent(element1, element2);
        if (parent == null) {
            return PsiElement.EMPTY_ARRAY;
        }

        while (true) {
            if (parent instanceof PyStatement) {
                parent = parent.getParent();
                break;
            }
            if (parent instanceof PyStatementList) {
                break;
            }
            if (parent == null || parent instanceof PsiFile) {
                return PsiElement.EMPTY_ARRAY;
            }
            parent = parent.getParent();
        }

        if (!parent.equals(element1)) {
            while (!parent.equals(element1.getParent())) {
                element1 = element1.getParent();
            }
        }
        if (startOffset != element1.getTextRange().getStartOffset() && !startsWithWhitespace) {
            return PsiElement.EMPTY_ARRAY;
        }

        if (!parent.equals(element2)) {
            while (!parent.equals(element2.getParent())) {
                element2 = element2.getParent();
            }
        }
        if (endOffset != element2.getTextRange().getEndOffset() && !endsWithWhitespace) {
            return PsiElement.EMPTY_ARRAY;
        }

        if (element1 instanceof PyFunction || element1 instanceof PyClass) {
            return PsiElement.EMPTY_ARRAY;
        }
        if (element2 instanceof PyFunction || element2 instanceof PyClass) {
            return PsiElement.EMPTY_ARRAY;
        }

        PsiElement[] children = parent.getChildren();

        boolean flag = false;
        for (PsiElement child : children) {
            if (child.equals(element1)) {
                flag = true;
            }
            if (flag && !(child instanceof PsiWhiteSpace)) {
                array.add(child);
            }
            if (child.equals(element2)) {
                break;
            }
        }

        while (endComment instanceof PsiComment) {
            array.add(endComment);
            endComment = PsiTreeUtil.skipWhitespacesForward(endComment);
        }

        for (PsiElement element : array) {
            if (!(element instanceof PyStatement || element instanceof PsiWhiteSpace || element instanceof PsiComment)) {
                return PsiElement.EMPTY_ARRAY;
            }
        }
        return PsiUtilCore.toPsiElementArray(array);
    }

    public static boolean areConflictingMethods(PyFunction pyFunction, PyFunction pyFunction1) {
        PyParameter[] firstParams = pyFunction.getParameterList().getParameters();
        PyParameter[] secondParams = pyFunction1.getParameterList().getParameters();
        String firstName = pyFunction.getName();
        String secondName = pyFunction1.getName();

        return Comparing.strEqual(firstName, secondName) && firstParams.length == secondParams.length;
    }

    /**
     * Selects the shortest unique name inside the scope of scopeAnchor generated using {@link NameSuggesterUtil#generateNamesByType(String)}.
     * If none of those names is suitable, unique names is made by appending number suffix.
     *
     * @param typeName    initial type name for generator
     * @param scopeAnchor PSI element used to determine correct scope
     * @return unique name in the scope of scopeAnchor
     */
    public static String selectUniqueNameFromType(String typeName, PsiElement scopeAnchor) {
        return selectUniqueName(typeName, true, scopeAnchor, PyRefactoringUtil::isValidNewName);
    }

    /**
     * Selects the shortest unique name inside the scope of scopeAnchor generated using {@link NameSuggesterUtil#generateNames(String)}.
     * If none of those names is suitable, unique names is made by appending number suffix.
     *
     * @param templateName initial template name for generator
     * @param scopeAnchor  PSI element used to determine correct scope
     * @return unique name in the scope of scopeAnchor
     */
    public static String selectUniqueName(String templateName, PsiElement scopeAnchor) {
        return selectUniqueName(templateName, false, scopeAnchor, PyRefactoringUtil::isValidNewName);
    }

    public static String selectUniqueName(String templateName,
                                                     PsiElement scopeAnchor,
                                                     BiPredicate<String, PsiElement> isValid) {
        return selectUniqueName(templateName, false, scopeAnchor, isValid);
    }

    private static String selectUniqueName(String templateName,
                                                      boolean templateIsType,
                                                      PsiElement scopeAnchor,
                                                      BiPredicate<String, PsiElement> isValid) {
        Collection<String> suggestions;
        if (templateIsType) {
            suggestions = NameSuggesterUtil.generateNamesByType(templateName);
        }
        else {
            suggestions = NameSuggesterUtil.generateNames(templateName);
        }
        for (String name : suggestions) {
            if (isValid.test(name, scopeAnchor)) {
                return name;
            }
        }

        String shortestName = ContainerUtil.getFirstItem(suggestions);
        return appendNumberUntilValid(shortestName, scopeAnchor, isValid);
    }

    /**
     * Appends increasing numbers starting from 1 to the name until it becomes unique within the scope of the scopeAnchor.
     *
     * @param name        initial name
     * @param scopeAnchor PSI element used to determine correct scope
     * @param predicate   used to test if suggested name is valid
     * @return unique name in the scope probably with number suffix appended
     */
    public static String appendNumberUntilValid(String name,
                                                           PsiElement scopeAnchor,
                                                           BiPredicate<String, PsiElement> predicate) {
        int counter = 1;
        String candidate = name;
        while (!predicate.test(candidate, scopeAnchor)) {
            candidate = name + counter;
            counter++;
        }
        return candidate;
    }

    public static boolean isValidNewName(String name, PsiElement scopeAnchor) {
        return !(IntroduceValidator.isDefinedInScope(name, scopeAnchor) || PyNames.isReserved(name));
    }

    public static @Nullable <T extends PsiElement> T findSameElementForPreview(@Nullable SmartPsiElementPointer<T> pointer,
                                                                                 PsiFile previewFile) {
        if (pointer == null) return null;
        T element = pointer.getElement();
        if (element == null) return null;
        return PsiTreeUtil.findSameElementInCopy(element, previewFile);
    }
    /**
     * Consulo: kept for compatibility, JB moved it to {@link PyPsiIndexUtil#findUsages(PsiNamedElement, boolean)}
     */
    @Deprecated
    public static List<UsageInfo> findUsages(PsiNamedElement element, boolean forHighlightUsages) {
        return PyPsiIndexUtil.findUsages(element, forHighlightUsages);
    }

    /**
     * Consulo: kept for compatibility, JB moved it to {@link PyUtil#collectUsedNames(PsiElement)}
     */
    @Deprecated
    public static Collection<String> collectUsedNames(@Nullable PsiElement scope) {
        return PyUtil.collectUsedNames(scope);
    }
}
