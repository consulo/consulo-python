// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.documentation.docstrings.DocStringParameterReference;
import com.jetbrains.python.impl.documentation.docstrings.DocStringTagCompletionContributor;
import com.jetbrains.python.impl.documentation.docstrings.DocStringTypeReference;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.PyDocStringOwner;
import com.jetbrains.python.psi.PyNamedParameter;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionInitializationContext;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.AutoCompletionPolicy;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiMultiReference;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.language.util.ProcessingContext;
import consulo.module.Module;

import java.util.Collection;

import static consulo.language.pattern.PlatformPatterns.psiComment;
import static consulo.language.pattern.PlatformPatterns.psiElement;
import static consulo.language.pattern.StandardPatterns.or;

/**
 * User : ktisha
 */
@ExtensionImpl(id = "PyDocstringCompletionContributor")
public final class PyDocstringCompletionContributor extends CompletionContributor implements DumbAware {
    public PyDocstringCompletionContributor() {
        extend(CompletionType.BASIC,
            or(psiElement().withParent(DocStringTagCompletionContributor.Helper.DOCSTRING_PATTERN), psiComment()),
            new IdentifierCompletionProvider());
    }

    private static class IdentifierCompletionProvider implements CompletionProvider {

        @Override
        @RequiredReadAction
        public void addCompletions(CompletionParameters parameters,
                                   ProcessingContext context,
                                   CompletionResultSet result) {
            PsiElement element = parameters.getOriginalPosition();
            if (element == null) {
                return;
            }
            PsiFile file = element.getContainingFile();
            // Parameter references are filled with DocStringParameterReference#getVariants
            PsiReference reference = file.findReferenceAt(parameters.getOffset());
            if (reference == null) {
                if (parameters.isAutoPopup()) {
                    return;
                }
                PyDocStringOwner docStringOwner = PsiTreeUtil.getParentOfType(element, PyDocStringOwner.class);
                Module module = ModuleUtilCore.findModuleForPsiElement(element);
                if (module != null) {
                    CompletionResultSet prefixedResult = result.withPrefixMatcher(getPrefix(parameters.getOffset(), file));
                    Collection<String> identifiers = PyUtil.collectUsedNames(docStringOwner);
                    for (String identifier : identifiers) {
                        prefixedResult.addElement(LookupElementBuilder.create(identifier)
                            .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE));
                    }

                    Collection<String> fileIdentifiers = PyUtil.collectUsedNames(parameters.getOriginalFile());
                    for (String identifier : fileIdentifiers) {
                        prefixedResult.addElement(LookupElementBuilder.create(identifier)
                            .withAutoCompletionPolicy(AutoCompletionPolicy.NEVER_AUTOCOMPLETE));
                    }
                }
            }
            // For reST declaration like ":param foo: int" "foo" is PsiMultiReference that combines DocStringParameterReference and DocStringTypeReference
            else if (reference instanceof PsiMultiReference) {
                for (PsiReference innerReference : ((PsiMultiReference) reference).getReferences()) {
                    addVariantsFromDocstringReference(innerReference, result);
                }
            }
            else {
                addVariantsFromDocstringReference(reference, result);
            }
        }

        private static void addVariantsFromDocstringReference(PsiReference reference, CompletionResultSet result) {
            if (reference instanceof DocStringParameterReference) {
                for (PyNamedParameter param : ((DocStringParameterReference) reference).collectParameterVariants()) {
                    result.addElement(LookupElementBuilder.createWithIcon(param));
                }
            }
            else if (reference instanceof DocStringTypeReference) {
                for (Object variant : ((DocStringTypeReference) reference).collectTypeVariants()) {
                    if (variant instanceof PsiNamedElement) {
                        result.addElement(LookupElementBuilder.createWithIcon((PsiNamedElement) variant));
                    }
                    else {
                        result.addElement(LookupElementBuilder.create(variant));
                    }
                }
            }
        }
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
    public void beforeCompletion(CompletionInitializationContext context) {
        // With standard dummy identifier inserted, docstring might become malformed
        // e.g. "@param para<caret>m" -> "@param paraIntellijIdeaRulezzz m"
        // and param is no longer parameter, but type reference now
        PsiReference ref = context.getFile().findReferenceAt(context.getCaret().getOffset());
        if (ref instanceof DocStringParameterReference || ref instanceof DocStringTypeReference) {
            context.setDummyIdentifier(CompletionInitializationContext.DUMMY_IDENTIFIER_TRIMMED);
        }
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
