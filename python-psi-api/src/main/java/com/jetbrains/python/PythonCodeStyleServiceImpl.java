// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python;

import com.jetbrains.python.formatter.PyCodeStyleSettings;
import consulo.annotation.component.ServiceImpl;
import consulo.fileEditor.util.FileContentUtil;
import consulo.language.codeStyle.CodeStyle;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import jakarta.inject.Singleton;

import java.util.Collections;

@ServiceImpl
@Singleton
public final class PythonCodeStyleServiceImpl extends PythonCodeStyleService {
  @Override
  public boolean isSpaceAroundEqInKeywordArgument(PsiFile file) {
    return CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class).SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT;
  }

  @Override
  public boolean isTabIndentation(PsiFile file) {
    return CodeStyle.getIndentOptions(file).USE_TAB_CHARACTER;
  }

  @Override
  public int getIndentSize(PsiFile file) {
    return CodeStyle.getIndentSize(file);
  }

  @Override
  public int getTabSize(PsiFile file) {
    return CodeStyle.getIndentOptions(file).TAB_SIZE;
  }

  @Override
  public boolean isOptimizeImportsSortedByTypeFirst(PsiFile file) {
    return CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class).OPTIMIZE_IMPORTS_SORT_BY_TYPE_FIRST;
  }

  @Override
  public boolean isOptimizeImportsAlwaysSplitFromImports(PsiFile file) {
    return CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class).OPTIMIZE_IMPORTS_ALWAYS_SPLIT_FROM_IMPORTS;
  }

  @Override
  public boolean isOptimizeImportsCaseSensitiveOrder(PsiFile file) {
    return CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class).OPTIMIZE_IMPORTS_CASE_INSENSITIVE_ORDER;
  }

  @Override
  public boolean isOptimizeImportsSortNamesInFromImports(PsiFile file) {
    return CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class).OPTIMIZE_IMPORTS_SORT_NAMES_IN_FROM_IMPORTS;
  }

  @Override
  public boolean isOptimizeImportsSortImports(PsiFile file) {
    return CodeStyle.getCustomSettings(file, PyCodeStyleSettings.class).OPTIMIZE_IMPORTS_SORT_IMPORTS;
  }

  @Override
  public void reparseOpenEditorFiles(Project project) {
    FileContentUtil.reparseFiles(project, Collections.emptyList(), true);
  }

  @Override
  public void setSpaceAroundEqInKeywordArgument(Project project, boolean enabled) {
    CodeStyle.getSettings(project).getCustomSettings(PyCodeStyleSettings.class).SPACE_AROUND_EQ_IN_KEYWORD_ARGUMENT = enabled;
  }
}
