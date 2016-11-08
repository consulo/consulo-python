package consulo.jython.module.extension;

import consulo.python.module.extension.PyModuleExtension;
import org.jetbrains.annotations.NotNull;
import com.intellij.openapi.projectRoots.SdkType;
import com.jetbrains.python.sdk.PythonSdkType;
import consulo.extension.impl.ModuleExtensionWithSdkImpl;
import consulo.roots.ModuleRootLayer;

/**
 * @author VISTALL
 * @since 25.10.13.
 */
public class JythonModuleExtension extends ModuleExtensionWithSdkImpl<JythonModuleExtension> implements PyModuleExtension<JythonModuleExtension>
{
	public JythonModuleExtension(@NotNull String id, @NotNull ModuleRootLayer module)
	{
		super(id, module);
	}

	@NotNull
	@Override
	public Class<? extends SdkType> getSdkTypeClass()
	{
		return PythonSdkType.class;
	}
}
