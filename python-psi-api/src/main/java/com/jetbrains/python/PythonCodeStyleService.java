package com.jetbrains.python;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.application.Application;
import consulo.language.psi.PsiFile;
import consulo.project.Project;
import consulo.util.dataholder.Key;

@ServiceAPI(ComponentScope.APPLICATION)
public class PythonCodeStyleService {

  public static final Key<Boolean> IMPORT_GROUP_BEGIN = Key.create("com.jetbrains.python.formatter.importGroupBegin");

  public boolean isSpaceAroundEqInKeywordArgument(PsiFile file) {
    return false;
  }

  public boolean isTabIndentation(PsiFile file) {
    return false;
  }

  public int getIndentSize(PsiFile file) {
    return 4;
  }

  public int getTabSize(PsiFile file) {
    return 4;
  }

  public boolean isOptimizeImportsSortedByTypeFirst(PsiFile file) {
    return false;
  }

  public boolean isOptimizeImportsAlwaysSplitFromImports(PsiFile file) {
    return false;
  }

  public boolean isOptimizeImportsCaseSensitiveOrder(PsiFile file) {
    return false;
  }

  public boolean isOptimizeImportsSortNamesInFromImports(PsiFile file) {
    return false;
  }

  public boolean isOptimizeImportsSortImports(PsiFile file) {
    return false;
  }

  public void reparseOpenEditorFiles(Project project) {
  }

  public void setSpaceAroundEqInKeywordArgument(Project project, boolean enabled) {
  }

  public static PythonCodeStyleService getInstance() {
    return Application.get().getInstance(PythonCodeStyleService.class);
  }
}
