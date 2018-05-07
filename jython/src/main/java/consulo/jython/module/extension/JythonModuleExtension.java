package consulo.jython.module.extension;

import javax.annotation.Nonnull;

import com.intellij.openapi.projectRoots.SdkType;
import com.jetbrains.python.sdk.PythonSdkType;
import consulo.module.extension.impl.ModuleExtensionWithSdkImpl;
import consulo.python.module.extension.PyModuleExtension;
import consulo.roots.ModuleRootLayer;

/**
 * @author VISTALL
 * @since 25.10.13.
 */
public class JythonModuleExtension extends ModuleExtensionWithSdkImpl<JythonModuleExtension> implements PyModuleExtension<JythonModuleExtension>
{
	public JythonModuleExtension(@Nonnull String id, @Nonnull ModuleRootLayer module)
	{
		super(id, module);
	}

	@Nonnull
	@Override
	public Class<? extends SdkType> getSdkTypeClass()
	{
		return PythonSdkType.class;
	}
}
