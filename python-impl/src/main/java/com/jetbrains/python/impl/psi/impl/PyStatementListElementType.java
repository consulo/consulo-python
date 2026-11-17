// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.PyIndentUtil;
import com.jetbrains.python.lexer.PythonIndentingLexerForLazyElements;
import com.jetbrains.python.lexer.PythonIndentingProcessor;
import com.jetbrains.python.parsing.PyLazyParser;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.Application;
import consulo.application.progress.ProgressManager;
import consulo.application.util.registry.Registry;
import consulo.language.Language;
import consulo.language.ast.ASTNode;
import consulo.language.ast.ICompositeElementType;
import consulo.language.ast.IReparseableElementType;
import consulo.language.ast.TokenType;
import consulo.language.impl.ast.LazyParseableElement;
import consulo.language.lexer.Lexer;
import consulo.language.parser.PsiBuilder;
import consulo.language.parser.PsiBuilderFactory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.logging.Logger;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import org.jspecify.annotations.Nullable;

public class PyStatementListElementType extends IReparseableElementType implements ICompositeElementType {

    public PyStatementListElementType() {
        super("PyStatementList", PythonLanguage.INSTANCE);
    }

    private static final Logger LOG = Logger.getInstance(PyStatementListElementType.class);

    private static final Key<LanguageLevel> LANGUAGE_LEVEL_KEY = Key.create("LANGUAGE_LEVEL_FOR_REPARSEABLE_ELEMENT");
    private static final Key<Integer> BASE_INDENT_KEY = Key.create("FIRST_LINE_INDENT_FOR_REPARSEABLE_ELEMENT");

    // TODO: thresholds below may require further fine-tuning once more benchmark data is available

    /**
     * Below this file size full reparse is cheap enough; 20K chars ≈ 500 lines of typical Python.
     */
    private static int minFileCharsThreshold() {
        return Math.max(0, Registry.intValue("python.statement.lists.incremental.reparse.min.file.chars", 20_000));
    }

    /**
     * Hard cap on statement list size: protects EDT from per-keystroke lex over huge blocks.
     */
    private static int maxListCharsThreshold() {
        return Math.max(0, Registry.intValue("python.statement.lists.incremental.reparse.max.list.chars", 10_000));
    }

    /**
     * Statement list size cap as % of file: keeps savings worth the per-commit overhead.
     */
    private static int maxRatioPercentThreshold() {
        return Math.max(0, Registry.intValue("python.statement.lists.incremental.reparse.max.ratio.percent", 10));
    }

    // TODO consulo: JB overrides IReparseableElementTypeBase#isReparseable(ASTNode, CharSequence, Language, Project), which is missing
    //  in Consulo (consulo.language.ast.IReparseableElementTypeBase only has isParsable(parent, ...), and BlockSupportImpl#tryReparseNode
    //  calls isParsable). Until the platform gets isReparseable, this method is not invoked and statement lists are not reparsed
    //  incrementally (isParsable returns false by default).
    @RequiredReadAction
    public boolean isReparseable(ASTNode currentNode,
                                 CharSequence newText,
                                 Language fileLanguage,
                                 Project project) {
        // Consulo: the registry key is declared in JB plugin.xml with defaultValue="true"
        if (!Registry.is("python.statement.lists.incremental.reparse", true)) {
            return false;
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("Attempting to reparse lazy element of type " + this
                + "\nparent: " + currentNode.getTreeParent()
                + "\nold text: \n" + currentNode.getText()
                + "\n\nnew text: \n" + newText);
        }

        if (newText.isEmpty() || !fileLanguage.is(PythonLanguage.INSTANCE)) { // do not reparse Cython statement lists
            return false;
        }

        if (!isLargeEnoughForIncrementalReparse(currentNode, newText)) {
            return false;
        }

        // Error elements in this or any ancestor statement list mean the tree structure
        // may be wrong (e.g., after a cascading string literal break). In that case,
        // incremental reparse of an inner statement list can produce a tree inconsistent
        // with the outer, because the inner's text boundaries in the broken tree don't
        // match the correct code structure. Decline to let the platform reparse higher.
        if (anyAncestorHasErrors(currentNode)) {
            LOG.debug("Statement list or ancestor contains error elements, reparse is declined");
            return false;
        }

        boolean isAfterColonOnSameLine = isAfterColonOnSameLine((PyStatementListImpl) currentNode);
        int baseIndent = isAfterColonOnSameLine
            ? 0
            : PythonIndentingProcessor.getIndentWidth(PyIndentUtil.getElementIndent(currentNode.getPsi()));

        currentNode.putUserData(BASE_INDENT_KEY, baseIndent);

        PythonIndentingLexerForLazyElements lexer = new PythonIndentingLexerForLazyElements(baseIndent);
        return checkIndentDedentBalanceWithLexer(newText, lexer, isAfterColonOnSameLine);
    }

    @RequiredReadAction
    private static boolean isAfterColonOnSameLine(PsiElement currentNode) {
        PsiElement prevSibling = PyPsiUtils.getPrevNonWhitespaceSiblingOnSameLine(currentNode);
        return prevSibling != null && prevSibling.getNode().getElementType() == PyTokenTypes.COLON;
    }

    /**
     * Checks the balance between the number of INDENT and DEDENT tokens in the given text using the provided lexer.
     * Negative balance immediately indicates that given statement list is no longer reparseable.
     * Any positive balance or zero may be incorrect, but does not make a new statement list unparseable.
     *
     * @param text  the input text to check
     * @param lexer the lexer to use for tokenizing the text
     * @return true if the balance is positive or zero, otherwise - false
     */
    private static boolean checkIndentDedentBalanceWithLexer(CharSequence text, Lexer lexer, boolean isOnTheSameLine) {
        lexer.start(text);
        int balance = isOnTheSameLine ? 0 : -1;
        int tokenCount = 0;
        while (lexer.getTokenType() != null) {
            if (++tokenCount % 1000 == 0) {
                ProgressManager.checkCanceled();
            }
            if (lexer.getTokenType() == PyTokenTypes.INDENT) {
                balance++;
            }
            else if (lexer.getTokenType() == PyTokenTypes.DEDENT) {
                balance--;
            }
            if (balance < 0) {
                LOG.debug("Indent/Dedent balance is negative, incremental reparse declined");
                return false;
            }
            lexer.advance();
        }
        return true; // positive balance is safe
    }


    @Override
    @RequiredReadAction
    public boolean isValidReparse(ASTNode oldNode, ASTNode newNode) {
        PsiFile file = oldNode.getPsi().getContainingFile();
        if (!(file instanceof PyFile)) {
            return false;
        }

        LanguageLevel languageLevel = LanguageLevel.forElement(file);
        // Reuse base indent cached by isReparseable on the same node
        Integer cachedIndent = oldNode.getUserData(BASE_INDENT_KEY);
        int baseIndent = cachedIndent != null ? cachedIndent : 0;

        newNode.putUserData(LANGUAGE_LEVEL_KEY, languageLevel);
        newNode.putUserData(BASE_INDENT_KEY, baseIndent);

        // Single pass: verify children exist and contain no error elements
        ASTNode child = newNode.getFirstChildNode();
        if (child == null) {
            return false;
        }
        while (child != null) {
            if (child.getElementType() == TokenType.ERROR_ELEMENT) {
                LOG.debug("Reparsed tree contains error elements, declining reparse");
                return false;
            }
            child = child.getTreeNext();
        }

        LOG.debug("Element of type " + this + " reparsed successfully");
        return true;
    }

    @Override
    @RequiredReadAction
    protected @Nullable ASTNode doParseContents(ASTNode chameleon, PsiElement psi) {
        PsiElement parentPsiElement = chameleon.getTreeParent().getPsi();
        assert parentPsiElement != null : "parent psi is null: " + chameleon;

        // BASE_INDENT_KEY is set by isReparseable during incremental reparse.
        // During full file parse, it's not set — fall back to 0 (file-level indent).
        Integer indent = chameleon.getUserData(BASE_INDENT_KEY);
        if (indent == null) {
            indent = 0;
        }

        LanguageLevel languageLevel = chameleon.getUserData(LANGUAGE_LEVEL_KEY);
        if (languageLevel == null) {
            languageLevel = LanguageLevel.getDefault();
        }

        Lexer lexer = new PythonIndentingLexerForLazyElements(indent);
        PsiBuilder builder = PsiBuilderFactory.getInstance()
            .createBuilder(parentPsiElement.getProject(), chameleon, lexer, PythonLanguage.INSTANCE, parentPsiElement.getLanguageVersion(),
                chameleon.getChars());

        return new PyLazyParser().parseLazyElement(this, builder, languageLevel);
    }

    @Override
    public boolean reuseCollapsedTokens() {
        return true;
    }

    @Override
    public ASTNode createNode(CharSequence text) {
        return new PyStatementListImpl(text);
    }

    @Override
    public ASTNode createCompositeNode() {
        return new PyStatementListImpl(this, null);
    }

    /**
     * Walks up the tree from the given node, checking all ancestors (including the file)
     * for error elements among their direct children. When any ancestor has errors,
     * the tree structure may be wrong (e.g., nodes that belong inside a class or function
     * may have "spilled out" to a higher level), making incremental reparse unsafe.
     * <p>
     * Skips unparsed lazy nodes to avoid triggering their parsing.
     * The walk is cheap: typically 3–7 hops, each scanning only direct children.
     */
    private static boolean anyAncestorHasErrors(ASTNode node) {
        for (ASTNode current = node; current != null; current = current.getTreeParent()) {
            if (current instanceof LazyParseableElement lazy && !lazy.isParsed()) {
                continue;
            }
            if (hasErrorElements(current)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if any direct AST child of the given node is an error element.
     * <p>
     * Only checks direct children (not recursive) because:
     * <ul>
     *   <li>Errors at deeper nesting levels (inside nested if/for/def) would be the same
     *       regardless of incremental vs full reparse, so they don't indicate a reparse problem.</li>
     *   <li>Recursion into nested lazy elements would trigger their parsing,
     *       defeating the purpose of lazy parsing.</li>
     * </ul>
     */
    private static boolean hasErrorElements(ASTNode node) {
        ASTNode child = node.getFirstChildNode();
        while (child != null) {
            if (child.getElementType() == TokenType.ERROR_ELEMENT) {
                return true;
            }
            child = child.getTreeNext();
        }
        return false;
    }

    /**
     * Returns {@code true} if the file is large enough and the statement list is small enough for incremental reparse to be beneficial.
     */
    @RequiredReadAction
    private static boolean isLargeEnoughForIncrementalReparse(ASTNode currentNode, CharSequence newText) {
        if (Application.get().isUnitTestMode()) {
            return true;
        }

        PsiFile containingFile = currentNode.getPsi().getContainingFile();
        if (containingFile == null) {
            return true;
        }

        return isLargeEnoughForIncrementalReparse(
            containingFile.getTextLength(),
            newText.length(),
            minFileCharsThreshold(),
            maxListCharsThreshold(),
            maxRatioPercentThreshold()
        );
    }

    /**
     * Pure heuristic: file ≥ {@code minFileChars} AND list ≤ min({@code maxListChars}, file * ratio%).
     * Ratio binds below the {@code maxListChars * 100 / maxRatioPercent} crossover (100K with defaults), abs cap above.
     */
    public static boolean isLargeEnoughForIncrementalReparse(int fileLength,
                                                             int newTextLength,
                                                             int minFileChars,
                                                             int maxListChars,
                                                             int maxRatioPercent) {
        if (fileLength < minFileChars) {
            return false;
        }
        long maxAllowed = Math.min(maxListChars, (long) fileLength * maxRatioPercent / 100);
        return newTextLength <= maxAllowed;
    }

    @Override
    public String toString() {
        return "PyStatementList";
    }
}
