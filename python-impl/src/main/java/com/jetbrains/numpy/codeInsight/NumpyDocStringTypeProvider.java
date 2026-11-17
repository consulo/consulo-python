// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.numpy.codeInsight;

import consulo.module.Module;
import consulo.language.util.ModuleUtilCore;
import consulo.util.lang.ref.Ref;
import consulo.util.lang.StringUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.QualifiedName;
import com.jetbrains.python.PyNames;
import consulo.annotation.component.ExtensionImpl;
import com.jetbrains.python.impl.codeInsight.typing.PyTypingTypeProvider;
import com.jetbrains.python.documentation.PyDocumentationSettings;
import com.jetbrains.python.documentation.docstrings.DocStringFormat;
import com.jetbrains.python.impl.documentation.docstrings.DocStringUtil;
import com.jetbrains.python.documentation.docstrings.NumpyDocString;
import com.jetbrains.python.documentation.docstrings.SectionBasedDocString.SectionField;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyCallSiteExpression;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyNumericLiteralExpression;
import com.jetbrains.python.psi.PyPsiFacade;
import com.jetbrains.python.psi.PyStringLiteralExpression;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyBuiltinCache;
import com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeProviderBase;
import com.jetbrains.python.impl.psi.types.PyTypeUtil;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.toolbox.Substring;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.jetbrains.python.impl.psi.types.PyTypeUtilKt.isUnknown;

/**
 * Provides type information extracted from NumPy docstring format.
 *
 * @author avereshchagin
 */
@ExtensionImpl(id = "numpyDocStringTypeProvider", order = "before pythonDocstringTypeProvider, after pyTypingTypeProvider")
public final class NumpyDocStringTypeProvider extends PyTypeProviderBase {
    private static final Map<String, String> NUMPY_ALIAS_TO_REAL_TYPE = new HashMap<>();
    private static final Pattern REDIRECT = Pattern.compile("^Refer to `(.*)` for full documentation.$");
    private static final Pattern NUMPY_UNION_PATTERN = Pattern.compile("^\\{(.*)\\}$");
    private static final Pattern NUMPY_ARRAY_PATTERN = Pattern.compile("(\\(\\.\\.\\..*\\))(.*)");
    public static final String NDARRAY = "numpy.core.multiarray.ndarray";

    private static final String NDARRAY_OR_ITERABLE = NDARRAY + " or collections.abc.Iterable or int or long or float";

    static {
        NUMPY_ALIAS_TO_REAL_TYPE.put("ndarray", NDARRAY);
        NUMPY_ALIAS_TO_REAL_TYPE.put("numpy.ndarray", NDARRAY);
        // 184 occurrences

        NUMPY_ALIAS_TO_REAL_TYPE.put("array_like", NDARRAY_OR_ITERABLE);

        NUMPY_ALIAS_TO_REAL_TYPE.put("array-like", NDARRAY_OR_ITERABLE);
        // Parameters marked as 'data-type' actually get any Python type identifier such as 'bool' or
        // an instance of 'numpy.core.multiarray.dtype', however the type checker isn't able to check it.
        // 30 occurrences
        NUMPY_ALIAS_TO_REAL_TYPE.put("data-type", "object");
        NUMPY_ALIAS_TO_REAL_TYPE.put("dtype", "object");
        // 16 occurrences
        NUMPY_ALIAS_TO_REAL_TYPE.put("scalar", "int or long or float or complex");
        // 10 occurrences
        NUMPY_ALIAS_TO_REAL_TYPE.put("array", NDARRAY_OR_ITERABLE);
        NUMPY_ALIAS_TO_REAL_TYPE.put("numpy.array", NDARRAY_OR_ITERABLE);
        // 9 occurrences
        NUMPY_ALIAS_TO_REAL_TYPE.put("any", "object");
        // 5 occurrences
        NUMPY_ALIAS_TO_REAL_TYPE.put("Standard Python scalar object", "int or long or float or complex");
        // 4 occurrences
        NUMPY_ALIAS_TO_REAL_TYPE.put("Python type", "object");
        // 3 occurrences
        NUMPY_ALIAS_TO_REAL_TYPE.put("callable", "collections.abc.Callable");
        // 3 occurrences
        NUMPY_ALIAS_TO_REAL_TYPE.put("number", "int or long or float or complex");

        //treat all collections as iterable
        NUMPY_ALIAS_TO_REAL_TYPE.put("sequence", "collections.abc.Iterable");
        NUMPY_ALIAS_TO_REAL_TYPE.put("set", "collections.abc.Iterable");
        NUMPY_ALIAS_TO_REAL_TYPE.put("list", "collections.abc.Iterable");
        NUMPY_ALIAS_TO_REAL_TYPE.put("tuple", "collections.abc.Iterable or tuple");

        NUMPY_ALIAS_TO_REAL_TYPE.put("ints", "int");
        NUMPY_ALIAS_TO_REAL_TYPE.put("non-zero int", "int");
    }

    private static @Nullable NumpyDocString forFunction(PyFunction function,
            @Nullable PsiElement reference,
            @Nullable String knownSignature) {
        String docString = function.getDocStringValue();
        if (docString == null) {
            // Docstring for constructor can be found in the docstring of class
            PyClass cls = PyUtil.turnConstructorIntoClass(function);
            if (cls != null) {
                docString = cls.getDocStringValue();
            }
        }

        if (docString != null) {
            NumpyDocString parsed = (NumpyDocString)DocStringUtil.parseDocStringContent(DocStringFormat.NUMPY, docString);
            if (parsed.getReturnFields().isEmpty() && parsed.getParameterFields().isEmpty()) {
                return null;
            }

            String signature = parsed.getSignature();
            String redirect = findRedirect(parsed.getLines());
            if (redirect != null && reference != null) {
                PyFunction resolvedFunction = resolveRedirectToFunction(redirect, reference);
                if (resolvedFunction != null) {
                    return forFunction(resolvedFunction, reference, knownSignature != null ? knownSignature : signature);
                }
            }
            return parsed;
        }
        return null;
    }

    /**
     * Returns NumPyDocString object confirming to Numpy-style formatted docstring of specified function.
     *
     * @param function  Function containing docstring for which Numpy wrapper object is to be obtained.
     * @param reference An original reference element to specified function.
     * @return Numpy docstring wrapper object for specified function.
     */
    public static @Nullable NumpyDocString forFunction(PyFunction function, @Nullable PsiElement reference) {
        return forFunction(function, reference, null);
    }

    private static @Nullable String findRedirect(List<Substring> lines) {
        for (Substring line : lines) {
            Matcher matcher = REDIRECT.matcher(line);
            if (matcher.matches() && matcher.groupCount() > 0) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * Returns PyFunction object for specified fully qualified name accessible from specified reference.
     *
     * @param redirect  A fully qualified name of function that is redirected to.
     * @param reference An original reference element.
     * @return Resolved function or null if it was not resolved.
     */
    private static @Nullable PyFunction resolveRedirectToFunction(String redirect, PsiElement reference) {
        QualifiedName qualifiedName = QualifiedName.fromDottedString(redirect);
        String functionName = qualifiedName.getLastComponent();
        List<PsiElement> items = PyResolveImportUtil.resolveQualifiedName(qualifiedName.removeLastComponent(),
            PyResolveImportUtil.fromFoothold(reference));
        for (PsiElement item : items) {
            if (item instanceof PsiDirectory) {
                item = ((PsiDirectory)item).findFile(PyNames.INIT_DOT_PY);
            }
            if (item instanceof PyFile) {
                PsiElement element = ((PyFile)item).getElementNamed(functionName);
                if (element instanceof PyFunction) {
                    return (PyFunction)element;
                }
            }
        }
        return null;
    }

    public static @Nullable String cleanupOptional(String typeString) {
        int index = typeString.indexOf(", optional");
        if (index >= 0) {
            return typeString.substring(0, index);
        }

        // Match ", default None" with various separators and spacings:
        //   ", default None", ", default=None", ", default:None",
        //   ", default: None", ", default = None", etc.
        int defaultIndex = findDefaultSuffixStart(typeString);
        if (defaultIndex >= 0 && isDefaultValueNone(typeString, defaultIndex)) {
            return typeString.substring(0, defaultIndex);
        }

        return null;
    }

    /**
     * Strips ", default <value>" from the type string regardless of the default value.
     * Returns the original string when no such suffix is present.
     * Unlike {@link #cleanupOptional}, this does not imply the parameter is Optional.
     */
    public static String stripDefaultValue(String typeString) {
        int index = findDefaultSuffixStart(typeString);
        return index >= 0 ? typeString.substring(0, index) : typeString;
    }

    private static int findDefaultSuffixStart(String typeString) {
        int index = typeString.indexOf(", default");
        if (index < 0) return -1;
        int after = index + ", default".length();
        if (after >= typeString.length()) return -1;
        char nextChar = typeString.charAt(after);
        return (nextChar == ' ' || nextChar == '=' || nextChar == ':') ? index : -1;
    }

    private static boolean isDefaultValueNone(String typeString, int defaultStart) {
        int pos = defaultStart + ", default".length();
        while (pos < typeString.length() && Character.isWhitespace(typeString.charAt(pos))) pos++;
        if (pos < typeString.length() && (typeString.charAt(pos) == '=' || typeString.charAt(pos) == ':')) {
            pos++;
            while (pos < typeString.length() && Character.isWhitespace(typeString.charAt(pos))) pos++;
        }
        if (!typeString.startsWith("None", pos)) return false;
        if (pos + 4 == typeString.length()) return true;
        return !Character.isJavaIdentifierPart(typeString.charAt(pos + 4));
    }

    public static List<String> getNumpyUnionType(String typeString) {
        Matcher arrayMatcher = NUMPY_ARRAY_PATTERN.matcher(typeString);
        if (arrayMatcher.matches()) {
            typeString = arrayMatcher.group(2);
        }
        Matcher matcher = NUMPY_UNION_PATTERN.matcher(typeString);
        if (matcher.matches()) {
            typeString = matcher.group(1);
        }
        return splitTopLevelByComma(typeString);
    }

    private static List<String> splitTopLevelByComma(String typeString) {
        List<String> result = new ArrayList<>();
        int depth = 0;
        int start = 0;
        for (int i = 0; i < typeString.length(); i++) {
            switch (typeString.charAt(i)) {
                case '[', '(', '{' -> depth++;
                case ']', ')', '}' -> depth = Math.max(0, depth - 1);
                case ',' -> {
                    if (depth == 0) {
                        result.add(typeString.substring(start, i).trim());
                        start = i + 1;
                    }
                }
            }
        }
        result.add(typeString.substring(start).trim());
        return result;
    }

    @Override
    public @Nullable Ref<PyType> getCallType(PyFunction function,
            PyCallSiteExpression callSite,
            TypeEvalContext context) {
        if (isApplicable(function)) {
            if (PyTypingTypeProvider.getReturnTypeAnnotation(function, context) != null) {
                return null;
            }
            PyExpression callee = callSite instanceof PyCallExpression ? ((PyCallExpression)callSite).getCallee() : null;
            NumpyDocString docString = forFunction(function, callee);
            if (docString != null) {
                List<SectionField> returns = docString.getReturnFields();
                PyPsiFacade facade = getPsiFacade(function);

                return switch (returns.size()) {
                    case 0 -> null;
                    case 1 ->
                        // Function returns single value
                        Optional
                            .ofNullable(returns.get(0).getType())
                            .filter(StringUtil::isNotEmpty)
                            .map(typeName -> isUfuncType(function, typeName)
            ? facade.parseTypeAnnotation("T", function)
            : parseNumpyDocType(function, typeName))
                            .map(type -> PyTypingTypeProvider.toAsyncIfNeeded(function, type))
                            .map(Ref::create)
                            .orElse(null);
                    default -> {
                        // Function returns a tuple
                        List<PyType> unionMembers = new ArrayList<>();
                        List<PyType> members = new ArrayList<>();

                        for (int i = 0; i < returns.size(); i++) {
                            String memberTypeName = returns.get(i).getType();
                            PyType returnType = StringUtil.isNotEmpty(memberTypeName) ? parseNumpyDocType(function, memberTypeName) : null;
                            boolean isOptional = StringUtil.isNotEmpty(memberTypeName) && memberTypeName.contains("optional");

                            if (isOptional && i != 0) {
                                if (members.size() > 1) {
                                    unionMembers.add(facade.createTupleType(members, function));
                                }
                                else if (members.size() == 1) {
                                    unionMembers.add(members.get(0));
                                }
                            }
                            members.add(returnType);

                            if (i == returns.size() - 1 && isOptional) {
                                unionMembers.add(facade.createTupleType(members, function));
                            }
                        }

                        PyType type = unionMembers.isEmpty() ? facade.createTupleType(members, function) : facade.createUnionType(unionMembers);
                        yield Ref.create(PyTypingTypeProvider.toAsyncIfNeeded(function, type));
                    }
                };
            }
        }

        return null;
    }

    @Override
    public @Nullable Ref<PyType> getParameterType(PyNamedParameter parameter,
            PyFunction function,
            TypeEvalContext context) {
        if (isApplicable(function)) {
            String name = parameter.getName();
            if (name != null) {
                PyType type = getParameterType(function, name);
                if (type != null) {
                    return Ref.create(type);
                }
            }
        }
        return null;
    }

    public static boolean isInsideNumPy(PsiElement element) {
        PsiFile file = element.getContainingFile();

        if (file != null) {
            PyPsiFacade facade = getPsiFacade(element);
            VirtualFile virtualFile = file.getVirtualFile();
            if (virtualFile != null) {
                String name = facade.findShortestImportableName(virtualFile, element);
                return name != null && (name.startsWith("numpy.") || name.startsWith("matplotlib.") || name.startsWith("scipy."));
            }
        }
        return false;
    }

    private static boolean isApplicable(PsiElement element) {
        Module module = ModuleUtilCore.findModuleForPsiElement(element);
        if (module != null) {
            if (PyDocumentationSettings.getInstance(module).isNumpyFormat(element.getContainingFile())) {
                return true;
            }
        }

        return isInsideNumPy(element);
    }

    private static PyPsiFacade getPsiFacade(PsiElement anchor) {
        return PyPsiFacade.getInstance(anchor.getProject());
    }

    private static @Nullable PyType parseSingleNumpyDocType(PsiElement anchor, String typeString) {
        PyPsiFacade facade = getPsiFacade(anchor);
        String realTypeName = getNumpyRealTypeName(typeString);

        if (!realTypeName.equals(typeString) || typeString.contains(" or ")) {
            PyType type = facade.parseTypeAnnotation(realTypeName, anchor);
            if (!isUnknown(type)) {
                return type;
            }
        }

        PyExpression expression = PyUtil.createExpressionFromFragment(typeString, anchor);
        if (expression != null) {
            PyType type = PyTypeUtil.derefOrUnknown(
                PyTypingTypeProvider.getType(expression, TypeEvalContext.codeInsightFallback(anchor.getProject())));
            if (!isUnknown(type)) {
                return type;
            }
        }

        PyType type = facade.parseTypeAnnotation(typeString, anchor);
        if (!isUnknown(type)) {
            return type;
        }
        return getNominalType(anchor, typeString);
    }

    private static String getNumpyRealTypeName(String typeString) {
        String realTypeName = NUMPY_ALIAS_TO_REAL_TYPE.get(typeString);
        if (realTypeName != null) {
            return realTypeName;
        }
        List<String> typeSubStrings = StringUtil.split(typeString, " ");
        List<String> typeParts = new ArrayList<>();
        for (String string : typeSubStrings) {
            String type = NUMPY_ALIAS_TO_REAL_TYPE.get(string);
            typeParts.add(type != null ? type : string);
        }
        typeString = StringUtil.join(typeParts, " ");
        return typeString;
    }

    /**
     * Converts literal into type, e.g. -1 -> int, 'fro' -> str
     */
    private static @Nullable PyType getNominalType(PsiElement anchor, String typeString) {
        PyExpression expression = PyUtil.createExpressionFromFragment(typeString, anchor);
        if (expression != null) {
            PyBuiltinCache builtinCache = PyBuiltinCache.getInstance(anchor);
            if (expression instanceof PyStringLiteralExpression) {
                return builtinCache.getStrType();
            }
            if (expression instanceof PyNumericLiteralExpression) {
                return builtinCache.getIntType();
            }
        }
        return null;
    }

    private static @Nullable PyType parseNumpyDocType(PsiElement anchor, String typeString) {
        String withoutOptional = cleanupOptional(typeString);
        Set<PyType> types = new LinkedHashSet<>();
        if (withoutOptional != null) {
            typeString = withoutOptional;
        }
        else {
            typeString = stripDefaultValue(typeString);
        }
        for (String typeName : getNumpyUnionType(typeString)) {
            PyType parsedType = parseSingleNumpyDocType(anchor, typeName);
            if (parsedType != null) {
                types.add(parsedType);
            }
        }
        if (!types.isEmpty() && withoutOptional != null) {
            PyType noneType = PyBuiltinCache.getInstance(anchor).getNoneType();
            if (noneType != null) {
                types.add(noneType);
            }
        }
        return getPsiFacade(anchor).createUnionType(types);
    }

    public static boolean isUfuncType(PsiElement anchor, String typeString) {
        for (String typeName : getNumpyUnionType(typeString)) {
            if (anchor instanceof PyFunction && isInsideNumPy(anchor) && NumpyUfuncs.isUFunc(((PyFunction)anchor).getName()) &&
                    ("array_like".equals(typeName) || "ndarray".equals(typeName))) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable PyType getParameterType(PyFunction function, String parameterName) {
        NumpyDocString docString = forFunction(function, function);
        if (docString != null) {
            String paramType = docString.getParamType(parameterName);

            // If parameter name starts with "p_", and we failed to obtain it from the docstring,
            // try to obtain parameter named without such prefix.
            if (paramType == null && parameterName.startsWith("p_")) {
                paramType = docString.getParamType(parameterName.substring(2));
            }
            if (paramType != null) {
                if (isUfuncType(function, paramType)) {
                    return getPsiFacade(function).parseTypeAnnotation("numbers.Number or numpy.core.multiarray.ndarray or collections.abc.Iterable",
            function);
                }
                PyType numpyDocType = parseNumpyDocType(function, paramType);
                if ("size".equals(parameterName)) {
                    return getPsiFacade(function).createUnionType(Arrays.asList(numpyDocType, PyBuiltinCache.getInstance(function).getIntType()));
                }
                return numpyDocType;
            }
        }
        return null;
    }

    @Override
    public @Nullable Ref<PyType> getReferenceType(PsiElement referenceTarget, TypeEvalContext context, @Nullable PsiElement anchor) {
        if (referenceTarget instanceof PyFunction) {
            if (NumpyUfuncs.isUFunc(((PyFunction)referenceTarget).getName()) && isInsideNumPy(referenceTarget)) {
                // we intentionally looking here for the user stub class
                PyPsiFacade facade = PyPsiFacade.getInstance(referenceTarget.getProject());
                PyClass uFuncClass = facade.createClassByQName("numpy.core.ufunc", referenceTarget);
                if (uFuncClass != null) {
                    return Ref.create(new PyClassTypeImpl(uFuncClass, false));
                }
            }
        }
        return null;
    }
}
