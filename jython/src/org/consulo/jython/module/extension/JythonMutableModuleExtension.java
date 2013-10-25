package org.consulo.jython.module.extension;

import javax.swing.JComponent;

import org.consulo.module.extension.MutableModuleExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;

/**
 * @author VISTALL
 * @since 25.10.13.
 */
public class JythonMutableModuleExtension extends JythonModuleExtension implements MutableModuleExtension<JythonModuleExtension>
{
	private JythonModuleExtension myJythonModuleExtension;

	public JythonMutableModuleExtension(@NotNull String id, @NotNull Module module, JythonModuleExtension jythonModuleExtension)
	{
		super(id, module);
		myJythonModuleExtension = jythonModuleExtension;
	}

	@Nullable
	@Override
	public JComponent createConfigurablePanel(@NotNull ModifiableRootModel modifiableRootModel, @Nullable Runnable runnable)
	{
		return null;
	}

	@Override
	public void setEnabled(boolean b)
	{
		myIsEnabled = b;
	}

	@Override
	public boolean isModified()
	{
		return isEnabled() != myJythonModuleExtension.isEnabled();
	}

	@Override
	public void commit()
	{
		myJythonModuleExtension.commit(this);
	}
}
