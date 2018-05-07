package consulo.jython.module.extension;

import javax.annotation.Nonnull;
import javax.swing.JComponent;
import javax.swing.JPanel;

import javax.annotation.Nullable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.VerticalFlowLayout;
import consulo.annotations.RequiredDispatchThread;
import consulo.extension.ui.ModuleExtensionSdkBoxBuilder;
import consulo.module.extension.MutableModuleExtensionWithSdk;
import consulo.module.extension.MutableModuleInheritableNamedPointer;
import consulo.roots.ModuleRootLayer;

/**
 * @author VISTALL
 * @since 25.10.13.
 */
public class JythonMutableModuleExtension extends JythonModuleExtension implements MutableModuleExtensionWithSdk<JythonModuleExtension>
{
	public JythonMutableModuleExtension(@Nonnull String id, @Nonnull ModuleRootLayer module)
	{
		super(id, module);
	}

	@RequiredDispatchThread
	@Nullable
	@Override
	public JComponent createConfigurablePanel(@Nullable Runnable runnable)
	{
		JPanel panel = new JPanel(new VerticalFlowLayout(true, false));
		panel.add(ModuleExtensionSdkBoxBuilder.createAndDefine(this, runnable).build());
		return panel;
	}

	@Override
	public void setEnabled(boolean b)
	{
		myIsEnabled = b;
	}

	@Override
	public boolean isModified(@Nonnull JythonModuleExtension extension)
	{
		return isModifiedImpl(extension);
	}

	@Nonnull
	@Override
	public MutableModuleInheritableNamedPointer<Sdk> getInheritableSdk()
	{
		return (MutableModuleInheritableNamedPointer<Sdk>) super.getInheritableSdk();
	}
}
