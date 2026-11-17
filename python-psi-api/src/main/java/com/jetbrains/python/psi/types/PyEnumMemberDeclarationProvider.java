package com.jetbrains.python.psi.types;

import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ExtensionAPI;
import consulo.component.extension.ExtensionPointName;
import consulo.util.lang.ref.Ref;
import org.jspecify.annotations.Nullable;

/**
 * Lets framework-specific enum implementations model how their metaclass/constructor transforms a member declaration
 * into the actual member value. For example, Django's `ChoicesType.__new__` treats a trailing `str`/`Promise` element
 * of a tuple declaration as the member's label and drops it, so `A = "x", "label"` has value type `str`, not
 * `tuple[str, str]`.
 */
@ExtensionAPI(ComponentScope.APPLICATION)
public interface PyEnumMemberDeclarationProvider {
    ExtensionPointName<PyEnumMemberDeclarationProvider> EP_NAME = ExtensionPointName.create(PyEnumMemberDeclarationProvider.class);

    /**
     * Returns the member value type produced from {@code valueExpression} for {@code enumClass}, wrapped in a {@link Ref} (which may hold
     * {@code null} for an unknown value type), or {@code null} if this provider does not transform declarations for {@code enumClass}.
     */
    @Nullable
    Ref<PyType> getMemberValueType(PyClass enumClass, PyExpression valueExpression, TypeEvalContext context);

    /**
     * Whether this provider models how {@code enumClass} builds its member values.
     * Then the member values of {@code enumClass} are checked even though its metaclass or constructor is custom.
     */
    default boolean isApplicable(PyClass enumClass, TypeEvalContext context) {
        return false;
    }
}
