package consulo.python.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.fileTemplate.FileTemplateContributor;
import consulo.fileTemplate.FileTemplateRegistrator;

/**
 * @author VISTALL
 * @since 21/06/2023
 */
@ExtensionImpl
public class PythonFileTemplateContributor implements FileTemplateContributor {
  @Override
  public void register(FileTemplateRegistrator fileTemplateRegistrator) {
    fileTemplateRegistrator.registerInternalTemplate("Python Script");
    fileTemplateRegistrator.registerInternalTemplate("Python Unit Test");
    fileTemplateRegistrator.registerInternalTemplate("Setup Script");
  }
}
