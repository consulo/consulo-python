// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.documentation;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.highlighting.PyHighlighter;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.types.PyLegacyDocstringTypeParser;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyTypeAliasStatement;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.application.util.HtmlChunk;
import consulo.language.editor.documentation.DocumentationManagerProtocol;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.StringUtil;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

import static com.jetbrains.python.impl.documentation.PyDocSignaturesHighlighterKt.styledReference;
import static com.jetbrains.python.impl.documentation.PyDocSignaturesHighlighterKt.styledSpan;
import static com.jetbrains.python.impl.psi.types.PyNoneTypeKt.isNoneType;

public final class PyDocumentationLink {

  /**
   * Prefix understood by {@code com.intellij.codeInsight.hint.ElementLinkHandler} for navigable tooltip links.
   */
  public static final String TOOLTIP_ELEMENT_LINK_PREFIX = "#element/";

  private static final String LINK_TYPE_CLASS = "#class#";
  private static final String LINK_TYPE_PARAM = "#param#";
  private static final String LINK_TYPE_TYPENAME = "#typename#";
  private static final String LINK_TYPE_FUNC = "#func#";
  private static final String LINK_TYPE_MODULE = "#module#";

  private PyDocumentationLink() {
  }

  public static HtmlChunk toContainingClass(String content) {
    return HtmlChunk.link(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + LINK_TYPE_CLASS, content);
  }

  public static HtmlChunk toPossibleClass(String typeName, PsiElement anchor, TypeEvalContext context) {
    PyClassType type = resolveNamedClassType(typeName, anchor, context);
    if (type != null) {
      HtmlChunk text = toClass(type.getPyClass(), typeName);
      if (isNoneType(type)) {
        return styledSpan(text, PyHighlighter.PY_KEYWORD);
      }
      else {
        return styledReference(text, type.getPyClass());
      }
    }
    return HtmlChunk.text(typeName);
  }

  /**
   * Resolves {@code typeName} to a single {@link PyClassType}. When the numeric tower is enabled, parsing a name such as
   * {@code float} yields a {@code float | int} union rather than a bare class type (see {@code PyNumericTowerUtil}); in that case
   * the union member whose name matches {@code typeName} is returned, so the rendered name still links to its own class
   * instead of falling back to plain, unhighlighted text.
   */
  private static @Nullable PyClassType resolveNamedClassType(String typeName, PsiElement anchor, TypeEvalContext context) {
    PyType type = PyLegacyDocstringTypeParser.getTypeByName(anchor, typeName, context);
    if (type instanceof PyClassType classType) {
      return classType;
    }
    else if (type instanceof PyUnionType unionType) {
      for (PyType member : unionType.getMembers()) {
        if (member instanceof PyClassType memberClassType && Objects.equals(memberClassType.getName(), typeName)) {
          return memberClassType;
        }
      }
      return null;
    }
    return null;
  }

  /**
   * Like {@link #toPossibleClass}, but produces a link in the {@code #element/<fqn>} format understood by the editor
   * tooltip link handler (see {@code com.intellij.codeInsight.hint.ElementLinkHandler}) rather than the
   * {@code psi_element://} protocol used by Quick Documentation. Use this to render clickable, highlighted
   * type names inside inspection tooltips.
   * <p>
   * The same highlighting as {@link #toPossibleClass} is applied, so builtins keep their builtin color. When the
   * resolved class has no qualified name (e.g. a local class) the name is rendered as highlighted text
   * without a link, so it can still be navigated to visually even if it cannot be resolved from the tooltip.
   */
  public static HtmlChunk toPossibleClassTooltipLink(String typeName, PsiElement anchor, TypeEvalContext context) {
    return toTypeTooltipLink(resolveNamedClassType(typeName, anchor, context), typeName);
  }

  /**
   * Renders {@code displayName} as a clickable, highlighted type-name chunk for an inspection tooltip — the same
   * {@code #element/<fqn>} link and builtin/keyword highlighting as {@link #toPossibleClassTooltipLink}, but from an already
   * resolved {@code type}, so a caller that holds the {@code PyType} need not re-resolve a name (and is not limited to types
   * resolvable from an anchor). A non-class type, or a class with no qualified name, renders as highlighted text
   * without a link.
   */
  public static HtmlChunk toTypeTooltipLink(@Nullable PyType type, String displayName) {
    PyClass pyClass = type instanceof PyClassType classType ? classType.getPyClass() : null;
    String qualifiedName = pyClass != null ? pyClass.getQualifiedName() : null;
    HtmlChunk text = qualifiedName != null && !qualifiedName.isEmpty()
                     ? HtmlChunk.link(TOOLTIP_ELEMENT_LINK_PREFIX + qualifiedName, displayName)
                     : HtmlChunk.text(displayName);
    return styledTypeName(type, text);
  }

  /**
   * Renders {@code typeName} with the same highlighting as {@link #toPossibleClassTooltipLink}, but without a link.
   */
  public static HtmlChunk toPossibleClassText(String typeName, PsiElement anchor, TypeEvalContext context) {
    PyClassType type = resolveNamedClassType(typeName, anchor, context);
    return styledTypeName(type, HtmlChunk.text(typeName));
  }

  /**
   * Renders {@code typeName} with the same highlighting as {@link #toPossibleClassTooltipLink} for {@code type}, but without a link.
   */
  public static HtmlChunk toPossibleClassText(PyClassLikeType type, String typeName, PsiElement anchor, TypeEvalContext context) {
    PyClass pyClass = type instanceof PyClassType classType && !isNoneType(type) ? classType.getPyClass() : null;
    return pyClass != null ? styledReference(HtmlChunk.text(typeName), pyClass)
                           : toPossibleClassText(typeName, anchor, context);
  }

  private static HtmlChunk styledTypeName(@Nullable PyType type, HtmlChunk text) {
    PyClass pyClass = type instanceof PyClassType classType ? classType.getPyClass() : null;
    if (isNoneType(type)) {
      return styledSpan(text, PyHighlighter.PY_KEYWORD);
    }
    else if (pyClass != null) {
      return styledReference(text, pyClass);
    }
    return text;
  }

  /**
   * Like {@link #toPossibleClassTooltipLink(String, PsiElement, TypeEvalContext)} but builds the link from {@code type}'s own class identity instead of
   * re-resolving the displayed {@code typeName} from {@code anchor}'s scope. This makes class names that are not imported
   * into the anchor file — most notably the {@code collections.abc} ABCs — navigable in tooltips, where name-based
   * resolution would have failed and rendered them as plain text. Falls back to the name-based
   * {@link #toPossibleClassTooltipLink(String, PsiElement, TypeEvalContext)} for {@code None} (so it keeps its keyword styling) and for class-like types without
   * a usable {@link PyClass}.
   */
  public static HtmlChunk toPossibleClassTooltipLink(PyClassLikeType type, String typeName, PsiElement anchor, TypeEvalContext context) {
    PyClass pyClass = asLinkableClass(type);
    return pyClass != null ? styledReference(toQualifiedNameTooltipLink(Objects.requireNonNull(pyClass.getQualifiedName()), typeName), pyClass)
                           : toPossibleClassTooltipLink(typeName, anchor, context);
  }

  /**
   * The {@link PyClass} to link to directly: the class of a non-{@code None} class type that carries a qualified name.
   * {@code None} is excluded so it keeps its keyword styling via the name-resolution path.
   */
  private static @Nullable PyClass asLinkableClass(PyClassLikeType type) {
    if (isNoneType(type)) return null;
    PyClass pyClass = type instanceof PyClassType classType ? classType.getPyClass() : null;
    return pyClass != null && !StringUtil.isEmpty(pyClass.getQualifiedName()) ? pyClass : null;
  }

  /**
   * Renders {@code linkText} as a tooltip-navigable link ({@code #element/<fqn>}).
   */
  public static HtmlChunk toQualifiedNameTooltipLink(String qualifiedName, String linkText) {
    return HtmlChunk.link(TOOLTIP_ELEMENT_LINK_PREFIX + qualifiedName, linkText);
  }

  public static HtmlChunk toClass(PyClass pyClass, String linkText) {
    return toClass(StringUtil.notNullize(pyClass.getQualifiedName()), linkText);
  }

  /**
   * Renders {@code linkText} as a tooltip-navigable link ({@code #element/<fqn>}, see
   * {@code com.intellij.codeInsight.hint.ElementLinkHandler}) pointing at {@code target}, for use in inspection tooltips.
   * Falls back to plain text when {@code target} has no qualified name (e.g. a local class), so it still renders but
   * is simply not clickable.
   */
  public static HtmlChunk toElementTooltipLink(PyQualifiedNameOwner target, String linkText) {
    String qualifiedName = target.getQualifiedName();
    return StringUtil.isEmpty(qualifiedName) ? HtmlChunk.text(linkText)
                                             : HtmlChunk.link(TOOLTIP_ELEMENT_LINK_PREFIX + qualifiedName, linkText);
  }

  public static HtmlChunk toClass(String qualifiedName, String linkText) {
    String linkTarget = Objects.requireNonNull(PyNames.FQN.unqualifyBuiltinName(qualifiedName));
    return HtmlChunk.link(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + LINK_TYPE_TYPENAME + linkTarget, linkText);
  }

  public static HtmlChunk toFunction(String content, PyFunction func) {
    String qualifiedName = func.getQualifiedName();
    if (qualifiedName != null) {
      return HtmlChunk.link(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + LINK_TYPE_FUNC + qualifiedName, content);
    }
    return HtmlChunk.text(content);
  }

  public static HtmlChunk toTypeAliasStatement(String content, PyTypeAliasStatement typeAliasStatement) {
    String qualifiedName = typeAliasStatement.getQualifiedName();
    if (qualifiedName != null) {
      return HtmlChunk.link(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + LINK_TYPE_FUNC + qualifiedName, content);
    }
    return HtmlChunk.text(content);
  }

  public static HtmlChunk toModule(String content, String qualifiedName) {
    return HtmlChunk.link(DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL + LINK_TYPE_MODULE + qualifiedName, content);
  }

  public static @Nullable PsiElement elementForLink(String link, PsiElement element, TypeEvalContext context) {
    if (link.equals(LINK_TYPE_CLASS)) {
      return containingClass(element);
    }
    else if (link.equals(LINK_TYPE_PARAM)) {
      return parameterPossibleClass(element, context);
    }
    else if (link.startsWith(LINK_TYPE_TYPENAME)) {
      return possibleClass(link.substring(LINK_TYPE_TYPENAME.length()), element, context);
    }
    else if (link.startsWith(LINK_TYPE_FUNC)) {
      return possibleFunction(link.substring(LINK_TYPE_FUNC.length()), element);
    }
    else if (link.startsWith(LINK_TYPE_MODULE)) {
      return possibleModule(link.substring(LINK_TYPE_MODULE.length()), element);
    }
    return null;
  }

  private static @Nullable PyFile possibleModule(String qualifiedName, PsiElement element) {
    PyPsiFacade facade = PyPsiFacade.getInstance(element.getProject());
    QualifiedName qName = QualifiedName.fromDottedString(qualifiedName);

    PyQualifiedNameResolveContext resolveContext = facade.createResolveContextFromFoothold(element);
    for (PsiElement resolved : facade.resolveQualifiedName(qName, resolveContext)) {
      if (resolved instanceof PsiFileSystemItem) {
        if (PyUtil.turnDirIntoInit(resolved) instanceof PyFile pyFile) {
          return pyFile;
        }
      }
    }
    return null;
  }

  private static @Nullable PyFunction possibleFunction(String qualifiedName, PsiElement element) {
    // TODO a better, more general way to resolve qualified names of function
    PyPsiFacade facade = PyPsiFacade.getInstance(element.getProject());
    QualifiedName qName = QualifiedName.fromDottedString(qualifiedName);

    PyQualifiedNameResolveContext resolveContext = facade.createResolveContextFromFoothold(element).copyWithMembers();
    for (PsiElement resolved : facade.resolveQualifiedName(qName, resolveContext)) {
      if (resolved instanceof PyFunction topLevel) {
        return topLevel;
      }
    }

    for (PsiElement resolved : facade.resolveQualifiedName(qName.removeLastComponent(), resolveContext)) {
      if (resolved instanceof PyClass pyClass) {
        return pyClass.findMethodByName(qName.getLastComponent(), false, null);
      }
    }
    return null;
  }

  private static @Nullable PyClass containingClass(PsiElement element) {
    if (element instanceof PyClass pyClass) {
      return pyClass;
    }
    else if (element instanceof PyFunction function) {
      return function.getContainingClass();
    }
    return PsiTreeUtil.getParentOfType(element, PyClass.class);
  }

  private static @Nullable PyClass parameterPossibleClass(PsiElement parameter, TypeEvalContext context) {
    if (parameter instanceof PyNamedParameter namedParameter) {
      PyType type = context.getType(namedParameter);
      if (type instanceof PyClassType classType) {
        return classType.getPyClass();
      }
    }
    return null;
  }

  private static @Nullable PyClass possibleClass(String type, PsiElement anchor, TypeEvalContext context) {
    PyClassType classType = resolveNamedClassType(type, anchor, context);
    return classType != null ? classType.getPyClass() : null;
  }
}
