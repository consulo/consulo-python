package com.jetbrains.python.documentation;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import org.jetbrains.annotations.NotNull;

import static com.intellij.patterns.PlatformPatterns.psiElement;

/**
 * @author yole
 */
public class DocStringTagCompletionContributor extends CompletionContributor {
  public static final PsiElementPattern.Capture<PyStringLiteralExpression> DOCSTRING_PATTERN = psiElement(PyStringLiteralExpression.class)
    .withParent(psiElement(PyExpressionStatement.class).inside(PyDocStringOwner.class));

  public DocStringTagCompletionContributor() {
    extend(CompletionType.BASIC, psiElement().withParent(DOCSTRING_PATTERN),
           new CompletionProvider<CompletionParameters>() {
             @Override
             protected void addCompletions(@NotNull CompletionParameters parameters,
                                           ProcessingContext context,
                                           @NotNull CompletionResultSet result) {
               final Module module = ModuleUtilCore.findModuleForPsiElement(parameters.getPosition());
               if (module == null) return;
               final PyDocumentationSettings settings = PyDocumentationSettings.getInstance(module);
               final PsiFile file = parameters.getOriginalFile();
               if (settings.isEpydocFormat(file) || settings.isReSTFormat(file)) {
                 int offset = parameters.getOffset();
                 final String text = file.getText();
                 char prefix = settings.isEpydocFormat(file) ? '@' : ':';
                 if (offset > 0) {
                   offset--;
                 }
                 StringBuilder prefixBuilder = new StringBuilder();
                 while(offset > 0 && (Character.isLetterOrDigit(text.charAt(offset)) || text.charAt(offset) == prefix)) {
                   prefixBuilder.insert(0, text.charAt(offset));
                   if (text.charAt(offset) == prefix) {
                     offset--;
                     break;
                   }
                   offset--;
                 }
                 while(offset > 0) {
                   offset--;
                   if (text.charAt(offset) == '\n' || text.charAt(offset) == '\"' || text.charAt(offset) == '\'') {
                     break;
                   }
                   if (!Character.isWhitespace(text.charAt(offset))) {
                     return;
                   }
                 }
                 String[] allTags = settings.isEpydocFormat(file) ? EpydocString.ALL_TAGS : SphinxDocString.ALL_TAGS;
                 if (prefixBuilder.length() > 0) {
                   result = result.withPrefixMatcher(prefixBuilder.toString());
                 }
                 for (String tag : allTags) {
                   result.addElement(LookupElementBuilder.create(tag));
                 }
               }
             }
           }
           );
  }
}
