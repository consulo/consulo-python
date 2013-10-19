package com.jetbrains.python.facet;

import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.ui.FacetEditorContext;
import com.intellij.facet.ui.FacetEditorTab;
import com.intellij.facet.ui.FacetValidatorsManager;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import com.jetbrains.python.sdk.PythonSdkType;
import org.jdom.Element;

/**
 * @author yole
 */
public class PythonFacetConfiguration extends PythonFacetSettings implements FacetConfiguration {
  private static final String SDK_NAME = "sdkName";

  public FacetEditorTab[] createEditorTabs(FacetEditorContext editorContext, FacetValidatorsManager validatorsManager) {
    return new FacetEditorTab[] {
      new PythonSdkEditorTab(editorContext)
    };
  }

  public void readExternal(Element element) throws InvalidDataException {
    String sdkName = element.getAttributeValue(SDK_NAME);
    mySdk = StringUtil.isEmpty(sdkName) ? null : ProjectJdkTable.getInstance().findJdk(sdkName, PythonSdkType.getInstance().getName());
  }

  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(SDK_NAME, mySdk == null ? "" : mySdk.getName());
  }

}
