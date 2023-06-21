/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.jetbrains.python.PyNames;
import com.jetbrains.python.PythonLanguage;
import com.jetbrains.python.documentation.PythonDocumentationLinkProvider;
import com.jetbrains.python.documentation.PythonDocumentationQuickInfoProvider;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.impl.console.PydevConsoleRunner;
import com.jetbrains.python.impl.console.PydevDocumentationProvider;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.psi.types.PyTypeParser;
import com.jetbrains.python.impl.toolbox.ChainIterable;
import com.jetbrains.python.impl.toolbox.FP;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.annotation.component.ExtensionImpl;
import consulo.application.Application;
import consulo.application.ApplicationManager;
import consulo.codeEditor.Editor;
import consulo.component.extension.Extensions;
import consulo.content.bundle.Sdk;
import consulo.ide.setting.ShowSettingsUtil;
import consulo.language.Language;
import consulo.language.editor.documentation.AbstractDocumentationProvider;
import consulo.language.editor.documentation.ExternalDocumentationProvider;
import consulo.language.editor.documentation.LanguageDocumentationProvider;
import consulo.language.psi.*;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.module.content.ProjectRootManager;
import consulo.project.Project;
import consulo.ui.ex.awt.Messages;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jetbrains.annotations.NonNls;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import static com.jetbrains.python.impl.documentation.DocumentationBuilderKit.*;

/**
 * Provides quick docs for classes, methods, and functions.
 * Generates documentation stub
 */
@ExtensionImpl
public class PythonDocumentationProvider extends AbstractDocumentationProvider implements ExternalDocumentationProvider, LanguageDocumentationProvider {
  @NonNls
  static final String LINK_TYPE_CLASS = "#class#";
  @NonNls
  static final String LINK_TYPE_PARENT = "#parent#";
  @NonNls
  static final String LINK_TYPE_PARAM = "#param#";
  @NonNls
  static final String LINK_TYPE_TYPENAME = "#typename#";

  @Nonnull
  @Override
  public Language getLanguage() {
    return PythonLanguage.INSTANCE;
  }

  // provides ctrl+hover info
  @Override
  @Nullable
  public String getQuickNavigateInfo(PsiElement element, @Nonnull PsiElement originalElement) {
    for (PythonDocumentationQuickInfoProvider point : PythonDocumentationQuickInfoProvider.EP_NAME.getExtensionList()) {
      final String info = point.getQuickInfo(originalElement);
      if (info != null) {
        return info;
      }
    }

    if (element instanceof PyFunction) {
      final PyFunction func = (PyFunction)element;
      final StringBuilder cat = new StringBuilder();
      final PyClass cls = func.getContainingClass();
      if (cls != null) {
        final String clsName = cls.getName();
        cat.append("class ").append(clsName).append("\n");
        // It would be nice to have class import info here, but we don't know the ctrl+hovered reference and context
      }
      String summary = "";
      final PyStringLiteralExpression docStringExpression = func.getDocStringExpression();
      if (docStringExpression != null) {
        final StructuredDocString docString = DocStringUtil.parse(docStringExpression.getStringValue(), docStringExpression);
        summary = docString.getSummary();
      }
      return $(cat.toString()).add(describeDecorators(func, LSame2, ", ", LSame1))
                              .add(describeFunction(func, LSame2, LSame1))
                              .toString() + "\n" + summary;
    }
    else if (element instanceof PyClass) {
      final PyClass cls = (PyClass)element;
      String summary = "";
      PyStringLiteralExpression docStringExpression = cls.getDocStringExpression();
      if (docStringExpression == null) {
        final PyFunction initOrNew = cls.findInitOrNew(false, null);
        if (initOrNew != null) {
          docStringExpression = initOrNew.getDocStringExpression();
        }
      }
      if (docStringExpression != null) {
        final StructuredDocString docString = DocStringUtil.parse(docStringExpression.getStringValue(), docStringExpression);
        summary = docString.getSummary();
      }

      return describeDecorators(cls, LSame2, ", ", LSame1).add(describeClass(cls, LSame2, false, false)).toString() + "\n" + summary;
    }
    else if (element instanceof PyExpression) {
      return describeExpression((PyExpression)element, originalElement);
    }
    return null;
  }

  /**
   * Creates a HTML description of function definition.
   *
   * @param fun             the function
   * @param funcNameWrapper puts a tag around the function name
   * @param escaper         sanitizes values that come directly from doc string or code
   * @return chain of strings for further chaining
   */
  @Nonnull
  static ChainIterable<String> describeFunction(@Nonnull PyFunction fun,
                                                FP.Lambda1<Iterable<String>, Iterable<String>> funcNameWrapper,
                                                @Nonnull FP.Lambda1<String, String> escaper) {
    final ChainIterable<String> cat = new ChainIterable<>();
    final String name = fun.getName();
    cat.addItem("def ").addWith(funcNameWrapper, $(name));
    final TypeEvalContext context = TypeEvalContext.userInitiated(fun.getProject(), fun.getContainingFile());
    final List<PyParameter> parameters = PyUtil.getParameters(fun, context);
    final String paramStr = "(" +
      StringUtil.join(parameters, parameter -> PyUtil.getReadableRepr(parameter, false), ", ") +
      ")";
    cat.addItem(escaper.apply(paramStr));
    if (!PyNames.INIT.equals(name)) {
      cat.addItem(escaper.apply("\nInferred type: "));
      getTypeDescription(fun, cat);
      cat.addItem(BR);
    }
    return cat;
  }

  @Nullable
  private static String describeExpression(@Nonnull PyExpression expr, @Nonnull PsiElement originalElement) {
    final String name = expr.getName();
    if (name != null) {
      final StringBuilder result = new StringBuilder((expr instanceof PyNamedParameter) ? "parameter" : "variable");
      result.append(String.format(" \"%s\"", name));
      if (expr instanceof PyNamedParameter) {
        final PyFunction function = PsiTreeUtil.getParentOfType(expr, PyFunction.class);
        if (function != null) {
          result.append(" of ").append(function.getContainingClass() == null ? "function" : "method");
          result.append(String.format(" \"%s\"", function.getName()));
        }
      }
      if (originalElement instanceof PyTypedElement) {
        result.append("\n").append(describeType((PyTypedElement)originalElement));
      }
      return result.toString();
    }
    return null;
  }

  private static String describeType(@Nonnull PyTypedElement element) {
    final TypeEvalContext context = TypeEvalContext.userInitiated(element.getProject(), element.getContainingFile());
    return String.format("Inferred type: %s", getTypeName(context.getType(element), context));
  }

  private static void getTypeDescription(@Nonnull PyFunction fun, @Nonnull ChainIterable<String> body) {
    final TypeEvalContext context = TypeEvalContext.userInitiated(fun.getProject(), fun.getContainingFile());
    final PyTypeModelBuilder builder = new PyTypeModelBuilder(context);
    builder.build(context.getType(fun), true).toBodyWithLinks(body, fun);
  }

  public static String getTypeName(@Nullable PyType type, @Nonnull TypeEvalContext context) {
    final PyTypeModelBuilder.TypeModel typeModel = buildTypeModel(type, context);
    return typeModel.asString();
  }

  private static PyTypeModelBuilder.TypeModel buildTypeModel(PyType type, TypeEvalContext context) {
    PyTypeModelBuilder builder = new PyTypeModelBuilder(context);
    return builder.build(type, true);
  }

  public static void describeExpressionTypeWithLinks(@Nonnull ChainIterable<String> body,
                                                     @Nonnull PyReferenceExpression expression,
                                                     @Nonnull TypeEvalContext context) {
    final PyType type = context.getType(expression);
    describeTypeWithLinks(body, expression, type, context);
  }

  public static void describeTypeWithLinks(@Nonnull ChainIterable<String> body,
                                           @Nonnull PsiElement anchor,
                                           PyType type,
                                           TypeEvalContext context) {
    final PyTypeModelBuilder builder = new PyTypeModelBuilder(context);
    builder.build(type, true).toBodyWithLinks(body, anchor);
  }


  @Nonnull
  static ChainIterable<String> describeDecorators(@Nonnull PyDecoratable what,
                                                  FP.Lambda1<Iterable<String>, Iterable<String>> decoNameWrapper,
                                                  @Nonnull String decoSeparator,
                                                  FP.Lambda1<String, String> escaper) {
    final ChainIterable<String> cat = new ChainIterable<>();
    final PyDecoratorList decoList = what.getDecoratorList();
    if (decoList != null) {
      for (PyDecorator deco : decoList.getDecorators()) {
        cat.add(describeDeco(deco, decoNameWrapper, escaper)).addItem(decoSeparator); // can't easily pass describeDeco to map() %)
      }
    }
    return cat;
  }

  /**
   * Creates a HTML description of function definition.
   *
   * @param cls         the class
   * @param nameWrapper wrapper to render the name with
   * @param allowHtml
   * @param linkOwnName if true, add link to class's own name  @return cat for easy chaining
   */
  @Nonnull
  static ChainIterable<String> describeClass(@Nonnull PyClass cls,
                                             FP.Lambda1<Iterable<String>, Iterable<String>> nameWrapper,
                                             boolean allowHtml,
                                             boolean linkOwnName) {
    final ChainIterable<String> cat = new ChainIterable<>();
    final String name = cls.getName();
    cat.addItem("class ");
    if (allowHtml && linkOwnName) {
      cat.addWith(LinkMyClass, $(name));
    }
    else {
      cat.addWith(nameWrapper, $(name));
    }
    final PyExpression[] ancestors = cls.getSuperClassExpressions();
    if (ancestors.length > 0) {
      cat.addItem("(");
      boolean isNotFirst = false;
      for (PyExpression parent : ancestors) {
        final String parentName = parent.getName();
        if (parentName == null) {
          continue;
        }
        if (isNotFirst) {
          cat.addItem(", ");
        }
        else {
          isNotFirst = true;
        }
        if (allowHtml) {
          cat.addWith(new LinkWrapper(LINK_TYPE_PARENT + parentName), $(parentName));
        }
        else {
          cat.addItem(parentName);
        }
      }
      cat.addItem(")");
    }
    return cat;
  }

  //
  @Nonnull
  private static Iterable<String> describeDeco(@Nonnull PyDecorator deco, FP.Lambda1<Iterable<String>, Iterable<String>> nameWrapper,
                                               //  addWith in tags, if need be
                                               FP.Lambda1<String, String> argWrapper
                                               // add escaping, if need be
  ) {
    final ChainIterable<String> cat = new ChainIterable<>();
    cat.addItem("@").addWith(nameWrapper, $(PyUtil.getReadableRepr(deco.getCallee(), true)));
    if (deco.hasArgumentList()) {
      final PyArgumentList arglist = deco.getArgumentList();
      if (arglist != null) {
        cat.addItem("(").add(interleave(FP.map(FP.combine(LReadableRepr, argWrapper), arglist.getArguments()), ", ")).addItem(")");
      }
    }
    return cat;
  }

  // provides ctrl+Q doc
  public String generateDoc(@Nullable PsiElement element, @Nullable PsiElement originalElement) {
    if (element != null && PydevConsoleRunner.isInPydevConsole(element) || originalElement != null && PydevConsoleRunner.isInPydevConsole(
      originalElement)) {
      return PydevDocumentationProvider.createDoc(element, originalElement);
    }
    return new PyDocumentationBuilder(element, originalElement).build();
  }

  @Override
  public PsiElement getDocumentationElementForLink(PsiManager psiManager, @Nonnull String link, @Nonnull PsiElement context) {
    if (link.equals(LINK_TYPE_CLASS)) {
      return inferContainingClassOf(context);
    }
    else if (link.equals(LINK_TYPE_PARAM)) {
      return inferClassOfParameter(context);
    }
    else if (link.startsWith(LINK_TYPE_PARENT)) {
      final PyClass cls = inferContainingClassOf(context);
      if (cls != null) {
        final String desiredName = link.substring(LINK_TYPE_PARENT.length());
        for (PyClass parent : cls.getAncestorClasses(null)) {
          final String parentName = parent.getName();
          if (parentName != null && parentName.equals(desiredName)) {
            return parent;
          }
        }
      }
    }
    else if (link.startsWith(LINK_TYPE_TYPENAME)) {
      final String typeName = link.substring(LINK_TYPE_TYPENAME.length());
      final PyType type = PyTypeParser.getTypeByName(context, typeName);
      if (type instanceof PyClassType) {
        return ((PyClassType)type).getPyClass();
      }
    }
    return null;
  }

  @Override
  public List<String> getUrlFor(PsiElement element, PsiElement originalElement) {
    final String url = getUrlFor(element, originalElement, true);
    return url == null ? null : Collections.singletonList(url);
  }

  @Nullable
  public static String getUrlFor(PsiElement element, PsiElement originalElement, boolean checkExistence) {
    PsiFileSystemItem file = element instanceof PsiFileSystemItem ? (PsiFileSystemItem)element : element.getContainingFile();
    if (file == null) {
      return null;
    }
    if (PyNames.INIT_DOT_PY.equals(file.getName())) {
      file = file.getParent();
      assert file != null;
    }
    final Sdk sdk = PyBuiltinCache.findSdkForFile(file);
    if (sdk == null) {
      return null;
    }
    final QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(element, originalElement);
    if (qName == null) {
      return null;
    }
    final PythonDocumentationMap map = PythonDocumentationMap.getInstance();
    final String pyVersion = pyVersion(sdk.getVersionString());
    PsiNamedElement namedElement =
      (element instanceof PsiNamedElement && !(element instanceof PsiFileSystemItem)) ? (PsiNamedElement)element : null;
    if (namedElement instanceof PyFunction && PyNames.INIT.equals(namedElement.getName())) {
      final PyClass containingClass = ((PyFunction)namedElement).getContainingClass();
      if (containingClass != null) {
        namedElement = containingClass;
      }
    }
    final String url = map.urlFor(qName, namedElement, pyVersion);
    if (url != null) {
      if (checkExistence && !pageExists(url)) {
        return map.rootUrlFor(qName);
      }
      return url;
    }
    for (PythonDocumentationLinkProvider provider : Extensions.getExtensions(PythonDocumentationLinkProvider.EP_NAME)) {
      final String providerUrl = provider.getExternalDocumentationUrl(element, originalElement);
      if (providerUrl != null) {
        if (checkExistence && !pageExists(providerUrl)) {
          return provider.getExternalDocumentationRoot(sdk);
        }
        return providerUrl;
      }
    }
    return null;
  }

  private static boolean pageExists(@Nonnull String url) {
    if (new File(url).exists()) {
      return true;
    }

    RequestConfig build = RequestConfig.custom().setConnectTimeout(5000).setSocketTimeout(5000).build();
    try (CloseableHttpClient client = HttpClients.custom().setDefaultRequestConfig(build).build()) {
      return client.execute(new HttpHead(url), httpResponse -> httpResponse.getStatusLine().getStatusCode() != 404);
    }
    catch (IOException ignored) {
    }
    catch (IllegalArgumentException e) {
      return false;
    }
    return true;
  }

  @Nullable
  public static String pyVersion(@Nullable String versionString) {
    final String prefix = "Python ";
    if (versionString != null && versionString.startsWith(prefix)) {
      final String version = versionString.substring(prefix.length());
      int dot = version.indexOf('.');
      if (dot > 0) {
        dot = version.indexOf('.', dot + 1);
        if (dot > 0) {
          return version.substring(0, dot);
        }
        return version;
      }
    }
    return null;
  }

  @Override
  public String fetchExternalDocumentation(Project project, PsiElement element, List<String> docUrls) {
    return null;
  }

  @Override
  public boolean hasDocumentationFor(PsiElement element, PsiElement originalElement) {
    return getUrlFor(element, originalElement, false) != null;
  }

  @Override
  public boolean canPromptToConfigureDocumentation(@Nonnull PsiElement element) {
    final PsiFile containingFile = element.getContainingFile();
    if (containingFile instanceof PyFile) {
      final Project project = element.getProject();
      final VirtualFile vFile = containingFile.getVirtualFile();
      if (vFile != null && ProjectRootManager.getInstance(project).getFileIndex().isInLibraryClasses(vFile)) {
        final QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(element, element);
        if (qName != null && qName.getComponentCount() > 0) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public void promptToConfigureDocumentation(@Nonnull PsiElement element) {
    final Project project = element.getProject();
    final QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(element, element);
    if (qName != null && qName.getComponentCount() > 0) {
      Application application = ApplicationManager.getApplication();
      application.invokeLater(() -> {
        final int rc =
          Messages.showOkCancelDialog(project, "No external documentation URL configured for module " + qName.getComponents().get(0) +
            ".\nWould you like to configure it now?", "Python External Documentation", Messages.getQuestionIcon());
        if (rc == Messages.OK) {
          ShowSettingsUtil.getInstance().showSettingsDialog(project, PythonDocumentationConfigurable.ID, "");
        }
      }, application.getNoneModalityState());
    }
  }

  @Nullable
  @Override
  public PsiElement getCustomDocumentationElement(@Nonnull Editor editor, @Nonnull PsiFile file, @Nullable PsiElement contextElement) {
    if (contextElement != null && PythonDialectsTokenSetProvider.INSTANCE.getKeywordTokens()
                                                                         .contains(contextElement.getNode().getElementType())) {
      return contextElement;
    }
    return super.getCustomDocumentationElement(editor, file, contextElement);
  }

  @Nullable
  private static PyClass inferContainingClassOf(PsiElement context) {
    if (context instanceof PyClass) {
      return (PyClass)context;
    }
    if (context instanceof PyFunction) {
      return ((PyFunction)context).getContainingClass();
    }
    else {
      return PsiTreeUtil.getParentOfType(context, PyClass.class);
    }
  }

  @Nullable
  private static PyClass inferClassOfParameter(@Nonnull PsiElement context) {
    if (context instanceof PyNamedParameter) {
      final PyType type =
        TypeEvalContext.userInitiated(context.getProject(), context.getContainingFile()).getType((PyNamedParameter)context);
      if (type instanceof PyClassType) {
        return ((PyClassType)type).getPyClass();
      }
    }
    return null;
  }

  public static final LinkWrapper LinkMyClass = new LinkWrapper(LINK_TYPE_CLASS);
  // link item to containing class
}
