// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter;

import com.jetbrains.python.PythonLanguage;
import consulo.application.util.registry.Registry;
import consulo.language.codeStyle.CodeStyleSettings;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * Top-level declarations of JB {@code PyCodeStyleDefaults.kt}.
 */
public final class PyCodeStyleDefaultsKt {
  /**
   * Master switch (flag 1) for the PY-85946 new Python formatter defaults. When off, the whole feature
   * is inert: classic everywhere, no migration, no balloon — byte-identical to the historical behavior.
   */
  public static final String PY_NEW_FORMATTER_DEFAULTS_ENABLED_KEY = "python.formatter.new.defaults.enabled";

  /**
   * The advanced setting that holds the per-installation rollout state (flag 2): whether this installation uses
   * the new defaults. The rollout turns it on for a new installation, and for an upgrade when the user accepts
   * the switch balloon. It has an effect only while the master switch ({@link #PY_NEW_FORMATTER_DEFAULTS_ENABLED_KEY}) is on.
   */
  public static final String PY_NEW_FORMATTER_DEFAULTS_SETTING_ID = "python.formatter.use.new.defaults";

  private PyCodeStyleDefaultsKt() {
  }

  /**
   * The master switch (flag 1): whether the new-defaults feature is enabled at all.
   */
  public static boolean isPyNewFormatterDefaultsFeatureEnabled() {
    return Registry.is(PY_NEW_FORMATTER_DEFAULTS_ENABLED_KEY, false);
  }

  /**
   * Whether the modern defaults are the active baseline for this installation: the master switch (flag 1)
   * and the per-installation rollout state (flag 2) are both on.
   */
  public static boolean isPyNewFormatterDefaultsActive() {
    return isPyNewFormatterDefaultsFeatureEnabled() && isPyNewFormatterDefaultsSettingOn();
  }

  // A light test application, such as the one of ParsingTestCase, has no AdvancedSettings service. The setting then keeps its default.
  private static boolean isPyNewFormatterDefaultsSettingOn() {
    // TODO consulo: AdvancedSettings missing - serviceOrNull<AdvancedSettings>() != null && AdvancedSettings.getBoolean(PY_NEW_FORMATTER_DEFAULTS_SETTING_ID)
    return false;
  }

  /**
   * The profile a fresh Python scheme defaults to: the modern {@link PyDefaultStyleGuide} when the new defaults
   * are active (so the IDE-level "Default" scheme simply *is* modern and reads clean), otherwise the
   * historical {@link PyClassicStyleGuide}.
   */
  public static String defaultPyCodeStyleId() {
    return isPyNewFormatterDefaultsActive() ? PyDefaultStyleGuide.CODE_STYLE_ID : PyClassicStyleGuide.CODE_STYLE_ID;
  }

  public static @Nullable PyCommonCodeStyleSettings getPyCommonSettings(CodeStyleSettings settings) {
    return settings.getCommonSettings(PythonLanguage.getInstance()) instanceof PyCommonCodeStyleSettings pySettings ? pySettings : null;
  }

  public static PyCodeStyleSettings getPyCustomSettings(CodeStyleSettings settings) {
    return settings.getCustomSettings(PyCodeStyleSettings.class);
  }

  /**
   * The active Python code style profile id, but only when the custom and common settings agree on it
   * (otherwise the scheme is in an inconsistent/partially-migrated state and we report no profile).
   *
   * @see PyDefaultStyleGuide
   * @see PyClassicStyleGuide
   */
  public static @Nullable String pyCodeStyleProfile(CodeStyleSettings settings) {
    String customStyleId = getPyCustomSettings(settings).CODE_STYLE_PROFILE;
    if (customStyleId == null) {
      return null;
    }
    PyCommonCodeStyleSettings commonSettings = getPyCommonSettings(settings);
    return Objects.equals(customStyleId, commonSettings != null ? commonSettings.CODE_STYLE_PROFILE : null) ? customStyleId : null;
  }
}
