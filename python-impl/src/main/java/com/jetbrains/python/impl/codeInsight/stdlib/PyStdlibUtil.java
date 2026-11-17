// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.stdlib;

import com.jetbrains.python.impl.PythonHelpersLocator;
import consulo.logging.Logger;
import consulo.util.lang.lazy.LazyValue;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public final class PyStdlibUtil {
    private static final Supplier<@Nullable Set<String>> PACKAGES = LazyValue.nullable(PyStdlibUtil::loadStdlibPackagesList);

    private PyStdlibUtil() {
    }

    public static @Nullable Set<String> getPackages() {
        return PACKAGES.get();
    }

    private static @Nullable Set<String> loadStdlibPackagesList() {
        Logger log = Logger.getInstance(PyStdlibUtil.class.getName());
        String helperPath = PythonHelpersLocator.getHelperPath("/tools/stdlib_packages.txt");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(helperPath), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.toSet());
        }
        catch (IOException e) {
            log.error("Cannot read list of standard library packages: " + e.getMessage());
        }
        return null;
    }
}
