// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.completion;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.codeInsight.imports.AddImportHelper;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyReferenceExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.dumb.DumbAware;
import consulo.codeEditor.Editor;
import consulo.document.Document;
import consulo.document.RangeMarker;
import consulo.language.Language;
import consulo.language.editor.completion.CompletionContributor;
import consulo.language.editor.completion.CompletionParameters;
import consulo.language.editor.completion.CompletionProvider;
import consulo.language.editor.completion.CompletionResultSet;
import consulo.language.editor.completion.CompletionType;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.template.Template;
import consulo.language.editor.template.TemplateManager;
import consulo.language.pattern.PlatformPatterns;
import consulo.language.pattern.PsiElementPattern;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.util.ProcessingContext;
import consulo.platform.base.icon.PlatformIconGroup;
import consulo.project.Project;

import static com.jetbrains.python.impl.codeInsight.imports.AddImportHelper.addOrUpdateFromImportStatement;

/**
 * Offers completions that scaffold common Python class forms (`dataclass`, `Enum`, `TypedDict`).
 * <p>
 * <p>
 * Selecting such a completion at the start of a statement inserts the corresponding class skeleton, adds the required
 * import, and starts a live template with editable stops for the class name and the class body.
 */
@ExtensionImpl
public class PyClassFormCompletionContributor extends CompletionContributor implements DumbAware {
    private enum ClassForm {
        DATACLASS("dataclass", "dataclasses", "dataclass", "@dataclass\nclass $NAME$:\n    $END$"),
        ENUM("Enum", "enum", "Enum", "class $NAME$(Enum):\n    $END$"),
        TYPED_DICT("TypedDict", "typing", "TypedDict", "class $NAME$(TypedDict):\n    $END$"),
        NAMED_TUPLE("NamedTuple", "typing", "NamedTuple", "class $NAME$(NamedTuple):\n    $END$"),
        PROTOCOL("Protocol", "typing", "Protocol", "class $NAME$(Protocol):\n    $END$");

        private final String myLookupString;
        private final String myImportModule;
        private final String myImportName;
        private final String myTemplateText;

        ClassForm(String lookupString, String importModule, String importName, String templateText) {
            myLookupString = lookupString;
            myImportModule = importModule;
            myImportName = importName;
            myTemplateText = templateText;
        }
    }

    public PyClassFormCompletionContributor() {
        PsiElementPattern.Capture<PsiElement> place =
            PlatformPatterns.psiElement().withParents(PyReferenceExpression.class, PyExpressionStatement.class);
        extend(CompletionType.BASIC, place, new CompletionProvider() {
            @Override
            @RequiredReadAction
            public void addCompletions(CompletionParameters parameters,
                                       ProcessingContext context,
                                       CompletionResultSet result) {
                if (!isStatementStart(parameters.getPosition())) {
                    return;
                }
                for (ClassForm form : ClassForm.values()) {
                    result.addElement(
                        LookupElementBuilder
                            .create(form.myLookupString)
                            .withIcon(PlatformIconGroup.nodesTemplate())
                            .withTypeText(form.myImportModule + "." + form.myImportName)
                            .withCaseSensitivity(false)
                            .withInsertHandler(createInsertHandler(form))
                    );
                }
            }
        });
    }

    private static boolean isStatementStart(PsiElement position) {
        if (!(position.getParent() instanceof PyReferenceExpression parent)) {
            return false;
        }
        if (parent.getQualifier() != null) {
            return false;
        }
        return parent.getParent() instanceof PyExpressionStatement;
    }

    private static InsertHandler<LookupElement> createInsertHandler(ClassForm form) {
        return (insertionContext, item) -> {
            Project project = insertionContext.getProject();
            Editor editor = insertionContext.getEditor();
            Document document = insertionContext.getDocument();
            PsiFile file = insertionContext.getFile();

            // Mark the text inserted by the completion itself with a non-empty range so the marker reliably tracks an
            // import inserted above it. A zero-width marker at the file start would not move past an import added at the
            // same offset, leaving the scaffold above its own import. The marked text is removed below; the live template
            // provides the real content.
            RangeMarker anchor = document.createRangeMarker(insertionContext.getStartOffset(), insertionContext.getTailOffset());
            insertionContext.commitDocument();

            addOrUpdateFromImportStatement(
                file, form.myImportModule, form.myImportName, null,
                AddImportHelper.ImportPriority.BUILTIN, null
            );
            // Flush the PSI changes made by the import insertion back to the document before the live template edits it,
            // otherwise the document stays locked by postponed reformatting.
            PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(document);

            document.deleteString(anchor.getStartOffset(), anchor.getEndOffset());
            editor.getCaretModel().moveToOffset(anchor.getStartOffset());
            anchor.dispose();

            TemplateManager manager = TemplateManager.getInstance(project);
            Template template = manager.createTemplate("", "", form.myTemplateText);
            template.setToReformat(true);
            template.addVariable("NAME", "", "", true);
            manager.startTemplate(editor, template);
        };
    }

    @Override
    public Language getLanguage() {
        return PythonLanguage.INSTANCE;
    }
}
