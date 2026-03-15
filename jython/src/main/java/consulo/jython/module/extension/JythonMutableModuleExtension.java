package consulo.jython.module.extension;

import consulo.content.bundle.Sdk;
import consulo.disposer.Disposable;
import consulo.module.content.layer.ModuleRootLayer;
import consulo.module.extension.MutableModuleExtensionWithSdk;
import consulo.module.extension.MutableModuleInheritableNamedPointer;
import consulo.module.ui.extension.ModuleExtensionBundleBoxBuilder;
import consulo.ui.Component;
import consulo.ui.annotation.RequiredUIAccess;
import consulo.ui.layout.VerticalLayout;

import org.jspecify.annotations.Nullable;

/**
 * @author VISTALL
 * @since 25.10.13.
 */
public class JythonMutableModuleExtension extends JythonModuleExtension implements MutableModuleExtensionWithSdk<JythonModuleExtension> {
  public JythonMutableModuleExtension(String id, ModuleRootLayer module) {
    super(id, module);
  }

  @RequiredUIAccess
  @Nullable
  @Override
  public Component createConfigurationComponent(Disposable disposable, Runnable runnable) {
    VerticalLayout layout = VerticalLayout.create();
    layout.add(ModuleExtensionBundleBoxBuilder.createAndDefine(this, disposable, runnable).build());
    return layout;
  }

  @Override
  public void setEnabled(boolean b) {
    myIsEnabled = b;
  }

  @Override
  public boolean isModified(JythonModuleExtension extension) {
    return isModifiedImpl(extension);
  }

  @Override
  public MutableModuleInheritableNamedPointer<Sdk> getInheritableSdk() {
    return (MutableModuleInheritableNamedPointer<Sdk>)super.getInheritableSdk();
  }
}
