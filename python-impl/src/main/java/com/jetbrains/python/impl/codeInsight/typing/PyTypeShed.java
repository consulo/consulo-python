// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.codeInsight.typing;

import com.jetbrains.python.impl.PythonBundledPyiStubsLocator;
import com.jetbrains.python.impl.PythonRuntimeService;
import com.jetbrains.python.psi.LanguageLevel;
import consulo.application.util.CachedValue;
import consulo.application.util.CachedValueProvider;
import consulo.application.util.CachedValuesManager;
import consulo.container.boot.ContainerPathManager;
import consulo.content.bundle.Sdk;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiManager;
import consulo.language.psi.util.QualifiedName;
import consulo.project.Project;
import consulo.util.dataholder.Key;
import consulo.util.lang.Pair;
import consulo.util.lang.lazy.LazyValue;
import consulo.virtualFileSystem.StandardFileSystems;
import consulo.virtualFileSystem.VirtualFile;
import consulo.virtualFileSystem.VirtualFileManager;
import consulo.virtualFileSystem.util.VirtualFileUtil;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Utilities for managing the local copy of the typeshed repository.
 * <p>
 * The original Git repo is located <a href="https://github.com/JetBrains/typeshed">here</a>.
 */
public final class PyTypeShed {
    public static final PyTypeShed INSTANCE = new PyTypeShed();

    private static final String METADATA_TOML = "METADATA.toml";
    private static final String PARTIAL_STUB_KEY = "partial_stub";
    private static final Key<CachedValue<Boolean>> PARTIAL_THIRD_PARTY_STUB_PACKAGE_KEY =
        Key.create("PyTypeShed.partialThirdPartyStubPackage");

    private static final Map<String, Pair<LanguageLevel, @Nullable LanguageLevel>> stdlibNamesAvailableOnlyInSubsetOfSupportedLanguageLevels;

    static {
        Map<String, Pair<LanguageLevel, @Nullable LanguageLevel>> m = new LinkedHashMap<>();
        // name to python versions when this name was introduced and removed
        m.put("_bootlocale", Pair.create(LanguageLevel.PYTHON36, LanguageLevel.PYTHON39));
        m.put("_dummy_thread", Pair.create(LanguageLevel.PYTHON36, LanguageLevel.PYTHON38));
        m.put("_dummy_threading", Pair.create(LanguageLevel.PYTHON27, LanguageLevel.PYTHON38));
        m.put("_interpchannels", Pair.create(LanguageLevel.PYTHON313, null));
        m.put("_interpqueues", Pair.create(LanguageLevel.PYTHON313, null));
        m.put("_interpreters", Pair.create(LanguageLevel.PYTHON313, null));
        m.put("_py_abc", Pair.create(LanguageLevel.PYTHON37, null));
        m.put("aifc", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("asynchat", Pair.create(LanguageLevel.PYTHON27, LanguageLevel.PYTHON311));
        m.put("asyncio.exceptions", Pair.create(LanguageLevel.PYTHON38, null)); // likely it is ignored now
        m.put("asyncio.format_helpers", Pair.create(LanguageLevel.PYTHON37, null)); // likely it is ignored now
        m.put("asyncio.mixins", Pair.create(LanguageLevel.PYTHON310, null)); // likely it is ignored now
        m.put("asyncio.runners", Pair.create(LanguageLevel.PYTHON37, null)); // likely it is ignored now
        m.put("asyncio.staggered", Pair.create(LanguageLevel.PYTHON38, null)); // likely it is ignored now
        m.put("asyncio.taskgroups", Pair.create(LanguageLevel.PYTHON311, null)); // likely it is ignored now
        m.put("asyncio.threads", Pair.create(LanguageLevel.PYTHON39, null)); // likely it is ignored now
        m.put("asyncio.timeouts", Pair.create(LanguageLevel.PYTHON311, null)); // likely it is ignored now
        m.put("asyncio.trsock", Pair.create(LanguageLevel.PYTHON38, null)); // likely it is ignored now
        m.put("asyncore", Pair.create(LanguageLevel.PYTHON27, LanguageLevel.PYTHON311));
        m.put("audioop", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("binhex", Pair.create(LanguageLevel.PYTHON27, LanguageLevel.PYTHON310));
        m.put("cgi", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("cgitb", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("chunk", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("contextvars", Pair.create(LanguageLevel.PYTHON37, null));
        m.put("crypt", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("dataclasses", Pair.create(LanguageLevel.PYTHON37, null));
        m.put("dbm.sqlite3", Pair.create(LanguageLevel.PYTHON313, null));
        m.put("distutils", Pair.create(LanguageLevel.PYTHON27, LanguageLevel.PYTHON311));
        m.put("distutils.command.bdist_msi", Pair.create(LanguageLevel.PYTHON27, LanguageLevel.PYTHON310)); // likely it is ignored now
        m.put("distutils.command.bdist_wininst", Pair.create(LanguageLevel.PYTHON27, LanguageLevel.PYTHON39)); // likely it is ignored now
        m.put("dummy_threading", Pair.create(LanguageLevel.PYTHON27, LanguageLevel.PYTHON38));
        m.put("formatter", Pair.create(LanguageLevel.PYTHON27, LanguageLevel.PYTHON39));
        m.put("graphlib", Pair.create(LanguageLevel.PYTHON39, null));
        m.put("imghdr", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("imp", Pair.create(LanguageLevel.PYTHON27, LanguageLevel.PYTHON311));
        m.put("importlib._abc", Pair.create(LanguageLevel.PYTHON310, null));
        m.put("importlib.metadata", Pair.create(LanguageLevel.PYTHON38, null)); // likely it is ignored now
        m.put("importlib.metadata._meta", Pair.create(LanguageLevel.PYTHON310, null)); // likely it is ignored now
        m.put("importlib.metadata.diagnose", Pair.create(LanguageLevel.PYTHON313, null));
        m.put("importlib.readers", Pair.create(LanguageLevel.PYTHON310, null));
        m.put("importlib.resources", Pair.create(LanguageLevel.PYTHON37, null)); // likely it is ignored now
        m.put("importlib.resources.abc", Pair.create(LanguageLevel.PYTHON311, null));
        m.put("importlib.resources.readers", Pair.create(LanguageLevel.PYTHON311, null));
        m.put("importlib.resources.simple", Pair.create(LanguageLevel.PYTHON311, null));
        m.put("importlib.simple", Pair.create(LanguageLevel.PYTHON311, null));
        m.put("lib2to3", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("mailcap", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("msilib", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("multiprocessing.resource_tracker", Pair.create(LanguageLevel.PYTHON38, null)); // likely it is ignored now
        m.put("multiprocessing.shared_memory", Pair.create(LanguageLevel.PYTHON38, null)); // likely it is ignored now
        m.put("nis", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("nntplib", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("ossaudiodev", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("parser", Pair.create(LanguageLevel.PYTHON27, LanguageLevel.PYTHON39));
        m.put("pipes", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("smtpd", Pair.create(LanguageLevel.PYTHON27, LanguageLevel.PYTHON311));
        m.put("sndhdr", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("spwd", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("sunau", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("symbol", Pair.create(LanguageLevel.PYTHON27, LanguageLevel.PYTHON39));
        m.put("sys._monitoring", Pair.create(LanguageLevel.PYTHON312, null));
        m.put("telnetlib", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("tkinter.tix", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("tomllib", Pair.create(LanguageLevel.PYTHON311, null));
        m.put("unittest._log", Pair.create(LanguageLevel.PYTHON39, null)); // likely it is ignored now
        m.put("unittest.async_case", Pair.create(LanguageLevel.PYTHON38, null)); // likely it is ignored now
        m.put("uu", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("wsgiref.types", Pair.create(LanguageLevel.PYTHON311, null)); // likely it is ignored now
        m.put("xdrlib", Pair.create(LanguageLevel.PYTHON30, LanguageLevel.PYTHON312));
        m.put("zipfile._path", Pair.create(LanguageLevel.PYTHON312, null));
        m.put("zoneinfo", Pair.create(LanguageLevel.PYTHON39, null));
        // Modified by script 2024-07-31 07:30:25
        stdlibNamesAvailableOnlyInSubsetOfSupportedLanguageLevels = Collections.unmodifiableMap(m);
    }

    // mapping of definitions that are in the incorrect place in typeshed
    private static final Map<String, String> typingRedirections;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("typing.Hashable", "_collections_abc.Hashable");
        m.put("typing.Awaitable", "_collections_abc.Awaitable");
        m.put("typing.Coroutine", "_collections_abc.Coroutine");
        m.put("typing.AsyncIterable", "_collections_abc.AsyncIterable");
        m.put("typing.AsyncIterator", "_collections_abc.AsyncIterator");
        m.put("typing.Iterable", "_collections_abc.Iterable");
        m.put("typing.Iterator", "_collections_abc.Iterator");
        m.put("typing.Reversible", "_collections_abc.Reversible");
        m.put("typing.Sized", "_collections_abc.Sized");
        m.put("typing.Container", "_collections_abc.Container");
        m.put("typing.Collection", "_collections_abc.Collection");
        m.put("typing.Callable", "_collections_abc.Callable");
        m.put("typing.AbstractSet", "_collections_abc.Set");
        m.put("typing.MutableSet", "_collections_abc.MutableSet");
        m.put("typing.Mapping", "_collections_abc.Mapping");
        m.put("typing.MutableMapping", "_collections_abc.MutableMapping");
        m.put("typing.Sequence", "_collections_abc.Sequence");
        m.put("typing.MutableSequence", "_collections_abc.MutableSequence");
        m.put("typing.ByteString", "_collections_abc.ByteString");
        m.put("typing.Deque", "_collections.deque");
        m.put("typing.MappingView", "_collections_abc.MappingView");
        m.put("typing.KeysView", "_collections_abc.KeysView");
        m.put("typing.ItemsView", "_collections_abc.ItemsView");
        m.put("typing.ValuesView", "_collections_abc.ValuesView");
        m.put("typing.DefaultDict", "_collections.defaultdict");
        m.put("typing.Generator", "_collections_abc.Generator");
        m.put("typing.AsyncGenerator", "_collections_abc.AsyncGenerator");
        typingRedirections = Collections.unmodifiableMap(m);
    }

    private static final Map<String, String> typeshedModuleRedirections = Map.of(
        "collections.abc", "_collections_abc"
    );

    /**
     * The actual typeshed directory.
     */
    private final Supplier<@Nullable VirtualFile> directory = LazyValue.nullable(() -> {
        String path = getDirectoryPath();
        if (path == null) {
            return null;
        }
        return StandardFileSystems.local().findFileByPath(path);
    });

    private final Supplier<@Nullable VirtualFile> thirdPartyStubRoot = LazyValue.nullable(() -> {
        VirtualFile dir = getDirectory();
        return dir != null ? dir.findChild("stubs") : null;
    });

    private final Supplier<@Nullable VirtualFile> stdlibRoot = LazyValue.nullable(() -> {
        VirtualFile dir = getDirectory();
        return dir != null ? dir.findChild("stdlib") : null;
    });

    private PyTypeShed() {
    }

    // mapping of definitions that are in the incorrect place in typeshed
    public Map<String, String> getTypingRedirections() {
        return typingRedirections;
    }

    public Map<String, String> getTypeshedModuleRedirections() {
        return typeshedModuleRedirections;
    }

    /**
     * Returns true if we allow to search typeshed for a stub for {@code name}.
     */
    public boolean maySearchForStubInRoot(QualifiedName name, VirtualFile root, LanguageLevel currentLanguageLevel) {
        if (isInStandardLibrary(root)) {
            String head = name.getFirstComponent();
            if (head == null) {
                return true;
            }
            Pair<LanguageLevel, @Nullable LanguageLevel> languageLevels = stdlibNamesAvailableOnlyInSubsetOfSupportedLanguageLevels.get(head);
            if (languageLevels == null) {
                return true;
            }
            LanguageLevel maxLanguageLevel = languageLevels.getSecond();
            return currentLanguageLevel.isAtLeast(languageLevels.getFirst()) &&
                (maxLanguageLevel == null || maxLanguageLevel.isAtLeast(currentLanguageLevel));
        }
        return isInThirdPartyLibraries(root);
    }

    /**
     * Returns the stdlib roots in Typeshed for the Python language level of {@code sdk}.
     * <p>
     * For Python 2, there are two entries: typeshed/stdlib and typeshed/stdlib/@python2.
     */
    public List<VirtualFile> findStdlibRootsForSdk(Sdk sdk) {
        LanguageLevel level = PythonRuntimeService.getInstance().getLanguageLevelForSdk(sdk);
        return findStdlibRootsForLanguageLevel(level);
    }

    /**
     * Returns the stdlib roots in Typeshed for the specified Python language {@code level}.
     * <p>
     * For Python 2, there are two entries: typeshed/stdlib and typeshed/stdlib/@python2.
     */
    public List<VirtualFile> findStdlibRootsForLanguageLevel(LanguageLevel level) {
        VirtualFile dir = getDirectory();
        if (dir == null) {
            return Collections.emptyList();
        }

        VirtualFile stdlib = stdlibRoot.get();
        if (stdlib == null) {
            return Collections.emptyList();
        }
        if (level.isPython2()) {
            List<VirtualFile> result = new ArrayList<>();
            VirtualFile python2 = stdlib.findChild("@python2");
            if (python2 != null) {
                result.add(python2);
            }
            result.add(stdlib);
            return result;
        }
        return List.of(stdlib);
    }

    /**
     * Returns both stdlib and third-party stub roots in Typeshed for the specified Python language {@code level}.
     */
    public List<VirtualFile> findAllRootsForLanguageLevel(LanguageLevel level) {
        // We no longer include Python 2 stubs for third-party packages, only for stdlib
        List<VirtualFile> result = new ArrayList<>(findStdlibRootsForLanguageLevel(level));
        VirtualFile thirdParty = getThirdPartyStubRoot();
        if (thirdParty != null) {
            Collections.addAll(result, thirdParty.getChildren());
        }
        return result;
    }

    /**
     * Checks if the {@code file} is located inside the typeshed directory.
     */
    public boolean isInside(VirtualFile file) {
        VirtualFile dir = getDirectory();
        return dir != null && VirtualFileUtil.isAncestor(dir, file, true);
    }

    /**
     * The actual typeshed directory.
     */
    public @Nullable VirtualFile getDirectory() {
        return directory.get();
    }

    public @Nullable VirtualFile getThirdPartyStubRoot() {
        return thirdPartyStubRoot.get();
    }

    private static @Nullable String getDirectoryPath() {
        List<String> paths = List.of(ContainerPathManager.get().getConfigPath() + "/typeshed",
            ContainerPathManager.get().getConfigPath() + "/../typeshed",
            PythonBundledPyiStubsLocator.findPath("typeshed").toAbsolutePath().toString());
        return paths.stream()
            .filter(it -> new File(it).exists())
            .findFirst()
            .orElse(null);
    }

    public boolean isInThirdPartyLibraries(VirtualFile file) {
        VirtualFile root = getThirdPartyStubRoot();
        return root != null && VirtualFileUtil.isAncestor(root, file, false);
    }

    public boolean isInStandardLibrary(VirtualFile file) {
        VirtualFile root = stdlibRoot.get();
        return root != null && VirtualFileUtil.isAncestor(root, file, false);
    }

    public boolean isInPartialThirdPartyStubPackage(PsiElement element) {
        VirtualFile file = null;
        if (element instanceof PsiFileSystemItem fileSystemItem) {
            file = fileSystemItem.getVirtualFile();
        }
        if (file == null) {
            PsiFile containingFile = element.getContainingFile();
            file = containingFile != null ? containingFile.getVirtualFile() : null;
        }
        if (file == null) {
            return false;
        }
        VirtualFile distributionRoot = getThirdPartyDistributionRoot(file);
        if (distributionRoot == null) {
            return false;
        }
        return isPartialThirdPartyStubPackage(element.getProject(), distributionRoot);
    }

    private @Nullable VirtualFile getThirdPartyDistributionRoot(VirtualFile file) {
        VirtualFile stubsRoot = getThirdPartyStubRoot();
        if (stubsRoot == null) {
            return null;
        }
        if (!VirtualFileUtil.isAncestor(stubsRoot, file, false)) {
            return null;
        }

        VirtualFile current = file.isDirectory() ? file : file.getParent();
        while (current != null && !stubsRoot.equals(current.getParent())) {
            current = current.getParent();
        }

        return current != null && stubsRoot.equals(current.getParent()) ? current : null;
    }

    /**
     * Find the directory containing .pyi stubs for the package {@code packageName} under `typeshed/stubs`.
     * <p>
     * {@code packageName} should match the name of the package on PyPI.
     */
    public @Nullable VirtualFile getStubRootForPackage(String packageName) {
        VirtualFile root = getThirdPartyStubRoot();
        return root != null ? root.findChild(packageName) : null;
    }

    private boolean isPartialThirdPartyStubPackage(Project project, VirtualFile distributionRoot) {
        PsiDirectory psiDirectory = PsiManager.getInstance(project).findDirectory(distributionRoot);
        if (psiDirectory == null) {
            return false;
        }
        VirtualFile metadataFile = distributionRoot.findChild(METADATA_TOML);
        return CachedValuesManager.getManager(project).getCachedValue(psiDirectory, PARTIAL_THIRD_PARTY_STUB_PACKAGE_KEY, () -> {
            // TODO consulo: TOML PSI (org.toml.lang.psi.TomlFile, consulo-toml) missing - metadataPsiFile, hasPartialStubMarker(), readPartialStubMarker()
            boolean isPartial = false;
            return CachedValueProvider.Result.create(
                isPartial,
                metadataFile != null ? metadataFile : psiDirectory,
                VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS
            );
        }, false);
    }
}
