// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation.doctest;

import com.jetbrains.python.psi.PyIndentUtil;
import consulo.document.util.TextRange;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Finds Markdown fenced code blocks (<code>```</code> / {@code ~~~})
 */
final class PyDocstringMarkdownFences {
    private PyDocstringMarkdownFences() {
    }

    /**
     * @param content         docstring text with the surrounding prefix and quotes already stripped
     * @param pythonLanguages accepted fence info-strings, lower-case (e.g. {@code py}, {@code python})
     * @return the content ranges of fenced blocks whose language is in {@code pythonLanguages}, relative to {@code content}
     */
    static List<TextRange> pythonFenceContentRanges(CharSequence content, Set<String> pythonLanguages) {
        if (content.length() == 0) {
            return Collections.emptyList();
        }

        List<String> lines = Arrays.asList(content.toString().split("\n", -1));
        List<String> dedentedLines = PyIndentUtil.removeCommonIndent(lines, true);
        String dedented = String.join("\n", dedentedLines);
        int[] originalLineStart = lineStartOffsets(lines);
        int[] dedentedLineStart = lineStartOffsets(dedentedLines);

        ArrayList<TextRange> ranges = new ArrayList<>();
        // TODO consulo: org.intellij.markdown parser missing (MarkdownParser(CommonMarkFlavourDescriptor).buildMarkdownTreeFromString(dedented) visited for CODE_FENCE nodes -> collectFence)
        return ranges;
    }

    private static int[] lineStartOffsets(List<String> lines) {
        int[] starts = new int[lines.size()];
        int offset = 0;
        for (int index = 0; index < lines.size(); index++) {
            starts[index] = offset;
            offset += lines.get(index).length() + 1; // + the '\n' that split() removed
        }
        return starts;
    }

    /**
     * Index of the line whose dedented start offset is the greatest one not exceeding {@code offset}.
     */
    private static int lineAt(int[] dedentedLineStart, int offset) {
        for (int i = dedentedLineStart.length - 1; i >= 0; i--) {
            if (dedentedLineStart[i] <= offset) {
                return i;
            }
        }
        return -1;
    }
}
