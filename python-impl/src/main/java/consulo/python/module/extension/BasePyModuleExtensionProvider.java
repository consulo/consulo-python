package consulo.python.module.extension;

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ModuleExtensionProvider;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.python.psi.icon.PythonPsiIconGroup;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 18/06/2023
 */
@ExtensionImpl
public class BasePyModuleExtensionProvider implements ModuleExtensionProvider<BasePyModuleExtension> {
  @Nonnull
  @Override
  public String getId() {
    return "python";
  }

  @Nonnull
  @Override
  public LocalizeValue getName() {
    return LocalizeValue.localizeTODO("Python");
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return PythonPsiIconGroup.python();
  }

  @Nonnull
  @Override
  public ModuleExtension<BasePyModuleExtension> createImmutableExtension(@Nonnull ModuleRootLayer moduleRootLayer) {
    return new BasePyModuleExtension(getId(), moduleRootLayer);
  }

  @Nonnull
  @Override
  public MutableModuleExtension<BasePyModuleExtension> createMutableExtension(@Nonnull ModuleRootLayer moduleRootLayer) {
    return new BasePyMutableModuleExtension(getId(), moduleRootLayer);
  }
}
