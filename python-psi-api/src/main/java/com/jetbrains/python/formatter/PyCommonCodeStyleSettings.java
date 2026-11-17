// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/*
 * Copyright 2013-2026 consulo.io
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
package com.jetbrains.python.formatter;

import com.jetbrains.python.PythonLanguage;
import consulo.language.codeStyle.CodeStyleSettings;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.language.codeStyle.arrangement.ArrangementSettings;
import consulo.language.codeStyle.arrangement.ArrangementUtil;
import consulo.language.codeStyle.setting.LanguageCodeStyleSettingsProvider;
import consulo.util.lang.reflect.ReflectionUtil;
import consulo.util.xml.serializer.DefaultJDOMExternalizer;
import consulo.util.xml.serializer.DifferenceFilter;
import consulo.util.xml.serializer.InvalidDataException;
import consulo.util.xml.serializer.WriteExternalException;
import consulo.util.xml.serializer.XmlSerializer;
import org.jdom.Element;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Python-specific {@link CommonCodeStyleSettings} that carries the active code style profile id
 * ({@link #CODE_STYLE_PROFILE}) for the common (language-shared) formatter fields, mirroring the
 * Kotlin `KotlinCommonCodeStyleSettings`.
 * <p>
 * <p>
 * Without this subclass there would be no place to store a per-profile baseline for common fields
 * such as `METHOD_PARAMETERS_WRAP` / `CALL_PARAMETERS_WRAP` / the `RPAREN_ON_NEXT_LINE`
 * flags / `ALIGN_MULTILINE_PARAMETERS*` / `RIGHT_MARGIN`, so they would be serialized as
 * explicit diffs for every "default"-profile user. See PY-85946.
 */
public class PyCommonCodeStyleSettings extends CommonCodeStyleSettings {
  private static final String INDENT_OPTIONS_TAG = "indentOptions";
  private static final String ARRANGEMENT_ELEMENT_NAME = "arrangement";

  private final boolean isTempForDeserialize;

  // `@JvmField` is required so this is a real public Java field, exactly like the Java mirror's
  // `public String CODE_STYLE_PROFILE`. The profile baseline machinery reaches it purely through
  // public-field reflection — `copyPublicFields` in [clone] (ReflectionUtil.copyFields over
  // getFields()), `DefaultJDOMExternalizer.write` in [writeExternalBase], and
  // [comparePublicNonFinalFieldsWithSkip] in [equals]. A plain Kotlin `var` compiles to a private
  // backing field that none of them would see, silently dropping the profile id on clone and
  // failing to serialize it whenever soft margins are empty (the classic profile). The
  // `@field:` target keeps `SkipInEquals` on the field that reflection inspects.
  @PyCodeStyleReflectionUtil.SkipInEquals
  public @Nullable String CODE_STYLE_PROFILE = null;

  public PyCommonCodeStyleSettings() {
    this(false);
  }

  private PyCommonCodeStyleSettings(boolean isTempForDeserialize) {
    super(PythonLanguage.INSTANCE);
    this.isTempForDeserialize = isTempForDeserialize;
    if (!isTempForDeserialize) {
      // The in-memory baseline follows the active default profile (PY-85946): modern when the opt-in
      // flag is on so a fresh "Default" scheme is modern and reads clean; classic (unmarked, exactly
      // as historically) otherwise.
      if (PyCodeStyleDefaultsKt.isPyNewFormatterDefaultsActive()) {
        applyPyCodeStyle(PyDefaultStyleGuide.CODE_STYLE_ID, this, true);
      }
      else {
        applyPyCodeStyle(PyClassicStyleGuide.CODE_STYLE_ID, this, false);
      }
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    if (isTempForDeserialize) {
      super.readExternal(element);
      return;
    }

    PyCommonCodeStyleSettings tempDeserialize = createForTempDeserialize();
    tempDeserialize.readExternal(element);

    applyPyCodeStyle(PyCodeStyleSettings.storedOrLegacyProfile(tempDeserialize.CODE_STYLE_PROFILE), this, true);

    super.readExternal(element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    // Consulo: CommonCodeStyleSettings#writeExternal has no LanguageCodeStyleProvider parameter, the provider is looked up by language
    LanguageCodeStyleSettingsProvider provider = LanguageCodeStyleSettingsProvider.forLanguage(getLanguage());
    CommonCodeStyleSettings defaultSettings = provider != null ? provider.getDefaultCommonSettings() : null;
    if (provider == null || defaultSettings == null) {
      super.writeExternal(element);
      return;
    }

    // Diff against the profile the scheme is also read against (see storedOrLegacyProfile).
    applyPyCodeStyle(PyCodeStyleSettings.storedOrLegacyProfile(CODE_STYLE_PROFILE), defaultSettings, false);
    // Always write the profile, so that a scheme without it is a legacy scheme.
    if (defaultSettings instanceof PyCommonCodeStyleSettings pyDefaultSettings) {
      pyDefaultSettings.CODE_STYLE_PROFILE = null;
    }

    writeExternalBase(element, defaultSettings, provider);
  }

  //<editor-fold desc="Copied and adapted from CommonCodeStyleSettings">
  private void writeExternalBase(Element element,
                                 CommonCodeStyleSettings defaultSettings,
                                 LanguageCodeStyleSettingsProvider provider) throws WriteExternalException {
    Set<String> supportedFields = provider.getSupportedFields();
    if (supportedFields != null) {
      supportedFields.add("FORCE_REARRANGE_MODE");
      supportedFields.add("CODE_STYLE_PROFILE");
    }
    else {
      return;
    }

    DefaultJDOMExternalizer.writeExternal(this, element, new SupportedFieldsDiffFilter(this, supportedFields, defaultSettings));
    List<Integer> softMargins = getSoftMargins();
    serializeInto(softMargins, element);

    IndentOptions myIndentOptions = getIndentOptions();
    if (myIndentOptions != null) {
      IndentOptions defaultIndentOptions = defaultSettings.getIndentOptions();
      Element indentOptionsElement = new Element(INDENT_OPTIONS_TAG);
      myIndentOptions.serialize(indentOptionsElement, defaultIndentOptions);
      if (!indentOptionsElement.getChildren().isEmpty()) {
        element.addContent(indentOptionsElement);
      }
    }

    ArrangementSettings myArrangementSettings = getArrangementSettings();
    if (myArrangementSettings != null) {
      Element container = new Element(ARRANGEMENT_ELEMENT_NAME);
      ArrangementUtil.writeExternal(container, myArrangementSettings, provider.getLanguage());
      if (!container.getChildren().isEmpty()) {
        element.addContent(container);
      }
    }
  }

  // SoftMargins.serializeInto
  private void serializeInto(List<Integer> softMargins, Element element) {
    if (!softMargins.isEmpty()) {
      XmlSerializer.serializeInto(this, element);
    }
  }

  // Consulo: CommonCodeStyleSettings.SupportedFieldsDiffFilter is private in Consulo
  private static class SupportedFieldsDiffFilter extends DifferenceFilter<CommonCodeStyleSettings> {
    private final Set<String> mySupportedFieldNames;

    SupportedFieldsDiffFilter(CommonCodeStyleSettings object, Set<String> supportedFiledNames, CommonCodeStyleSettings parentObject) {
      super(object, parentObject);
      mySupportedFieldNames = supportedFiledNames;
    }

    @Override
    public boolean test(Field field) {
      if (mySupportedFieldNames != null && mySupportedFieldNames.contains(field.getName())) {
        return super.test(field);
      }
      return false;
    }
  }

  //</editor-fold>
  @Override
  public CommonCodeStyleSettings clone(CodeStyleSettings rootSettings) {
    PyCommonCodeStyleSettings commonSettings = new PyCommonCodeStyleSettings();
    // TODO consulo: CommonCodeStyleSettings#copyPublicFields is package-private
    ReflectionUtil.copyFields(commonSettings.getClass().getFields(), this, commonSettings);

    // consulo: setRootSettings / setSoftMargins are protected in Consulo, so JB's reflective calls are not needed
    commonSettings.setRootSettings(rootSettings);

    commonSettings.setForceArrangeMenuAvailable(isForceArrangeMenuAvailable());

    IndentOptions indentOptions = getIndentOptions();
    if (indentOptions != null) {
      IndentOptions targetIndentOptions = commonSettings.initIndentOptions();
      targetIndentOptions.copyFrom(indentOptions);
    }

    ArrangementSettings arrangementSettings = getArrangementSettings();
    if (arrangementSettings != null) {
      commonSettings.setArrangementSettings(arrangementSettings.clone());
    }

    commonSettings.setSoftMargins(getSoftMargins());

    return commonSettings;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (!(obj instanceof PyCommonCodeStyleSettings other)) {
      return false;
    }

    if (!PyCodeStyleReflectionUtil.comparePublicNonFinalFieldsWithSkip(this, other)) {
      return false;
    }

    if (!Objects.equals(getSoftMargins(), other.getSoftMargins())) {
      return false;
    }

    IndentOptions options = getIndentOptions();
    if ((options == null && other.getIndentOptions() != null) ||
        (options != null && !options.equals(other.getIndentOptions()))) {
      return false;
    }

    return arrangementSettingsEqual(other);
  }

  private static PyCommonCodeStyleSettings createForTempDeserialize() {
    return new PyCommonCodeStyleSettings(true);
  }

  private static void applyPyCodeStyle(@Nullable String codeStyleId, CommonCodeStyleSettings codeStyleSettings, boolean modifyCodeStyle) {
    if (codeStyleId == null) {
      return;
    }
    switch (codeStyleId) {
      case PyDefaultStyleGuide.CODE_STYLE_ID -> PyDefaultStyleGuide.applyToCommonSettings(codeStyleSettings, modifyCodeStyle);
      case PyClassicStyleGuide.CODE_STYLE_ID -> PyClassicStyleGuide.applyToCommonSettings(codeStyleSettings, modifyCodeStyle);
      default -> {
      }
    }
  }
}
