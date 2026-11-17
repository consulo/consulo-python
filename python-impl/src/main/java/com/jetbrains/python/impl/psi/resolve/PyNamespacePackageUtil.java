// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.resolve;

import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.lexer.PythonLexer;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.language.ast.TokenSet;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public final class PyNamespacePackageUtil {
  private PyNamespacePackageUtil() {
  }

  public static boolean isNamespacePackage(PsiElement element) {
    if (element instanceof PsiDirectory) {
      LanguageLevel level = LanguageLevel.forElement(element);
      PsiElement initFile = PyUtil.turnDirIntoInit(element);
      if (initFile == null) {
        return !level.isPython2();
      }

      PythonLexer lexer = new PythonLexer();
      lexer.start(initFile.getText());
      while (TOKENS_TO_SKIP.contains(lexer.getTokenType())) {
        lexer.advance();
      }

      String codeStart = initFile.getText().substring(lexer.getTokenStart());
      Pattern nextPattern = null;
      for (Iterator<String> iterator = codeStart.lines().iterator(); iterator.hasNext(); ) {
        String line = iterator.next();
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
          continue;
        }
        else if (nextPattern != null) {
          return nextPattern.matcher(trimmed).matches();
        }
        else if (ONE_LINE_NAMESPACE_DECLARATIONS.stream().anyMatch(it -> it.matcher(trimmed).matches())) {
          return true;
        }
        else {
          List<Pattern> matched = TWO_LINE_NAMESPACE_DECLARATIONS.stream()
            .filter(it -> it.get(0).matcher(trimmed).matches())
            .findFirst()
            .orElse(null);
          if (matched == null) {
            return false;
          }
          nextPattern = matched.get(1);
        }
      }
    }
    return false;
  }

  public static boolean isInNamespacePackage(PsiElement element) {
    PsiFile myFile = element.getContainingFile();
    if (myFile == null) return false;
    PsiDirectory parentDirectory = myFile.getContainingDirectory();
    if (parentDirectory == null) return false;
    if (PyUtil.getSourceRoots(element).contains(parentDirectory.getVirtualFile())) return false;
    return isNamespacePackage(parentDirectory);
  }

  private static final TokenSet TOKENS_TO_SKIP = TokenSet.create(PyTokenTypes.DOCSTRING,
                                                                 PyTokenTypes.END_OF_LINE_COMMENT,
                                                                 PyTokenTypes.LINE_BREAK,
                                                                 PyTokenTypes.SPACE,
                                                                 PyTokenTypes.TRY_KEYWORD,
                                                                 PyTokenTypes.COLON);

  private static final List<List<Pattern>> TWO_LINE_NAMESPACE_DECLARATIONS = List.of(
    patterns("^from pkgutil import extend_path.*", "^__path__[ ]?=[ ]?extend_path\\(__path__,[ ]?__name__\\).*"),
    patterns("^import pkgutil.*", "^__path__[ ]?=[ ]?pkgutil\\.extend_path\\(__path__,[ ]?__name__\\).*"),
    patterns("^from pkg_resources import declare_namespace.*", "^declare_namespace\\(__name__\\).*"),
    patterns("^import pkg_resources.*", "^pkg_resources.declare_namespace\\(__name__\\).*")
  );

  private static final List<Pattern> ONE_LINE_NAMESPACE_DECLARATIONS = patterns(
    "^__path__[ ]?=[ ]?__import__\\(['\"]pkgutil['\"]\\).extend_path\\(__path__, __name__\\).*",
    "^__import__\\(['\"]pkg_resources['\"]\\).declare_namespace\\(__name__\\).*"
  );

  private static List<Pattern> patterns(String... patterns) {
    return Arrays.stream(patterns).map(Pattern::compile).toList();
  }
}
