package consulo.jython.module.extension;

import consulo.annotation.component.ExtensionImpl;
import consulo.jython.icon.JythonIconGroup;
import consulo.localize.LocalizeValue;
import consulo.module.content.layer.ModuleExtensionProvider;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.ModuleExtension;
import consulo.module.extension.MutableModuleExtension;
import consulo.ui.image.Image;
import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 18/06/2023
 */
@ExtensionImpl
public class JythonModuleExtensionProvider implements ModuleExtensionProvider<JythonModuleExtension> {
  @Override
  public String getId() {
    return "jython";
  }

  @Nullable
  @Override
  public String getParentId() {
    return "java";
  }

  @Override
  public LocalizeValue getName() {
    return LocalizeValue.localizeTODO("Jython");
  }

  @Override
  public Image getIcon() {
    return JythonIconGroup.jython();
  }

  @Override
  public ModuleExtension<JythonModuleExtension> createImmutableExtension(ModuleRootLayer moduleRootLayer) {
    return new JythonModuleExtension(getId(), moduleRootLayer);
  }

  @Override
  public MutableModuleExtension<JythonModuleExtension> createMutableExtension(ModuleRootLayer moduleRootLayer) {
    return new JythonMutableModuleExtension(getId(), moduleRootLayer);
  }
}
