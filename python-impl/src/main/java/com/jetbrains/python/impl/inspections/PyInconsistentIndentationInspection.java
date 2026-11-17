// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.inspections.quickfix.ConvertIndentsFix;
import com.jetbrains.python.lexer.PythonIndentingLexer;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.ast.IElementType;
import consulo.language.editor.inspection.ProblemDescriptor;
import consulo.language.editor.inspection.ProblemHighlightType;
import consulo.language.editor.inspection.scheme.InspectionManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

import java.util.ArrayList;
import java.util.List;

@ExtensionImpl
public final class PyInconsistentIndentationInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameInconsistentIndentation();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public ProblemDescriptor[] checkFile(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
        if (file.getLanguage() instanceof PythonLanguage) {
            return new IndentValidator(file, manager, isOnTheFly).invoke();
        }
        return ProblemDescriptor.EMPTY_ARRAY;
    }

    private static class IndentValidator {
        private final PsiFile myFile;
        private final InspectionManager myManager;
        private final boolean myOnTheFly;
        private final List<ProblemDescriptor> myProblems;
        private int myLastTabs = 0;
        private int myLastSpaces = 0;

        IndentValidator(PsiFile file, InspectionManager manager, boolean isOnTheFly) {
            myFile = file;
            myManager = manager;
            myOnTheFly = isOnTheFly;
            myProblems = new ArrayList<>();
        }

        public ProblemDescriptor[] invoke() {
            PythonIndentingLexer lexer = new PythonIndentingLexer();
            String text = myFile.getText();
            lexer.start(text);
            while (lexer.getTokenType() != null) {
                IElementType tokenType = lexer.getTokenType();
                if (tokenType == PyTokenTypes.STATEMENT_BREAK) {
                    lexer.advance();
                    while (lexer.getTokenType() != null && lexer.getTokenType() != PyTokenTypes.LINE_BREAK) {
                        lexer.advance();
                    }
                    if (lexer.getTokenType() == PyTokenTypes.LINE_BREAK) {
                        String indent = text.substring(lexer.getTokenStart(), lexer.getTokenEnd());
                        validateIndent(lexer.getTokenStart(), indent);
                    }
                }
                lexer.advance();
            }
            return myProblems.toArray(ProblemDescriptor.EMPTY_ARRAY);
        }

        private void validateIndent(int tokenStart, String indent) {
            int lastLF = indent.lastIndexOf('\n');
            String lastLineIndent = indent.substring(lastLF + 1);
            int spaces = 0;
            int tabs = 0;
            int length = lastLineIndent.length();
            for (int i = 0; i < length; i++) {
                char c = lastLineIndent.charAt(i);
                if (c == ' ') {
                    spaces++;
                }
                else if (c == '\t') {
                    tabs++;
                }
            }
            int problemStart = tokenStart + lastLF + 1;
            if (spaces > 0 && tabs > 0) {
                reportProblem(PyLocalize.inspInconsistentIndentationMixTabsSpaces().get(), problemStart, length);
                // don't know which one is correct => don't complain about inconsistent indentation on subsequent lines which use
                // either tabs or spaces
                myLastSpaces = 0;
                myLastTabs = 0;
            }
            else {
                if (spaces > 0 && myLastTabs > 0) {
                    reportProblem(PyLocalize.inspInconsistentIndentationPreviousLineUsedTabsThisLineUsesSpaces().get(),
                                  problemStart, length);
                }
                else if (tabs > 0 && myLastSpaces > 0) {
                    reportProblem(PyLocalize.inspInconsistentIndentationPreviousLineUsedSpacesThisLineUsesTabs().get(),
                                  problemStart, length);
                }
                if (spaces > 0 || tabs > 0) {
                    myLastTabs = tabs;
                    myLastSpaces = spaces;
                }
            }
        }

        private void reportProblem(String descriptionTemplate, int problemStart, int problemLength) {
            PsiElement elt = myFile.findElementAt(problemStart);
            int startOffset = problemStart - elt.getTextRange().getStartOffset();
            int endOffset = startOffset + problemLength;
            myProblems.add(myManager.createProblemDescriptor(elt, new TextRange(startOffset, endOffset),
                                                             descriptionTemplate,
                                                             ProblemHighlightType.GENERIC_ERROR_OR_WARNING, myOnTheFly,
                                                             new ConvertIndentsFix(false), new ConvertIndentsFix(true)));
        }
    }
}
