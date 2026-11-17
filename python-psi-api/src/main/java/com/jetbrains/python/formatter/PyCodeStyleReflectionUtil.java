// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.formatter;

import consulo.util.lang.Comparing;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Reflection helper used by Python code style settings to exclude bookkeeping fields (such as the
 * active code style profile id) from `equals` comparisons.
 * <p>
 * <p>
 * Mirrors the Kotlin plugin helper `org.jetbrains.kotlin.idea.util.ReflectionUtil`; the
 * platform {@code com.intellij.util.ReflectionUtil.comparePublicNonFinalFields} has no skip variant.
 */
public final class PyCodeStyleReflectionUtil {
  private PyCodeStyleReflectionUtil() {
  }

  public static boolean comparePublicNonFinalFieldsWithSkip(Object first, Object second) {
    return comparePublicNonFinalFields(first, second, field -> field.getAnnotation(SkipInEquals.class) == null);
  }

  private static boolean comparePublicNonFinalFields(Object first, Object second, Predicate<Field> acceptPredicate) {
    Set<Field> firstFields = new HashSet<>(List.of(first.getClass().getFields()));

    for (Field field : second.getClass().getFields()) {
      if (firstFields.contains(field)) {
        if (isPublic(field) && !isFinal(field) && (acceptPredicate.test(field))) {
          try {
            if (!Comparing.equal(field.get(first), field.get(second))) {
              return false;
            }
          }
          catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        }
      }
    }

    return true;
  }

  private static boolean isPublic(Field field) {
    return (field.getModifiers() & Modifier.PUBLIC) != 0;
  }

  private static boolean isFinal(Field field) {
    return (field.getModifiers() & Modifier.FINAL) != 0;
  }

  @Retention(RetentionPolicy.RUNTIME)
  public @interface SkipInEquals {
  }
}
