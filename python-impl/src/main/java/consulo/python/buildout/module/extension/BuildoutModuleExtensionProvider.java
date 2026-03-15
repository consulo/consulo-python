package consulo.python.buildout.module.extension;

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ModuleExtensionProvider;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.python.impl.icon.PythonImplIconGroup;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 21/06/2023
 */
@ExtensionImpl
public class BuildoutModuleExtensionProvider implements ModuleExtensionProvider<BuildoutModuleExtension> {
  @Override
  public String getId() {
    return "py-buildout";
  }

  @Nullable
  @Override
  public String getParentId() {
    return "python";
  }

  @Override
  public LocalizeValue getName() {
    return LocalizeValue.localizeTODO("Buildout");
  }

  @Override
  public Image getIcon() {
    return PythonImplIconGroup.pythonBuildoutBuildout();
  }

  @Override
  public ModuleExtension<BuildoutModuleExtension> createImmutableExtension(ModuleRootLayer moduleRootLayer) {
    return new BuildoutModuleExtension(getId(), moduleRootLayer);
  }

  @Override
  public MutableModuleExtension<BuildoutModuleExtension> createMutableExtension(ModuleRootLayer moduleRootLayer) {
    return new BuildoutMutableModuleExtension(getId(), moduleRootLayer);
  }
}
