// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PythonIcons;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.MainPythonSdkKt;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.sdk.skeleton.PySkeletonUtil;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.content.bundle.Sdk;
import consulo.language.editor.completion.AutoCompletionContext;
import consulo.language.editor.completion.AutoCompletionDecision;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.LookupElementPresentation;
import consulo.language.editor.completion.lookup.LookupElementRenderer;
import consulo.language.editor.completion.lookup.TailType;
import consulo.language.editor.completion.lookup.TailTypeDecorator;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ModuleUtilCore;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import one.util.streamex.StreamEx;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * Various utils for custom completions
 */
public final class PyCompletionUtilsKt {
    private PyCompletionUtilsKt() {
    }

    /**
     * Implementation of CompletionContributor#handleAutoCompletionPossibility
     * <p>
     * auto-insert the obvious only case; else show other cases.
     */
    public static AutoCompletionDecision autoInsertSingleItem(AutoCompletionContext context) {
        if (context.getItems().length == 1) {
            return AutoCompletionDecision.insertItem(context.getItems()[0]);
        }
        else {
            return AutoCompletionDecision.SHOW_LOOKUP;
        }
    }

    public static @Nullable PyClass getPyClass(CompletionParameters parameters) {
        return ScopeUtil.getScopeOwner(parameters.getPosition()) instanceof PyFunction function ? function.getContainingClass() : null;
    }

    public static @Nullable PsiFile getFile(CompletionParameters parameters) {
        return ScopeUtil.getScopeOwner(parameters.getPosition()) instanceof PyFunction function ? function.getContainingFile() : null;
    }

    public static TypeEvalContext getTypeEvalContext(CompletionParameters parameters) {
        return TypeEvalContext.codeCompletion(parameters.getOriginalFile().getProject(), parameters.getOriginalFile());
    }

    public static void addMethodToResult(CompletionResultSet result,
                                         @Nullable PyClass pyClass,
                                         TypeEvalContext typeEvalContext,
                                         String methodName) {
        addMethodToResult(result, pyClass, typeEvalContext, methodName, "(self)", null);
    }

    /**
     * Add method completion to result.
     *
     * @param result               destination
     * @param pyClass              if provided will check if method does not exist already
     * @param builderPostprocessor function to be used to tune lookup builder
     */
    public static void addMethodToResult(CompletionResultSet result,
                                         @Nullable PyClass pyClass,
                                         TypeEvalContext typeEvalContext,
                                         String methodName,
                                         String methodParentheses,
                                         @Nullable Function<LookupElementBuilder, LookupElementBuilder> builderPostprocessor) {
        if (pyClass != null && pyClass.findMethodByName(methodName, false, typeEvalContext) != null) {
            return;
        }

        LookupElementBuilder item = LookupElementBuilder.create(methodName + methodParentheses).withIcon(PythonIcons.Python.Nodes.Cyan_dot);
        result.addElement(TailTypeDecorator.withTail(builderPostprocessor != null ? builderPostprocessor.apply(item) : item,
            TailType.CASE_COLON));
    }

    public static void addFunctionToResult(CompletionResultSet result, @Nullable PyFile pyFile, String functionName) {
        addFunctionToResult(result, pyFile, functionName, "()", null);
    }

    /**
     * Add function completion to result.
     *
     * @param result               destination
     * @param pyFile               if provided will check if function does not exist already
     * @param builderPostprocessor function to be used to tune lookup builder
     */
    public static void addFunctionToResult(CompletionResultSet result,
                                           @Nullable PyFile pyFile,
                                           String functionName,
                                           String functionParentheses,
                                           @Nullable Function<LookupElementBuilder, LookupElementBuilder> builderPostprocessor) {
        if (pyFile != null && pyFile.findTopLevelFunction(functionName) != null) {
            return;
        }

        LookupElementBuilder item = LookupElementBuilder.create(functionName + functionParentheses).withIcon(PythonIcons.Python.Nodes.Cyan_dot);
        result.addElement(TailTypeDecorator.withTail(builderPostprocessor != null ? builderPostprocessor.apply(item) : item,
            TailType.CASE_COLON));
    }

    /**
     * Create {@link LookupElementBuilder} for {@code element} in the {@code file}.
     * <p>
     * Can be used to create lookup elements for packages and modules.
     * Uses canonical import path for item representation.
     */
    public static @Nullable LookupElementBuilder createLookupElementBuilder(PsiFile file, PsiFileSystemItem element) {
        String name = FileUtil.getNameWithoutExtension(element.getName());
        if (!PyNames.isIdentifier(name)) {
            return null;
        }

        return LookupElementBuilder.create(element, name)
            .withExpensiveRenderer(new LookupElementRenderer<LookupElement>() {
                @Override
                public void renderElement(LookupElement lookupElement, LookupElementPresentation presentation) {
                    QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(element, file);
                    if (importPath != null) {
                        importPath = importPath.removeLastComponent();
                    }
                    presentation.setItemText(lookupElement.getLookupString());
                    presentation.setIcon(IconDescriptorUpdaters.getIcon(element, 0));
                    if (importPath == null) {
                        return;
                    }
                    presentation.setTypeText(importPath.toString());
                }
            });
    }

    private static final int ELEMENT_TYPE = 10;
    private static final int LOCATION = 100;
    private static final int PRIVATE_API = 1_000;
    private static final int LOCATION_NOT_YET_IMPORTED = 10_000;
    private static final int UNDERSCORE_IN_NAME = 100_000;
    public static final int FALLBACK_WEIGHT = -1_000_000;

    /**
     * Determines weight of suggested completion/import item.
     *
     * @param completionLocation file, in which the completion is taking place
     * @param nameOnly           indicates that we just need to check the name for underscores
     */
    public static int computeCompletionWeight(PsiElement element,
                                              @Nullable String elementName,
                                              @Nullable QualifiedName path,
                                              @Nullable PsiFile completionLocation,
                                              boolean nameOnly) {
        int weight = 0;
        if (elementName == null) {
            return FALLBACK_WEIGHT;
        }
        String name = elementName;

        if (name.startsWith("__") && name.endsWith("__")) {
            weight -= UNDERSCORE_IN_NAME * 3;
        }
        else if (name.startsWith("__")) {
            weight -= UNDERSCORE_IN_NAME * 2;
        }
        else if (name.startsWith("_")) {
            weight -= UNDERSCORE_IN_NAME;
        }

        if (nameOnly) {
            return weight;
        }

        VirtualFile vFile = null;
        Sdk sdk = null;
        PsiFile containingFile = element.getContainingFile();
        if (element instanceof PsiDirectory directory) {
            vFile = directory.getVirtualFile();
            sdk = MainPythonSdkKt.pythonSdkIfReady(element);
        }
        else if (containingFile != null) {
            vFile = containingFile.getVirtualFile();
            sdk = MainPythonSdkKt.pythonSdkIfReady(containingFile);
        }

        QualifiedName importPath = path != null ? path : QualifiedNameFinder.findShortestImportableQName(element.getContainingFile());
        if (importPath == null) {
            return FALLBACK_WEIGHT;
        }
        if (completionLocation != null && !hasImportsFrom(completionLocation, importPath)) {
            weight -= LOCATION_NOT_YET_IMPORTED;
        }

        int privatePathComponents = (int) importPath.getComponents().stream().filter(it -> it.startsWith("_")).count() * PRIVATE_API;
        weight -= privatePathComponents + importPath.getComponentCount();

        if (vFile != null) {
            if (PySkeletonUtil.isStdLib(vFile, sdk)) {
                weight -= LOCATION;
            }
            else if (ModuleUtilCore.findModuleForFile(vFile, element.getProject()) == null) {
                weight -= LOCATION * 2;
            }
        }

        PsiElement initOrDir = PyUtil.turnInitIntoDir(element);
        if (initOrDir instanceof PsiDirectory) {
            weight -= ELEMENT_TYPE * 2;
        }
        else if (initOrDir instanceof PyFile) {
            weight -= ELEMENT_TYPE;
        }

        return weight;
    }

    public static boolean hasImportsFrom(PsiFile file, QualifiedName qName) {
        if (file instanceof PyFile pyFile) {
            return StreamEx.of(pyFile.getFromImports()).map(it -> it.getImportSourceQName())
                .nonNull()
                .anyMatch(qName::equals);
        }
        else {
            return false;
        }
    }
}
