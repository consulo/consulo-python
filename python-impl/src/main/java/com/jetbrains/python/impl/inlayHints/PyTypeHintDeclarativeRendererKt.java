// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inlayHints;

import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.impl.psi.types.*;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.editor.inlay.DeclarativePresentationTreeBuilder;
import consulo.language.editor.inlay.InlayActionData;
import consulo.language.editor.inlay.InlayActionPayload;
import consulo.language.psi.PsiFile;
import consulo.language.psi.SmartPointerManager;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static com.jetbrains.python.PyLanguageFacadeKt.getEffectiveLanguageLevel;
import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;

public final class PyTypeHintDeclarativeRendererKt {
    // TODO consulo: PresentationTreeBuilderImpl.MAX_SEGMENT_TEXT_LENGTH is internal (ide-impl) in Consulo
    private static final int MAX_SEGMENT_TEXT_LENGTH = 30;

    // TODO consulo: PsiPointerInlayActionNavigationHandler missing; JB's PsiPointerInlayActionNavigationHandler.HANDLER_ID
    private static final String PSI_POINTER_NAVIGATION_HANDLER_ID = "psi.pointer.navigation.handler";

    private PyTypeHintDeclarativeRendererKt() {
    }

    /**
     * Renders {@code type} as a PEP 484-compliant type hint into a declarative inlay, making class names clickable so that
     * Ctrl/Cmd-clicking a name navigates to the corresponding class definition.
     * <p>
     * For the type shapes handled explicitly, the produced text matches {@link PythonDocumentationProvider#getTypeHint}; for
     * everything else the rendering falls back to that method's plain (non-navigable) text, so the inlay text always stays
     * in sync with the canonical renderer.
     */
    static void printPyTypeHint(DeclarativePresentationTreeBuilder builder, @Nullable PyType type, TypeEvalContext context) {
        if (type == null || isNoneType(type)) {
            plainText(builder, PythonDocumentationProvider.getTypeHint(type, context));
        }
        // Literal[...] renders its expression text rather than a class name.
        else if (type instanceof PyLiteralType || type instanceof PyLiteralStringType) {
            fallbackText(builder, type, context);
        }
        // tuple[...] (homogeneous/empty forms) and NamedTuple have dedicated formatting.
        else if (type instanceof PyTupleType || type instanceof PyNamedTupleType) {
            fallbackText(builder, type, context);
        }
        else if (type instanceof PyUnionType unionType) {
            printUnion(builder, unionType, context);
        }
        else if (type instanceof PyClassType classType && classType.isParameterized() && !classType.isDefinition()) {
            printGenericType(builder, classType, context);
        }
        else if (type instanceof PyClassType classType && !classType.isDefinition()) {
            printClassType(builder, classType, context);
        }
        else {
            fallbackText(builder, type, context);
        }
    }

    private static void printUnion(DeclarativePresentationTreeBuilder builder, PyUnionType union, TypeEvalContext context) {
        // Without PEP 604 `X | Y` syntax, unions are rendered as Union[...]/Optional[...]; defer those to the canonical renderer.
        if (!PyTypingTypeProvider.isBitwiseOrUnionAvailable(context)) {
            fallbackText(builder, union, context);
            return;
        }
        List<@Nullable PyType> members = new ArrayList<>(union.getMembers());
        // Optional: `X | None`, with the non-None member always rendered first (as PythonDocumentationProvider does).
        if (members.size() == 2 && members.stream().anyMatch(it -> isNoneType(it))) {
            PyType nonNone = members.stream().filter(it -> !isNoneType(it)).findFirst().orElse(null);
            if (nonNone != null) {
                printPyTypeHint(builder, nonNone, context);
                plainText(builder, " | ");
                printPyTypeHint(builder, members.stream().filter(it -> isNoneType(it)).findFirst().orElseThrow(), context);
                return;
            }
        }
        // Plain `A | B | C`. Unions with the unknown type (null) or 2+ literals are formatted specially downstream.
        if (members.stream().noneMatch(it -> it == null || isNoneType(it)) &&
            members.stream().filter(it -> it instanceof PyLiteralType).count() < 2) {
            for (int index = 0; index < members.size(); index++) {
                if (index > 0) {
                    plainText(builder, " | ");
                }
                printPyTypeHint(builder, members.get(index), context);
            }
            return;
        }
        fallbackText(builder, union, context);
    }

    private static void printGenericType(DeclarativePresentationTreeBuilder builder, PyClassType type, TypeEvalContext context) {
        String name = type.getName();
        PsiFile origin = context.getOrigin();
        boolean builtinGenericsAvailable = origin == null || getEffectiveLanguageLevel(origin).isAtLeast(LanguageLevel.PYTHON39);
        // On older language levels typing.List/Dict/... are substituted for list[]/dict[]/...; defer that to the canonical renderer.
        if (name == null || (!builtinGenericsAvailable && PyTypingTypeProvider.TYPING_COLLECTION_CLASSES.containsKey(name))) {
            fallbackText(builder, type, context);
            return;
        }
        clickableClassName(builder, name, type.getPyClass());
        plainText(builder, "[");
        List<PyType> typeArguments = type.getTypeArguments();
        for (int index = 0; index < typeArguments.size(); index++) {
            if (index > 0) {
                plainText(builder, ", ");
            }
            printPyTypeHint(builder, typeArguments.get(index), context);
        }
        plainText(builder, "]");
    }

    private static void printClassType(DeclarativePresentationTreeBuilder builder, PyClassType type, TypeEvalContext context) {
        String name = type.getName();
        if (name == null) {
            fallbackText(builder, type, context);
            return;
        }
        clickableClassName(builder, name, type.getPyClass());
    }

    private static void clickableClassName(DeclarativePresentationTreeBuilder builder, String name, PyClass pyClass) {
        InlayActionData actionData = new InlayActionData(
            new InlayActionPayload.PsiPointerInlayActionPayload(SmartPointerManager.createPointer(pyClass)),
            PSI_POINTER_NAVIGATION_HANDLER_ID
        );
        // The platform forbids a single text segment longer than MAX_SEGMENT_TEXT_LENGTH characters.
        for (String segment : chunked(name)) {
            builder.text(segment, actionData);
        }
    }

    private static void fallbackText(DeclarativePresentationTreeBuilder builder, @Nullable PyType type, TypeEvalContext context) {
        plainText(builder, PythonDocumentationProvider.getTypeHint(type, context));
    }

    private static void plainText(DeclarativePresentationTreeBuilder builder, String text) {
        // The platform forbids a single text segment longer than MAX_SEGMENT_TEXT_LENGTH characters.
        for (String segment : chunked(text)) {
            builder.text(segment);
        }
    }

    private static List<String> chunked(String text) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < text.length(); i += MAX_SEGMENT_TEXT_LENGTH) {
            result.add(text.substring(i, Math.min(text.length(), i + MAX_SEGMENT_TEXT_LENGTH)));
        }
        return result;
    }
}
