// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.documentation.PythonDocumentationProvider;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyCallableParameter;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeRendererFeature;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.util.HtmlChunk;
import consulo.python.impl.localize.PyLocalize;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

/**
 * Shared logic and messaging for "this call matches none of the candidate signatures" inspection reports,
 * used by both {@link PyTypeCheckerInspection} (argument type mismatches) and {@link PyArgumentListInspection}
 * (unexpected/unfilled arguments). Both inspections describe the failure with the same model and render it
 * the same way:
 *
 * <pre>
 * No overload of `f` matches the arguments         (header; "No signature matches the arguments" if candidates differ)
 * Argument types:  (int, c=str)                     ← provided argument types; unmatched ones stand out
 * Expected one of: (a: int, b: int)                 ← one row per candidate; unmatched parameters stand out
 *                  (a: int, option: None)
 * </pre>
 * <p>
 * The parts that match are muted; only the offending parts are emphasized. The plain-text description (for
 * the Problems view and batch mode) carries the same content without styling.
 */
public final class PyMismatchTooltips {
    private PyMismatchTooltips() {
    }

    /**
     * One provided argument or expected parameter: a {@code name} part (a {@code keyword=} for arguments, a {@code name: } for
     * parameters, or empty) followed by its type. The type is carried as a {@link PyType} — never just a rendered string —
     * so the grid can ALWAYS colour and link it; a slot that shows a type without one cannot be built. Construct
     * slots only through {@link #argument}/{@link #parameter}/{@link #ofType} (the constructor is private), which derive the displayed
     * {@code type} name from the {@link PyType}, so the name and the type can never diverge.
     */
    public static final class Slot {
        private final String name;
        private final @Nullable PyType pyType;
        private final String type;
        /**
         * The type name for the aligned grid tooltip: identical to {@code type} except that literals are shown bare ({@code 1}, not
         * {@code Literal[1]}), which the diff prefers. The plain-text {@link #getText()}/description keeps the full {@code type}.
         */
        private final String gridType;
        private final boolean matched;
        /**
         * Whether the parameter NAME is highlighted too — set for a wholly missing (unfilled) parameter, where the
         * whole parameter is the incompatibility, not just its type (a plain type mismatch highlights only the type).
         */
        private final boolean nameMismatch;

        private Slot(String name, @Nullable PyType pyType, String type, String gridType, boolean matched) {
            this(name, pyType, type, gridType, matched, false);
        }

        private Slot(String name, @Nullable PyType pyType, String type, String gridType, boolean matched, boolean nameMismatch) {
            this.name = name;
            this.pyType = pyType;
            this.type = type;
            this.gridType = gridType;
            this.matched = matched;
            this.nameMismatch = nameMismatch;
        }

        public String getName() {
            return name;
        }

        public @Nullable PyType getPyType() {
            return pyType;
        }

        public String getType() {
            return type;
        }

        public String getGridType() {
            return gridType;
        }

        public boolean getMatched() {
            return matched;
        }

        public boolean getNameMismatch() {
            return nameMismatch;
        }

        /**
         * The plain {@code name + type} text, for the Problems-view description.
         */
        public String getText() {
            return name + type;
        }

        /**
         * A provided argument rendered as {@code type} or {@code keyword=type}. An argument always has an (inferred) type, so it
         * is always rendered — {@code getTypeName} shows {@code Any} when it can't be determined.
         */
        public static Slot argument(PyExpression argument, @Nullable PyType type, TypeEvalContext context, boolean matched) {
            String keyword = argument instanceof PyKeywordArgument keywordArgument ? keywordArgument.getKeyword() : null;
            String name = keyword != null ? keyword + "=" : "";
            return new Slot(name, type, typeName(type, context), bareTypeName(type, context), matched);
        }

        public static Slot parameter(PyCallableParameter parameter, TypeEvalContext context, boolean matched) {
            return parameter(parameter, context, matched, false);
        }

        /**
         * An expected parameter using its DECLARED type: an unannotated parameter (a null type) shows just its name,
         * with no {@code : type}. {@code nameMismatch} highlights the name for a wholly missing parameter.
         */
        public static Slot parameter(PyCallableParameter parameter, TypeEvalContext context, boolean matched, boolean nameMismatch) {
            PyType type = parameter.getType(context);
            boolean render = !isUnknown(type);
            return of(parameter, type, render ? typeName(type, context) : null,
                render ? bareTypeName(type, context) : null, matched, nameMismatch);
        }

        public static Slot parameter(PyCallableParameter parameter, @Nullable PyType type, TypeEvalContext context, boolean matched) {
            return parameter(parameter, type, context, matched, false);
        }

        /**
         * An expected parameter shown with a specific {@code type} (e.g. one substituted at the call site) rather than its
         * declared one — always rendered ({@code getTypeName} shows {@code Any} when unknown). The {@code type} is REQUIRED, so its
         * colour and link are never dropped.
         */
        public static Slot parameter(PyCallableParameter parameter,
                                     @Nullable PyType type,
                                     TypeEvalContext context,
                                     boolean matched,
                                     boolean nameMismatch) {
            return of(parameter, type, typeName(type, context), bareTypeName(type, context), matched, nameMismatch);
        }

        /**
         * A bare type with no parameter name (an expected position whose parameter is unknown).
         */
        public static Slot ofType(@Nullable PyType type, TypeEvalContext context, boolean matched) {
            return new Slot("", type, typeName(type, context), bareTypeName(type, context), matched);
        }

        private static String typeName(@Nullable PyType type, TypeEvalContext context) {
            return PythonDocumentationProvider.getTypeName(type, context);
        }

        /**
         * The grid's type name: literals shown bare ({@code 1}, not {@code Literal[1]}).
         */
        private static String bareTypeName(@Nullable PyType type, TypeEvalContext context) {
            return PythonDocumentationProvider.getTypeName(type, context, PyTypeRendererFeature.BARE_LITERAL);
        }

        /**
         * Assembles a parameter slot: {@code name: typeName} when {@code typeName} is given (an explicit/declared type), or just
         * the name when it is null (an unannotated parameter); always carries {@code type} for the grid to colour + link.
         */
        private static Slot of(PyCallableParameter parameter,
                               @Nullable PyType type,
                               @Nullable String typeName,
                               @Nullable String bareTypeName,
                               boolean matched,
                               boolean nameMismatch) {
            String prefix = containerPrefix(parameter);
            String name = parameter.getName();
            if (name == null) {
                return new Slot("", type, typeName != null ? typeName : "", bareTypeName != null ? bareTypeName : "", matched, nameMismatch);
            }
            return typeName == null
                ? new Slot(prefix + name, type, "", "", matched, nameMismatch)
                : new Slot(prefix + name + ": ", type, typeName, bareTypeName != null ? bareTypeName : "", matched, nameMismatch);
        }
    }

    /**
     * The {@code *}/{@code **} prefix for a positional/keyword container parameter, or empty for an ordinary parameter.
     */
    public static String containerPrefix(PyCallableParameter parameter) {
        return parameter.isPositionalContainer() ? "*" : parameter.isKeywordContainer() ? "**" : "";
    }

    /**
     * The header naming the single common callee (rendered with the name as a {@code <code>} span in the tooltip and
     * in single quotes in the description), or a generic "no signature" message when the candidates differ or
     * are anonymous. A {@code __init__}/{@code __new__} callee is named after its class, since the dunder name carries no
     * information for the reader.
     */
    public static PyInspectionMessages.ProblemMessage header(List<? extends @Nullable PyCallable> callables) {
        List<@Nullable String> names = new ArrayList<>();
        for (PyCallable callable : callables) {
            names.add(calleeDisplayName(callable));
        }
        String common = names.isEmpty() ? null : names.get(0);
        boolean allSame = true;
        for (String name : names) {
            if (!java.util.Objects.equals(name, common)) {
                allSame = false;
                break;
            }
        }
        return common != null && !common.isBlank() && allSame
            ? PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerNoOverloadMatchesArguments(params[0]), common)
            : PyInspectionMessages.problemMessage(params -> PyLocalize.inspTypeCheckerNoSignatureMatchesArguments());
    }

    private static @Nullable String calleeDisplayName(@Nullable PyCallable callable) {
        String name = callable != null ? callable.getName() : null;
        if (name == null) {
            return null;
        }
        if (name.equals(PyNames.INIT) || name.equals(PyNames.NEW)) {
            if (callable instanceof PyFunction function) {
                PyClass containingClass = function.getContainingClass();
                String className = containingClass != null ? containingClass.getName() : null;
                if (className != null) {
                    return className;
                }
            }
        }
        return name;
    }

    public static String description(PyInspectionMessages.ProblemMessage header,
                                     List<Slot> argumentSlots,
                                     List<List<Slot>> expectedRows) {
        return description(header, argumentSlots, expectedRows, List.of());
    }

    /**
     * Flat one-line description for the Problems view and batch mode.
     */
    public static String description(PyInspectionMessages.ProblemMessage header,
                                     List<Slot> argumentSlots,
                                     List<List<Slot>> expectedRows,
                                     List<String> expectedTypeParameters) {
        List<String> expectedParts = new ArrayList<>();
        for (int i = 0; i < expectedRows.size(); i++) {
            String typeParameters = i < expectedTypeParameters.size() ? expectedTypeParameters.get(i) : "";
            expectedParts.add(typeParameters + tupleText(expectedRows.get(i)));
        }
        String expected = String.join(", ", expectedParts);
        return header.description() + ". " +
            PyLocalize.inspTypeCheckerArgumentTypesLabel().get() + " " + tupleText(argumentSlots) + ". " +
            PyLocalize.inspTypeCheckerExpectedOneOfLabel().get() + " " + expected;
    }

    public static String tooltip(PyInspectionMessages.ProblemMessage header,
                                 List<Slot> argumentSlots,
                                 List<List<Slot>> expectedRows) {
        return tooltip(header, argumentSlots, expectedRows, List.of());
    }

    /**
     * Styled HTML tooltip for the editor hover. The provided argument types and each candidate signature are
     * rendered as aligned, code-styled rows (via {@link PyTypeDiffGrid}) so the arguments line up column-by-column with the
     * parameters; the parts that match are muted and only the offending parts stand out.
     */
    public static String tooltip(PyInspectionMessages.ProblemMessage header,
                                 List<Slot> argumentSlots,
                                 List<List<Slot>> expectedRows,
                                 List<String> expectedTypeParameters) {
        // When the structural diff is disabled, fall back to the plain description as the tooltip (no aligned grid).
        if (!PyTypeDiff.diffTooltipsEnabled()) {
            return HtmlChunk.text(description(header, argumentSlots, expectedRows, expectedTypeParameters)).wrapWith("html").toString();
        }
        int columnCount = argumentSlots.size();
        for (List<Slot> row : expectedRows) {
            columnCount = Math.max(columnCount, row.size());
        }
        // The provided arguments are one PROVIDED (red) row; each candidate signature is an EXPECTED (green) row. So the
        // overload report colours its mismatches exactly like the structural diff — red provided vs green expected, each
        // over a background — because it goes through the SAME shared grid with each row tagged by its side. A generic
        // candidate carries a leading `[T: bound]` type-parameter column (the argument row and non-generic candidates
        // pad it), so the reader sees the type variables the signature is written in.
        List<PyTypeDiffGrid.Row> rows = new ArrayList<>();
        rows.add(new PyTypeDiffGrid.Row(PyLocalize.inspTypeCheckerArgumentTypesLabel().get(),
            rowCells("", argumentSlots, columnCount), PyTypeDiffGrid.Side.PROVIDED));
        for (int i = 0; i < expectedRows.size(); i++) {
            String label = i == 0 ? PyLocalize.inspTypeCheckerExpectedOneOfLabel().get() : "";
            String typeParameters = i < expectedTypeParameters.size() ? expectedTypeParameters.get(i) : "";
            rows.add(new PyTypeDiffGrid.Row(label, rowCells(typeParameters, expectedRows.get(i), columnCount), PyTypeDiffGrid.Side.EXPECTED));
        }
        String headerHtml = PyInspectionMessages.removeSurrounding(header.tooltip(), "<html>", "</html>");
        return PyTypeDiffGrid.tooltip(HtmlChunk.raw(headerHtml), rows);
    }

    /**
     * A {@code (slot, slot, …)} tuple rendered as {@link PyTypeDiffGrid} cells. Each slot becomes a right-aligned name cell and a
     * type cell (so the types line up); an unmatched slot's type is red, but its name is never highlighted.
     */
    private static List<PyTypeDiffGrid.Cell> rowCells(String typeParameters, List<Slot> slots, int columnCount) {
        // A leading `[T: bound]` column, present only on generic candidates; the argument row and non-generic candidates
        // pass an empty prefix (padded to this column's width) so every `(` still lines up.
        List<PyTypeDiffGrid.Cell> cells = new ArrayList<>();
        cells.add(PyTypeDiffGrid.value(typeParameters, false));
        cells.add(PyTypeDiffGrid.delim("("));
        for (int i = 0; i < columnCount; i++) {
            Slot slot = i < slots.size() ? slots.get(i) : null;
            String suffix = i < slots.size() - 1 ? ", " : "";
            if (slot == null) {
                cells.add(PyTypeDiffGrid.delim(""));
                cells.add(PyTypeDiffGrid.delim(""));
            }
            else {
                cells.add(PyTypeDiffGrid.value(slot.getName(), slot.getNameMismatch(), true));
                cells.add(PyTypeDiffGrid.typeValue(slot.getPyType(), slot.getGridType(), !slot.getMatched(), false, suffix));
            }
        }
        cells.add(PyTypeDiffGrid.delim(")"));
        return cells;
    }

    private static String tupleText(List<Slot> slots) {
        return slots.stream().map(Slot::getText).collect(Collectors.joining(", ", "(", ")"));
    }
}
