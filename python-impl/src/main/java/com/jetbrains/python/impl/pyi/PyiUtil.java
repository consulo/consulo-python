// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.pyi;

import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.typing.PyTypeShed;
import com.jetbrains.python.impl.psi.PyKnownDecorator;
import com.jetbrains.python.impl.psi.PyKnownDecoratorUtil;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.MainPythonSdkKt;
import com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.impl.sdk.PySdkUtil;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyElement;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFunction;
import com.jetbrains.python.psi.PyTypedElement;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.TypeEvalContext;
import com.jetbrains.python.pyi.PyiUtilCore;
import consulo.content.bundle.Sdk;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.util.collection.ContainerUtil;
import consulo.util.lang.ObjectUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

public final class PyiUtil {
    private PyiUtil() { }

    public static boolean isInsideStub(PsiElement element) {
        return PyiUtilCore.isInsideStub(element);
    }

    public static @Nullable PsiElement getPythonStub(PyElement element) {
        final PsiFile file = element.getContainingFile();
        if (pyButNotPyiFile(file)) {
            final PyiFile pythonStubFile = getPythonStubFile((PyFile)file);
            if (pythonStubFile != null) {
                return findSimilarElement(element, pythonStubFile);
            }
        }
        return null;
    }

    public static @Nullable PsiElement getOriginalElement(PyElement element) {
        final PsiFile file = element.getContainingFile();
        if (!(file instanceof PyiFile)) return null;

        // PY-38169 special case definitions in typing.pyi as stubs for definitions in `_collections_abc`/`_collections`
        final var pyClass = (element instanceof PyClass elementClass ? elementClass : PsiTreeUtil.getParentOfType(element, PyClass.class));
        if (pyClass != null) {
            final var typingRedirection = PyTypeShed.INSTANCE.getTypingRedirections().get(pyClass.getQualifiedName());

            if (typingRedirection != null) {
                final var result = PyResolveImportUtil.resolveQualifiedName(
                    QualifiedName.fromDottedString(typingRedirection),
                    PyResolveImportUtil.fromFoothold(element).copyWithoutStubs().copyWithMembers()
                );
                if (result.isEmpty()) return null;
                return result.get(0);
            }
        }

        final PyFile originalFile = getOriginalFile((PyiFile)file);
        if (originalFile == null) return null;

        PsiElement result = findSimilarElement(element, originalFile);

        // If a name is defined in a .pyi stub and the corresponding .py module in a different manner, e.g.
        // it's exported through an assignment to a top-level attribute in a .pyi stub, but though a regular
        // "from" import in .py file, we might end up in another stub file again. It happens because resolving 
        // an imported name in a .py file in findSimilarElement() still prioritizes .pyi stubs over implementations
        // as PyResolveContext doesn't retain the PyQualifiedNameResolveContext.getWithoutStubs flag.
        // TODO propagate "without stubs" property through PyResolveContext
        if (result instanceof PyElement && isInsideStub(result) && result.getContainingFile() != file) {
            result = getOriginalElement((PyElement)result);
        }

        if (result != null) return result;

        if (element instanceof PyFunction) {
            PyClass containingClass = PyUtil.turnConstructorIntoClass((PyFunction)element);
            if (containingClass != null) {
                result = findSimilarElement(containingClass, originalFile);
            }
        }

        return result;
    }

    /**
      * Finds the element that navigation from {@code source} to {@code target} must open.
      * <p>
      * A stub target maps to its implementation when {@code source} is outside of a stub.
      * The stub target stays when it has no implementation, or when the implementation is in a generated skeleton.
      */
    public static PsiElement getNavigationTarget(PsiElement target, PsiElement source) {
        if (!(target instanceof PyElement pyTarget) || !isInsideStub(target) || isInsideStub(source)) return target;
        final PsiElement original = getOriginalElement(pyTarget);
        return original == null || isInSkeleton(original) ? target : original;
    }

    private static boolean isInSkeleton(PsiElement element) {
        if (!(element.getContainingFile() instanceof PyFile file) || file instanceof PyiFile) return false;
        final VirtualFile virtualFile = file.getVirtualFile();
        if (virtualFile == null) return false;
        final Sdk sdk = MainPythonSdkKt.pythonSdkIfReady(file);
        if (sdk == null) return false;
        final VirtualFile skeletonsDir = PySdkUtil.findSkeletonsDir(sdk);
        return skeletonsDir != null && VirtualFileUtil.isAncestor(skeletonsDir, virtualFile, false);
    }

    /**
      * @param function function whose scope is used to look for implementation
      * @param context  context to be used in determining if some function is overload
      * @return implementation in the scope owner of {@code function} with the same name as {@code function} has.
      * <i>Note: returns {@code null} if {@code function} is located in pyi-file.</i>
      */
    public static @Nullable PyFunction getImplementation(PyFunction function, TypeEvalContext context) {
        final PsiFile file = function.getContainingFile();
        if (file instanceof PyiFile) return null;
        return ContainerUtil.getLastItem(collectImplementationsOrOverloads(function, true, context));
    }

    /**
      * @param function function whose scope is used to look for overloads
      * @param context  context to be used in determining if some function is overload
      * @return overloads in the scope owner of {@code function} with the same name as {@code function} has.
      */
    public static List<PyFunction> getOverloads(PyFunction function, TypeEvalContext context) {
        return collectImplementationsOrOverloads(function, false, context);
    }

    public static boolean isOverload(PsiElement element, TypeEvalContext context) {
        return element instanceof PyFunction function &&
                      PyKnownDecoratorUtil.knownDecoratorsOf(function, context).contains(PyKnownDecorator.TYPING_OVERLOAD);
    }

    /**
      * @param element element whose original element should be found
      * @param cls     class of original element's type
      * @param <T>     expected type of original element
      * @return original element or {@code element} if original is not instance of {@code cls} or does not exist.
      */
    public static <T extends PyElement> T getOriginalElementOrLeaveAsIs(T element, Class<T> cls) {
        return ObjectUtil.notNull(PyUtil.as(getOriginalElement(element), cls), element);
    }

    public static boolean isPyiFileOfPackage(PsiElement element) {
        return element instanceof PyiFile || PyUtil.turnDirIntoInitPyi(element) instanceof PyiFile;
    }

    private static boolean pyButNotPyiFile(@Nullable PsiFile file) {
        return file instanceof PyFile && !(file instanceof PyiFile);
    }

    private static @Nullable PyiFile getPythonStubFile(PyFile file) {
        final QualifiedName name = QualifiedNameFinder.findCanonicalImportPath(file, file);
        if (name == null) {
            return null;
        }
        final PyQualifiedNameResolveContext context = PyResolveImportUtil.fromFoothold(file);
        return PyUtil.as(PyResolveImportUtil.resolveQualifiedName(name, context)
                                              .stream()
                                              .findFirst()
                                              .map(PyUtil::turnDirIntoInitPyi)
                                              .orElse(null), PyiFile.class);
    }

    private static @Nullable PyFile getOriginalFile(PyiFile file) {
        QualifiedName name = QualifiedNameFinder.findCanonicalImportPath(file, file);
        if (name == null) {
            return null;
        }
        String moduleRedirect = PyTypeShed.INSTANCE.getTypeshedModuleRedirections().get(name.toString());
        if (moduleRedirect != null) {
            name = QualifiedName.fromDottedString(moduleRedirect);
        }
        final PyQualifiedNameResolveContext context = PyResolveImportUtil.fromFoothold(file).copyWithoutStubs();
        return PyUtil.as(PyResolveImportUtil.resolveQualifiedName(name, context)
                                              .stream()
                                              .findFirst()
                                              .map(PyUtil::turnDirIntoInitPy)
                                              .orElse(null), PyFile.class);
    }

    private static @Nullable PsiElement findSimilarElement(PyElement element, PyFile file) {
        if (element instanceof PyFile) {
            return file;
        }
        final ScopeOwner owner = ScopeUtil.getScopeOwner(element);
        final String name = element.getName();
        if (owner != null && name != null) {
            assert owner != element;
            final PsiElement originalOwner = findSimilarElement(owner, file);
            if (originalOwner instanceof PyTypedElement) {
                final TypeEvalContext context = TypeEvalContext.codeInsightFallback(file.getProject());
                final PyType type = context.getType((PyTypedElement)originalOwner);
                final PsiElement result = resolveSimilarMember(type, name, context);
                return result == element ? null : result;
            }
        }
        return null;
    }

    private static @Nullable PsiElement resolveSimilarMember(@Nullable PyType similarOwnerType,
                                                                                                                      String name,
                                                                                                                      TypeEvalContext context) {
        if (similarOwnerType == null) return null;

        final PyResolveContext resolveContext = PyResolveContext.defaultContext(context);

        final List<? extends RatedResolveResult> results =
            similarOwnerType instanceof PyClassLikeType
            ? ((PyClassLikeType)similarOwnerType).resolveMember(name, null, AccessDirection.READ, resolveContext, false)
            : similarOwnerType.resolveMember(name, null, AccessDirection.READ, resolveContext);

        return takeTopPriorityElement(results);
    }

    private static List<PyFunction> collectImplementationsOrOverloads(PyFunction function,
                                                                                                                                                          boolean implementations,
                                                                                                                                                          TypeEvalContext context) {
        final ScopeOwner owner = ScopeUtil.getScopeOwner(function);
        final String name = function.getName();

        final List<PyFunction> result = new ArrayList<>();

        final Predicate<PyFunction> overloadsProcessor = f -> {
            if (name != null && name.equals(f.getName()) && implementations ^ isOverload(f, context)) {
                result.add(f);
            }
            return true;
        };

        if (owner instanceof PyClass cls) {
            if (name != null) {
                cls.visitMethods(overloadsProcessor, false, context);
            }
        }
        else if (owner instanceof PyFile file) {
            for (PyFunction f : file.getTopLevelFunctions()) {
                if (!overloadsProcessor.test(f)) {
                    break;
                }
            }
        }

        return result;
    }

    private static @Nullable PsiElement takeTopPriorityElement(@Nullable List<? extends RatedResolveResult> resolveResults) {
        if (!ContainerUtil.isEmpty(resolveResults)) {
            return Collections.max(resolveResults, Comparator.comparingInt(RatedResolveResult::getRate)).getElement();
        }
        return null;
    }
}
