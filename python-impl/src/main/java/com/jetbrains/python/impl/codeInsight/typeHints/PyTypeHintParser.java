// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typeHints;

import com.jetbrains.python.impl.documentation.doctest.PyDoctestTokenTypes;
import com.jetbrains.python.parsing.ExpressionParsing;
import com.jetbrains.python.parsing.ParsingContext;
import com.jetbrains.python.parsing.PyParser;
import com.jetbrains.python.parsing.StatementParsing;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.language.ast.IElementType;
import consulo.language.parser.PsiBuilder;

class PyTypeHintParser extends PyParser {
    @Override
    protected ParsingContext createParsingContext(PsiBuilder builder, LanguageLevel languageLevel) {
        return new ParsingContext(builder, languageLevel) {
            private final StatementParsing myStatementParsing = new StatementParsing(this) {
                @Override
                protected IElementType getReferenceType() {
                    return PyDoctestTokenTypes.DOC_REFERENCE;
                }
            };
            private final ExpressionParsing myExpressionParsing = new ExpressionParsing(this) {
                @Override
                protected IElementType getReferenceType() {
                    return PyDoctestTokenTypes.DOC_REFERENCE;
                }
            };

            @Override
            public StatementParsing getStatementParser() {
                return myStatementParsing;
            }

            @Override
            public ExpressionParsing getExpressionParser() {
                return myExpressionParsing;
            }
        };
    }
}
