// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.impl;

import com.jetbrains.python.PyNames;
import com.jetbrains.python.impl.PythonRuntimeService;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.resolve.MainPythonSdkKt;
import com.jetbrains.python.impl.psi.resolve.PyResolveImportUtil;
import com.jetbrains.python.impl.psi.resolve.PythonSdkPathCache;
import com.jetbrains.python.impl.psi.types.PyClassTypeImpl;
import com.jetbrains.python.impl.psi.types.PyUnionType;
import com.jetbrains.python.impl.sdk.PythonSdkType;
import com.jetbrains.python.psi.LanguageLevel;
import com.jetbrains.python.psi.PyClass;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyQualifiedExpression;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.psi.types.PyAnyType;
import com.jetbrains.python.psi.types.PyClassLikeType;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyType;
import consulo.content.bundle.Sdk;
import consulo.language.psi.FileContextUtil;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.PsiReference;
import consulo.language.psi.util.QualifiedName;
import consulo.language.util.ModuleUtilCore;
import consulo.module.Module;
import consulo.module.content.ProjectRootManager;
import consulo.module.content.layer.orderEntry.LibraryOrderEntry;
import consulo.module.content.layer.orderEntry.ModuleExtensionWithSdkOrderEntry;
import consulo.module.content.layer.orderEntry.OrderEntry;
import consulo.project.Project;
import consulo.virtualFileSystem.VirtualFile;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Provides access to Python builtins via skeletons.
 * <p>
 * Consulo: JB keys this cache by {@code com.intellij.python.sdk.backend.PythonInterpreter}, Consulo keys it by the {@link Sdk}
 * of the interpreter (as {@link PythonSdkPathCache} does).
 */
public class PyBuiltinCache {
    public static final String BUILTIN_MODULE = "__builtin__";
    public static final String BUILTIN_MODULE_3K = "builtins";
    public static final String TYPES_MODULE = "types";
    public static final String TYPESHED_MODULE = "_typeshed";
    private static final String EXCEPTIONS_MODULE = "exceptions";

    private static final PyBuiltinCache DUD_INSTANCE = new PyBuiltinCache(null, null, null, null);

    private final @Nullable CachedFile myBuiltinsFile;
    private final @Nullable CachedFile myTypesFile;
    private final @Nullable CachedFile myTypeshedFile;
    private final @Nullable CachedFile myExceptionsFile;

    private final @Nullable PyFile myBuiltinsPyFile;

    private PyBuiltinCache(
        @Nullable CachedFile builtinsFile,
        @Nullable CachedFile typesFile,
        @Nullable CachedFile typeshedFile,
        @Nullable CachedFile exceptionsFile
    ) {
        myBuiltinsFile = builtinsFile;
        myTypesFile = typesFile;
        myTypeshedFile = typeshedFile;
        myExceptionsFile = exceptionsFile;
        myBuiltinsPyFile = builtinsFile != null ? builtinsFile.getFile() : null;
    }

    public PyBuiltinCache(Project project, Sdk interpreter) {
        this(
            toCachedFile(getBuiltinsForInterpreter(project, interpreter)),
            toCachedFile(getFileForInterpreter(project, interpreter, QualifiedName.fromDottedString(TYPES_MODULE))),
            toCachedFile(getFileForInterpreter(project, interpreter, QualifiedName.fromDottedString(TYPESHED_MODULE))),
            toCachedFile(getFileForInterpreter(project, interpreter, QualifiedName.fromDottedString(EXCEPTIONS_MODULE)))
        );
    }

    public @Nullable PyFile getBuiltinsFile() {
        return myBuiltinsPyFile;
    }

    public boolean isValid() {
        PyFile builtinsFile = getBuiltinsFile();
        return builtinsFile != null && builtinsFile.isValid();
    }

    /**
     * Looks for a top-level named item. (Package builtins does not contain any sensible nested names anyway.)
     *
     * @param name to look for
     * @return found element, or null.
     */
    public @Nullable PsiElement getByName(@Nullable String name) {
        // Accept both the short name ("int") and the canonical builtin qualified name ("builtins.int")
        String shortName = PyNames.FQN.unqualifyBuiltinName(name);
        PyFile builtinsFile = getBuiltinsFile();
        if (builtinsFile != null) {
            PsiElement element = builtinsFile.getElementNamed(shortName);
            if (element != null) {
                return element;
            }
        }
        return myExceptionsFile != null ? myExceptionsFile.getFile().getElementNamed(shortName) : null;
    }

    public @Nullable PyClass getClass(String name) {
        PyFile builtinsFile = getBuiltinsFile();
        if (builtinsFile == null) {
            return null;
        }
        String shortName = PyNames.FQN.unqualifyBuiltinName(name);
        return builtinsFile.findTopLevelClass(shortName != null ? shortName : name);
    }

    public @Nullable PyClassType getObjectType(String name) {
        if (myBuiltinsFile == null) {
            return null;
        }
        String shortName = PyNames.FQN.unqualifyBuiltinName(name);
        return myBuiltinsFile.getClassType(shortName != null ? shortName : name);
    }

    public @Nullable PyClassType getObjectType() {
        return getObjectType("object");
    }

    public @Nullable PyClassType getListType() {
        return getObjectType("list");
    }

    public @Nullable PyClassType getDictType() {
        return getObjectType("dict");
    }

    public @Nullable PyClassType getSetType() {
        return getObjectType("set");
    }

    public @Nullable PyClassType getTupleType() {
        return getObjectType("tuple");
    }

    public @Nullable PyClassType getIntType() {
        return getObjectType("int");
    }

    public @Nullable PyClassType getFloatType() {
        return getObjectType("float");
    }

    public @Nullable PyClassType getComplexType() {
        return getObjectType("complex");
    }

    public @Nullable PyClassType getStrType() {
        return getObjectType("str");
    }

    public @Nullable PyClassType getBytesType(LanguageLevel level) {
        return getObjectType(level.isPy3K() ? "bytes" : "str");
    }

    public @Nullable PyClassType getUnicodeType(LanguageLevel level) {
        return getObjectType(level.isPy3K() ? "str" : "unicode");
    }

    public @Nullable PyType getStringType(LanguageLevel level) {
        if (level.isPy3K()) {
            return getObjectType("str");
        }
        else {
            return getStrOrUnicodeType();
        }
    }

    public @Nullable PyType getByteStringType(LanguageLevel level) {
        if (level.isPy3K()) {
            return getObjectType("bytes");
        }
        else {
            return getStrOrUnicodeType();
        }
    }

    public @Nullable PyType getStrOrUnicodeType() {
        return getStrOrUnicodeType(false);
    }

    public @Nullable PyType getStrOrUnicodeType(boolean definition) {
        PyClassLikeType str = getObjectType("str");
        PyClassLikeType unicode = getObjectType("unicode");

        if (str != null && str.isDefinition() ^ definition) {
            str = definition ? str.toClass() : str.toInstance();
        }

        if (unicode != null && unicode.isDefinition() ^ definition) {
            unicode = definition ? unicode.toClass() : unicode.toInstance();
        }

        return PyUnionType.unionOrUnknown(str, unicode != null ? unicode : PyAnyType.getUnknown());
    }

    public @Nullable PyClassType getBoolType() {
        return getObjectType("bool");
    }

    public @Nullable PyClassType getClassMethodType() {
        return getObjectType("classmethod");
    }

    public @Nullable PyClassType getStaticMethodType() {
        return getObjectType("staticmethod");
    }

    public @Nullable PyClassType getTypeType() {
        return getObjectType("type");
    }

    public @Nullable PyClassType getSliceType() {
        return getObjectType("slice");
    }

    public @Nullable PyClassType getNoneType() {
        PyClassType type = myTypesFile != null ? myTypesFile.getClassType("NoneType") : null;
        if (type != null) {
            return type;
        }
        return myTypeshedFile != null ? myTypeshedFile.getClassType("NoneType") : null;
    }

    public @Nullable PyClassType getEllipsisType() {
        return myTypesFile != null ? myTypesFile.getClassType("EllipsisType") : null;
    }

    /**
     * @param target an element to check.
     * @return true iff target is inside the __builtins__.py
     */
    public boolean isBuiltin(@Nullable PsiElement target) {
        if (target == null) {
            return false;
        }
        PyPsiUtils.assertValid(target);
        if (!target.isValid()) {
            return false;
        }
        PsiFile the_file = target.getContainingFile();
        if (!(the_file instanceof PyFile)) {
            return false;
        }
        // files are singletons, no need to compare URIs
        return the_file == getBuiltinsFile() || (myExceptionsFile != null && the_file == myExceptionsFile.getFile());
    }

    /**
     * Returns an instance of builtin cache. Instances differ per module and are cached.
     *
     * @param reference something to define the module from.
     * @return an instance of cache. If reference was null, the instance is a fail-fast dud one.
     */
    public static PyBuiltinCache getInstance(@Nullable PsiElement reference) {
        if (reference != null) {
            try {
                Sdk interpreter = findInterpreterForFile(reference.getContainingFile());
                if (interpreter != null) {
                    return PythonSdkPathCache.getInstance(reference.getProject(), interpreter).getBuiltins();
                }
            }
            catch (PsiInvalidElementAccessException ignored) {
            }
        }
        return DUD_INSTANCE; // a non-functional fail-fast instance, for a case when skeletons are not available
    }

    /**
     * The SDK of {@link #findInterpreterForFile}.
     */
    public static @Nullable Sdk findSdkForFile(@Nullable PsiFileSystemItem psiFile) {
        return findInterpreterForFile(psiFile);
    }

    /**
     * The interpreter that resolves the names of {@code psiFile}: the interpreter of its module, or for a file outside every
     * module, see {@link #findInterpreterForNonModuleFile}.
     */
    public static @Nullable Sdk findInterpreterForFile(@Nullable PsiFileSystemItem psiFile) {
        if (psiFile == null) {
            return null;
        }
        Module module = ModuleUtilCore.findModuleForPsiElement(psiFile);
        return module != null ? MainPythonSdkKt.pythonSdkIfReady(module) : findInterpreterForNonModuleFile(psiFile);
    }

    /**
     * The interpreter for {@code psiFile} outside every module: the main interpreter, or the interpreter of the library that
     * holds the file.
     */
    public static @Nullable Sdk findInterpreterForNonModuleFile(PsiFileSystemItem psiFile) {
        VirtualFile vfile;
        if (psiFile instanceof PsiFile file) {
            PsiFile contextFile = FileContextUtil.getContextFile(file);
            vfile = contextFile != null ? contextFile.getOriginalFile().getVirtualFile() : null;
        }
        else {
            vfile = psiFile.getVirtualFile();
        }
        Sdk interpreter = null;
        if (vfile != null) { // reality
            ProjectRootManager projectRootManager = ProjectRootManager.getInstance(psiFile.getProject());
            // A Python console is served by `PythonRuntimeService.getConsoleSdk` together with `visitAllModules`, so
            // answering here would hand it a second, competing interpreter (PY-89831).
            if (PythonRuntimeService.getInstance().getConsoleSdk(psiFile) == null) {
                interpreter = MainPythonSdkKt.mainPythonSdk(psiFile.getProject());
            }
            if (interpreter == null) {
                List<OrderEntry> orderEntries = projectRootManager.getFileIndex().getOrderEntriesForFile(vfile);
                for (OrderEntry orderEntry : orderEntries) {
                    // A module names its interpreter by one carrier, and the platform loads the other too, see
                    // isIgnoredInterpreterEntry.
                    // TODO consulo: com.intellij.python.sdk.backend isIgnoredInterpreterEntry/isInterpreterLibraryCarrier missing
                    if (orderEntry == null) {
                        continue;
                    }
                    if (orderEntry instanceof ModuleExtensionWithSdkOrderEntry sdkOrderEntry) {
                        interpreter = sdkOrderEntry.getSdk();
                    }
                    // The interpreter library of a module carries the roots of its interpreter, as its SDK does.
                    else if (orderEntry instanceof LibraryOrderEntry libraryOrderEntry && libraryOrderEntry.isModuleLevel()) {
                        interpreter = MainPythonSdkKt.pythonSdkIfReady(orderEntry.getOwnerModule());
                    }
                }
            }
        }
        return interpreter;
    }

    /**
     * {@link #getBuiltinsForInterpreter} for the interpreter that the registry of {@code project} holds for {@code sdk}.
     */
    public static @Nullable PyFile getBuiltinsForSdk(Project project, Sdk sdk) {
        return getBuiltinsForInterpreter(project, sdk);
    }

    public static @Nullable PyFile getBuiltinsForInterpreter(Project project, Sdk interpreter) {
        String moduleName = getBuiltinsModuleName(PythonSdkType.getLanguageLevelForSdk(interpreter));
        return getFileForInterpreter(project, interpreter, QualifiedName.fromDottedString(moduleName));
    }

    public static String getBuiltinsFileName(LanguageLevel level) {
        return getBuiltinsModuleName(level) + PyNames.DOT_PY;
    }

    public static String getBuiltinsModuleName(LanguageLevel level) {
        return level.isPython2() ? BUILTIN_MODULE : BUILTIN_MODULE_3K;
    }

    private static @Nullable PyFile getFileForInterpreter(Project project, Sdk interpreter, QualifiedName moduleName) {
        List<PsiElement> results = PyResolveImportUtil.resolveQualifiedName(moduleName, PyResolveImportUtil.fromInterpreter(project, interpreter));
        if (results.isEmpty()) {
            return null;
        }
        return PyUtil.turnDirIntoInit(results.get(0)) instanceof PyFile file ? file : null;
    }

    public static boolean isInBuiltins(PyExpression expression) {
        if (expression instanceof PyQualifiedExpression qualifiedExpression && qualifiedExpression.isQualified()) {
            return false;
        }
        String name = expression.getName();
        PsiReference reference = expression.getReference();
        if (reference != null && name != null) {
            PyBuiltinCache cache = getInstance(expression);
            if (cache.getByName(name) != null) {
                PsiElement resolved = reference.resolve();
                if (resolved != null && cache.isBuiltin(resolved)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static @Nullable CachedFile toCachedFile(@Nullable PyFile file) {
        return file != null ? new CachedFile(file) : null;
    }

    private static final class CachedFile {
        private final PyFile myFile;
        private long myModificationStamp = -1;
        /**
         * Stores the most often used types, returned by nNNType.
         */
        private final Map<String, @Nullable PyClassType> myTypeCache = new HashMap<>();

        private CachedFile(PyFile file) {
            myFile = file;
        }

        PyFile getFile() {
            return myFile;
        }

        @Nullable PyClassType getClassType(String name) {
            PyClassType cached;
            synchronized (myTypeCache) {
                if (myModificationStamp != myFile.getModificationStamp()) {
                    myTypeCache.clear();
                    myModificationStamp = myFile.getModificationStamp();
                }
                cached = myTypeCache.get(name);
                if (cached != null) {
                    cached.assertValid(name);
                }
            }
            if (cached != null) {
                return cached;
            }
            PyClassType resolved = resolveTopLevel(name);
            if (resolved != null) {
                synchronized (myTypeCache) {
                    myTypeCache.put(name, resolved);
                }
            }
            return resolved;
        }

        @Nullable PyClassType resolveTopLevel(String name) {
            PyClass pyClass = myFile.findTopLevelClass(name);
            return pyClass != null ? new PyClassTypeImpl(pyClass, false) : null;
        }
    }
}
