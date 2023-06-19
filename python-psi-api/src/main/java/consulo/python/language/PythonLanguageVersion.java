package consulo.python.language;

import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.language.version.LanguageVersion;
import jakarta.annotation.Nonnull;

/**
 * TODO fully migrate from LanguageLevel
 * 
 * @author VISTALL
 * @since 18/06/2023
 */
public class PythonLanguageVersion extends LanguageVersion {
  private final LanguageLevel myLanguageLevel;

  public PythonLanguageVersion(LanguageLevel languageLevel, PythonLanguage language) {
    super(languageLevel.name(), languageLevel.name(), language);
    myLanguageLevel = languageLevel;
  }

  public int getVersion() {
    return myLanguageLevel.getVersion();
  }

  public boolean hasWithStatement() {
    return myLanguageLevel.hasWithStatement();
  }

  public boolean hasPrintStatement() {
    return myLanguageLevel.hasPrintStatement();
  }

  public boolean supportsSetLiterals() {
    return myLanguageLevel.supportsSetLiterals();
  }

  public boolean isPy3K() {
    return myLanguageLevel.isPy3K();
  }

  public boolean isOlderThan(@Nonnull LanguageLevel other) {
    return myLanguageLevel.isOlderThan(other);
  }

  public boolean isAtLeast(@Nonnull LanguageLevel other) {
    return myLanguageLevel.isAtLeast(other);
  }

  public LanguageLevel getLanguageLevel() {
    return myLanguageLevel;
  }
}
