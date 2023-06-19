package consulo.python.impl;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.language.Language;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;
import consulo.language.version.LanguageVersionResolver;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 18/06/2023
 */
@ExtensionImpl
public class PythonLanguageVersionResolver implements LanguageVersionResolver {
  @RequiredReadAction
  @Nonnull
  @Override
  public LanguageVersion getLanguageVersion(@Nonnull Language language, @Nullable PsiElement psiElement) {
    if (psiElement == null) {
      return PythonLanguage.INSTANCE.getVersion(LanguageLevel.getDefault());
    }

    PsiFile containingFile = psiElement.getContainingFile();
    if (containingFile instanceof PyFile pyFile) {
      return PythonLanguage.INSTANCE.getVersion(pyFile.getLanguageLevel());
    }

    return PythonLanguage.INSTANCE.getVersion(LanguageLevel.getDefault());
  }

  @RequiredReadAction
  @Nonnull
  @Override
  public LanguageVersion getLanguageVersion(@Nonnull Language language, @Nullable Project project, @Nullable VirtualFile virtualFile) {
    if (project != null && virtualFile != null) {
      LanguageLevel level = PyUtil.getLanguageLevelForVirtualFile(project, virtualFile);
      return PythonLanguage.INSTANCE.getVersion(level);
    }

    return PythonLanguage.INSTANCE.getVersion(LanguageLevel.getDefault());
  }

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }
}
