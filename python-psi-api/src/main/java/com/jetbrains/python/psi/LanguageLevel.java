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
package com.jetbrains.python.psi;

import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.version.LanguageVersion;
import consulo.python.language.PythonLanguageVersion;
import consulo.util.collection.ArrayUtil;
import consulo.util.dataholder.Key;

import javax.annotation.Nonnull;

/**
 * @author yole
 */
public enum LanguageLevel {

  /**
   * @apiNote This level is not supported since 2018.1.
   */
  PYTHON24(204),
  /**
   * @apiNote This level is not supported since 2018.1.
   */
  PYTHON25(205),
  /**
   * @apiNote This level is not supported since 2019.1.
   */
  PYTHON26(206),
  PYTHON27(207),
  /**
   * @apiNote This level is not supported since 2018.1.
   * Use it only to distinguish Python 2 and Python 3.
   * Consider using {@link LanguageLevel#isPython2()}.
   * Replace {@code level.isOlderThan(PYTHON30)} with {@code level.isPython2()}
   * and {@code level.isAtLeast(PYTHON30)} with {@code !level.isPython2()}.
   */
  PYTHON30(300),
  /**
   * @apiNote This level is not supported since 2018.1.
   */
  PYTHON31(301),
  /**
   * @apiNote This level is not supported since 2018.1.
   */
  PYTHON32(302),
  /**
   * @apiNote This level is not supported since 2018.1.
   */
  PYTHON33(303),
  /**
   * @apiNote This level is not supported since 2019.1.
   */
  PYTHON34(304),
  /**
   * @apiNote This level is not supported since 2020.3.
   */
  PYTHON35(305),
  PYTHON36(306),
  PYTHON37(307),
  PYTHON38(308),
  PYTHON39(309),
  PYTHON310(310),
  PYTHON311(311),
  PYTHON312(312);

  private static final LanguageLevel DEFAULT2 = PYTHON27;
  private static final LanguageLevel DEFAULT3 = PYTHON35;

  public static LanguageLevel FORCE_LANGUAGE_LEVEL = null;

  @Nonnull
  public static LanguageLevel getDefault() {
    return DEFAULT2;
  }

  private final int myVersion;

  LanguageLevel(int version) {
    myVersion = version;
  }

  /**
   * @return an int where major and minor version are represented decimally: "version 2.5" is 25.
   */
  public int getVersion() {
    return myVersion;
  }

  public boolean hasWithStatement() {
    return isAtLeast(PYTHON26);
  }

  public boolean hasPrintStatement() {
    return isPython2();
  }

  public boolean supportsSetLiterals() {
    return this == PYTHON27 || isAtLeast(PYTHON31);
  }

  public boolean isPython2() {
    return getMajorVersion() == 2;
  }

  public boolean isPy3K() {
    return getMajorVersion() == 3;
  }

  public int getMajorVersion() {
    return myVersion / 100;
  }

  public int getMinorVersion() {
    return myVersion % 100;
  }
  
  public boolean isOlderThan(@Nonnull LanguageLevel other) {
    return myVersion < other.myVersion;
  }

  public boolean isAtLeast(@Nonnull LanguageLevel other) {
    return myVersion >= other.myVersion;
  }

  public static LanguageLevel fromPythonVersion(@Nonnull String pythonVersion) {
    if (pythonVersion == null) return null;

    if (pythonVersion.startsWith("2")) {
      if (pythonVersion.startsWith("2.4")) {
        return PYTHON24;
      }
      if (pythonVersion.startsWith("2.5")) {
        return PYTHON25;
      }
      if (pythonVersion.startsWith("2.6")) {
        return PYTHON26;
      }
      if (pythonVersion.startsWith("2.7")) {
        return PYTHON27;
      }
      return DEFAULT2;
    }
    if (pythonVersion.startsWith("3")) {
      if (pythonVersion.startsWith("3.0")) {
        return PYTHON30;
      }
      if (pythonVersion.startsWith("3.1.") || pythonVersion.equals("3.1")) {
        return PYTHON31;
      }
      if (pythonVersion.startsWith("3.2")) {
        return PYTHON32;
      }
      if (pythonVersion.startsWith("3.3")) {
        return PYTHON33;
      }
      if (pythonVersion.startsWith("3.4")) {
        return PYTHON34;
      }
      if (pythonVersion.startsWith("3.5")) {
        return PYTHON35;
      }
      if (pythonVersion.startsWith("3.6")) {
        return PYTHON36;
      }
      if (pythonVersion.startsWith("3.7")) {
        return PYTHON37;
      }
      if (pythonVersion.startsWith("3.8")) {
        return PYTHON38;
      }
      if (pythonVersion.startsWith("3.9")) {
        return PYTHON39;
      }
      if (pythonVersion.startsWith("3.10")) {
        return PYTHON310;
      }
      if (pythonVersion.startsWith("3.11")) {
        return PYTHON311;
      }
      if (pythonVersion.startsWith("3.12")) {
        return PYTHON312;
      }
      return DEFAULT3;
    }
    return getDefault();
  }

  public static final Key<LanguageLevel> KEY = Key.create("python.language.level");

  @Nonnull
  public static LanguageLevel forElement(@Nonnull PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile instanceof PyFile) {
      LanguageVersion languageVersion = containingFile.getLanguageVersion();
      if (languageVersion instanceof PythonLanguageVersion pythonLanguageVersion) {
        return pythonLanguageVersion.getLanguageLevel();
      }
      return ((PyFile)containingFile).getLanguageLevel();
    }
    return getDefault();
  }

  @Nonnull
  public String toPythonVersion() {
    return getMajorVersion() + "." + getMinorVersion();
  }

  @Nonnull
  public static LanguageLevel getLatest() {
    //noinspection ConstantConditions
    return ArrayUtil.getLastElement(values());
  }

  @Override
  public String toString() {
    return toPythonVersion();
  }
}
