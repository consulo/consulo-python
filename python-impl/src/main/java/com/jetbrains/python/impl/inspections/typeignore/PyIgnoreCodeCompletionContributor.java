// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections.typeignore;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.inspections.PyIgnoreCommentUtil;
import com.jetbrains.python.impl.inspections.PySuppressionUtil;
import com.jetbrains.python.impl.inspections.PyTypeCheckerSuppressionCode;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.psi.PsiElement;
import consulo.language.util.ProcessingContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static consulo.language.pattern.PlatformPatterns.psiComment;

/**
 * Completes the inspection codes in the brackets of a {@code # type: ignore[...]} or {@code # pycharm: ignore[...]} comment.
 * <p>
 * The variants are the granular type-checker codes and one code for each Python inspection: its kebab-case
 * alias, or its suppress id when it has no alias. The suppress id also matches the alias. A code that the
 * comment already lists, in any spelling, is not offered again. After a {@code pycharm:} namespace, the variants
 * complete the text after it.
 */
// Consulo: JB registers it with order="first", Consulo rejects "first" for plugin extensions
@ExtensionImpl
public class PyIgnoreCodeCompletionContributor extends CompletionContributor {
    private static final String TYPE_CHECKER_SUPPRESS_ID = "PyTypeChecker";
    private static final Pattern PYCHARM_NAMESPACE_PREFIX = Pattern.compile("^pycharm\\s*:\\s*", Pattern.CASE_INSENSITIVE);

    public PyIgnoreCodeCompletionContributor() {
        extend(CompletionType.BASIC, psiComment().withLanguage(PythonLanguage.INSTANCE), new PyIgnoreCodeCompletionProvider());
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private static class PyIgnoreCodeCompletionProvider implements CompletionProvider {
        @Override
        @RequiredReadAction
        public void addCompletions(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
            PsiElement comment = parameters.getPosition();
            String textBeforeCaret = comment.getText().substring(0, parameters.getOffset() - comment.getTextRange().getStartOffset());
            Matcher matcher = PyIgnoreCommentUtil.UNCLOSED_CODES_PATTERN.matcher(textBeforeCaret);
            if (!matcher.matches()) {
                return;
            }
            result.stopHere();

            boolean isPyCharmDirective = matcher.group(1).equalsIgnoreCase("pycharm");
            PyIgnoreCommentUtil.Directive directive = isPyCharmDirective ? PyIgnoreCommentUtil.Directive.PYCHARM : PyIgnoreCommentUtil.Directive.TYPE;
            List<String> entries = Arrays.asList(matcher.group(2).split(",", -1));
            Set<String> listedCodes = new HashSet<>();
            for (String entry : entries.subList(0, entries.size() - 1)) {
                listedCodes.addAll(listedSpellings(directive, entry.trim()));
            }
            CompletionResultSet codeResult = result.withPrefixMatcher(codePrefix(entries.get(entries.size() - 1)));

            List<PyTypeIgnoreSuppressIds.PythonInspection> inspections = PyTypeIgnoreSuppressIds.getInstance().pythonInspections();
            String typeCheckerName = inspections.stream()
                .filter(it -> it.suppressId().equals(TYPE_CHECKER_SUPPRESS_ID))
                .findFirst()
                .map(PyTypeIgnoreSuppressIds.PythonInspection::displayName)
                .orElse(TYPE_CHECKER_SUPPRESS_ID);
            for (PyTypeCheckerSuppressionCode granular : PyTypeCheckerSuppressionCode.values()) {
                if (listedCodes.contains(granular.getId())) {
                    continue;
                }
                codeResult.addElement(LookupElementBuilder.create(granular.getId()).withTypeText(typeCheckerName, true));
            }
            for (PyTypeIgnoreSuppressIds.PythonInspection inspection : inspections) {
                if (listedCodes.contains(inspection.suppressId())) {
                    continue;
                }
                String kebabAlias = inspection.kebabAlias();
                LookupElementBuilder element = LookupElementBuilder.create(kebabAlias != null ? kebabAlias : inspection.suppressId())
                    .withTypeText(inspection.displayName(), true)
                    .withCaseSensitivity(false);
                codeResult.addElement(kebabAlias != null ? element.withLookupString(inspection.suppressId()) : element);
            }
        }
    }

    /**
     * The part of the last code before the caret. A {@code pycharm:} namespace and the spaces around it are not part of it.
     */
    private static String codePrefix(String lastEntry) {
        return PYCHARM_NAMESPACE_PREFIX.matcher(lastEntry.stripLeading()).replaceFirst("");
    }

    /**
     * The spellings of a listed code: its name, and for an inspection its suppress id and its kebab-case alias.
     */
    private static List<String> listedSpellings(PyIgnoreCommentUtil.Directive directive, String rawCode) {
        PyIgnoreCommentUtil.CodeRef ref = PyIgnoreCommentUtil.codeRef(directive, rawCode);
        if (ref == null) {
            return List.of();
        }
        if (!(PyIgnoreCodeResolver.resolve(ref) instanceof PyIgnoreCodeResolver.Resolution.Inspection inspection)) {
            return List.of(ref.name());
        }
        List<String> result = new ArrayList<>();
        result.add(ref.name());
        result.add(inspection.suppressId());
        String alias = PySuppressionUtil.toSuppressionCode(inspection.suppressId());
        if (alias != null) {
            result.add(alias);
        }
        return result;
    }
}
