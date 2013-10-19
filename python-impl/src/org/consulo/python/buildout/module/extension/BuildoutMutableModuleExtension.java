package org.consulo.python.buildout.module.extension;

import javax.swing.JComponent;

import org.consulo.module.extension.MutableModuleExtension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.util.Comparing;
import com.jetbrains.python.buildout.BuildoutConfigPanel;

/**
 * @author VISTALL
 * @since 20.10.13.
 */
public class BuildoutMutableModuleExtension extends BuildoutModuleExtension implements MutableModuleExtension<BuildoutModuleExtension>
{
	private BuildoutModuleExtension myOriginalExtension;

	public BuildoutMutableModuleExtension(@NotNull String id, @NotNull Module module, BuildoutModuleExtension buildoutModuleExtension)
	{
		super(id, module);
		myOriginalExtension = buildoutModuleExtension;
	}

	@Nullable
	@Override
	public JComponent createConfigurablePanel(@NotNull ModifiableRootModel modifiableRootModel, @Nullable Runnable runnable)
	{
		return wrapToNorth(new BuildoutConfigPanel(this));
	}

	@Override
	public void setEnabled(boolean b)
	{
		myIsEnabled = b;
	}

	@Override
	public boolean isModified()
	{
		return myIsEnabled != myOriginalExtension.isEnabled() || !Comparing.strEqual(myScriptName, myOriginalExtension.getScriptName());
	}

	public void setScriptName(String s)
	{
		myScriptName = s;
	}

	@Override
	public void commit()
	{
		myOriginalExtension.commit(this);
	}
}
