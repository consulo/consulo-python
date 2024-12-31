/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.jetbrains.python.impl.inspections.unresolvedReference;

import com.jetbrains.python.psi.PyImportedNameDefiner;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;

import jakarta.annotation.Nonnull;

/**
 * Inject this point to ask "unused reference" inspection to skip some unused references.
 * For example in Django you may import "I18N" to your "settings.py". It is not used in "settings.py", but used by Django
 * and should not be marked as "unused".
 *
 * @author Ilya.Kazakevich
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyUnresolvedReferenceSkipperExtPoint {
  @Nonnull
  ExtensionPointName<PyUnresolvedReferenceSkipperExtPoint> EP_NAME = ExtensionPointName.create(PyUnresolvedReferenceSkipperExtPoint.class);

  /**
   * Checks if some unused import should be skipped
   *
   * @param importNameDefiner unused import
   * @return true if should be skipped
   */
  boolean unusedImportShouldBeSkipped(@Nonnull PyImportedNameDefiner importNameDefiner);
}
