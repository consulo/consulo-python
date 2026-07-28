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
import com.jetbrains.python.PyTokenTypes;
import com.jetbrains.python.PythonFileType;
import com.jetbrains.python.impl.PythonDialectsTokenSetProvider;
import com.jetbrains.python.impl.PythonHelpersLocator;
import com.jetbrains.python.impl.console.PyConsoleUtil;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.impl.PyCallExpressionHelper;
import com.jetbrains.python.impl.psi.resolve.RootVisitor;
import com.jetbrains.python.impl.psi.resolve.RootVisitorHost;
import com.jetbrains.python.impl.psi.types.PyDynamicallyEvaluatedType;
import com.jetbrains.python.impl.psi.types.PyTypeParser;
import com.jetbrains.python.impl.toolbox.ChainIterable;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedResolveResult;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.Maybe;
import consulo.annotation.access.RequiredReadAction;
import consulo.application.util.LineTokenizer;
import consulo.content.bundle.Sdk;
import consulo.language.ast.ASTNode;
import consulo.language.codeStyle.CodeStyleSettingsManager;
import consulo.language.psi.PsiElement;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.module.Module;
import consulo.project.Project;
import consulo.python.impl.localize.PyLocalize;
import consulo.util.collection.ArrayUtil;
import consulo.util.io.FileUtil;
import consulo.util.lang.Couple;
import consulo.util.lang.Pair;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.impl.documentation.DocumentationBuilderKit.*;

public class PyDocumentationBuilder {
  private final PsiElement myElement;
  private final PsiElement myOriginalElement;
  private ChainIterable<String> myResult;
  private final ChainIterable<String> myProlog;      // sequence for reassignment info, etc
  private final ChainIterable<String> myBody;        // sequence for doc string
  private final ChainIterable<String> myEpilogue;    // sequence for doc "copied from" notices and such

  private static final Pattern ourSpacesPattern = Pattern.compile("^\\s+");
  private final ChainIterable<String> myReassignmentChain;

  public PyDocumentationBuilder(PsiElement element, PsiElement originalElement) {
    myElement = element;
    myOriginalElement = originalElement;
    myResult = new ChainIterable<>();
    myProlog = new ChainIterable<>();
    myBody = new ChainIterable<>();
    myEpilogue = new ChainIterable<>();

    myResult.add(myProlog).addWith(TagCode, myBody).add(myEpilogue); // pre-assemble; then add stuff to individual cats as needed
    myResult = wrapInTag("html", wrapInTag("body", myResult));
    myReassignmentChain = new ChainIterable<>();
  }

  @Nullable
  @RequiredReadAction
  public String build() {
    TypeEvalContext context = TypeEvalContext.userInitiated(myElement.getProject(), myElement.getContainingFile());
    PsiElement outerElement = myOriginalElement != null ? myOriginalElement.getParent() : null;

    PsiElement elementDefinition = resolveToDocStringOwner();
    boolean isProperty = buildFromProperty(elementDefinition, outerElement, context);

    if (myProlog.isEmpty() && !isProperty && !isAttribute()) {
      myProlog.add(myReassignmentChain);
    }

    if (elementDefinition instanceof PyDocStringOwner) {
      buildFromDocString(elementDefinition, isProperty);
    }
    else if (isAttribute()) {
      buildFromAttributeDoc();
    }
    else if (elementDefinition instanceof PyNamedParameter) {
      buildFromParameter(context, outerElement, elementDefinition);
    }
    else if (elementDefinition != null && outerElement instanceof PyReferenceExpression) {
      myBody.addItem(combUp("\nInferred type: "));
      PythonDocumentationProvider.describeExpressionTypeWithLinks(myBody, (PyReferenceExpression)outerElement, context);
    }

    if (elementDefinition != null) {
      ASTNode node = elementDefinition.getNode();
      if (node != null && PythonDialectsTokenSetProvider.INSTANCE.getKeywordTokens().contains(node.getElementType())) {
        String documentationName = elementDefinition.getText();
        if (node.getElementType() == PyTokenTypes.AS_KEYWORD || node.getElementType() == PyTokenTypes.ELSE_KEYWORD) {
          PyTryExceptStatement statement = PsiTreeUtil.getParentOfType(elementDefinition, PyTryExceptStatement.class);
          if (statement != null) {
            documentationName = "try";
          }
        }
        else if (node.getElementType() == PyTokenTypes.IN_KEYWORD) {
          PyForStatement statement = PsiTreeUtil.getParentOfType(elementDefinition, PyForStatement.class);
          if (statement != null) {
            documentationName = "for";
          }
        }
        buildForKeyword(documentationName);
      }
    }
    String url = PythonDocumentationProvider.getUrlFor(myElement, myOriginalElement, false);
    if (url != null) {
      myEpilogue.addItem(BR);
      myEpilogue.addWith(TagBold, $("External documentation:"));
      myEpilogue.addItem(BR);
      myEpilogue.addItem("<a href=\"").addItem(url).addItem("\">").addItem(url).addItem("</a>");
    }

    if (myBody.isEmpty() && myEpilogue.isEmpty()) {
      return null; // got nothing substantial to say!
    }
    else {
      return myResult.toString();
    }
  }

  private void buildForKeyword(String name) {
    try {
      FileReader reader = new FileReader(PythonHelpersLocator.getHelperPath("/tools/python_keywords/" + name));
      try {
        String text = FileUtil.loadTextAndClose(reader);
        myEpilogue.addItem(StringUtil.convertLineSeparators(text, "\n"));
      }
      catch (IOException ignored) {
      }
      finally {
        try {
          reader.close();
        }
        catch (IOException ignored) {
        }
      }
    }
    catch (FileNotFoundException ignored) {
    }
  }

  @RequiredReadAction
  private void buildFromParameter(TypeEvalContext context, @Nullable PsiElement outerElement, PsiElement elementDefinition) {
    myBody.addItem(combUp("Parameter " + PyUtil.getReadableRepr(elementDefinition, false)));
    boolean typeFromDocStringAdded = addTypeAndDescriptionFromDocString((PyNamedParameter)elementDefinition);
    if (outerElement instanceof PyExpression expr) {
      PyType type = context.getType(expr);
      if (type != null) {
        String typeString = null;
        if (type instanceof PyDynamicallyEvaluatedType) {
          if (!typeFromDocStringAdded) {
            typeString = "\nDynamically inferred type: ";
          }
        }
        else if (outerElement.getReference() != null
          && outerElement.getReference().resolve() instanceof PyTargetExpression target) {
          String targetName = target.getName();
          if (targetName != null && targetName.equals(((PyNamedParameter) elementDefinition).getName())) {
            typeString = "\nReassigned value has type: ";
          }
        }
        if (typeString == null && !typeFromDocStringAdded) {
          typeString = "\nInferred type: ";
        }
        if (typeString != null) {
          myBody.addItem(combUp(typeString));
          PythonDocumentationProvider.describeTypeWithLinks(myBody, elementDefinition, type, context);
        }
      }
    }
  }

  @RequiredReadAction
  private boolean buildFromProperty(PsiElement elementDefinition, @Nullable PsiElement outerElement, TypeEvalContext context) {
    if (myOriginalElement == null) {
      return false;
    }
    String elementName = myOriginalElement.getText();
    if (!PyNames.isIdentifier(elementName)) {
      return false;
    }
    if (!(outerElement instanceof PyQualifiedExpression qExpr)) {
      return false;
    }
    PyExpression qualifier = qExpr.getQualifier();
    if (qualifier == null) {
      return false;
    }
    PyType type = context.getType(qualifier);
    if (!(type instanceof PyClassType classType)) {
      return false;
    }
    PyClass cls = classType.getPyClass();
    Property property = cls.findProperty(elementName, true, null);
    if (property == null) {
      return false;
    }

    AccessDirection direction = AccessDirection.of((PyElement)outerElement);
    Maybe<PyCallable> accessor = property.getByDirection(direction);
    myProlog.addItem("property ")
            .addWith(TagBold, $().addWith(TagCode, $(elementName)))
            .addItem(" of ")
            .add(PythonDocumentationProvider.describeClass(cls, TagCode, true, true));
    if (accessor.isDefined() && property.getDoc() != null) {
      myBody.addItem(": ").addItem(property.getDoc()).addItem(BR);
    }
    else {
      PyCallable getter = property.getGetter().valueOrNull();
      if (getter != null && getter != myElement && getter instanceof PyFunction) {
        // not in getter, getter's doc comment may be useful
        PyStringLiteralExpression docString = ((PyFunction)getter).getDocStringExpression();
        if (docString != null) {
          myProlog.addItem(BR).addWith(TagItalic, $("Copied from getter:")).addItem(BR).addItem(docString.getStringValue());
        }
      }
      myBody.addItem(BR);
    }
    myBody.addItem(BR);
    if (accessor.isDefined() && accessor.value() == null) {
      elementDefinition = null;
    }
    String accessorKind = getAccessorKind(direction);
    if (elementDefinition != null) {
      myEpilogue.addWith(TagSmall, $(BR, BR, accessorKind, " of property")).addItem(BR);
    }

    if (!(elementDefinition instanceof PyDocStringOwner)) {
      myBody.addWith(TagItalic, elementDefinition != null ? $("Declaration: ") : $(accessorKind + " is not defined.")).addItem(BR);
      if (elementDefinition != null) {
        myBody.addItem(combUp(PyUtil.getReadableRepr(elementDefinition, false)));
      }
    }
    return true;
  }

  private static String getAccessorKind(AccessDirection dir) {
    String accessorKind;
    if (dir == AccessDirection.READ) {
      accessorKind = "Getter";
    }
    else if (dir == AccessDirection.WRITE) {
      accessorKind = "Setter";
    }
    else {
      accessorKind = "Deleter";
    }
    return accessorKind;
  }

  @RequiredReadAction
  private void buildFromDocString(PsiElement elementDefinition, boolean isProperty) {
    PyClass pyClass = null;
    PyStringLiteralExpression docStringExpression = ((PyDocStringOwner)elementDefinition).getDocStringExpression();

    if (elementDefinition instanceof PyClass) {
      pyClass = (PyClass)elementDefinition;
      myBody.add(PythonDocumentationProvider.describeDecorators(pyClass, TagItalic, BR, LCombUp));
      myBody.add(PythonDocumentationProvider.describeClass(pyClass, TagBold, true, false));
    }
    else if (elementDefinition instanceof PyFunction) {
      PyFunction pyFunction = (PyFunction)elementDefinition;
      if (!isProperty) {
        pyClass = pyFunction.getContainingClass();
        if (pyClass != null) {
          myBody.addWith(TagSmall, PythonDocumentationProvider.describeClass(pyClass, TagCode, true, true)).addItem(BR).addItem(BR);
        }
      }
      myBody.add(PythonDocumentationProvider.describeDecorators(pyFunction, TagItalic, BR, LCombUp))
            .add(PythonDocumentationProvider.describeFunction(pyFunction, TagBold, LCombUp));
      if (docStringExpression == null) {
        addInheritedDocString(pyFunction, pyClass);
      }
    }
    else if (elementDefinition instanceof PyFile) {
      addModulePath((PyFile)elementDefinition);
    }
    if (docStringExpression != null) {
      myBody.addItem(BR);
      addFormattedDocString(myElement, docStringExpression.getStringValue(), myBody, myEpilogue);
    }
  }

  @RequiredReadAction
  private boolean isAttribute() {
    return myElement instanceof PyTargetExpression && PyUtil.isAttribute((PyTargetExpression)myElement);
  }

  @Nullable
  @RequiredReadAction
  private PsiElement resolveToDocStringOwner() {
    // here the ^Q target is already resolved; the resolved element may point to intermediate assignments
    if (myElement instanceof PyTargetExpression target) {
      String targetName = myElement.getText();
      myReassignmentChain.addWith(TagSmall, $(PyLocalize.qdocAssignedTo$0(targetName).get()).addItem(BR));
      PyExpression assignedValue = target.findAssignedValue();
      if (assignedValue instanceof PyReferenceExpression refExpr) {
        PsiElement resolved = resolveWithoutImplicits(refExpr);
        if (resolved != null) {
          return resolved;
        }
      }
      return assignedValue;
    }
    if (myElement instanceof PyReferenceExpression refExpr) {
      myReassignmentChain.addWith(TagSmall, $(PyLocalize.qdocAssignedTo$0(myElement.getText()).get()).addItem(BR));
      return resolveWithoutImplicits(refExpr);
    }
    // it may be a call to a standard wrapper
    if (myElement instanceof PyCallExpression call) {
      Pair<String, PyFunction> wrapInfo = PyCallExpressionHelper.interpretAsModifierWrappingCall(call, myOriginalElement);
      if (wrapInfo != null) {
        String wrapperName = wrapInfo.getFirst();
        PyFunction wrappedFunction = wrapInfo.getSecond();
        myReassignmentChain.addWith(TagSmall, $(PyLocalize.qdocWrappedIn$0(wrapperName).get()).addItem(BR));
        return wrappedFunction;
      }
    }
    return myElement;
  }

  private static PsiElement resolveWithoutImplicits(PyReferenceExpression element) {
    QualifiedResolveResult resolveResult = element.followAssignmentsChain(PyResolveContext.noImplicits());
    return resolveResult.isImplicit() ? null : resolveResult.getElement();
  }

  @RequiredReadAction
  private void addInheritedDocString(PyFunction pyFunction, @Nullable PyClass pyClass) {
    boolean notFound = true;
    String methodName = pyFunction.getName();
    if (pyClass == null || methodName == null) {
      return;
    }
    boolean isConstructor = PyNames.INIT.equals(methodName);
    Iterable<PyClass> classes = pyClass.getAncestorClasses(null);
    if (isConstructor) {
      // look at our own class again and maybe inherit class's doc
      classes = new ChainIterable<>(pyClass).add(classes);
    }
    for (PyClass ancestor : classes) {
      PyStringLiteralExpression docStringElement = null;
      PyFunction inherited = null;
      boolean isFromClass = false;
      if (isConstructor) {
        docStringElement = pyClass.getDocStringExpression();
      }
      if (docStringElement != null) {
        isFromClass = true;
      }
      else {
        inherited = ancestor.findMethodByName(methodName, false, null);
      }
      if (inherited != null) {
        docStringElement = inherited.getDocStringExpression();
      }
      if (docStringElement != null) {
        String inheritedDoc = docStringElement.getStringValue();
        if (inheritedDoc.length() > 1) {
          myEpilogue.addItem(BR).addItem(BR);
          String ancestorName = ancestor.getName();
          String marker =
            (pyClass == ancestor) ? PythonDocumentationProvider.LINK_TYPE_CLASS : PythonDocumentationProvider.LINK_TYPE_PARENT;
          String ancestorLink = $().addWith(new LinkWrapper(marker + ancestorName), $(ancestorName)).toString();
          if (isFromClass) {
            myEpilogue.addItem(PyLocalize.qdocCopiedFromClass$0(ancestorLink).get());
          }
          else {
            myEpilogue.addItem(PyLocalize.qdocCopiedFrom$0$1(ancestorLink, methodName).get());
          }
          myEpilogue.addItem(BR).addItem(BR);
          ChainIterable<String> formatted = new ChainIterable<>();
          ChainIterable<String> unformatted = new ChainIterable<>();
          addFormattedDocString(pyFunction, inheritedDoc, formatted, unformatted);
          myEpilogue.addWith(TagCode, formatted).add(unformatted);
          notFound = false;
          break;
        }
      }
    }

    if (notFound) {
      // above could have not worked because inheritance is not searched down to 'object'.
      // for well-known methods, copy built-in doc string.
      // TODO: also handle predefined __xxx__ that are not part of 'object'.
      if (PyNames.UnderscoredAttributes.contains(methodName)) {
        addPredefinedMethodDoc(pyFunction, methodName);
      }
    }
  }

  private void addPredefinedMethodDoc(PyFunction fun, String methodName) {
    PyClassType objectType = PyBuiltinCache.getInstance(fun).getObjectType(); // old- and new-style classes share the __xxx__ stuff
    if (objectType != null) {
      PyClass objectClass = objectType.getPyClass();
      PyFunction predefinedMethod = objectClass.findMethodByName(methodName, false, null);
      if (predefinedMethod != null) {
        PyStringLiteralExpression predefinedDocString = predefinedMethod.getDocStringExpression();
        String predefinedDoc = predefinedDocString != null ? predefinedDocString.getStringValue() : null;
        if (predefinedDoc != null && predefinedDoc.length() > 1) { // only a real-looking doc string counts
          addFormattedDocString(fun, predefinedDoc, myBody, myBody);
          myEpilogue.addItem(BR).addItem(BR).addItem(PyLocalize.qdocCopiedFromBuiltin().get());
        }
      }
    }
  }

  private static void addFormattedDocString(PsiElement element,
                                            String docString,
                                            ChainIterable<String> formattedOutput,
                                            ChainIterable<String> unformattedOutput) {
    Project project = element.getProject();

    List<String> formatted = PyStructuredDocstringFormatter.formatDocstring(element, docString);
    if (formatted != null) {
      unformattedOutput.add(formatted);
      return;
    }

    boolean isFirstLine;
    List<String> result = new ArrayList<>();
    String[] lines = removeCommonIndentation(docString);

    // reconstruct back, dropping first empty fragment as needed
    isFirstLine = true;
    int tabSize = CodeStyleSettingsManager.getSettings(project).getTabSize(PythonFileType.INSTANCE);
    for (String line : lines) {
      if (isFirstLine && ourSpacesPattern.matcher(line).matches()) {
        continue; // ignore all initial whitespace
      }
      if (isFirstLine) {
        isFirstLine = false;
      }
      else {
        result.add(BR);
      }
      int leadingTabs = 0;
      while (leadingTabs < line.length() && line.charAt(leadingTabs) == '\t') {
        leadingTabs++;
      }
      if (leadingTabs > 0) {
        line = StringUtil.repeatSymbol(' ', tabSize * leadingTabs) + line.substring(leadingTabs);
      }
      result.add(combUp(line));
    }
    formattedOutput.add(result);
  }

  /**
   * Adds type and description representation from function doc-string
   *
   * @param parameter parameter of a function
   * @return true if type from doc-string was added
   */
  private boolean addTypeAndDescriptionFromDocString(PyNamedParameter parameter) {
    PyFunction function = PsiTreeUtil.getParentOfType(parameter, PyFunction.class);
    if (function != null) {
      String docString = PyPsiUtils.strValue(function.getDocStringExpression());
      Pair<String, String> typeAndDescr = getTypeAndDescription(docString, parameter);

      String type = typeAndDescr.first;
      String description = typeAndDescr.second;

      if (type != null) {
        PyType pyType = PyTypeParser.getTypeByName(parameter, type);
        if (pyType instanceof PyClassType) {
          myBody.addItem(": ").addWith(new LinkWrapper(PythonDocumentationProvider.LINK_TYPE_PARAM), $(pyType.getName()));
        }
        else {
          myBody.addItem(": ").addItem(type);
        }
      }

      if (description != null) {
        myEpilogue.addItem(BR).addItem(description);
      }

      return type != null;
    }

    return false;
  }

  private static Couple<String> getTypeAndDescription(@Nullable String docString, PyNamedParameter followed) {
    String type = null;
    String desc = null;
    if (docString != null) {
      StructuredDocString structuredDocString = DocStringUtil.parse(docString);
      String name = followed.getName();
      type = structuredDocString.getParamType(name);
      desc = structuredDocString.getParamDescription(name);
    }
    return Couple.of(type, desc);
  }

  @RequiredReadAction
  private void buildFromAttributeDoc() {
    PyClass cls = PsiTreeUtil.getParentOfType(myElement, PyClass.class);
    assert cls != null;
    String type = PyUtil.isInstanceAttribute((PyExpression)myElement) ? "Instance attribute " : "Class attribute ";
    myProlog.addItem(type)
            .addWith(TagBold, $().addWith(TagCode, $(((PyTargetExpression)myElement).getName())))
            .addItem(" of class ")
            .addWith(PythonDocumentationProvider.LinkMyClass, $()
              .addWith(TagCode, $(cls.getName())))
            .addItem(BR);

    String docString = ((PyTargetExpression)myElement).getDocStringValue();
    if (docString != null) {
      addFormattedDocString(myElement, docString, myBody, myEpilogue);
    }
  }

  public static String[] removeCommonIndentation(String docString) {
    // detect common indentation
    String[] lines = LineTokenizer.tokenize(docString, false);
    boolean isFirst = true;
    int cutWidth = Integer.MAX_VALUE;
    int firstIndentedLine = 0;
    for (String frag : lines) {
      if (frag.length() == 0) {
        continue;
      }
      int padWidth = 0;
      Matcher matcher = ourSpacesPattern.matcher(frag);
      if (matcher.find()) {
        padWidth = matcher.end();
      }
      if (isFirst) {
        isFirst = false;
        if (padWidth == 0) {    // first line may have zero padding
          firstIndentedLine = 1;
          continue;
        }
      }
      if (padWidth < cutWidth) {
        cutWidth = padWidth;
      }
    }
    // remove common indentation
    if (cutWidth > 0 && cutWidth < Integer.MAX_VALUE) {
      for (int i = firstIndentedLine; i < lines.length; i += 1) {
        if (lines[i].length() >= cutWidth) {
          lines[i] = lines[i].substring(cutWidth);
        }
      }
    }
    List<String> result = new ArrayList<>();
    for (String line : lines) {
      if (line.startsWith(PyConsoleUtil.ORDINARY_PROMPT)) {
        break;
      }
      result.add(line);
    }
    return ArrayUtil.toStringArray(result);
  }

  private void addModulePath(PyFile followed) {
    // what to prepend to a module description?
    VirtualFile file = followed.getVirtualFile();
    if (file == null) {
      myProlog.addWith(TagSmall, $(PyLocalize.qdocModulePathUnknown().get()));
    }
    else {
      String path = file.getPath();
      RootFinder finder = new RootFinder(path);
      RootVisitorHost.visitRoots(followed, finder);
      String rootPath = finder.getResult();
      if (rootPath != null) {
        String afterPart = path.substring(rootPath.length());
        myProlog.addWith(TagSmall, $(rootPath).addWith(TagBold, $(afterPart)));
      }
      else {
        myProlog.addWith(TagSmall, $(path));
      }
    }
  }

  private static class RootFinder implements RootVisitor {
    private String myResult;
    private final String myPath;

    private RootFinder(String path) {
      myPath = path;
    }

    @Override
    public boolean visitRoot(VirtualFile root, Module module, Sdk sdk, boolean isModuleSource) {
      String vPath = VirtualFileUtil.urlToPath(root.getUrl());
      if (myPath.startsWith(vPath)) {
        myResult = vPath;
        return false;
      }
      else {
        return true;
      }
    }

    String getResult() {
      return myResult;
    }
  }
}
