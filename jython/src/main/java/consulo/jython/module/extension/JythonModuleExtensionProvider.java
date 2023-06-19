package consulo.jython.module.extension;

import consulo.annotation.component.ExtensionImpl;
import consulo.jython.icon.JythonIconGroup;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ModuleExtensionProvider;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 18/06/2023
 */
@ExtensionImpl
public class JythonModuleExtensionProvider implements ModuleExtensionProvider<JythonModuleExtension> {
  @Nonnull
  @Override
  public String getId() {
    return "jython";
  }

  @Nullable
  @Override
  public String getParentId() {
    return "java";
  }

  @Nonnull
  @Override
  public LocalizeValue getName() {
    return LocalizeValue.localizeTODO("Jython");
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return JythonIconGroup.jython();
  }

  @Nonnull
  @Override
  public ModuleExtension<JythonModuleExtension> createImmutableExtension(@Nonnull ModuleRootLayer moduleRootLayer) {
    return new JythonModuleExtension(getId(), moduleRootLayer);
  }

  @Nonnull
  @Override
  public MutableModuleExtension<JythonModuleExtension> createMutableExtension(@Nonnull ModuleRootLayer moduleRootLayer) {
    return new JythonMutableModuleExtension(getId(), moduleRootLayer);
  }
}
