// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.PyExpectedTypeJudgement;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyLiteralPattern;
import com.jetbrains.python.psi.PyPlainStringElement;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyStringLiteralCoreUtil;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.document.Document;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.CompletionUtilCore;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementDecorator;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.PsiReference;
import consulo.language.util.ProcessingContext;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.CharArrayUtil;
import consulo.util.lang.StringUtil;

import java.util.List;

import static com.jetbrains.python.impl.psi.PyUtil.as;
import static consulo.language.pattern.PlatformPatterns.psiElement;

/**
 * Provides completion results after "{" in ordinary non-formatted string literals as if that literal
 * was a proper f-string and adds a missing "f" prefix and a closing brace on selection of such a variant.
 * <p>
 * For instance, in the next fragment
 * <pre><code>
 * for line in f:
 *     print('# {li&lt;caret&gt;')
 * </code></pre>
 * selecting "line" from the list of completion suggestions will automatically transform it into
 * <pre><code>
 * for line in f:
 *     print(f'# {line&lt;caret&gt;}')
 * </code></pre>
 */
@ExtensionImpl
public final class PyFStringLikeCompletionContributor extends CompletionContributor implements DumbAware {

    private static final PsiElementPattern.Capture<PyPlainStringElement> APPLICABLE_STRING_ELEMENT =
      psiElement(PyPlainStringElement.class)
        .withParent(PyStringLiteralExpression.class)
        .andNot(psiElement().withSuperParent(2, PyLiteralPattern.class))
        .andNot(psiElement().inside(PyStringFormatCompletionContributor.FORMAT_STRING_CAPTURE));

    public PyFStringLikeCompletionContributor() {
        extend(CompletionType.BASIC, APPLICABLE_STRING_ELEMENT, new CompletionProvider() {
            @Override
            @RequiredReadAction
            public void addCompletions(CompletionParameters parameters,
                                             ProcessingContext context,
                                             CompletionResultSet result) {
                PyPlainStringElement stringElem = as(parameters.getPosition(), PyPlainStringElement.class);
                if (stringElem == null || stringElem.isBytes() || stringElem.isUnicode()) {
                    return;
                }
                if (LanguageLevel.forElement(stringElem).isOlderThan(LanguageLevel.PYTHON36)) {
                    return;
                }
                PyStringLiteralExpression stringLiteral = (PyStringLiteralExpression)stringElem.getParent();
                boolean templateStringExpected = isTemplateStringExpected(stringLiteral);
                if (templateStringExpected && LanguageLevel.forElement(stringElem).isOlderThan(LanguageLevel.PYTHON314)) {
                    return;
                }
                String newPrefixChar = templateStringExpected ? "t" : "f";
                String stringElemText = stringElem.getText();
                int offset = parameters.getOffset();
                int stringElemStart = stringElem.getTextRange().getStartOffset();
                int relOffset = offset - stringElemStart;
                int braceOffset = CharArrayUtil.shiftBackwardUntil(stringElemText, relOffset - 1, "{");
                if (braceOffset < 0) {
                    return;
                }
                String completionPrefix = stringElemText.substring(braceOffset + 1, relOffset);
                boolean autoPopupAfterOpeningBrace = completionPrefix.isEmpty() && parameters.isAutoPopup();
                if (autoPopupAfterOpeningBrace) {
                    return;
                }
                boolean prefixCannotStartReference = !completionPrefix.isEmpty() && !PyNames.isIdentifier(completionPrefix);
                if (prefixCannotStartReference) {
                    return;
                }

                String fStringText = new StringBuilder()
                  .append(newPrefixChar).append(stringElemText)
                  .insert(relOffset + 1 + CompletionUtilCore.DUMMY_IDENTIFIER.length(), "} ")
                  .toString();
                PyExpression fString = PyUtil.createExpressionFromFragment(fStringText, stringLiteral.getParent());
                assert fString != null;
                PsiReference reference = fString.findReferenceAt(relOffset + 1);
                if (reference == null) {
                    return;
                }
                List<LookupElement> fStringVariants = ContainerUtil.mapNotNull(reference.getVariants(), v -> as(v, LookupElement.class));
                if (fStringVariants.isEmpty()) {
                    return;
                }
                CompletionResultSet prefixPatchedResultSet = result.withPrefixMatcher(completionPrefix);
                for (LookupElement variant : fStringVariants) {
                    prefixPatchedResultSet.addElement(new LookupElementDecorator<LookupElement>(variant) {
                        @Override
                        public void handleInsert(InsertionContext context) {
                            super.handleInsert(context);
                            Document document = context.getDocument();
                            CharSequence docChars = document.getCharsSequence();
                            int tailOffset = context.getTailOffset();
                            if (!(tailOffset < document.getTextLength() && docChars.charAt(tailOffset) == '}')) {
                                document.insertString(tailOffset, "}");
                            }
                            // It can happen when completion is invoked on multiple carets inside the same string
                            String stringElemPrefix = PyStringLiteralCoreUtil.getPrefix(docChars, stringElemStart);
                            if (!StringUtil.containsIgnoreCase(stringElemPrefix, newPrefixChar)) {
                                document.insertString(stringElemStart, newPrefixChar);
                            }
                        }
                    });
                }
            }
        });
    }

    private static boolean isTemplateStringExpected(PyStringLiteralExpression stringLiteral) {
        TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(stringLiteral.getProject(), stringLiteral.getContainingFile());
        PyPsiFacade psiFacade = PyPsiFacade.getInstance(stringLiteral.getProject());
        PyClass templateClass = psiFacade.createClassByQName(PyNames.TEMPLATELIB_TEMPLATE, stringLiteral);
        if (templateClass == null) {
            return false;
        }
        PyClassType templateType = psiFacade.createClassType(templateClass, false);
        PyType expectedType = PyExpectedTypeJudgement.getExpectedType(stringLiteral, typeEvalContext);
        return templateType.equals(expectedType);
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
