package com.jetbrains.python;

public enum ProtectionLevel {
  /**
   * {@code public} {@code __members__}
   */
  PUBLIC,

  /**
   * {@code _protected_members}
   */
  PROTECTED,

  /**
   * {@code __private_members}
   */
  PRIVATE;

  public static ProtectionLevel forName(String name) {
    if (name.length() > 2 && name.startsWith("__") && !name.endsWith("__") && name.charAt(2) != '_') {
      return PRIVATE;
    }
    if (name.length() > 1 && name.startsWith("_") && !name.endsWith("_") && name.charAt(1) != '_') {
      return PROTECTED;
    }
    return PUBLIC;
  }
}
