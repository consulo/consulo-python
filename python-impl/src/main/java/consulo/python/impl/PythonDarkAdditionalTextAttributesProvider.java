package consulo.python.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.colorScheme.AdditionalTextAttributesProvider;
import consulo.colorScheme.EditorColorsScheme;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 21/06/2023
 */
@ExtensionImpl
public class PythonDarkAdditionalTextAttributesProvider implements AdditionalTextAttributesProvider {
  @Nonnull
  @Override
  public String getColorSchemeName() {
    return EditorColorsScheme.DARCULA_SCHEME_NAME;
  }

  @Nonnull
  @Override
  public String getColorSchemeFile() {
    return "/colorSchemes/PythonDarcula.xml";
  }
}
