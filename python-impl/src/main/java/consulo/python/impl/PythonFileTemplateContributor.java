package consulo.python.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.fileTemplate.FileTemplateContributor;
import consulo.fileTemplate.FileTemplateRegistrator;
import jakarta.annotation.Nonnull;

/**
 * @author VISTALL
 * @since 21/06/2023
 */
@ExtensionImpl
public class PythonFileTemplateContributor implements FileTemplateContributor {
  @Override
  public void register(@Nonnull FileTemplateRegistrator fileTemplateRegistrator) {
    fileTemplateRegistrator.registerInternalTemplate("Python Script");
    fileTemplateRegistrator.registerInternalTemplate("Python Unit Test");
    fileTemplateRegistrator.registerInternalTemplate("Setup Script");
  }
}
