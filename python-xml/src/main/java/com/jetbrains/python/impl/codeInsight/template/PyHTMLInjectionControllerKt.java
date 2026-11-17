// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.template;

import com.intellij.xml.util.HtmlUtil;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Top-level declarations of {@code PyHTMLInjectionController.kt}.
 * <p>
 * Consulo: lives in the python-xml module, because {@link HtmlUtil} belongs to the XML plugin.
 */
public final class PyHTMLInjectionControllerKt {
    // JB lists "img" twice; Set.of rejects duplicates
    private static final Set<String> OBVIOUS_HTML_TAGS = Set.of("html", "div", "span", "body", "p", "h1", "h2", "h3", "img",
        "table", "ul", "ol", "li", "a", "form", "script");

    private static final Pattern TAG_NAME_PATTERN = Pattern.compile("<(\\w+)");

    private PyHTMLInjectionControllerKt() {
    }

    public static boolean looksLikeHTML(String content) {
        if (content.isEmpty()) {
            return false;
        }

        String cleanContent = content.trim().replaceAll("[\n\r]", "");

        if (cleanContent.startsWith("<")) {

            Matcher tagNameMatch = TAG_NAME_PATTERN.matcher(cleanContent);
            if (tagNameMatch.find()) {
                String tagName = tagNameMatch.group(1);
                // Consulo: HtmlUtil.isHtmlBlockTag(tagName, false) / HtmlUtil.isTagWithOptionalEnd(tagName, false) are case-insensitive
                // isHtmlBlockTag(String) / isOptionalEndForHtmlTag(String)
                if (OBVIOUS_HTML_TAGS.contains(tagName) ||
                    HtmlUtil.isHtmlBlockTag(tagName) ||
                    HtmlUtil.isOptionalEndForHtmlTag(tagName)) {
                    return true;
                }
            }
        }
        return false;
    }
}
