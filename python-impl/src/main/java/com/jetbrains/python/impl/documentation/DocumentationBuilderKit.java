/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.jetbrains.python.impl.documentation;

import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.toolbox.ChainIterable;
import com.jetbrains.python.impl.toolbox.FP;
import com.jetbrains.python.psi.PyExpression;
import consulo.language.editor.documentation.DocumentationManagerProtocol;
import consulo.util.lang.StringUtil;
import consulo.util.lang.xml.XmlStringUtil;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class DocumentationBuilderKit {
    static final TagWrapper TagBold = new TagWrapper("b");
    static final TagWrapper TagItalic = new TagWrapper("i");
    static final TagWrapper TagSmall = new TagWrapper("small");
    static final TagWrapper TagCode = new TagWrapper("code");

    static final FP.Lambda1<String, String> LCombUp = DocumentationBuilderKit::combUp;
    final static String BR = "<br>";
    static final FP.Lambda1<String, String> LSame1 = name -> name;
    static final FP.Lambda1<Iterable<String>, Iterable<String>> LSame2 = what -> what;
    public static FP.Lambda1<PyExpression, String> LReadableRepr = arg -> PyUtil.getReadableRepr(arg, true);

    private DocumentationBuilderKit() {
    }

    static ChainIterable<String> wrapInTag(String tag, Iterable<String> content) {
        return new ChainIterable<>("<" + tag + ">").add(content).addItem("</" + tag + ">");
    }

    private static final String[] COMB_UP_REPLACE_FROM = {"\n", " "};
    private static final String[] COMB_UP_REPLACE_TO = {BR, "&nbsp;"};

    static String combUp(String what) {
        return StringUtil.replace(XmlStringUtil.escapeText(what), COMB_UP_REPLACE_FROM, COMB_UP_REPLACE_TO);
    }

    static ChainIterable<String> $(String... content) {
        return new ChainIterable<>(Arrays.asList(content));
    }

    static <T> Iterable<T> interleave(Iterable<T> source, T filler) {
        List<T> ret = new LinkedList<>();
        boolean is_next = false;
        for (T what : source) {
            if (is_next) {
                ret.add(filler);
            }
            else {
                is_next = true;
            }
            ret.add(what);
        }
        return ret;
    }

    // make a first-order curried objects out of wrapInTag()
    static class TagWrapper implements FP.Lambda1<Iterable<String>, Iterable<String>> {
        private final String myTag;

        TagWrapper(String tag) {
            myTag = tag;
        }

        @Override
        public Iterable<String> apply(Iterable<String> contents) {
            return wrapInTag(myTag, contents);
        }
    }

    static class LinkWrapper implements FP.Lambda1<Iterable<String>, Iterable<String>> {
        private String myLink;

        LinkWrapper(String link) {
            myLink = link;
        }

        @Override
        public Iterable<String> apply(Iterable<String> contents) {
            return new ChainIterable<String>()
                .addItem("<a href=\"").addItem(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL).addItem(myLink).addItem("\">")
                .add(contents)
                .addItem("</a>");
        }
    }
}
