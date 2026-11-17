// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation;

import consulo.application.util.HtmlChunk;
import consulo.util.lang.StringUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * A layout node in the output of {@link PyTypeRenderer}.
 * <p>
 * Each node is an {@link HtmlChunk} that prints its flat form. Thus a caller that only calls {@link HtmlChunk#toString} gets the type on one line.
 * {@link #layout} breaks the lines of a rendered type so that the type fits in a given width.
 * Any other {@link HtmlChunk} in the tree is a leaf that {@link #layout} never breaks.
 */
abstract sealed class PyTypeLayout implements HtmlChunk
  permits PyTypeLayout.Concat, PyTypeLayout.Group, PyTypeLayout.Indent, PyTypeLayout.Line, PyTypeLayout.Fill {
  private static final int INDENT_SIZE = 4;

  static final class Concat extends PyTypeLayout {
    final List<HtmlChunk> parts;

    private Concat(List<HtmlChunk> parts) {
      this.parts = parts;
    }
  }

  /**
   * Prints {@code content} flat when it fits in the rest of the line, and breaks each {@link Line} that it owns otherwise.
   * A group that starts past the width stays flat, because its first line overflows anyway.
   */
  static final class Group extends PyTypeLayout {
    final HtmlChunk content;

    private Group(HtmlChunk content) {
      this.content = content;
    }
  }

  /**
   * Indents the lines that start inside {@code content} by {@link #INDENT_SIZE} more columns.
   */
  static final class Indent extends PyTypeLayout {
    final HtmlChunk content;

    private Indent(HtmlChunk content) {
      this.content = content;
    }
  }

  /**
   * Prints {@code flat} in a flat group. In a broken group, prints {@code beforeBreak}, a line break, the indentation, and then {@code afterBreak}.
   */
  static final class Line extends PyTypeLayout {
    final HtmlChunk flat;
    final HtmlChunk beforeBreak;
    final HtmlChunk afterBreak;

    private Line(HtmlChunk flat, HtmlChunk beforeBreak, HtmlChunk afterBreak) {
      this.flat = flat;
      this.beforeBreak = beforeBreak;
      this.afterBreak = afterBreak;
    }
  }

  /**
   * Puts as many items on a line as fit, and breaks only the separators that it must break.
   * The {@code parts} are the items, with a separator {@link Line} between each two items.
   */
  static final class Fill extends PyTypeLayout {
    final List<HtmlChunk> parts;

    private Fill(List<HtmlChunk> parts) {
      this.parts = parts;
    }
  }

  @Override
  public void appendTo(StringBuilder builder) {
    if (this instanceof Concat concat) {
      concat.parts.forEach(it -> it.appendTo(builder));
    }
    else if (this instanceof Group group) {
      group.content.appendTo(builder);
    }
    else if (this instanceof Indent indent) {
      indent.content.appendTo(builder);
    }
    else if (this instanceof Line line) {
      line.flat.appendTo(builder);
    }
    else if (this instanceof Fill fill) {
      fill.parts.forEach(it -> it.appendTo(builder));
    }
  }

  @Override
  public boolean isEmpty() {
    if (this instanceof Concat concat) {
      return concat.parts.stream().allMatch(HtmlChunk::isEmpty);
    }
    else if (this instanceof Group group) {
      return group.content.isEmpty();
    }
    else if (this instanceof Indent indent) {
      return indent.content.isEmpty();
    }
    else if (this instanceof Line) {
      return false;
    }
    else {
      return ((Fill)this).parts.stream().allMatch(HtmlChunk::isEmpty);
    }
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    appendTo(builder);
    return builder.toString();
  }

  static HtmlChunk concat(HtmlChunk... parts) {
    return new Concat(List.of(parts));
  }

  static HtmlChunk concat(List<HtmlChunk> parts) {
    return new Concat(parts);
  }

  /**
   * A separator that prints {@code flat} on one line.
   * When the separator breaks, the line ends with {@code beforeBreak}, and the next line starts with {@code afterBreak}.
   */
  static HtmlChunk separator(HtmlChunk flat, HtmlChunk beforeBreak, HtmlChunk afterBreak) {
    return new Line(flat, beforeBreak, afterBreak);
  }

  /**
   * Joins {@code items} with {@code separator}, and breaks only the separators that must break. The next lines get one more indent.
   */
  static HtmlChunk fill(List<HtmlChunk> items, HtmlChunk separator) {
    return new Indent(new Fill(interleave(items, separator)));
  }

  static HtmlChunk bracketed(HtmlChunk open,
                             List<HtmlChunk> items,
                             HtmlChunk separator,
                             HtmlChunk trailingComma,
                             HtmlChunk close) {
    return bracketed(open, items, separator, trailingComma, close, false);
  }

  /**
   * Puts {@code items} between {@code open} and {@code close}.
   * <p>
   * When the items do not fit on one line, each item goes on its own indented line, and the last item gets {@code trailingComma}.
   * One item stays next to the brackets, so {@code list[dict[str, int]]} breaks inside {@code dict[...]} only.
   * With {@code fill}, the items fill each line instead of one item for each line.
   */
  static HtmlChunk bracketed(HtmlChunk open,
                             List<HtmlChunk> items,
                             HtmlChunk separator,
                             HtmlChunk trailingComma,
                             HtmlChunk close,
                             boolean fill) {
    switch (items.size()) {
      case 0:
        return new Concat(List.of(open, close));
      case 1:
        return new Concat(List.of(open, items.get(0), close));
      default: {
        HtmlChunk content = fill ? new Fill(interleave(items, separator)) : new Concat(interleave(items, separator));
        Line softLine = new Line(HtmlChunk.empty(), HtmlChunk.empty(), HtmlChunk.empty());
        Line closingLine = new Line(HtmlChunk.empty(), trailingComma, HtmlChunk.empty());
        return new Group(new Concat(List.of(open, new Indent(new Concat(List.of(softLine, content))), closingLine, close)));
      }
    }
  }

  /**
   * Prints {@code chunk} with its lines broken to fit in {@code width} columns.
   * <p>
   * The first line starts at {@code startColumn}. A new line starts at {@code indent} columns, plus the indentation of its nesting level.
   * The line break is {@code \n}, and the indentation is spaces.
   * Set {@code html} when the leaves of {@code chunk} are HTML, so that the tags and the entities do not count as columns.
   */
  static String layout(HtmlChunk chunk, int width, int startColumn, int indent, boolean html) {
    return new Printer(width, startColumn, html).print(new Command(indent, Mode.BREAK, chunk));
  }

  /**
   * The number of visible columns in the last line of {@code html}.
   */
  static int lastLineWidth(String html) {
    return visibleWidth(html.substring(html.lastIndexOf('\n') + 1), true);
  }

  private static List<HtmlChunk> interleave(List<HtmlChunk> items, HtmlChunk separator) {
    ArrayList<HtmlChunk> result = new ArrayList<>(items.size() * 2);
    for (HtmlChunk item : items) {
      if (!result.isEmpty()) {
        result.add(separator);
      }
      result.add(item);
    }
    return result;
  }

  private static int visibleWidth(String text, boolean html) {
    return html ? StringUtil.unescapeXml(StringUtil.stripHtml(text, false)).length() : text.length();
  }

  private enum Mode {FLAT, BREAK}

  private record Command(int indent, Mode mode, HtmlChunk node) {
  }

  /**
   * A Wadler-style printer, with the fill of Prettier.
   */
  private static final class Printer {
    private final int width;
    private final boolean html;
    private final StringBuilder output = new StringBuilder();
    private int column;

    private Printer(int width, int startColumn, boolean html) {
      this.width = width;
      this.html = html;
      this.column = startColumn;
    }

    String print(Command root) {
      List<Command> stack = new ArrayList<>();
      stack.add(root);
      while (!stack.isEmpty()) {
        Command command = stack.remove(stack.size() - 1);
        int indent = command.indent();
        Mode mode = command.mode();
        HtmlChunk node = command.node();
        if (node instanceof Concat concat) {
          pushParts(stack, command, concat.parts);
        }
        else if (node instanceof Group group) {
          Command flat = new Command(indent, Mode.FLAT, group.content);
          stack.add(mode == Mode.FLAT || column >= width || fits(flat, stack) ? flat : new Command(indent, Mode.BREAK, group.content));
        }
        else if (node instanceof Indent indentNode) {
          stack.add(new Command(indent + INDENT_SIZE, mode, indentNode.content));
        }
        else if (node instanceof Line line) {
          switch (mode) {
            case FLAT -> text(line.flat);
            case BREAK -> {
              text(line.beforeBreak);
              newLine(indent);
              text(line.afterBreak);
            }
          }
        }
        else if (node instanceof Fill fill) {
          switch (mode) {
            case FLAT -> pushParts(stack, command, fill.parts);
            case BREAK -> fill(stack, indent, fill.parts);
          }
        }
        else {
          text(node);
        }
      }
      return output.toString();
    }

    /**
     * Prints the first item of a broken {@link Fill}, and decides if the separator after it breaks.
     * The separator stays flat when the next item also fits on the line.
     */
    private void fill(List<Command> stack, int indent, List<HtmlChunk> parts) {
      if (parts.isEmpty()) return;
      HtmlChunk content = parts.get(0);
      boolean contentFits = fits(new Command(indent, Mode.FLAT, content), restAfter(parts, 0, indent, stack));
      Command contentCommand = new Command(indent, contentFits ? Mode.FLAT : Mode.BREAK, content);
      if (parts.size() < 3) {
        if (parts.size() == 2) {
          stack.add(new Command(indent, contentFits ? Mode.FLAT : Mode.BREAK, parts.get(1)));
        }
        stack.add(contentCommand);
        return;
      }
      HtmlChunk separator = parts.get(1);
      Command pair = new Command(indent, Mode.FLAT, new Concat(List.of(content, separator, parts.get(2))));
      boolean pairFits = fits(pair, restAfter(parts, 2, indent, stack));
      stack.add(new Command(indent, Mode.BREAK, new Fill(parts.subList(2, parts.size()))));
      stack.add(new Command(indent, pairFits ? Mode.FLAT : Mode.BREAK, separator));
      stack.add(contentCommand);
    }

    /**
     * The commands that follow the item at {@code index} of a {@link Fill}: the separator after it, or else the rest of the {@code stack}.
     */
    private static List<Command> restAfter(List<HtmlChunk> parts, int index, int indent, List<Command> stack) {
      return index + 1 < parts.size() ? List.of(new Command(indent, Mode.BREAK, parts.get(index + 1))) : stack;
    }

    /**
     * Tells if {@code next} fits flat in the rest of the line.
     * The check goes on into {@code rest}, up to the first line break that {@code rest} can make, and counts the text before that break.
     */
    private boolean fits(Command next, List<Command> rest) {
      int remaining = width - column;
      List<Command> commands = new ArrayList<>();
      commands.add(next);
      int restIndex = rest.size();
      while (remaining >= 0) {
        if (commands.isEmpty()) {
          if (restIndex == 0) return true;
          commands.add(rest.get(--restIndex));
          continue;
        }
        Command command = commands.remove(commands.size() - 1);
        HtmlChunk node = command.node();
        if (node instanceof Concat concat) {
          pushParts(commands, command, concat.parts);
        }
        else if (node instanceof Group group) {
          commands.add(new Command(command.indent(), command.mode(), group.content));
        }
        else if (node instanceof Indent indentNode) {
          commands.add(new Command(command.indent(), command.mode(), indentNode.content));
        }
        else if (node instanceof Line line) {
          switch (command.mode()) {
            case FLAT -> remaining -= widthOf(line.flat);
            case BREAK -> {
              return remaining >= widthOf(line.beforeBreak);
            }
          }
        }
        else if (node instanceof Fill fill) {
          pushParts(commands, command, fill.parts);
        }
        else {
          remaining -= widthOf(node);
        }
      }
      return false;
    }

    private static void pushParts(List<Command> stack, Command command, List<HtmlChunk> parts) {
      for (int i = parts.size() - 1; i >= 0; i--) {
        stack.add(new Command(command.indent(), command.mode(), parts.get(i)));
      }
    }

    private void text(HtmlChunk chunk) {
      chunk.appendTo(output);
      column += widthOf(chunk);
    }

    private void newLine(int indent) {
      output.append('\n');
      for (int i = 0; i < indent; i++) {
        output.append(' ');
      }
      column = indent;
    }

    private int widthOf(HtmlChunk chunk) {
      return visibleWidth(chunk.toString(), html);
    }
  }
}
