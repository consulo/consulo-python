// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package consulo.python.regexp.impl.internal;

import com.jetbrains.python.impl.codeInsight.PyInjectionUtil;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.PyArgumentList;
import com.jetbrains.python.psi.PyBinaryExpression;
import com.jetbrains.python.psi.PyCallExpression;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyNamedParameter;
import com.jetbrains.python.psi.PyParameterList;
import com.jetbrains.python.psi.PyReferenceExpression;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.types.TypeEvalContext;
import consulo.language.Language;
import consulo.language.inject.MultiHostInjector;
import consulo.language.inject.MultiHostRegistrar;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ArrayUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Consulo registers one injector per host element class (see subclasses), JB registers a single injector for
 * {@code PyStringLiteralExpression, PyParenthesizedExpression, PyBinaryExpression, PyCallExpression}.
 */
public abstract class PythonRegexpInjector implements MultiHostInjector {
    private static final class RegexpMethodDescriptor {
        private final String methodName;
        private final int argIndex;

        private RegexpMethodDescriptor(String methodName, int argIndex) {
            this.methodName = methodName;
            this.argIndex = argIndex;
        }
    }

    private enum RegexpModule {
        RE,
        MRAB_REGEX
    }

    private static final String FLAGS = "flags";

    private final List<RegexpMethodDescriptor> myDescriptors = new ArrayList<>();

    public PythonRegexpInjector() {
        addMethod("compile");
        addMethod("search");
        addMethod("match");
        addMethod("fullmatch");
        addMethod("split");
        addMethod("splititer");
        addMethod("findall");
        addMethod("finditer");
        addMethod("sub");
        addMethod("subf");
        addMethod("subn");
        addMethod("subfn");
        addMethod("template");
    }

    private void addMethod(String name) {
        myDescriptors.add(new RegexpMethodDescriptor(name, 0));
    }

    @Override
    public void injectLanguages(MultiHostRegistrar registrar, PsiElement context) {
        PyArgumentList argumentList = PyUtil.as(context.getParent(), PyArgumentList.class);
        if (PyInjectionUtil.getLargestStringLiteral(context) == context && argumentList != null) {
            PyCallExpression call = PsiTreeUtil.getParentOfType(context, PyCallExpression.class);
            if (call != null && resolvePossibleRegexpCall(call) instanceof PyFunction function) {
                RegexpModule module = findRegexpModule(function);
                RegexpMethodDescriptor methodDescriptor = module != null ? findRegexpMethodDescriptor(function) : null;
                if (methodDescriptor != null && methodDescriptor.argIndex == ArrayUtil.indexOf(argumentList.getArguments(), context)) {
                    injectRegexpLanguage(registrar, context, module, isVerbose(call, function));
                }
            }
        }
    }

    private @Nullable PsiElement resolvePossibleRegexpCall(PyCallExpression call) {
        PyExpression callee = call.getCallee();

        if (callee instanceof PyReferenceExpression referenceExpression && canBeRegexpCall(callee)) {
            TypeEvalContext context = TypeEvalContext.codeAnalysis(call.getProject(), call.getContainingFile());
            return referenceExpression.getReference(PyResolveContext.defaultContext(context)).resolve();
        }

        return null;
    }

    private static void injectRegexpLanguage(MultiHostRegistrar registrar,
                                             PsiElement context,
                                             RegexpModule module,
                                             boolean verbose) {
        Language language = switch (module) {
            case RE -> verbose ? PythonVerboseRegexpLanguage.INSTANCE : PythonRegexpLanguage.INSTANCE;
            case MRAB_REGEX -> verbose ? PythonMrabVerboseRegexpLanguage.INSTANCE : PythonMrabRegexpLanguage.INSTANCE;
        };
        PyInjectionUtil.registerStringLiteralInjection(context, registrar, language);
    }

    private static boolean isVerbose(PyCallExpression call, PyFunction function) {
        PyExpression keywordFlags = call.getKeywordArgument(FLAGS);
        if (keywordFlags != null) {
            return isVerbose(keywordFlags);
        }
        PyParameterList parameterList = function.getParameterList();
        PyNamedParameter flagsParameter = parameterList.findParameterByName(FLAGS);
        int position = flagsParameter != null ? ArrayUtil.indexOf(parameterList.getParameters(), flagsParameter) : -1;
        PyExpression[] arguments = call.getArguments();
        return position >= 0 && position < arguments.length && isVerbose(arguments[position]);
    }

    private static boolean isVerbose(@Nullable PyExpression expression) {
        if (expression instanceof PyReferenceExpression) {
            String flagName = ((PyReferenceExpression) expression).getReferencedName();
            return "VERBOSE".equals(flagName) || "X".equals(flagName);
        }
        if (expression instanceof PyBinaryExpression binaryExpression) {
            return isVerbose(binaryExpression.getLeftExpression()) || isVerbose(binaryExpression.getRightExpression());
        }
        return false;
    }

    /**
     * The {@code regex} module keeps its functions in {@code regex._main} from release 2025.10.23.
     * Earlier releases keep them in {@code regex.regex}.
     */
    private static @Nullable RegexpModule findRegexpModule(PyFunction function) {
        if (!(ScopeUtil.getScopeOwner(function) instanceof PyFile)) {
            return null;
        }
        PsiFile containingFile = function.getContainingFile();
        if (ArrayUtil.contains(containingFile.getName(), "re.py", "re.pyi")) {
            return RegexpModule.RE;
        }
        QualifiedName qName = QualifiedNameFinder.findCanonicalImportPath(containingFile, null);
        if (qName != null && ArrayUtil.contains(qName.toString(), "regex._main", "regex.regex")) {
            return RegexpModule.MRAB_REGEX;
        }
        return null;
    }

    private @Nullable RegexpMethodDescriptor findRegexpMethodDescriptor(PyFunction function) {
        String functionName = function.getName();
        return myDescriptors
            .stream()
            .filter(descriptor -> descriptor.methodName.equals(functionName))
            .findAny()
            .orElse(null);
    }

    private boolean canBeRegexpCall(PyExpression callee) {
        String text = callee.getText();
        return myDescriptors
            .stream()
            .anyMatch(descriptor -> text.endsWith(descriptor.methodName));
    }
}
