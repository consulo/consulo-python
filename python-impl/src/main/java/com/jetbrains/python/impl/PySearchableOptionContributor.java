// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.search.SearchableOptionContributor;
import consulo.configurable.search.SearchableOptionProcessor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;

@ExtensionImpl
public final class PySearchableOptionContributor extends SearchableOptionContributor {
  @Override
  public void processOptions(SearchableOptionProcessor processor) {
    processIntegratedTools(processor);
    processProjectSettings(processor);
    processScientific(processor);
  }

  private static void processScientific(final SearchableOptionProcessor processor) {
    // TODO consulo: PyScientificConfigurable missing
  }

  private static void processProjectSettings(SearchableOptionProcessor processor) {
    // TODO consulo: PyActiveSdkModuleConfigurable missing (Consulo selects the Python SDK in the module extension settings)
  }

  private static void processIntegratedTools(SearchableOptionProcessor processor) {
    // consulo: id of PyIntegratedToolsModulesConfigurable
    final String configurableId = "py.integrated.tools";
    final LocalizeValue displayName = PyLocalize.configurablePyintegratedtoolsmodulesconfigurableDisplayName();
    processor.addOption(PyLocalize.searchOptionPackageRequirementsFile(), PyLocalize.searchOptionPackageRequirementsFile(),
                        configurableId, displayName);
    processor.addOption(PyLocalize.searchOptionDefaultTestRunner(), PyLocalize.searchOptionDefaultTestRunner(),
                        configurableId, displayName);
    processor.addOption(PyLocalize.searchOptionDocstringFormat(), PyLocalize.searchOptionDocstringFormat(),
                        configurableId, displayName);
    processor.addOption(PyLocalize.searchOptionAnalyzePythonCodeInDocstrings(), PyLocalize.searchOptionAnalyzePythonCodeInDocstrings(),
                        configurableId, displayName);
    processor.addOption(PyLocalize.searchOptionSphinxWorkingDirectory(), PyLocalize.searchOptionSphinxWorkingDirectory(),
                        configurableId, displayName);
    processor.addOption(PyLocalize.searchOptionTreatTxtFilesAsRestructuredtext(),
                        PyLocalize.searchOptionTreatTxtFilesAsRestructuredtextHit(),
                        configurableId, displayName);
    processor.addOption(PyLocalize.searchOptionRestructuredtext(), PyLocalize.searchOptionDocstringFormat(),
                        configurableId, displayName);
    processor.addOption(PyLocalize.searchOptionPlain(), PyLocalize.searchOptionDocstringFormat(),
                        configurableId, displayName);
    processor.addOption(PyLocalize.searchOptionUnittests(), PyLocalize.searchOptionDefaultTestRunner(),
                        configurableId, displayName);
    processor.addOption(PyLocalize.searchOptionPytest(), PyLocalize.searchOptionDefaultTestRunner(),
                        configurableId, displayName);
    processor.addOption(PyLocalize.searchOptionNosetests(), PyLocalize.searchOptionDefaultTestRunner(),
                        configurableId, displayName);
    processor.addOption(PyLocalize.searchOptionAttests(), PyLocalize.searchOptionDefaultTestRunner(),
                        configurableId, displayName);
    // TODO consulo: "Path to Pipenv executable" option missing in PyIntegratedToolsConfigurable
  }
}
