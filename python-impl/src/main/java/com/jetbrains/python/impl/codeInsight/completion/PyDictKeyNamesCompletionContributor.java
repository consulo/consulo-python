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
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.AllIcons;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.editor.completion.*;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ProcessingContext;
import consulo.util.lang.StringUtil;

import jakarta.annotation.Nonnull;

import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * User: catherine
 * <p>
 * Complete known keys for dictionaries
 */
@ExtensionImpl
public class PyDictKeyNamesCompletionContributor extends CompletionContributor {
  public PyDictKeyNamesCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().inside(PySubscriptionExpression.class), new CompletionProvider() {
      @Override
      public void addCompletions(@Nonnull final CompletionParameters parameters,
                                 final ProcessingContext context,
                                 @Nonnull final CompletionResultSet result) {
        final PsiElement original = parameters.getOriginalPosition();
        final int offset = parameters.getOffset();
        if (original == null) {
          return;
        }
        final CompletionResultSet dictCompletion = createResult(original, result, offset);

        PySubscriptionExpression subscription = PsiTreeUtil.getParentOfType(original, PySubscriptionExpression.class);
        if (subscription == null) {
          return;
        }
        PsiElement operand = subscription.getOperand();
        PsiReference reference = operand.getReference();
        if (reference != null) {
          PsiElement resolvedElement = reference.resolve();
          if (resolvedElement instanceof PyTargetExpression) {
            PyDictLiteralExpression dict = PsiTreeUtil.getNextSiblingOfType(resolvedElement, PyDictLiteralExpression.class);
            if (dict != null) {
              addDictLiteralKeys(dict, dictCompletion);
              PsiFile file = parameters.getOriginalFile();
              addAdditionalKeys(file, operand, dictCompletion);
            }
            PyCallExpression dictConstructor = PsiTreeUtil.getNextSiblingOfType(resolvedElement, PyCallExpression.class);
            if (dictConstructor != null) {
              addDictConstructorKeys(dictConstructor, dictCompletion);
              PsiFile file = parameters.getOriginalFile();
              addAdditionalKeys(file, operand, dictCompletion);
            }
          }
        }
      }
    });
  }

  /**
   * create completion result with prefix matcher if needed
   *
   * @param original is original element
   * @param result   is initial completion result
   * @param offset
   * @return
   */
  private static CompletionResultSet createResult(@Nonnull final PsiElement original,
                                                  @Nonnull final CompletionResultSet result,
                                                  final int offset) {
    PyStringLiteralExpression prevElement = PsiTreeUtil.getPrevSiblingOfType(original, PyStringLiteralExpression.class);
    if (prevElement != null) {
      ASTNode prevNode = prevElement.getNode();
      if (prevNode != null) {
        if (prevNode.getElementType() != PyTokenTypes.LBRACKET) {
          return result.withPrefixMatcher(findPrefix(prevElement, offset));
        }
      }
    }
    final PsiElement parentElement = original.getParent();
    if (parentElement != null) {
      if (parentElement instanceof PyStringLiteralExpression) {
        return result.withPrefixMatcher(findPrefix((PyElement)parentElement, offset));
      }
    }
    final PyNumericLiteralExpression number =
      PsiTreeUtil.findElementOfClassAtOffset(original.getContainingFile(), offset - 1, PyNumericLiteralExpression.class, false);
    if (number != null) {
      return result.withPrefixMatcher(findPrefix(number, offset));
    }
    return result;
  }

  /**
   * finds prefix. For *'str'* returns just *'str*.
   *
   * @param element to find prefix of
   * @return prefix
   */
  private static String findPrefix(final PyElement element, final int offset) {
    return TextRange.create(element.getTextRange().getStartOffset(), offset).substring(element.getContainingFile().getText());
  }

  /**
   * add keys to completion result from dict constructor
   */
  private static void addDictConstructorKeys(final PyCallExpression dictConstructor, final CompletionResultSet result) {
    final PyExpression callee = dictConstructor.getCallee();
    if (callee == null) {
      return;
    }
    final String name = callee.getText();
    if ("dict".equals(name)) {
      final TypeEvalContext context = TypeEvalContext.codeCompletion(callee.getProject(), callee.getContainingFile());
      final PyType type = context.getType(dictConstructor);
      if (type != null && type.isBuiltin()) {
        final PyArgumentList list = dictConstructor.getArgumentList();
        if (list == null) {
          return;
        }
        final PyExpression[] argumentList = list.getArguments();
        for (final PyExpression argument : argumentList) {
          if (argument instanceof PyKeywordArgument) {
            result.addElement(createElement("'" + ((PyKeywordArgument)argument).getKeyword() + "'"));
          }
        }
      }
    }
  }

  /**
   * add keys from assignment statements
   * For instance, dictionary['b']=b
   *
   * @param file    to get additional keys
   * @param operand is operand of origin element
   * @param result  is completion result set
   */
  private static void addAdditionalKeys(final PsiFile file, final PsiElement operand, final CompletionResultSet result) {
    PySubscriptionExpression[] subscriptionExpressions = PyUtil.getAllChildrenOfType(file, PySubscriptionExpression.class);
    for (PySubscriptionExpression expr : subscriptionExpressions) {
      if (expr.getOperand().getText().equals(operand.getText())) {
        final PsiElement parent = expr.getParent();
        if (parent instanceof PyAssignmentStatement) {
          if (expr.equals(((PyAssignmentStatement)parent).getLeftHandSideExpression())) {
            PyExpression key = expr.getIndexExpression();
            if (key != null) {
              boolean addHandler = PsiTreeUtil.findElementOfClassAtRange(file,
                                                                         key.getTextRange().getStartOffset(),
                                                                         key.getTextRange().getEndOffset(),
                                                                         PyStringLiteralExpression.class)
                != null;
              result.addElement(createElement(key.getText(), addHandler));
            }
          }
        }
      }
    }
  }

  /**
   * add keys from dict literal expression
   */
  public static void addDictLiteralKeys(final PyDictLiteralExpression dict, final CompletionResultSet result) {
    PyKeyValueExpression[] keyValues = dict.getElements();
    for (PyKeyValueExpression expression : keyValues) {
      boolean addHandler = PsiTreeUtil.findElementOfClassAtRange(dict.getContainingFile(),
                                                                 expression.getTextRange().getStartOffset(),
                                                                 expression.getTextRange().getEndOffset(),
                                                                 PyStringLiteralExpression.class) != null;
      result.addElement(createElement(expression.getKey().getText(), addHandler));
    }
  }

  private static LookupElementBuilder createElement(final String key) {
    return createElement(key, true);
  }

  private static LookupElementBuilder createElement(final String key, final boolean addHandler) {
    LookupElementBuilder item;
    item = LookupElementBuilder.create(key).withTypeText("dict key").withIcon(AllIcons.Nodes.Parameter);

    if (addHandler) {
      item = item.withInsertHandler(new InsertHandler<LookupElement>() {
        @Override
        public void handleInsert(final InsertionContext context, final LookupElement item) {
          final PyStringLiteralExpression str =
            PsiTreeUtil.findElementOfClassAtOffset(context.getFile(), context.getStartOffset(), PyStringLiteralExpression.class, false);
          if (str != null) {
            final boolean isDictKeys = PsiTreeUtil.getParentOfType(str, PySubscriptionExpression.class) != null;
            if (isDictKeys) {
              final int off = context.getStartOffset() + str.getTextLength();
              final PsiElement element = context.getFile().findElementAt(off);
              final boolean atRBrace = element == null || element.getNode().getElementType() == PyTokenTypes.RBRACKET;
              final boolean badQuoting = (!StringUtil.startsWithChar(str.getText(), '\'') || !StringUtil.endsWithChar(str.getText(),
                                                                                                                      '\'')) && (!StringUtil
                .startsWithChar(str.getText()
                  , '"') || !StringUtil.endsWithChar(str.getText(), '"'));
              if (badQuoting || !atRBrace) {
                final Document document = context.getEditor().getDocument();
                final int offset = context.getTailOffset();
                document.deleteString(offset - 1, offset);
              }
            }
          }
        }
      });
    }
    return item;
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
