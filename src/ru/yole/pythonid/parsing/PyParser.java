/*
 * Copyright 2006 Dmitry Jemerov (yole)
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

package ru.yole.pythonid.parsing;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilder.Marker;
import com.intellij.lang.PsiParser;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import ru.yole.pythonid.PythonLanguage;

public class PyParser
  implements PsiParser
{
  private static final Logger LOGGER = Logger.getInstance(PyParser.class.getName());
  private PythonLanguage language;

  public PyParser(PythonLanguage language)
  {
    this.language = language;
  }
  @NotNull
  public ASTNode parse(IElementType root, PsiBuilder builder) {
    builder.setDebugMode(false);
    long start = System.currentTimeMillis();
    PsiBuilder.Marker rootMarker = builder.mark();
    ParsingContext context = new ParsingContext(this.language);
    while (!builder.eof()) {
      context.getStatementParser().parseStatement(builder);
    }
    rootMarker.done(root);
    ASTNode ast = builder.getTreeBuilt();
    long diff = System.currentTimeMillis() - start;
    double kb = builder.getCurrentOffset() / 1000.0D;
    LOGGER.debug("Parsed " + String.format("%.1f", new Object[] { Double.valueOf(kb) }) + "K file in " + diff + "ms");
    ASTNode tmp147_145 = ast; if (tmp147_145 == null) throw new IllegalStateException("@NotNull method must not return null"); return tmp147_145;
  }
}