// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inlayHints;

import com.jetbrains.python.impl.psi.impl.references.PyKeywordPatternReference;
import com.jetbrains.python.psi.PyClassPattern;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.concurrent.coroutine.ReadLock;
import consulo.codeEditor.event.EditorMouseEvent;
import consulo.language.editor.hint.HintManager;
import consulo.language.editor.inlay.InlayActionHandler;
import consulo.language.editor.inlay.InlayActionPayload;
import consulo.language.psi.PsiElement;
import consulo.language.psi.ResolveResult;
import consulo.language.psi.SmartPsiElementPointer;
import consulo.language.psi.util.PsiNavigateUtil;
import consulo.localize.LocalizeValue;
import consulo.project.Project;
import consulo.ui.UIAction;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.util.concurrent.coroutine.Coroutine;
import consulo.util.concurrent.coroutine.CoroutineScope;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@ExtensionImpl
public class PyPseudoKeywordPatternInlayActionHandler implements InlayActionHandler {
    public static final String HANDLER_NAME = "py.pseudo.keyword.pattern";

    // TODO consulo: InlayActionPayload is sealed, so JB's Payload(classPattern, keyword) data class is
    //  represented as PsiPointerInlayActionPayload(classPattern pointer, tag = keyword)
    public static InlayActionPayload payload(SmartPsiElementPointer<PyClassPattern> classPattern, String keyword) {
        return new InlayActionPayload.PsiPointerInlayActionPayload(classPattern, keyword);
    }

    @Override
    public String getHandlerId() {
        return HANDLER_NAME;
    }

    @RequiredUIAccess
    @Override
    public void handleClick(EditorMouseEvent e, InlayActionPayload payload) {
        InlayActionPayload.PsiPointerInlayActionPayload pointerPayload = (InlayActionPayload.PsiPointerInlayActionPayload) payload;

        Project project = e.getEditor().getProject();
        CoroutineScope.launchAsync(
            project != null ? project.coroutineContext() : Application.get().coroutineContext(),
            () -> Coroutine
                .first(ReadLock.<Object, Optional<ResolveResult[]>>apply(ignored -> {
                    if (!(pointerPayload.getPointer().getElement() instanceof PyClassPattern classPattern)) {
                        return Optional.empty();
                    }
                    String keyword = pointerPayload.getTag();
                    if (keyword == null) {
                        return Optional.empty();
                    }
                    TypeEvalContext typeEvalContext = TypeEvalContext.userInitiated(classPattern.getProject(), classPattern.getContainingFile());
                    return Optional.of(PyKeywordPatternReference.resolveKeyword(classPattern, keyword, PyResolveContext.defaultContext(typeEvalContext)));
                }))
                .then(UIAction.<Optional<ResolveResult[]>, Optional<ResolveResult[]>>apply(resolveResults -> {
                    PsiElement element = getFirstElement(resolveResults.orElse(null));
                    if (element == null) {
                        // TODO consulo: CodeInsightBundle "declaration.navigation.nowhere.to.go" missing in CodeInsightLocalize
                        HintManager.getInstance().showInformationHint(e.getEditor(), LocalizeValue.localizeTODO("Cannot find declaration to go to"));
                        return resolveResults;
                    }

                    PsiNavigateUtil.navigate(element);
                    return resolveResults;
                }))
        );
    }

    private static @Nullable PsiElement getFirstElement(ResolveResult @Nullable [] resolveResults) {
        if (resolveResults == null || resolveResults.length == 0) {
            return null;
        }
        return resolveResults[0].getElement();
    }
}
