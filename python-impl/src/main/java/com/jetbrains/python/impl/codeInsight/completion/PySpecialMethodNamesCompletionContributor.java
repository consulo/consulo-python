// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.impl.extensions.CaptureExtKt;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.language.Language;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.completion.AutoCompletionContext;
import consulo.language.editor.completion.AutoCompletionDecision;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.psi.PsiFile;
import consulo.language.util.ProcessingContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ExtensionImpl
public class PySpecialMethodNamesCompletionContributor extends CompletionContributor implements DumbAware {
    @Override
    public AutoCompletionDecision handleAutoCompletionPossibility(AutoCompletionContext context) {
        return PyCompletionUtilsKt.autoInsertSingleItem(context);
    }

    public PySpecialMethodNamesCompletionContributor() {
        extend(CompletionType.BASIC, CaptureExtKt.afterDefInFunction(PlatformPatterns.psiElement()), MyCompletionProvider.INSTANCE);
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private static final class MyCompletionProvider implements CompletionProvider {
        private static final MyCompletionProvider INSTANCE = new MyCompletionProvider();

        // Dunders that only make sense on a metaclass (a subclass of `type`). They are suppressed here so they aren't suggested in
        // ordinary classes; metaclasses still get them through the regular base-method override completion. Operator/protocol dunders
        // that `type` happens to implement (`__call__`, `__or__`, `__ror__`, ...) are intentionally NOT listed, since they are
        // legitimate to define on ordinary classes (PY-90275).
        private static final Set<String> METACLASS_ONLY_METHODS = Set.of(PyNames.PREPARE, "__instancecheck__", "__subclasscheck__");

        @Override
        @RequiredReadAction
        public void addCompletions(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
            TypeEvalContext typeEvalContext = PyCompletionUtilsKt.getTypeEvalContext(parameters);

            PyClass pyClass = PyCompletionUtilsKt.getPyClass(parameters);
            if (pyClass != null) {
                PyBuiltinCache builtins = PyBuiltinCache.getInstance(pyClass);
                PyClass objectClass = builtins.getClass(PyNames.OBJECT);
                List<String> fromObject = new ArrayList<>();
                if (objectClass != null) {
                    for (PyFunction method : new LinkedHashSet<>(Arrays.asList(objectClass.getMethods()))) {
                        fromObject.add(method.getName());
                    }
                }
                for (Map.Entry<String, PyNames.BuiltinDescription> entry : PyNames.getBuiltinMethods(LanguageLevel.forElement(pyClass))
                    .entrySet()) {
                    if (fromObject.contains(entry.getKey()) || METACLASS_ONLY_METHODS.contains(entry.getKey())) {
                        continue;
                    }
                    String name = entry.getKey();
                    PyNames.BuiltinDescription description = entry.getValue();
                    String signature = description.getSignature();

                    PyCompletionUtilsKt.addMethodToResult(result, pyClass, typeEvalContext, name, signature,
                        it -> postProcessCompletion(it, name, description));
                }
            }
            else {
                PsiFile file = PyCompletionUtilsKt.getFile(parameters);
                if (file != null) {
                    PyNames
                        .getModuleBuiltinMethods(LanguageLevel.forElement(file))
                        .forEach((name, description) ->
                            PyCompletionUtilsKt.addFunctionToResult(result, file instanceof PyFile pyFile ? pyFile : null, name,
                                description.getSignature(), it -> postProcessCompletion(it, name, description)));
                }
            }
        }

        private static LookupElementBuilder postProcessCompletion(LookupElementBuilder lookupElementBuilder,
                                                                  String name,
                                                                  PyNames.BuiltinDescription description) {
            return lookupElementBuilder
                .withTypeText("predefined")
                .withInsertHandler((insertionContext, item) -> {
                    WriteCommandAction.writeCommandAction(insertionContext.getFile()).run(() -> {
                        for (String it : description.getImports()) {
                            int dotIndex = it.lastIndexOf('.');
                            AddImportHelper.addFromImportStatement(
                                insertionContext.getFile(),
                                it.substring(0, dotIndex),
                                it.substring(dotIndex + 1),
                                null,
                                null,
                                null
                            );
                        }
                    });
                });
        }
    }
}
