// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight;

import consulo.language.codeStyle.CodeStyle;
import consulo.language.editor.action.TypedHandlerDelegate;
import consulo.language.ast.ASTNode;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.ide.impl.idea.openapi.editor.EditorModificationUtil;
import consulo.virtualFileSystem.fileType.FileType;
import consulo.project.DumbService;
import consulo.project.Project;
import consulo.document.util.TextRange;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCodeInsightSettings;
import consulo.annotation.component.ExtensionImpl;
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.impl.PyPsiUtils;

import java.util.regex.Pattern;

/**
  * Adds appropriate first parameter to a freshly-typed method declaration.
  * <br/>
  */
@ExtensionImpl(id = "pyMethodNameTypedHandler")
public final class PyMethodNameTypedHandler extends TypedHandlerDelegate {
    private static final Pattern DEF_THEN_IDENTIFIER = Pattern.compile(".*\\bdef\\s+" + PyNames.IDENTIFIER_RE);

    @Override
    public Result beforeCharTyped(char character, Project project, Editor editor, PsiFile file, FileType fileType) {
        if (DumbService.isDumb(project) || !(fileType instanceof PythonFileType)) {
            return Result.CONTINUE; // else we'd mess up with other file types!
        }
        if (character == '(') {
            if (!PyCodeInsightSettings.getInstance().INSERT_SELF_FOR_METHODS) {
                return Result.CONTINUE;
            }
            Document document = editor.getDocument();
            PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
            int offset = editor.getCaretModel().getOffset();
            int lineNumber = document.getLineNumber(offset);

            String linePrefix = document.getText(TextRange.create(document.getLineStartOffset(lineNumber), offset));
            if (!DEF_THEN_IDENTIFIER.matcher(linePrefix).matches()) {
                return Result.CONTINUE;
            }
            documentManager.commitDocument(document);

            PsiElement token = file.findElementAt(offset - 1);
            if (token == null) {
                return Result.CONTINUE; // sanity check: beyond EOL
            }

            ASTNode tokenNode = token.getNode();
            if (tokenNode != null && tokenNode.getElementType() == PyTokenTypes.IDENTIFIER) {
                PsiElement maybeDef = PyPsiUtils.getPrevNonCommentSibling(token.getPrevSibling(), false);
                if (maybeDef != null) {
                    ASTNode defNode = maybeDef.getNode();
                    if (defNode != null && defNode.getElementType() == PyTokenTypes.DEF_KEYWORD) {
                        PsiElement maybeFunc = token.getParent();
                        if (maybeFunc instanceof PyFunction func) {
                            PyUtil.MethodFlags flags = PyUtil.MethodFlags.of(func);
                            if (flags != null) {
                                // we're in a method
                                // TODO: all string constants go to Settings
                                String paramName = flags.isClassMethod() || flags.isMetaclassMethod() ? "cls" : "self";
                                boolean isNew = PyUtil.isNewMethod(func);
                                if (flags.isMetaclassMethod() && isNew) {
                                    paramName = "typ";
                                }
                                else if (flags.isClassMethod() || isNew) {
                                    paramName = "cls";
                                }
                                else if (flags.isStaticMethod()) {
                                    paramName = "";
                                }
                                // TODO: only print the ")" if Settings require it
                                CommonCodeStyleSettings settings = CodeStyle.getLanguageSettings(file, PythonLanguage.getInstance());
                                StringBuilder textToType = new StringBuilder();
                                textToType.append("(");
                                if (!paramName.isEmpty()) {
                                    if (settings.SPACE_WITHIN_METHOD_PARENTHESES) {
                                        textToType.append(" ");
                                    }
                                    textToType.append(paramName);
                                    if (settings.SPACE_WITHIN_METHOD_PARENTHESES) {
                                        textToType.append(" ");
                                    }
                                }
                                else if (settings.SPACE_WITHIN_EMPTY_METHOD_PARENTHESES) {
                                    textToType.append(" ");
                                }
                                textToType.append(")");
                                int caretOffset = editor.getCaretModel().getOffset();
                                CharSequence chars = editor.getDocument().getCharsSequence();
                                if (caretOffset == chars.length() || chars.charAt(caretOffset) != ':') {
                                    textToType.append(':');
                                }
                                EditorModificationUtil.insertStringAtCaret(editor, textToType.toString(), true, 1 + paramName.length()); // right after param name
                                return Result.STOP;
                            }
                        }
                    }
                }
            }
        }
        return Result.CONTINUE; // the default
    }
}
