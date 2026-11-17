// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.console.completion;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.jetbrains.python.console.pydev.ConsoleCommunication;
import com.jetbrains.python.console.pydev.IToken;
import com.jetbrains.python.console.pydev.PyCodeCompletionImages;
import com.jetbrains.python.console.pydev.PydevCompletionVariant;
import com.jetbrains.python.impl.console.PyConsoleOptions;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyElementGenerator;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyExpressionStatement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import consulo.annotation.access.RequiredReadAction;
import consulo.codeEditor.Editor;
import consulo.component.ProcessCanceledException;
import consulo.document.Document;
import consulo.document.util.TextRange;
import consulo.language.editor.completion.CompletionInitializationContext;
import consulo.language.editor.completion.lookup.InsertHandler;
import consulo.language.editor.completion.lookup.InsertionContext;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.editor.completion.lookup.ParenthesesInsertHandler;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiManager;
import consulo.language.psi.PsiPolyVariantReferenceBase;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.util.ModuleUtilCore;
import consulo.logging.Logger;
import consulo.module.Module;
import consulo.util.collection.ArrayUtil;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class PydevConsoleReference extends PsiPolyVariantReferenceBase<PyReferenceExpression> {
    private static final Logger LOG = Logger.getInstance(PydevConsoleReference.class);

    private final ConsoleCommunication myCommunication;
    private final String myPrefix;
    private final boolean myAllowRemoteResolve;

    public PydevConsoleReference(
        PyReferenceExpression expression,
        ConsoleCommunication communication,
        String prefix,
        boolean allowRemoteResolve
    ) {
        super(expression, true);
        myCommunication = communication;
        myPrefix = prefix;
        myAllowRemoteResolve = allowRemoteResolve;
    }

    @Override
    @RequiredReadAction
    public ResolveResult[] multiResolve(boolean incompleteCode) {
        if (!myAllowRemoteResolve) {
            return RatedResolveResult.EMPTY_ARRAY;
        }
        PyExpression pyExpression = resolveToDummyDescription();
        if (pyExpression == null) {
            return RatedResolveResult.EMPTY_ARRAY;
        }

        if (pyExpression instanceof PyReferenceExpression ref) {
            PsiElement resolved = ref.getReference().resolve();
            if (resolved != null) {
                return new ResolveResult[]{new RatedResolveResult(RatedResolveResult.RATE_HIGH, resolved)};
            }
        }

        return RatedResolveResult.EMPTY_ARRAY;
    }

    @RequiredReadAction
    public @Nullable PyElement getDocumentationElement() {
        return resolveToDummyDescription();
    }


    @RequiredReadAction
    private @Nullable PyExpression resolveToDummyDescription() {
        String qualifiedName = myElement.getText();
        if (qualifiedName == null) {
            return null;
        }
        String description;
        try {
            description = myCommunication.getDescription(qualifiedName);
            if (Strings.isNullOrEmpty(description)) {
                return null;
            }
        }
        catch (ProcessCanceledException e) {
            return null;
        }
        catch (Exception e) {
            LOG.warn(e);
            return null;
        }

        PyElementGenerator generator = PyElementGenerator.getInstance(myElement.getProject());
        PyFile dummyFile = (PyFile) generator.createDummyFile(LanguageLevel.forElement(myElement), description);
        Module module = ModuleUtilCore.findModuleForPsiElement(myElement);
        if (module != null) {
            dummyFile.putUserData(ModuleUtilCore.KEY_MODULE, module);
        }

        List<PyStatement> statements = dummyFile.getStatements();
        PyStatement pyStatement = statements.get(statements.size() - 1);
        return pyStatement instanceof PyExpressionStatement ? ((PyExpressionStatement) pyStatement).getExpression() : null;
    }

    @Override
    @RequiredReadAction
    public Object[] getVariants() {
        if (!PyConsoleOptions.getInstance(getElement().getProject()).isRuntimeCodeCompletion()) {
            return ArrayUtil.EMPTY_OBJECT_ARRAY;
        }

        Map<String, LookupElement> variants = Maps.newHashMap();
        try {
            List<PydevCompletionVariant> completions = myCommunication.getCompletions(getText(), myPrefix);
            for (PydevCompletionVariant completion : completions) {
                PsiManager manager = myElement.getManager();
                String name = completion.getName();
                int type = completion.getType();
                LookupElementBuilder builder = LookupElementBuilder.create(new PydevConsoleElement(manager, name, completion.getDescription()))
                    .withIcon(PyCodeCompletionImages.getImageForType(type));


                String args = completion.getArgs();
                if (args.equals("(%)")) {
                    builder = builder.withPresentableText("%" + completion.getName());
                    builder = builder.withInsertHandler(new InsertHandler<>() {
                        @Override
                        public void handleInsert(InsertionContext context, LookupElement item) {
                            Editor editor = context.getEditor();
                            Document document = editor.getDocument();
                            int offset = context.getStartOffset();
                            if (offset == 0 || !"%".equals(document.getText(TextRange.from(offset - 1, 1)))) {
                                document.insertString(offset, "%");
                            }
                        }
                    });
                    args = "";
                }
                else if (!StringUtil.isEmptyOrSpaces(args)) {
                    builder = builder.withTailText(args);
                }
                // Set function insert handler
                if (type == IToken.TYPE_FUNCTION || args.endsWith(")")) {
                    builder = builder.withInsertHandler(ParenthesesInsertHandler.WITH_PARAMETERS);
                }
                variants.put(name, builder);
            }
        }
        catch (Exception e) {
            //LOG.error(e);
        }
        return variants.values().toArray();
    }

    @RequiredReadAction
    private String getText() {
        PsiElement element = PsiTreeUtil.getParentOfType(getElement(), PyFile.class);
        if (element != null) {
            return element.getText().replace(CompletionInitializationContext.DUMMY_IDENTIFIER, "");
        }
        return myPrefix;
    }
}
