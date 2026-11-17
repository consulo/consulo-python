/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jetbrains.python.documentation.docstrings;

import com.jetbrains.python.psi.PyIndentUtil;
import consulo.language.psi.PsiFile;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

/**
 * @author Mikhail Golubev
 */
public class GoogleCodeStyleDocStringBuilder extends SectionBasedDocStringBuilder {
  public static final String DEFAULT_CONTINUATION_INDENT = PyIndentUtil.FOUR_SPACES;

  public static String getDefaultSectionIndent(PsiFile settingsAnchor) {
    return PyIndentUtil.getIndentFromSettings(settingsAnchor);
  }

  public static GoogleCodeStyleDocStringBuilder forSettings(PsiFile settingsAnchor) {
    return new GoogleCodeStyleDocStringBuilder(getDefaultSectionIndent(settingsAnchor));
  }

  public GoogleCodeStyleDocStringBuilder(String sectionIndent) {
    super(sectionIndent, DEFAULT_CONTINUATION_INDENT);
  }

  @Override
  protected String getDefaultParametersHeader() {
    return "Args";
  }

  @Override
  protected String getDefaultReturnsHeader() {
    return "Returns";
  }

  @Override
  protected SectionBasedDocStringBuilder startSection(String title) {
    super.startSection(title);
    addLine(StringUtil.capitalize(title) + ":");
    return this;
  }

  @Override
  public SectionBasedDocStringBuilder addParameter(String name, @Nullable String type, String description) {
    if (type != null) {
      addSectionLine(String.format("%s (%s): %s", name, type, description));
    }
    else {
      addSectionLine(String.format("%s: %s", name, description));
    }
    return this;
  }

  @Override
  public SectionBasedDocStringBuilder addReturnValue(@Nullable String name, String type, String description) {
    if (name != null) {
      addSectionLine(String.format("%s (%s): %s", name, type, description));
    }
    else {
      addSectionLine(String.format("%s: %s", type, description));
    }
    return this;
  }
}
