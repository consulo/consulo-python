// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.parsing;

import consulo.language.ast.ASTNode;
import consulo.language.parser.PsiBuilder;
import consulo.language.parser.PsiParser;
import consulo.language.ast.IElementType;
import consulo.language.version.LanguageVersion;


public class PyParser extends PythonParser implements PsiParser {

    public PyParser() {
        super();
    }

    @Override
    public ASTNode parse(IElementType root, PsiBuilder builder, LanguageVersion languageVersion) {
        long start = System.currentTimeMillis();
        parseRoot(root, builder);
        ASTNode ast = builder.getTreeBuilt();
        if (LOGGER.isDebugEnabled()) {
            long diff = System.currentTimeMillis() - start;
            double kb = builder.getCurrentOffset() / 1000.0;
            if (diff > 5) { // Only log heavy file parsing
              LOGGER.debug("Parsed " + String.format("%.1f", kb) + "K file in " + diff + "ms");
            }
        }
        return ast;
    }
}
