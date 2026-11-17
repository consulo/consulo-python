// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.PythonRuntimeService;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.search.PySearchUtilBase;
import com.jetbrains.python.impl.psi.stubs.PyQualifiedNameCompletionMatcher;
import com.jetbrains.python.impl.psi.stubs.PyQualifiedNameCompletionMatcher.QualifiedNameMatcher;
import com.jetbrains.python.impl.psi.types.PyModuleType;
import com.jetbrains.python.psi.PyDecorator;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.application.progress.ProgressManager;
import consulo.document.Document;
import consulo.language.Language;
import consulo.language.editor.WriteCommandAction;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.LookupElementDecorator;
import consulo.language.icon.IconDescriptorUpdaters;
import consulo.language.pattern.PatternCondition;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.PsiReference;
import consulo.language.psi.scope.GlobalSearchScope;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ProcessingContext;
import consulo.project.Project;
import consulo.util.dataholder.Key;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.jetbrains.python.impl.inspections.unresolvedReference.PyCommonImportAliasesKt.PY_COMMON_IMPORT_ALIASES;
import static com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil.fromFoothold;
import static com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil.resolveQualifiedName;
import static consulo.language.pattern.PlatformPatterns.psiElement;

@ExtensionImpl
public class PyUnresolvedModuleAttributeCompletionContributor extends CompletionContributor implements DumbAware {

    public PyUnresolvedModuleAttributeCompletionContributor() {
        extend(CompletionType.BASIC, QUALIFIED_REFERENCE_EXPRESSION, new CompletionProvider() {
            @Override
            @RequiredReadAction
            public void addCompletions(CompletionParameters parameters, ProcessingContext context, CompletionResultSet result) {
                PsiElement originalPosition = parameters.getOriginalPosition();
                PyReferenceExpression originalReferenceExpr =
                    originalPosition != null && originalPosition.getParent() instanceof PyReferenceExpression ref ? ref : null;
                // It cannot be checked in the pattern, because the default placeholder splits the reference, e.g. "foo.ba<caret>IntellijIdeaRulezzz z".
                boolean isOtherReferenceQualifier = originalReferenceExpr != null && originalReferenceExpr.getParent() instanceof PyReferenceExpression;
                if (isOtherReferenceQualifier) {
                    return;
                }

                Project project = parameters.getPosition().getProject();
                PsiFile originalFile = parameters.getOriginalFile();
                String attribute = result.getPrefixMatcher().getPrefix();
                QualifiedName qualifier = context.get(REFERENCE_QUALIFIER);
                Set<String> suggestedQualifiedNames = new HashSet<>();

                ProgressManager.checkCanceled();
                QualifiedName qualifiedName = qualifier.append(attribute);
                String packageNameForAlias = PY_COMMON_IMPORT_ALIASES.get(qualifier.toString());
                QualifiedName packageName = packageNameForAlias != null ? QualifiedName.fromDottedString(packageNameForAlias) : qualifier;
                CompletionResultSet resultMatchingCompleteReference = result.withPrefixMatcher(new QualifiedNameMatcher(qualifiedName));
                GlobalSearchScope scope = PySearchUtilBase.defaultSuggestionScope(parameters.getOriginalFile());
                TypeEvalContext typeContext = TypeEvalContext.userInitiated(project, originalFile);

                List<PsiElement> availableModules = resolveQualifiedName(packageName, fromFoothold(originalFile));

                if (packageNameForAlias == null) {
                    for (PsiElement module : availableModules) {
                        if (!(module instanceof PsiDirectory directory)) {
                            continue;
                        }
                        for (LookupElement it : PyModuleType.getSubModuleVariants(directory, originalFile, null)) {
                            if (it.getLookupString().startsWith("_")) {
                                continue;
                            }
                            String qualifiedNameToSuggest = qualifier + "." + it.getLookupString();
                            if (suggestedQualifiedNames.add(qualifiedNameToSuggest)) {
                                resultMatchingCompleteReference.addElement(new LookupElementDecorator<LookupElement>(it) {
                                    @Override
                                    public String getLookupString() {
                                        return qualifiedNameToSuggest;
                                    }

                                    @Override
                                    public Set<String> getAllLookupStrings() {
                                        Set<String> strings = new HashSet<>();
                                        strings.add(getLookupString());
                                        return strings;
                                    }
                                });
                            }
                        }
                    }
                }

                for (PsiElement module : availableModules) {
                    PsiElement packageElement = module instanceof PsiDirectory directory ? PyUtil.getPackageElement(directory, originalFile) : module;
                    if (!(packageElement instanceof PyFile pyFile)) {
                        continue;
                    }
                    PyModuleType moduleType = new PyModuleType(pyFile);
                    for (LookupElement it : moduleType.getCompletionVariantsAsLookupElements(parameters.getPosition(), context, false, false,
                        typeContext)) {
                        if (!(PyNames.isIdentifier(it.getLookupString()) && !it.getLookupString().startsWith("_"))) {
                            continue;
                        }
                        if (!(attribute.isEmpty() ||
                            resultMatchingCompleteReference.getPrefixMatcher().prefixMatches(qualifier + "." + it.getLookupString()))) {
                            continue;
                        }
                        if (!(it.getPsiElement() instanceof PyElement element) || element instanceof PsiFileSystemItem) {
                            continue;
                        }
                        if (suggestedQualifiedNames.add(packageName + "." + it.getLookupString())) {
                            resultMatchingCompleteReference.addElement(
                                LookupElementBuilder.create(element, qualifier + "." + it.getLookupString())
                                    .withIcon(IconDescriptorUpdaters.getIcon(element, 0))
                                    .withInsertHandler(getInsertHandler(element, parameters.getPosition()))
                                    .withTypeText(packageNameForAlias)
                            );
                        }
                    }
                }

                if (attribute.isEmpty()) {
                    result.restartCompletionOnAnyPrefixChange();
                    return;
                }
                PyQualifiedNameCompletionMatcher.processMatchingExportedNames(
                    qualifiedName, originalFile, scope,
                    it -> {
                        ProgressManager.checkCanceled();
                        if (suggestedQualifiedNames.add(it.getQualifiedName().toString())) {
                            resultMatchingCompleteReference.addElement(LookupElementBuilder
                                .createWithSmartPointer(it.getQualifiedName().toString(), it.getElement())
                                .withIcon(IconDescriptorUpdaters.getIcon(it.getElement(), 0))
                                .withInsertHandler(getInsertHandler(it.getElement(), parameters.getPosition())));
                        }
                        return true;
                    });
            }
        });
    }

    @Override
    public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {
        if (PythonRuntimeService.getInstance().isInPydevConsole(parameters.getOriginalFile())) {
            return;
        }
        super.fillCompletionVariants(parameters, result);
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }

    private static final Key<QualifiedName> REFERENCE_QUALIFIER = Key.create("QUALIFIER");

    private static final PatternCondition<PyReferenceExpression> UNRESOLVED_FIRST_COMPONENT =
        new PatternCondition<>("unresolved first component") {
            @Override
            @RequiredReadAction
            public boolean accepts(PyReferenceExpression expression, ProcessingContext context) {
                QualifiedName qualifier = context.get(REFERENCE_QUALIFIER);
                String qualifiersFirstComponent = qualifier.getFirstComponent();
                if (qualifiersFirstComponent == null) {
                    return false;
                }
                ScopeOwner scopeOwner = ScopeUtil.getScopeOwner(expression);
                if (scopeOwner == null) {
                    return false;
                }
                return PyResolveUtil.resolveLocally(scopeOwner, qualifiersFirstComponent).isEmpty();
            }
        };

    private static final PsiElementPattern.Capture<PsiElement> QUALIFIED_REFERENCE_EXPRESSION = psiElement(PyTokenTypes.IDENTIFIER).withParent(
        psiElement(PyReferenceExpression.class)
            .andNot(psiElement().inside(PyImportStatementBase.class))
            .with(new PatternCondition<>("plain qualified name") {
                @Override
                public boolean accepts(PyReferenceExpression expression, ProcessingContext context) {
                    if (!expression.isQualified()) {
                        return false;
                    }
                    QualifiedName qualifiedName = expression.asQualifiedName();
                    if (qualifiedName == null) {
                        return false;
                    }
                    context.put(REFERENCE_QUALIFIER, qualifiedName.removeLastComponent());
                    return true;
                }
            })
            .with(UNRESOLVED_FIRST_COMPONENT)
    );

    private static final InsertHandler<LookupElement> FUNCTION_INSERT_HANDLER = new PyFunctionInsertHandler() {
        @Override
        public void handleInsert(InsertionContext context, LookupElement item) {
            int tailOffset = context.getTailOffset() - 1;
            super.handleInsert(context, item);  // adds parentheses, modifies tail offset
            context.commitDocument();
            addImportForLookupElement(context, item, tailOffset);
        }
    };

    private static final InsertHandler<LookupElement> IMPORTING_INSERT_HANDLER =
        (context, item) -> addImportForLookupElement(context, item, context.getTailOffset() - 1);

    private static void addImportForLookupElement(InsertionContext context, LookupElement item, int tailOffset) {
        PsiDocumentManager manager = PsiDocumentManager.getInstance(context.getProject());
        Document document = manager.getDocument(context.getFile());
        if (document != null) {
            manager.commitDocument(document);
        }
        PsiReference ref = context.getFile().findReferenceAt(tailOffset);

        WriteCommandAction.writeCommandAction(context.getFile()).run(() -> {
            PsiElement psiElement = item.getPsiElement();
            if (psiElement instanceof PsiNamedElement && psiElement.getContainingFile() != null) {
                String name = QualifiedName.fromDottedString(item.getLookupString()).removeLastComponent().toString();
                String packageNameForAlias = PY_COMMON_IMPORT_ALIASES.get(name);
                String nameToImport = packageNameForAlias != null ? packageNameForAlias : name;
                AddImportHelper.addImportStatement(context.getFile(), nameToImport, packageNameForAlias != null ? name : null,
                    AddImportHelper.getImportPriority(context.getFile(), psiElement.getContainingFile()),
                    ref != null && ref.getElement() instanceof PyElement pyElement ? pyElement : null);
            }
        });
    }

    private static InsertHandler<LookupElement> getInsertHandler(PyElement elementToInsert, PsiElement position) {
        PsiElement parent = position.getParent();
        return elementToInsert instanceof PyFunction && !(parent != null && parent.getParent() instanceof PyDecorator)
            ? FUNCTION_INSERT_HANDLER
            : IMPORTING_INSERT_HANDLER;
    }
}
