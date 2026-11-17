// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.inspections;

import com.jetbrains.python.impl.documentation.PyDocumentationLink;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import consulo.application.ui.UISettings;
import consulo.application.util.HtmlBuilder;
import consulo.application.util.HtmlChunk;
import consulo.ui.ex.ColorBlindness;
import consulo.ui.ex.JBColor;
import consulo.ui.ex.awt.UIUtil;
import consulo.ui.ex.awt.util.ColorUtil;
import org.jspecify.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * Shared renderer for the aligned, code-styled "type diff" tooltips. Lays out one or more rows of {@link Cell}s as
 * monospace {@code <code>} lines, padding each column to a common width (with non-breaking spaces) so the cells line
 * up vertically across rows; incompatible cells are shown in red and structural delimiters are muted.
 * <p>
 * Both {@link PyTypeDiff} (two-row signature/type diffs) and {@link PyMismatchTooltips} (the argument-vs-candidate
 * overload report) build their rows out of these cells and render them the same way, so the two displays share
 * one look — aligned columns wrapped in code spans.
 */
public final class PyTypeDiffGrid {
    private PyTypeDiffGrid() {
    }

    public enum Kind {
        DELIM, VALUE, MISMATCH
    }

    /**
     * Which side of the comparison a row shows. Like an editor diff, a {@link #PROVIDED} row's incompatible cells are red
     * and an {@link #EXPECTED} row's are green, each over a subtle matching background so they stand out. Every consumer
     * tags each of its rows with a side (see {@link Row}), so all displays — the two-row structural diff and the overload
     * report's argument-vs-candidate rows — colour their mismatches identically.
     */
    public enum Side {
        PROVIDED, EXPECTED
    }

    /**
     * A styled run of text inside a cell, used to color individual union members separately. {@code link}, when set, is a
     * navigable {@code #element/…} href wrapped around this run. {@code rich}, when set, is the platform's highlighted + linked
     * rendering of a non-mismatched value run, used verbatim in place of {@code text}.
     */
    public static final class Segment {
        final String text;
        final Kind kind;
        final @Nullable String link;
        final @Nullable HtmlChunk rich;

        Segment(String text, Kind kind) {
            this(text, kind, null, null);
        }

        Segment(String text, Kind kind, @Nullable String link, @Nullable HtmlChunk rich) {
            this.text = text;
            this.kind = kind;
            this.link = link;
            this.rich = rich;
        }
    }

    /**
     * One cell of the grid. {@code text} is the styled main content (or, when {@code segments} is non-null, the cell renders
     * those individually-colored runs instead — used to highlight only the offending members of a union). {@code suffix}
     * is an always-muted trailer (a comma and any {@code /}/{@code *} separators) that stays attached to the value while the
     * column padding falls after it, so commas hug their values. The column width counts the content + {@code suffix}.
     */
    public static final class Cell {
        final String text;
        final Kind kind;
        final boolean alignRight;
        final String suffix;
        final @Nullable List<Segment> segments;
        final @Nullable String link;
        final @Nullable HtmlChunk rich;

        Cell(String text, Kind kind) {
            this(text, kind, false, "", null, null, null);
        }

        Cell(String text,
             Kind kind,
             boolean alignRight,
             String suffix,
             @Nullable List<Segment> segments,
             @Nullable String link,
             @Nullable HtmlChunk rich) {
            this.text = text;
            this.kind = kind;
            this.alignRight = alignRight;
            this.suffix = suffix;
            this.segments = segments;
            this.link = link;
            this.rich = rich;
        }
    }

    /**
     * A structural delimiter such as {@code (}, {@code [}, {@code , } or {@code  -> } — rendered muted.
     */
    public static Cell delim(String text) {
        return new Cell(text, Kind.DELIM);
    }

    public static Cell value(String text, boolean mismatch) {
        return value(text, mismatch, false, "");
    }

    public static Cell value(String text, boolean mismatch, boolean alignRight) {
        return value(text, mismatch, alignRight, "");
    }

    /**
     * A NON-type value cell — a parameter name, a {@code = ...} default, a {@code keyword=}: plain text, red when {@code mismatch},
     * but NEVER a syntax colour or navigable link. A type must go through {@link #typeValue} instead, which is the only way
     * to attach a link + the platform's highlighted rendering — so a type can never be shown as a bare string.
     */
    public static Cell value(String text, boolean mismatch, boolean alignRight, String suffix) {
        return new Cell(text, mismatch ? Kind.MISMATCH : Kind.VALUE, alignRight, suffix, null, null, null);
    }

    public static Cell segmented(List<Segment> segments) {
        return segmented(segments, false, "");
    }

    /**
     * A value cell whose content is a sequence of {@code segments} (e.g. a union with only some members highlighted).
     */
    public static Cell segmented(List<Segment> segments, boolean alignRight, String suffix) {
        return new Cell("", Kind.VALUE, alignRight, suffix, segments, null, null);
    }

    /**
     * A NON-type segment run; a type member must go through {@link #typeSegment} to carry its colour + link.
     */
    public static Segment segment(String text, boolean mismatch) {
        return new Segment(text, mismatch ? Kind.MISMATCH : Kind.VALUE);
    }

    public static Segment segmentDelim(String text) {
        return new Segment(text, Kind.DELIM);
    }

    public static Cell typeValue(@Nullable PyType type, String name, boolean mismatch) {
        return typeValue(type, name, mismatch, false, "");
    }

    /**
     * Turns a {@code type} (shown as {@code name}) into a value cell — THE single place every consumer (the structural diff and
     * the overload report) turns a type into a cell, so type names render identically everywhere: red when {@code mismatch},
     * otherwise the platform's highlighted, navigable rendering. Requiring the {@code type} here (rather than a bare name)
     * is what guarantees every rendered type gets its colour + link.
     */
    public static Cell typeValue(@Nullable PyType type, String name, boolean mismatch, boolean alignRight, String suffix) {
        return new Cell(name, mismatch ? Kind.MISMATCH : Kind.VALUE, alignRight, suffix, null, elementLink(type), richType(type, name, mismatch));
    }

    /**
     * Like {@link #typeValue} but for one run inside a cell (a single union member).
     */
    public static Segment typeSegment(@Nullable PyType type, String name, boolean mismatch) {
        return new Segment(name, mismatch ? Kind.MISMATCH : Kind.VALUE, elementLink(type), richType(type, name, mismatch));
    }

    /**
     * The platform's highlighted + linked rendering of {@code type} shown as {@code name}, for a non-{@code mismatch} value (a
     * mismatched value keeps the diff's own red/green, which must win over the syntax colour).
     */
    private static @Nullable HtmlChunk richType(@Nullable PyType type, String name, boolean mismatch) {
        return mismatch ? null : PyDocumentationLink.toTypeTooltipLink(type, name);
    }

    /**
     * A navigable {@code #element/<fqn>} href to {@code type}'s class declaration (resolved by the platform ElementLinkHandler
     * purely by qualified name), or null for a non-class type.
     */
    private static @Nullable String elementLink(@Nullable PyType type) {
        if (type instanceof PyClassType classType) {
            String qualifiedName = classType.getPyClass().getQualifiedName();
            if (qualifiedName != null) {
                return PyDocumentationLink.TOOLTIP_ELEMENT_LINK_PREFIX + qualifiedName;
            }
        }
        return null;
    }

    /**
     * Returns a copy of {@code cell} with {@code suffix} appended to its current suffix (used to attach a trailing comma).
     */
    public static Cell withSuffix(Cell cell, String suffix) {
        return new Cell(cell.text, cell.kind, cell.alignRight, cell.suffix + suffix, cell.segments, cell.link, cell.rich);
    }

    private static final Cell EMPTY = new Cell("", Kind.DELIM);

    private static int width(Cell cell) {
        int content;
        if (cell.segments != null) {
            content = 0;
            for (Segment segment : cell.segments) {
                content += segment.text.length();
            }
        }
        else {
            content = cell.text.length();
        }
        return content + cell.suffix.length();
    }

    /**
     * One labeled row of the grid: its {@code label} (e.g. {@code Expected:}; "" to continue under the previous row's label),
     * the aligned {@code cells} of the line, and which {@code side} of the comparison it shows. Bundling the side WITH the cells
     * is what keeps every consumer's colouring correct — a row can't be rendered without saying which side it is.
     */
    public record Row(String label, List<Cell> cells, Side side) {
    }

    /**
     * Builds the tooltip HTML: an optional {@code headline} chunk above a grid of {@code rows}. Each {@link Row} carries its own label
     * (shown in a leading table column so the reader can tell the rows apart) and its {@link Side} (which colours its
     * mismatches); rows may differ in cell count and are padded on the right. Pass {@link HtmlChunk#empty} for no headline.
     */
    public static String tooltip(HtmlChunk headline, List<Row> rows) {
        int[] widths = columnWidths(rows);
        // A <table> is a block element, so the headline sits on its own line above the aligned rows.
        return new HtmlBuilder().append(headline).append(labeledTable(rows, widths)).wrapWith("html").toString();
    }

    private static int[] columnWidths(List<Row> rows) {
        int columnCount = 0;
        for (Row row : rows) {
            columnCount = Math.max(columnCount, row.cells().size());
        }
        int[] widths = new int[columnCount];
        for (int i = 0; i < columnCount; i++) {
            int max = 0;
            for (Row row : rows) {
                List<Cell> cells = row.cells();
                int w = i < cells.size() ? width(cells.get(i)) : 0;
                max = Math.max(max, w);
            }
            widths[i] = max;
        }
        return widths;
    }

    /**
     * One row rendered as a single monospace {@code <code>} line, each cell padded to its column width.
     */
    private static HtmlChunk line(List<Cell> row, int[] widths, Side side) {
        HtmlBuilder builder = new HtmlBuilder();
        for (int i = 0; i < widths.length; i++) {
            Cell cell = i < row.size() ? row.get(i) : EMPTY;
            int padCount = Math.max(widths[i] - width(cell), 0);
            // A mismatched component with no text of its own is a "gap": the value is absent on this side (a missing
            // parameter, name, type or default). Paint the column position it would occupy with the mismatch background
            // so the missing component is visible rather than an invisible empty cell, keeping any trailing separator muted.
            if (cell.kind == Kind.MISMATCH && cell.text.isEmpty() && cell.segments == null) {
                builder.append(missingBlock(padCount, side));
                if (!cell.suffix.isEmpty()) {
                    builder.append(styledText(cell.suffix, Kind.DELIM, side, null, null));
                }
                continue;
            }
            HtmlChunk pad = padCount == 0 ? HtmlChunk.empty() : HtmlChunk.nbsp(padCount);
            if (cell.alignRight) {
                builder.append(pad).append(styled(cell, side));
            }
            else {
                builder.append(styled(cell, side)).append(pad);
            }
        }
        return builder.toFragment().wrapWith(HtmlChunk.tag("code").style(CODE_LINE_STYLE));
    }

    // A diff row is one aligned line: wrapping it would break the column alignment, so it must stay on a single line
    // and the tooltip shows a horizontal scrollbar for a long signature instead. This overrides the platform tooltip
    // stylesheet's `code { overflow-wrap: anywhere; }`, which would otherwise break the row to fit the tooltip width.
    private static final String CODE_LINE_STYLE = "white-space: nowrap; overflow-wrap: normal;";

    private static HtmlChunk labeledTable(List<Row> rows, int[] widths) {
        HtmlBuilder table = new HtmlBuilder();
        for (Row row : rows) {
            table.append(HtmlChunk.tag("tr").children(
                HtmlChunk.tag("td").style(labelStyle()).addText(row.label()),
                HtmlChunk.tag("td").child(line(row.cells(), widths, row.side()))
            ));
        }
        return table.toFragment().wrapWith("table");
    }

    private static HtmlChunk styled(Cell cell, Side side) {
        HtmlChunk main;
        if (cell.segments != null) {
            HtmlBuilder builder = new HtmlBuilder();
            for (Segment it : cell.segments) {
                builder.append(styledText(it.text, it.kind, side, it.link, it.rich));
            }
            main = builder.toFragment();
        }
        else {
            main = styledText(cell.text, cell.kind, side, cell.link, cell.rich);
        }
        if (cell.suffix.isEmpty()) {
            return main;
        }
        return new HtmlBuilder().append(main).append(styledText(cell.suffix, Kind.DELIM, side, null, null)).toFragment();
    }

    private static HtmlChunk styledText(String text, Kind kind, Side side, @Nullable String link, @Nullable HtmlChunk rich) {
        // A non-mismatched value uses the platform's highlighted + linked rendering when supplied (`None` in the keyword
        // colour, a builtin in the builtin colour, carrying its own `#element/…` link), returned verbatim. A mismatched
        // value keeps the diff's own red/green, which must win over any syntax colour.
        if (kind == Kind.VALUE && rich != null) {
            return rich;
        }
        HtmlChunk styled = switch (kind) {
            case DELIM -> HtmlChunk.text(text).wrapWith(HtmlChunk.span().style(mutedStyle()));
            case VALUE -> HtmlChunk.text(text);
            case MISMATCH -> HtmlChunk.text(text).wrapWith(HtmlChunk.span().style(mismatchCss(side)));
        };
        // A navigable `#element/…` link to the type's declaration (resolved by the platform ElementLinkHandler purely by
        // qualified name); the diff keeps its own red/green/muted colour inside the link rather than the link colour.
        return link == null ? styled : HtmlChunk.tag("a").attr("href", link).child(styled);
    }

    /**
     * A background-only block {@code width} columns wide in the row's mismatch color, marking the position a component
     * would occupy on the side that is missing it (a missing parameter, name, type or default).
     */
    private static HtmlChunk missingBlock(int width, Side side) {
        if (width <= 0) {
            return HtmlChunk.empty();
        }
        return HtmlChunk.nbsp(width).wrapWith(HtmlChunk.span().style(missingCss(side)));
    }

    /**
     * The CSS for a mismatched cell: a red foreground for a provided value, green for an expected one, each over a
     * subtle matching background.
     */
    private static String mismatchCss(Side side) {
        return switch (side) {
            case PROVIDED -> foreground(errorForeground()) + tint(errorForeground());
            case EXPECTED -> foreground(successForeground()) + tint(successForeground());
        };
    }

    /**
     * The background tint for a missing position — the same soft band a mismatched cell uses, shown on its own
     * (there is no text) so the gap reads as red on the provided side and green on the expected side.
     */
    private static String missingCss(Side side) {
        return switch (side) {
            case PROVIDED -> tint(errorForeground());
            case EXPECTED -> tint(successForeground());
        };
    }

    private static String foreground(Color color) {
        return "color: " + ColorUtil.toHtmlColor(color) + ";";
    }

    /**
     * A soft background highlight: mostly the tooltip background with a hint of {@code color} mixed in, so the band sits
     * behind the colored text without overpowering it.
     */
    private static String tint(Color color) {
        return " background-color: " + ColorUtil.toHtmlColor(ColorUtil.mix(color, UIUtil.getToolTipBackground(), HIGHLIGHT_BACKGROUND_BLEND)) + ";";
    }

    /**
     * How much of the tooltip background to mix into a highlight color for its soft background tint.
     */
    private static final double HIGHLIGHT_BACKGROUND_BLEND = 0.82;

    // Like an editor diff, the provided side is red and the expected side green — but red+green is exactly the pair
    // red-green colour-vision deficiency (protanopia/deuteranopia) can't separate, and the platform's daltonization
    // filter only corrects painted components/icons, not the colours we emit in HTML tooltips. So when the IDE's
    // colour-blindness setting is one of those, switch to an orange/blue pair that stays distinguishable.
    private static boolean redGreenColorBlind() {
        ColorBlindness blindness = UISettings.getInstance().COLOR_BLINDNESS;
        return blindness == ColorBlindness.protanopia || blindness == ColorBlindness.deuteranopia;
    }

    private static Color errorForeground() {
        return redGreenColorBlind() ? CVD_PROVIDED : UIUtil.getErrorForeground();
    }

    private static Color successForeground() {
        // TODO consulo: UIUtil.getLabelSuccessForeground missing
        return redGreenColorBlind() ? CVD_EXPECTED : JBColor.namedColor("Label.successForeground", 0x368746, 0x50A661);
    }

    // The colour-blind-safe replacements for red/green: orange (provided) and blue (expected), with light/dark variants.
    private static final Color CVD_PROVIDED = new JBColor(new Color(0xB5570C), new Color(0xCC7832));
    private static final Color CVD_EXPECTED = new JBColor(new Color(0x256BB0), new Color(0x4F9DF5));

    private static String mutedStyle() {
        return "color: " + ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground()) + ";";
    }

    // Row labels keep the default foreground color (not muted) so they read as headings, not greyed-out text.
    private static String labelStyle() {
        return "padding: 0px 8px 0px 4px;";
    }
}
