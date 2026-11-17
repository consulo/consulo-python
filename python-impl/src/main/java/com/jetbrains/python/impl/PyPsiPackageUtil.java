// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl;

import com.jetbrains.python.impl.packaging.PyPackageName;
import com.jetbrains.python.packaging.PyPackage;
import consulo.logging.Logger;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Python package utility methods with no dependencies on the Python runtime.
 *
 * @see com.jetbrains.python.impl.packaging.PyPackageUtil for other package utility methods, including run-time dependent parts.
 */
public final class PyPsiPackageUtil {
    private static final Logger LOG = Logger.getInstance(PyPsiPackageUtil.class);

    /**
     * Contains mapping "importable top-level package" -> "package names on PyPI".
     */
    private static final Map<String, String> PACKAGES_TOPLEVEL = loadPackageAliases();

    private PyPsiPackageUtil() {
    }

    public static @Nullable PyPackage findPackage(List<PyPackage> packages, String name) {
        if (name.isEmpty()) {
            return null;
        }
        for (PyPackage pkg : packages) {
            if (name.equalsIgnoreCase(pkg.getName())) {
                return pkg;
            }
        }
        return null;
    }

    public static String moduleToPackageName(String module) {
        return moduleToPackageName(module, module);
    }

    public static String moduleToPackageName(String module, String defaultName) {
        return PyPackageName.normalizePackageName(PACKAGES_TOPLEVEL.getOrDefault(module, defaultName));
    }

    /**
     * Whether the letter case of {@code module} can be trusted to match the module that the package {@link #moduleToPackageName}
     * maps it to provides.
     * <p>
     * PyPI project names are case-insensitive, but module names are not: `Starlette` maps to the project `starlette`,
     * whose module is `starlette`. A module name with capitals is trusted only when the alias table lists it.
     */
    public static boolean isModuleNameCaseTrusted(String module) {
        return PACKAGES_TOPLEVEL.containsKey(module) || module.equals(module.toLowerCase());
    }

    private static Map<String, String> loadPackageAliases() {
        Map<String, String> result = new LinkedHashMap<>();
        try {
            InputStream resourceAsStream = Objects.requireNonNull(PyPsiPackageUtil.class.getResourceAsStream("/tools/packages"),
                "tool/packages is missed in distribution ");
            String text;
            try (resourceAsStream) {
                text = new String(resourceAsStream.readAllBytes(), StandardCharsets.UTF_8);
            }
            for (String line : text.lines().toList()) {
                if (line.isBlank()) {
                    continue;
                }
                String[] split = line.trim().split(" ");
                if (split.length != 2) {
                    throw new IllegalStateException("Each line should contain exactly two names: " + line);
                }
                result.put(split[0], split[1]);
            }
        }
        catch (IOException e) {
            LOG.error("Cannot find \"packages\". " + e.getMessage());
        }
        return Collections.unmodifiableMap(result);
    }
}
