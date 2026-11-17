// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.inspections.quickfix.AddEncodingQuickFix;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.configurable.ConfigurableBuilder;
import consulo.language.editor.inspection.InspectionToolState;
import consulo.language.editor.inspection.LocalInspectionToolSession;
import consulo.language.editor.inspection.ProblemsHolder;
import consulo.language.psi.PsiElementVisitor;
import consulo.localize.LocalizeValue;
import consulo.python.impl.localize.PyLocalize;
import consulo.ui.ComboBox;
import consulo.ui.Label;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * User : catherine
 */
@ExtensionImpl
public final class PyMandatoryEncodingInspection extends PyInspection {
    @Override
    public LocalizeValue getDisplayName() {
        return PyLocalize.inspNameMandatoryEncoding();
    }

    @Override
    public LocalizeValue getGroupDisplayName() {
        return PyLocalize.inspGroupPythonGeneral();
    }

    @Override
    public boolean isEnabledByDefault() {
        return false;
    }

    @Override
    public InspectionToolState<?> createStateProvider() {
        return new PyMandatoryEncodingInspectionState();
    }

    @Override
    public PsiElementVisitor buildVisitor(ProblemsHolder holder,
                                          boolean isOnTheFly,
                                          LocalInspectionToolSession session,
                                          Object state) {
        return new Visitor(holder, PyInspectionVisitor.getContext(session), (PyMandatoryEncodingInspectionState) state);
    }

    private static class Visitor extends PyInspectionVisitor {
        private final PyMandatoryEncodingInspectionState myState;

        Visitor(@Nullable ProblemsHolder holder, TypeEvalContext context, PyMandatoryEncodingInspectionState state) {
            super(holder, context);
            myState = state;
        }

        @Override
        public void visitPyFile(PyFile node) {
            if (!(myState.myAllPythons || LanguageLevel.forElement(node).isPython2())) {
                return;
            }

            String charsetString = PythonFileType.getCharsetFromEncodingDeclaration(node);
            if (charsetString == null) {
                ProblemsHolder holder = getHolder();
                if (holder != null) {
                    holder.registerProblem(node, PyLocalize.inspMandatoryEncodingNoEncodingSpecifiedForFile().get(),
                                           new AddEncodingQuickFix(myState.myDefaultEncoding, myState.myEncodingFormatIndex));
                }
            }
        }
    }

    /**
     * Consulo counterpart of JB {@code defaultEncodingDropDown()} option (also used by {@link PyNonAsciiCharInspection}).
     */
    static void defaultEncodingDropDown(ConfigurableBuilder<?> builder, Supplier<String> getter, Consumer<String> setter) {
        builder.component(() -> Label.create(PyLocalize.inspMandatoryEncodingLabelSelectDefaultEncoding()));
        builder.valueComponent(() -> ComboBox.create(PyEncodingUtil.POSSIBLE_ENCODINGS), getter, setter);
    }

    /**
     * Consulo counterpart of JB {@code encodingFormatDropDown()} option (also used by {@link PyNonAsciiCharInspection}).
     */
    static void encodingFormatDropDown(ConfigurableBuilder<?> builder, IntSupplier getter, IntConsumer setter) {
        builder.component(() -> Label.create(PyLocalize.inspMandatoryEncodingLabelEncodingCommentFormat()));
        builder.valueComponent(() -> ComboBox.create(PyEncodingUtil.ENCODING_FORMAT),
                               () -> {
                                   int index = getter.getAsInt();
                                   return index >= 0 && index < PyEncodingUtil.ENCODING_FORMAT.length
                                       ? PyEncodingUtil.ENCODING_FORMAT[index]
                                       : PyEncodingUtil.ENCODING_FORMAT[0];
                               },
                               value -> {
                                   for (int i = 0; i < PyEncodingUtil.ENCODING_FORMAT.length; i++) {
                                       if (PyEncodingUtil.ENCODING_FORMAT[i].equals(value)) {
                                           setter.accept(i);
                                           return;
                                       }
                                   }
                               });
    }
}
