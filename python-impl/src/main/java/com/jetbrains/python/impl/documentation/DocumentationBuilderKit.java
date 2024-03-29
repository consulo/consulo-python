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

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.jetbrains.annotations.NonNls;
import consulo.language.editor.documentation.DocumentationManagerProtocol;
import consulo.util.lang.xml.XmlStringUtil;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.toolbox.ChainIterable;
import com.jetbrains.python.impl.toolbox.FP;

public class DocumentationBuilderKit {
  static final TagWrapper TagBold = new TagWrapper("b");
  static final TagWrapper TagItalic = new TagWrapper("i");
  static final TagWrapper TagSmall = new TagWrapper("small");
  static final TagWrapper TagCode = new TagWrapper("code");

  static final FP.Lambda1<String, String> LCombUp = new FP.Lambda1<String, String>() {
    public String apply(String argname) {
      return combUp(argname);
    }
  };
  final static @NonNls String BR = "<br>";
  static final FP.Lambda1<String, String> LSame1 = new FP.Lambda1<String, String>() {
    public String apply(String name) {
      return name;
    }
  };
  static final FP.Lambda1<Iterable<String>, Iterable<String>> LSame2 = new FP.Lambda1<Iterable<String>, Iterable<String>>() {
    public Iterable<String> apply(Iterable<String> what) {
      return what;
    }
  };
  public static FP.Lambda1<PyExpression, String> LReadableRepr = new FP.Lambda1<PyExpression, String>() {
    public String apply(PyExpression arg) {
      return PyUtil.getReadableRepr(arg, true);
    }
  };

  private DocumentationBuilderKit() {
  }

  static ChainIterable<String> wrapInTag(String tag, Iterable<String> content) {
    return new ChainIterable<String>("<" + tag + ">").add(content).addItem("</" + tag + ">");
  }

  @NonNls
  static String combUp(@NonNls String what) {
    return XmlStringUtil.escapeString(what).replace("\n", BR).replace(" ", "&nbsp;");
  }

  static ChainIterable<String> $(String... content) {
    return new ChainIterable<String>(Arrays.asList(content));
  }

  static <T> Iterable<T> interleave(Iterable<T> source, T filler) {
    List<T> ret = new LinkedList<T>();
    boolean is_next = false;
    for (T what : source) {
      if (is_next) ret.add(filler);
      else is_next = true;
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

    public Iterable<String> apply(Iterable<String> contents) {
      return wrapInTag(myTag, contents);
    }

  }

  static class LinkWrapper implements FP.Lambda1<Iterable<String>, Iterable<String>> {
    private String myLink;

    LinkWrapper(String link) {
      myLink = link;
    }

    public Iterable<String> apply(Iterable<String> contents) {
      return new ChainIterable<String>()
        .addItem("<a href=\"").addItem(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL).addItem(myLink).addItem("\">")
        .add(contents).addItem("</a>")
      ;
    }
  }
}
