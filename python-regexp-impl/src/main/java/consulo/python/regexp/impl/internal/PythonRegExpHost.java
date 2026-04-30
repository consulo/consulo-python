// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.python.regexp.impl.internal;

import com.jetbrains.python.psi.PyStringLiteralExpression;
import consulo.annotation.access.RequiredReadAction;
import consulo.annotation.component.ExtensionImpl;
import jakarta.annotation.Nonnull;
import org.intellij.lang.regexp.DefaultRegExpPropertiesProvider;
import org.intellij.lang.regexp.RegExpLanguageHost;
import org.intellij.lang.regexp.psi.RegExpChar;
import org.intellij.lang.regexp.psi.RegExpGroup;
import org.intellij.lang.regexp.psi.RegExpNamedGroupRef;
import org.jspecify.annotations.Nullable;

@ExtensionImpl
public final class PythonRegExpHost implements RegExpLanguageHost {
    private final DefaultRegExpPropertiesProvider myPropertiesProvider;

    public PythonRegExpHost() {
        myPropertiesProvider = DefaultRegExpPropertiesProvider.getInstance();
    }

    @Nonnull
    @Override
    public Class getHostClass() {
        return PyStringLiteralExpression.class;
    }

    @Override
    @RequiredReadAction
    public boolean characterNeedsEscaping(char c) {
        return c == ']' || c == '}' || c == '\"' || c == '\'';
    }

    @Override
    public boolean supportsPerl5EmbeddedComments() {
        return true;
    }

    @Override
    public boolean supportsPossessiveQuantifiers() {
        return false;
    }

    @Override
    public boolean supportsPythonConditionalRefs() {
        return true;
    }

    @Override
    public boolean supportsNamedGroupSyntax(RegExpGroup group) {
        return group.getType() == RegExpGroup.Type.PYTHON_NAMED_GROUP;
    }

    @Override
    public boolean supportsNamedGroupRefSyntax(RegExpNamedGroupRef ref) {
        return ref.isPythonNamedGroupRef();
    }

    @Override
    public boolean supportsExtendedHexCharacter(RegExpChar regExpChar) {
        return false;
    }

    @Override
    public boolean isValidCategory(@Nonnull String category) {
        return myPropertiesProvider.isValidCategory(category);
    }

    @Nonnull
    @Override
    public String[][] getAllKnownProperties() {
        return myPropertiesProvider.getAllKnownProperties();
    }

    @Nullable
    @Override
    public String getPropertyDescription(@Nullable String name) {
        return myPropertiesProvider.getPropertyDescription(name);
    }

    @Nonnull
    @Override
    public String[][] getKnownCharacterClasses() {
        return myPropertiesProvider.getKnownCharacterClasses();
    }
}
