package consulo.python.module.extension;

import consulo.annotation.component.ExtensionImpl;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ModuleExtensionProvider;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.python.psi.icon.PythonPsiIconGroup;
import consulo.ui.image.Image;

/**
 * @author VISTALL
 * @since 18/06/2023
 */
@ExtensionImpl
public class BasePyModuleExtensionProvider implements ModuleExtensionProvider<BasePyModuleExtension> {
  @Override
  public String getId() {
    return "python";
  }

  @Override
  public LocalizeValue getName() {
    return LocalizeValue.localizeTODO("Python");
  }

  @Override
  public Image getIcon() {
    return PythonPsiIconGroup.python();
  }

  @Override
  public ModuleExtension<BasePyModuleExtension> createImmutableExtension(ModuleRootLayer moduleRootLayer) {
    return new BasePyModuleExtension(getId(), moduleRootLayer);
  }

  @Override
  public MutableModuleExtension<BasePyModuleExtension> createMutableExtension(ModuleRootLayer moduleRootLayer) {
    return new BasePyMutableModuleExtension(getId(), moduleRootLayer);
  }
}
