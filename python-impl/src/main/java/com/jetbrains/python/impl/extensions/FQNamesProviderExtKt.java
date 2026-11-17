// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.extensions;

import com.jetbrains.python.nameResolver.FQNamesProvider;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import consulo.language.psi.util.QualifiedName;
import consulo.navigation.NavigationItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class FQNamesProviderExtKt {
    private FQNamesProviderExtKt() {
    }

    public static QualifiedName[] getQualifiedNames(FQNamesProvider provider) {
        return Arrays.stream(provider.getNames()).map(QualifiedName::fromDottedString).toArray(QualifiedName[]::new);
    }

    public static String getFirstName(FQNamesProvider provider) {
        return provider.getNames()[0];
    }

    public static boolean shortNameMatches(FQNamesProvider provider, NavigationItem item) {
        return getShortNames(provider).contains(item.getName());
    }

    /**
     * @return all names in unqualified ("after last dot") format
     */
    public static List<String> getShortNames(FQNamesProvider provider) {
        List<String> result = new ArrayList<>();
        for (QualifiedName name : getQualifiedNames(provider)) {
            String last = name.getLastComponent();
            if (last != null) {
                result.add(last);
            }
        }
        return result;
    }

    /**
     * Checks if element name matches. {@link FQNamesProvider#alwaysCheckQualifiedName()} controls if full name should be checked, or only last and first
     * parts (name and package) are enough. It may be used for cases when physical FQN is not documented.
     */
    public static boolean isNameMatches(FQNamesProvider provider, PyQualifiedNameOwner qualifiedNameOwner) {
        String qualifiedName = qualifiedNameOwner.getQualifiedName();
        if (qualifiedName == null) {
            return false;
        }

        // Only check qualified name
        if (provider.alwaysCheckQualifiedName()) {
            return Arrays.asList(provider.getNames()).contains(qualifiedName);
        }

        // Relaxed check: package and name
        QualifiedName elementQualifiedName = QualifiedName.fromDottedString(qualifiedName);
        for (QualifiedName it : getQualifiedNames(provider)) {
            if (java.util.Objects.equals(it.getFirstComponent(), elementQualifiedName.getFirstComponent()) &&
                java.util.Objects.equals(it.getLastComponent(), elementQualifiedName.getLastComponent())) {
                return true;
            }
        }
        return false;
    }
}
