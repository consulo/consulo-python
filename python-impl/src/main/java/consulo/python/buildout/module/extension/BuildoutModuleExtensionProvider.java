package consulo.python.buildout.module.extension;

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ModuleExtensionProvider;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.python.impl.icon.PythonImplIconGroup;
import consulo.ui.image.Image;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * @author VISTALL
 * @since 21/06/2023
 */
@ExtensionImpl
public class BuildoutModuleExtensionProvider implements ModuleExtensionProvider<BuildoutModuleExtension> {
  @Nonnull
  @Override
  public String getId() {
    return "py-buildout";
  }

  @Nullable
  @Override
  public String getParentId() {
    return "python";
  }

  @Nonnull
  @Override
  public LocalizeValue getName() {
    return LocalizeValue.localizeTODO("Buildout");
  }

  @Nonnull
  @Override
  public Image getIcon() {
    return PythonImplIconGroup.pythonBuildoutBuildout();
  }

  @Nonnull
  @Override
  public ModuleExtension<BuildoutModuleExtension> createImmutableExtension(@Nonnull ModuleRootLayer moduleRootLayer) {
    return new BuildoutModuleExtension(getId(), moduleRootLayer);
  }

  @Nonnull
  @Override
  public MutableModuleExtension<BuildoutModuleExtension> createMutableExtension(@Nonnull ModuleRootLayer moduleRootLayer) {
    return new BuildoutMutableModuleExtension(getId(), moduleRootLayer);
  }
}
