package consulo.python.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.ui.ex.action.BundledQuickListsProvider;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 21/06/2023
 */
@ExtensionImpl
public class PythonBundledQuickListsProvider implements BundledQuickListsProvider {
  @Nonnull
  @Override
  public String[] getBundledListsRelativePaths() {
    return new String[] {
      "/liveTemplates/Python.xml"
    };
  }
}
