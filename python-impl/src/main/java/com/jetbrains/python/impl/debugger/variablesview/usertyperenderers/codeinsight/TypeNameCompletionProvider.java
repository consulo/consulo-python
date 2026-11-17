// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.impl.debugger.variablesview.usertyperenderers.codeinsight;

import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.search.PySearchUtilBase;
import com.jetbrains.python.impl.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.PyType;
import consulo.application.progress.ProgressManager;
import consulo.application.util.matcher.PlainPrefixMatcher;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.lookup.CharFilter;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.ui.textCompletion.TextCompletionProvider;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.stub.StubIndex;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ProcessingContext;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TypeNameCompletionProvider implements TextCompletionProvider {
    private final Project project;

    public TypeNameCompletionProvider(Project project) {
        this.project = project;
    }

    public Project getProject() {
        return project;
    }

    @Override
    public @Nullable String getAdvertisement() {
        return null;
    }

    @Override
    public String getPrefix(String text, int offset) {
        // Kotlin text.substringAfterLast('.'): the whole text when it has no dot
        return text.substring(text.lastIndexOf('.') + 1);
    }

    @Override
    public CompletionResultSet applyPrefixMatcher(CompletionResultSet result, String prefix) {
        return result.withPrefixMatcher(new PlainPrefixMatcher(prefix));
    }

    @Override
    public CharFilter.Result acceptChar(char c) {
        return CharFilter.Result.ADD_TO_PREFIX;
    }

    @Override
    public void fillCompletionVariants(CompletionParameters parameters, String prefix, CompletionResultSet result) {
        result.restartCompletionOnAnyPrefixChange();
        String text = parameters.getOriginalFile().getText();
        if (!text.contains(".")) {
            addClassesVariants(parameters, result);
        }
        List<String> split = Arrays.asList(text.split("\\.", -1));
        List<String> components = split.subList(0, split.size() - 1);
        addVariantsFromModuleComponents(parameters, result, components);
    }

    private void addClassesFromPyFile(CompletionParameters parameters, CompletionResultSet result, PyFile file, HashSet<String> added) {
        PyModuleType moduleType = new PyModuleType(file);
        ProcessingContext ctx = new ProcessingContext();
        ctx.put(PyType.CTX_NAMES, added);
        Object[] completionVariants = moduleType.getCompletionVariants("", parameters.getPosition().getParent(), ctx);
        List<LookupElement> variantsList = new ArrayList<>();
        for (Object variant : completionVariants) {
            if (variant instanceof LookupElementBuilder builder
                && builder.getPsiElement() instanceof PyClass
                && result.getPrefixMatcher().isStartMatch(builder)) {
                variantsList.add(builder);
            }
        }
        result.addAllElements(variantsList);
    }

    private void addVariantsFromModuleComponents(CompletionParameters parameters,
                                                 CompletionResultSet result,
                                                 List<String> moduleComponents) {
        HashSet<String> alreadyAddedModulesNames = new HashSet<>();
        HashSet<String> alreadyAddedClsNames = new HashSet<>();

        for (PsiElement element : PyTypeNameResolver.getElementsFromModule(moduleComponents, project)) {
            if (element instanceof PsiDirectory directory) {
                List<LookupElement> modules =
                    PyModuleType.getSubModuleVariants(directory, parameters.getPosition().getParent(), alreadyAddedModulesNames);
                result.addAllElements(modules);
            }
            else if (element instanceof PyFile pyFile) {
                addClassesFromPyFile(parameters, result, pyFile, alreadyAddedClsNames);
            }
        }
    }

    private void addClassesVariants(CompletionParameters parameters, CompletionResultSet result) {
        StubIndex stubIndex = StubIndex.getInstance();
        Collection<String> clsKeys = PyClassNameIndex.allKeys(project);
        GlobalSearchScope scope = PySearchUtilBase.defaultSuggestionScope(parameters.getOriginalFile());
        Set<String> alreadySuggested = new HashSet<>();

        for (String elementName : result.getPrefixMatcher().sortMatching(clsKeys)) {
            stubIndex.processElements(PyClassNameIndex.KEY, elementName, project, scope, PyClass.class, element -> {
                ProgressManager.checkCanceled();
                String name = element.getName();
                if (name == null) {
                    return true;
                }
                if (element.getQualifiedName() == null) {
                    return true;
                }
                QualifiedName importPath = QualifiedNameFinder.findCanonicalImportPath(element, null);
                if (importPath == null) {
                    return true;
                }
                String pathName = importPath + "." + name;
                if (alreadySuggested.add(pathName)) {
                    LookupElementBuilder builder = LookupElementBuilder
                        .create(pathName)
                        .withIcon(PlatformIconGroup.nodesClass())
                        .withTypeText(importPath.toString())
                        .withPresentableText(name);
                    result.addElement(builder);
                }
                return true;
            });
        }
    }
}
