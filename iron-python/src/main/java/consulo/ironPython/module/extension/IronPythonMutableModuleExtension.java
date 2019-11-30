package consulo.ironPython.module.extension;

import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.VerticalFlowLayout;
import consulo.extension.ui.ModuleExtensionSdkBoxBuilder;
import consulo.module.extension.MutableModuleExtensionWithSdk;
import consulo.module.extension.MutableModuleInheritableNamedPointer;
import consulo.roots.ModuleRootLayer;
import consulo.ui.annotation.RequiredUIAccess;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.swing.*;

/**
 * @author VISTALL
 * @since 11.02.14
 */
public class IronPythonMutableModuleExtension extends IronPythonModuleExtension implements MutableModuleExtensionWithSdk<IronPythonModuleExtension>
{
	public IronPythonMutableModuleExtension(@Nonnull String id, @Nonnull ModuleRootLayer module)
	{
		super(id, module);
	}

	@Nonnull
	@Override
	public MutableModuleInheritableNamedPointer<Sdk> getInheritableSdk()
	{
		return (MutableModuleInheritableNamedPointer<Sdk>) super.getInheritableSdk();
	}

	@RequiredUIAccess
	@Nullable
	@Override
	public JComponent createConfigurablePanel(@Nonnull Runnable runnable)
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
	public boolean isModified(@Nonnull IronPythonModuleExtension extension)
	{
		return isModifiedImpl(extension);
	}
}
