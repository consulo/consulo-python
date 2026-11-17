// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.PyStringFormatParser;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyAssignmentStatement;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyDictLiteralExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyKeyValueExpression;
import com.jetbrains.python.psi.PyKeywordArgument;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PySetLiteralExpression;
import com.jetbrains.python.psi.PyStarArgument;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.PyTargetExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pattern.PatternCondition;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.lang.ObjectUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static consulo.language.pattern.PlatformPatterns.psiElement;
import static consulo.language.pattern.StandardPatterns.or;

@ExtensionImpl
public final class PyStringFormatCompletionContributor extends CompletionContributor implements DumbAware {
    private static final String DICT_NAME = "dict";

    private static final PatternCondition<PyReferenceExpression> FORMAT_CALL_PATTERN_CONDITION =
      new PatternCondition<>("isFormatFunction") {

          @Override
          public boolean accepts(PyReferenceExpression expression, ProcessingContext context) {
              String expressionName = expression.getName();
              return expressionName != null && expressionName.equals(PyNames.FORMAT);
          }
      };

    private static final PatternCondition<PyReferenceExpression> DICT_CALL_PATTERN_CONDITION =
      new PatternCondition<>("isDictCall") {

          @Override
          public boolean accepts(PyReferenceExpression expression, ProcessingContext context) {
              String expressionName = expression.getName();
              return expressionName != null && expressionName.equals(DICT_NAME);
          }
      };

    public static final PsiElementPattern.Capture<PyStringLiteralExpression> FORMAT_STRING_CAPTURE =
      psiElement(PyStringLiteralExpression.class)
        .withParent(psiElement(PyReferenceExpression.class).with(FORMAT_CALL_PATTERN_CONDITION))
        .withSuperParent(2, PyCallExpression.class);

    private static final PsiElementPattern.Capture<PyStringLiteralExpression> PERCENT_STRING_CAPTURE =
      psiElement(PyStringLiteralExpression.class).beforeLeaf(psiElement().withText("%")).withParent(PyBinaryExpression.class);


    private static final @Nullable PatternCondition<PyBinaryExpression> PERCENT_BINARY_EXPRESSION_PATTERN =
      new PatternCondition<>("isBinaryFormatExpression") {
          @Override
          public boolean accepts(PyBinaryExpression expression, ProcessingContext context) {
              return expression.isOperator("%");
          }
      };

    private static final PsiElementPattern.Capture<PyKeywordArgument> DICT_FUNCTION_KEYWORD_ARGUMENT_CAPTURE =
      (psiElement(PyKeywordArgument.class))
        .withSuperParent(3,
                         psiElement(PyBinaryExpression.class)
                           .withChild(psiElement(PyCallExpression.class)
                                        .withChild(psiElement(PyReferenceExpression.class)
                                                     .with(DICT_CALL_PATTERN_CONDITION)))
                           .with(PERCENT_BINARY_EXPRESSION_PATTERN));

    private static final PsiElementPattern.Capture<PyReferenceExpression> DICT_FUNCTION_REFERENCE_ARGUMENT_CAPTURE =
      (psiElement(PyReferenceExpression.class))
        .withSuperParent(3,
                         psiElement(PyBinaryExpression.class)
                           .withChild(psiElement(PyCallExpression.class)
                                        .withChild(psiElement(PyReferenceExpression.class)
                                                     .with(DICT_CALL_PATTERN_CONDITION)))
                           .with(PERCENT_BINARY_EXPRESSION_PATTERN));

    private static final PsiElementPattern.Capture<PyKeywordArgument> FORMAT_FUNCTION_ARGUMENT_CAPTURE =
      psiElement(PyKeywordArgument.class)
        .withSuperParent(2, psiElement(PyCallExpression.class)
          .withChild(psiElement(PyReferenceExpression.class).with(FORMAT_CALL_PATTERN_CONDITION)));

    // to provide completion for: "{foo}".format(fo<caret>)
    private static final PsiElementPattern.Capture<PyReferenceExpression> FORMAT_FUNCTION_REFERENCE_ARGUMENT_CAPTURE =
      psiElement(PyReferenceExpression.class)
        .withSuperParent(2, psiElement(PyCallExpression.class)
          .withChild(psiElement(PyReferenceExpression.class).with(FORMAT_CALL_PATTERN_CONDITION)));

    private static final PsiElementPattern.Capture<PyStringLiteralExpression> DICT_LITERAL_STRING_KEY_CAPTURE =
      psiElement(PyStringLiteralExpression.class)
        .withParent(or(psiElement(PyKeyValueExpression.class)
                         .withParent(psiElement(PyDictLiteralExpression.class)
                                       .withParent(psiElement(PyBinaryExpression.class).with(PERCENT_BINARY_EXPRESSION_PATTERN))),
                       psiElement(PyDictLiteralExpression.class)
                         .withParent(psiElement(PyBinaryExpression.class).with(PERCENT_BINARY_EXPRESSION_PATTERN))));

    // to provide completion for: "%(foo)s % {"f<caret>"}
    private static final PsiElementPattern.Capture<PyStringLiteralExpression> SET_LITERAL_STRING_KEY_CAPTURE =
      psiElement(PyStringLiteralExpression.class)
        .withParent(psiElement(PySetLiteralExpression.class).withParent(psiElement(PyBinaryExpression.class)
                                                                          .with(PERCENT_BINARY_EXPRESSION_PATTERN)));

    public PyStringFormatCompletionContributor() {
        extend(
          CompletionType.BASIC,
          or(
            psiElement().inside(PERCENT_STRING_CAPTURE),
            psiElement().inside(FORMAT_STRING_CAPTURE)),
          new StringFormatCompletionProvider()
        );

        extend(
          CompletionType.BASIC,
          or(psiElement().inside(DICT_LITERAL_STRING_KEY_CAPTURE),
             psiElement().inside(DICT_FUNCTION_KEYWORD_ARGUMENT_CAPTURE),
             psiElement().inside(DICT_FUNCTION_REFERENCE_ARGUMENT_CAPTURE),
             psiElement().inside(SET_LITERAL_STRING_KEY_CAPTURE),
             psiElement().inside(FORMAT_FUNCTION_ARGUMENT_CAPTURE),
             psiElement().inside(FORMAT_FUNCTION_REFERENCE_ARGUMENT_CAPTURE)
          ),
          new StringFormatArgumentsCompletionProvider()
        );
    }

    private static class StringFormatArgumentsCompletionProvider implements CompletionProvider {

        @Override
        @RequiredReadAction
        public void addCompletions(CompletionParameters parameters,
                                         ProcessingContext context,
                                         CompletionResultSet result) {
            PsiElement original = parameters.getOriginalPosition();
            if (original != null) {
                CompletionResultSet prefixedResult = result.withPrefixMatcher(getPrefix(parameters.getOffset(), parameters.getOriginalFile()));
                PsiElement parent = original.getParent();
                if (parent.getParent() instanceof PyKeyValueExpression || parent instanceof PyStringLiteralExpression) {
                    PyBinaryExpression binExpr = PsiTreeUtil.getParentOfType(parent, PyBinaryExpression.class);
                    if (binExpr != null) {
                        PyStringLiteralExpression strExpr = PyUtil.as(binExpr.getLeftExpression(), PyStringLiteralExpression.class);
                        if (strExpr != null) {
                            prefixedResult.addAllElements(getPercentLookupBuilders(strExpr));
                        }
                    }
                }
                else if (PsiTreeUtil.instanceOf(parent, PyKeywordArgument.class, PyReferenceExpression.class)) {
                    prefixedResult.addAllElements(getElementsFromString(PsiTreeUtil.getParentOfType(original, PyArgumentList.class)));
                }
            }
        }

        private static List<LookupElement> getElementsFromString(@Nullable PyArgumentList argumentList) {
            if (argumentList != null) {
                PyReferenceExpression refExpr = PsiTreeUtil.getPrevSiblingOfType(argumentList, PyReferenceExpression.class);
                PyStringLiteralExpression strExpr = PsiTreeUtil.getChildOfType(refExpr, PyStringLiteralExpression.class);
                if (strExpr != null) {
                    return getFormatLookupBuilders(strExpr);
                }
                else {
                    PyBinaryExpression binExpr = PsiTreeUtil.getParentOfType(refExpr, PyBinaryExpression.class);
                    if (binExpr != null) {
                        PyStringLiteralExpression stringLiteralExpr = PyUtil.as(binExpr.getLeftExpression(), PyStringLiteralExpression.class);
                        if (stringLiteralExpr != null) {
                            return getPercentLookupBuilders(stringLiteralExpr);
                        }
                    }
                }
            }
            return Collections.emptyList();
        }

        private static List<LookupElement> getFormatLookupBuilders(PyStringLiteralExpression expression) {
            Map<String, PyStringFormatParser.SubstitutionChunk> chunks = PyStringFormatParser.getKeywordSubstitutions(
              PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parseNewStyleFormat(expression.getText())));
            return getLookupBuilders(chunks);
        }

        private static List<LookupElement> getPercentLookupBuilders(PyStringLiteralExpression expression) {
            Map<String, PyStringFormatParser.SubstitutionChunk> chunks = PyStringFormatParser.getKeywordSubstitutions(
              PyStringFormatParser.filterSubstitutions(PyStringFormatParser.parsePercentFormat(expression.getText())));
            return getLookupBuilders(chunks);
        }

        private static List<LookupElement> getLookupBuilders(Map<String, PyStringFormatParser.SubstitutionChunk> chunks) {
            return chunks.keySet().stream()
              .map(PyStringFormatCompletionContributor::createLookUpElement)
              .collect(Collectors.toList());
        }
    }

    private static class StringFormatCompletionProvider implements CompletionProvider {
        @Override
        @RequiredReadAction
        public void addCompletions(CompletionParameters parameters,
                                         ProcessingContext context,
                                         CompletionResultSet result) {
            PsiElement original = parameters.getOriginalPosition();
            if (original != null) {
                PsiElement parent = original.getParent();
                CompletionResultSet prefixedResult = result.withPrefixMatcher(getPrefix(parameters.getOffset(), parent.getContainingFile()));

                if (parent instanceof PyStringLiteralExpression) {
                    prefixedResult.addAllElements(addCompletionsForSubstitutions(parameters, original, (PyStringLiteralExpression)parent));
                }
            }
        }

        private static List<LookupElement> addCompletionsForSubstitutions(CompletionParameters parameters,
                                                                                   PsiElement original,
                                                                                   PyStringLiteralExpression stringExpression) {
            int stringOffset = getCaretStartOffsetInsideString(parameters, stringExpression);

            if (isInsideFormatSubstitutionChunk(stringExpression, stringOffset)) {
                PyExpression[] arguments = getFormatFunctionKeyWordArguments(original);
                List<LookupElement> elements = new ArrayList<>();
                for (PyExpression argument : arguments) {
                    if (argument instanceof PyKeywordArgument) {
                        elements.add(getKeywordArgument((PyKeywordArgument)argument));
                    }
                    else if (argument instanceof PyStarArgument) {
                        elements.addAll(getKeysFromStarArgument((PyStarArgument)argument));
                    }
                }
                return elements;
            }

            if (isInsidePercentSubstitutionChunk(stringExpression, stringOffset)) {
                PyBinaryExpression binExpr = PyUtil.as(PsiTreeUtil.getParentOfType(stringExpression, PyBinaryExpression.class),
                                                             PyBinaryExpression.class);
                if (binExpr != null) {
                    PyExpression rightExpr = PyPsiUtils.flattenParens(binExpr.getRightExpression());

                    PyDictLiteralExpression dict = PyUtil.as(rightExpr, PyDictLiteralExpression.class);
                    if (dict != null) {
                        return getElementsFromDict(dict);
                    }

                    PyCallExpression callExpression = PyUtil.as(rightExpr, PyCallExpression.class);
                    if (callExpression != null) {
                        PyExpression callee = callExpression.getCallee();
                        if (callee != null && callee.getName() != null && callee.getName().equals(DICT_NAME)) {
                            PyExpression[] arguments = callExpression.getArguments();
                            return Arrays.stream(arguments)
                              .filter(a -> a instanceof PyKeywordArgument)
                              .map(a -> getKeywordArgument((PyKeywordArgument)a))
                              .filter(e -> e != null)
                              .collect(Collectors.toList());
                        }
                    }
                }
            }
            return Collections.emptyList();
        }

        private static int getCaretStartOffsetInsideString(CompletionParameters parameters,
                                                           PyStringLiteralExpression parent) {
            int caretAbsoluteOffset = parameters.getOffset();
            int stringExprStartOffset = parameters.getPosition().getTextRange().getStartOffset();
            int stringValueStartOffset = parent.getStringValueTextRange().getStartOffset();
            return caretAbsoluteOffset - stringExprStartOffset - stringValueStartOffset;
        }

        private static boolean isInsideFormatSubstitutionChunk(PyStringLiteralExpression expression, int offset) {
            List<PyStringFormatParser.SubstitutionChunk> substitutions = PyStringFormatParser.filterSubstitutions(
              PyStringFormatParser.parseNewStyleFormat(expression.getStringValue()));
            return isInsideSubstitutionChunk(offset, substitutions);
        }

        private static boolean isInsidePercentSubstitutionChunk(PyStringLiteralExpression expression, int offset) {
            List<PyStringFormatParser.SubstitutionChunk> substitutions = PyStringFormatParser.filterSubstitutions(
              PyStringFormatParser.parsePercentFormat(expression.getStringValue()));
            return isInsideSubstitutionChunk(offset, substitutions);
        }

        private static boolean isInsideSubstitutionChunk(int offset, List<PyStringFormatParser.SubstitutionChunk> substitutions) {
            return substitutions.stream().anyMatch(s -> offset >= s.getStartIndex() && offset <= s.getEndIndex());
        }

        private static PyExpression [] getFormatFunctionKeyWordArguments(PsiElement original) {
            PsiElement pyReferenceExpression = PsiTreeUtil.getParentOfType(original, PyReferenceExpression.class);
            PyArgumentList argumentList = PsiTreeUtil.getNextSiblingOfType(pyReferenceExpression, PyArgumentList.class);
            return argumentList != null ? argumentList.getArguments() : PyExpression.EMPTY_ARRAY;
        }

        private static List<LookupElement> getKeysFromStarArgument(PyStarArgument arg) {
            PyDictLiteralExpression dict = ObjectUtil.chooseNotNull(PsiTreeUtil.getChildOfType(arg, PyDictLiteralExpression.class),
                                                                           getDictFromReference(arg));

            return dict != null ? getElementsFromDict(dict) : Collections.emptyList();
        }

        private static List<LookupElement> getElementsFromDict(PyDictLiteralExpression dict) {
            return Arrays.stream(dict.getElements())
              .map(e -> PyUtil.as(e.getKey(), PyStringLiteralExpression.class))
              .filter(k -> k != null)
              .map(k -> createLookUpElement(k.getStringValue()))
              .collect(Collectors.toList());
        }

        private static @Nullable PyDictLiteralExpression getDictFromReference(PyExpression arg) {
            PyReferenceExpression referenceExpression = PsiTreeUtil.getChildOfType(arg, PyReferenceExpression.class);
            if (referenceExpression != null) {
                PsiElement resolveResult = referenceExpression.getReference().resolve();
                if (resolveResult instanceof PyTargetExpression) {
                    PyAssignmentStatement assignmentStatement = PsiTreeUtil.getParentOfType(resolveResult, PyAssignmentStatement.class);
                    return PsiTreeUtil.getChildOfType(assignmentStatement, PyDictLiteralExpression.class);
                }
            }
            return null;
        }

        private static @Nullable LookupElement getKeywordArgument(PyKeywordArgument arg) {
            String keyword = arg.getKeyword();
            return keyword != null ? createLookUpElement(keyword) : null;
        }
    }

    private static LookupElement createLookUpElement(String element) {
        return LookupElementBuilder
          .create(element)
          .withTypeText("arg");
    }

    private static String getPrefix(int offset, PsiFile file) {
        int currentOffset = offset;
        if (currentOffset > 0) {
            currentOffset--;
        }
        String text = file.getText();
        StringBuilder prefixBuilder = new StringBuilder();
        while (currentOffset > 0 && Character.isLetterOrDigit(text.charAt(currentOffset))) {
            prefixBuilder.insert(0, text.charAt(currentOffset));
            currentOffset--;
        }
        return prefixBuilder.toString();
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
