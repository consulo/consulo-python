// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFunction;
import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.application.Application;
import consulo.component.persist.PersistentStateComponent;
import consulo.component.persist.State;
import consulo.component.persist.Storage;
import consulo.component.persist.StoragePathMacros;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.StringUtil;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


@State(name = "PythonDocumentationMap", storages = @Storage(file = StoragePathMacros.APP_CONFIG + "/other.xml"))
@ServiceAPI(ComponentScope.APPLICATION)
@ServiceImpl
@Singleton
public class PythonDocumentationMap implements PersistentStateComponent<PythonDocumentationMap.State> {
  private static final ImmutableMap<String, String> DEFAULT_ENTRIES = ImmutableMap.<String, String>builder()
    .put("PyQt4", "http://pyqt.sourceforge.net/Docs/PyQt4/{class.name.lower}.html#{function.name}")
    .put("PyQt5", "http://doc.qt.io/qt-5/{class.name.lower}.html#{functionToProperty.name}{functionIsProperty?-prop}")
    .put("PySide", "http://pyside.github.io/docs/pyside/{module.name.slashes}/{class.name}.html#{module.name}.{element.qname}")
    .put("gtk",
         "http://library.gnome.org/devel/pygtk/stable/class-gtk{class.name.lower}.html#method-gtk{class.name.lower}--{function.name.dashes}")
    .put("wx", "http://www.wxpython.org/docs/api/{module.name}.{class.name}-class.html#{function.name}")
    .put("kivy", "http://kivy.org/docs/api-{module.name}.html")
    .put("matplotlib", "http://matplotlib.org/api/{module.basename}_api.html#{element.qname}")
    .put("pyramid", "http://docs.pylonsproject.org/projects/pyramid/en/latest/api/{module.basename}.html#{element.qname}")
    .put("flask", "http://flask.pocoo.org/docs/latest/api/#{element.qname}")
    .put("pandas", "https://pandas.pydata.org/pandas-docs/stable/generated/{element.qname}.html")
    .build();

  public static PythonDocumentationMap getInstance() {
    return Application.get().getInstance(PythonDocumentationMap.class);
  }

  public static class State {
    private Map<String, String> myEntries = Maps.newHashMap(DEFAULT_ENTRIES);

    public Map<String, String> getEntries() {
      return myEntries;
    }

    public void setEntries(Map<String, String> entries) {
      myEntries = entries;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      State state = (State)o;
      return myEntries.equals(state.getEntries());
    }

    @Override
    public int hashCode() {
      return myEntries != null ? myEntries.hashCode() : 0;
    }
  }

  private State myState = new State();

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public void loadState(State state) {
    myState = state;

    addAbsentEntriesFromDefaultState(myState);
  }

  private static void addAbsentEntriesFromDefaultState(State state) {
    DEFAULT_ENTRIES.forEach((key, value) -> state.getEntries().putIfAbsent(key, value));
  }

  public Map<String, String> getEntries() {
    return Map.copyOf(myState.getEntries());
  }

  public void setEntries(Map<String, String> entries) {
    myState.setEntries(entries);
  }

  public @Nullable String urlFor(QualifiedName moduleQName, @Nullable PsiNamedElement element, @Nullable String pyVersion) {
    for (Map.Entry<String, String> entry : myState.getEntries().entrySet()) {
      if (moduleQName.matchesPrefix(QualifiedName.fromDottedString(entry.getKey()))) {
        return transformPattern(entry.getValue(), moduleQName, element, pyVersion);
      }
    }
    return null;
  }

  public boolean isUserDefinedUrl(String url) {
    return ContainerUtil.exists(getEntries().values(),
                                pattern -> url.startsWith(rootForPattern(pattern)) && !DEFAULT_ENTRIES.containsValue(pattern));
  }

  private static String rootForPattern(String urlPattern) {
    int pos = urlPattern.indexOf('{');
    return pos >= 0 ? urlPattern.substring(0, pos) : urlPattern;
  }

  private static @Nullable String transformPattern(String urlPattern, QualifiedName moduleQName, @Nullable PsiNamedElement element,
                                                   @Nullable String pyVersion) {
    Map<String, @Nullable String> macros = new HashMap<>();
    macros.put("element.name", element == null ? null : element.getName());
    PyClass pyClass = element == null ? null : PsiTreeUtil.getParentOfType(element, PyClass.class, false);
    macros.put("class.name", pyClass == null ? null : pyClass.getName());
    if (element != null) {
      StringBuilder qName = new StringBuilder(moduleQName.toString()).append(".");
      if (element instanceof PyFunction && ((PyFunction)element).getContainingClass() != null) {
        qName.append(((PyFunction)element).getContainingClass().getName()).append(".");
      }
      qName.append(element.getName());
      macros.put("element.qname", qName.toString());
    }
    else {
      macros.put("element.qname", "");
    }
    String functionName = element instanceof PyFunction && element.getName() != null ? element.getName() : "";
    macros.put("function.name", functionName);
    macros.put("functionToProperty.name", functionToProperty(functionName));
    macros.put("module.name", moduleQName.toString());
    macros.put("python.version", pyVersion);
    macros.put("module.basename", moduleQName.getLastComponent());

    macros.put("functionIsProperty?", Boolean.toString(!functionName.equals(functionToProperty(functionName))));

    final String pattern = transformPattern(urlPattern, macros);
    if (pattern == null) {
      return rootForPattern(urlPattern);
    }
    return pattern;
  }

  private static String functionToProperty(String name) {
    String functionOrProp = StringUtil.getPropertyName(name);
    if (functionOrProp == null) {
      functionOrProp = name;
    }
    return functionOrProp;
  }

  private static @Nullable String transformPattern(String urlPattern, Map<String, @Nullable String> macroValues) {
    for (Map.Entry<String, @Nullable String> entry : macroValues.entrySet()) {
      if (entry.getValue() == null) {
        if (urlPattern.contains("{" + entry.getKey())) {
          return null;
        }
        continue;
      }
      if (entry.getKey().endsWith("?")) {
        String regex = "\\{" + Pattern.quote(entry.getKey()) + "([^}]+)\\}";

        Matcher matcher = Pattern.compile(regex).matcher(urlPattern);
        if (matcher.find()) {
          String value = Boolean.parseBoolean(entry.getValue()) ? matcher.group(1) : "";

          urlPattern = urlPattern.replaceAll(regex, value);
        }
      }
      urlPattern = urlPattern
        .replace("{" + entry.getKey() + "}", entry.getValue())
        .replace("{" + entry.getKey() + ".lower}", StringUtil.toLowerCase(entry.getValue()))
        .replace("{" + entry.getKey() + ".slashes}", entry.getValue().replace(".", "/"))
        .replace("{" + entry.getKey() + ".dashes}", entry.getValue().replace("_", "-"));
    }
    return urlPattern.replace("{}", "");
  }
}
