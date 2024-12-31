package consulo.jython.module.extension;

import com.jetbrains.python.impl.sdk.PythonSdkType;
import consulo.content.bundle.SdkType;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.content.layer.extension.ModuleExtensionWithSdkBase;
import consulo.python.module.extension.PyModuleExtension;

import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 25.10.13.
 */
public class JythonModuleExtension extends ModuleExtensionWithSdkBase<JythonModuleExtension> implements PyModuleExtension<JythonModuleExtension> {
  public JythonModuleExtension(@Nonnull String id, @Nonnull ModuleRootLayer module) {
    super(id, module);
  }

  @Nonnull
  @Override
  public Class<? extends SdkType> getSdkTypeClass() {
    return PythonSdkType.class;
  }
}
