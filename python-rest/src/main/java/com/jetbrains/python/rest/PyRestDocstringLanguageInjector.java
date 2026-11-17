// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.rest;

import com.jetbrains.python.impl.documentation.doctest.PyDoctestLanguageDialect;
import com.jetbrains.rest.psi.RestLine;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import consulo.document.util.TextRange;
import consulo.language.inject.InjectedLanguagePlaces;
import consulo.language.inject.LanguageInjector;
import consulo.language.psi.PsiLanguageInjectionHost;
import consulo.util.lang.StringUtil;


import java.util.List;

@ExtensionImpl
public final class PyRestDocstringLanguageInjector implements LanguageInjector {
    @Override
    @RequiredReadAction
    public void injectLanguages(
        PsiLanguageInjectionHost host,
        InjectedLanguagePlaces injectionPlacesRegistrar
    ) {
        if (host instanceof RestLine) {
            int start = 0;
            int end = host.getTextLength() - 1;
            String text = host.getText();
            List<String> strings = StringUtil.split(text, "\n", false);

            boolean gotExample = false;

            int currentPosition = 0;
            boolean endsWithSlash = false;
            for (String string : strings) {
                String trimmedString = string.trim();
                if (!trimmedString.startsWith(">>>") && !trimmedString.startsWith("...") && gotExample && start < end) {
                    gotExample = false;
                    if (!endsWithSlash) {
                        injectionPlacesRegistrar.addPlace(PyDoctestLanguageDialect.getInstance(), TextRange.create(start, end), null, null);
                    }
                }
                if (endsWithSlash) {
                    endsWithSlash = false;
                    injectionPlacesRegistrar.addPlace(
                        PyDoctestLanguageDialect.getInstance(),
                        TextRange.create(start, getEndOffset(currentPosition, string)), null, null
                    );
                }

                if (trimmedString.startsWith(">>>")) {
                    if (trimmedString.endsWith("\\")) {
                        endsWithSlash = true;
                    }

                    if (!gotExample) {
                        start = currentPosition;
                    }

                    gotExample = true;
                    end = getEndOffset(currentPosition, string);
                }
                else if (trimmedString.startsWith("...") && gotExample) {
                    if (trimmedString.endsWith("\\")) {
                        endsWithSlash = true;
                    }

                    end = getEndOffset(currentPosition, string);
                }
                currentPosition += string.length();
            }
            if (gotExample && start < end) {
                injectionPlacesRegistrar.addPlace(PyDoctestLanguageDialect.getInstance(), TextRange.create(start, end), null, null);
            }
        }
    }

    private static int getEndOffset(int start, String s) {
        int length = s.length();
        int end = start + length;
        if (s.endsWith("\n")) {
            end -= 1;
        }
        return end;
    }
}
