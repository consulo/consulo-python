// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.python.impl.psi.types;

import com.google.common.collect.ImmutableSet;
import consulo.language.editor.completion.lookup.LookupElement;
import consulo.language.editor.completion.lookup.LookupElementBuilder;
import consulo.language.content.FileIndexFacade;
import consulo.util.lang.function.Condition;
import consulo.application.util.SystemInfo;
import consulo.util.io.FileUtil;
import consulo.virtualFileSystem.VirtualFile;
import consulo.language.psi.PsiDirectory;
import consulo.language.psi.PsiElement;
import consulo.language.psi.PsiFile;
import consulo.language.psi.PsiFileSystemItem;
import consulo.language.psi.PsiInvalidElementAccessException;
import consulo.language.psi.PsiNamedElement;
import consulo.language.psi.resolve.ResolveState;
import consulo.language.psi.resolve.PsiScopeProcessor;
import consulo.language.psi.util.PsiTreeUtil;
import consulo.language.psi.util.QualifiedName;
import consulo.util.lang.lazy.LazyValue;
import consulo.util.lang.ObjectUtil;
import consulo.language.util.ProcessingContext;
import java.util.function.Predicate;
import consulo.util.collection.ContainerUtil;
import com.jetbrains.python.PyNames;
import com.jetbrains.python.codeInsight.PyCustomMember;
import com.jetbrains.python.impl.codeInsight.completion.PyCompletionUtilsKt;
import com.jetbrains.python.codeInsight.controlflow.ScopeOwner;
import com.jetbrains.python.impl.codeInsight.dataflow.scope.ScopeUtil;
import com.jetbrains.python.impl.codeInsight.mlcompletion.PyCompletionMlElementInfo;
import com.jetbrains.python.impl.codeInsight.mlcompletion.PyCompletionMlElementKind;
import com.jetbrains.python.psi.AccessDirection;
import com.jetbrains.python.psi.PyExpression;
import com.jetbrains.python.psi.PyFile;
import com.jetbrains.python.psi.PyFromImportStatement;
import com.jetbrains.python.psi.PyImportElement;
import com.jetbrains.python.psi.PyImportStatement;
import com.jetbrains.python.psi.PyImportStatementBase;
import com.jetbrains.python.psi.PyQualifiedNameOwner;
import com.jetbrains.python.psi.PyStatement;
import com.jetbrains.python.impl.psi.PyUtil;
import com.jetbrains.python.impl.psi.impl.PyImportedModule;
import com.jetbrains.python.psi.impl.PyPsiUtils;
import com.jetbrains.python.impl.psi.impl.ResolveResultList;
import com.jetbrains.python.impl.psi.resolve.CompletionVariantsProcessor;
import com.jetbrains.python.psi.resolve.PointInImport;
import com.jetbrains.python.psi.resolve.PyResolveContext;
import com.jetbrains.python.impl.psi.resolve.PyResolveUtil;
import com.jetbrains.python.impl.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.resolve.RatedResolveResult;
import com.jetbrains.python.impl.psi.resolve.ResolveImportUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.jetbrains.python.psi.types.PyClassType;
import com.jetbrains.python.psi.types.PyModuleMembersProvider;
import com.jetbrains.python.psi.types.PyOverridingModuleMembersProvider;
import com.jetbrains.python.psi.types.PyType;
import com.jetbrains.python.psi.types.PyTypeVisitor;
import com.jetbrains.python.psi.types.TypeEvalContext;

import static com.jetbrains.python.impl.psi.PyUtil.inSameFile;


public class PyModuleType implements PyType { // Modules don't descend from object
    private final PyFile myModule;
    private final LazyValue<@Nullable QualifiedName> myQualifiedName;

    private static final ImmutableSet<String> MODULE_MEMBERS = ImmutableSet.of(
        "__name__", "__file__", "__path__", "__doc__", "__dict__", "__package__");

    public PyModuleType(PyFile source) {
        myModule = source;
        myQualifiedName = LazyValue.nullable(() -> QualifiedNameFinder.findShortestImportableQName(myModule));
    }

    @Override
    public String toString() {
        return "PyModuleType(" + myModule.getName() + ")";
    }

    public PyFile getModule() {
        return myModule;
    }

    public @Nullable PyClassType getModuleClassType() {
        return PyClassTypeImpl.createTypeByQName(myModule, "types.ModuleType", false);
    }

    @Override
    public @Nullable List<? extends RatedResolveResult> resolveMember(String name,
                                                                      @Nullable PyExpression location,
                                                                      AccessDirection direction,
                                                                      PyResolveContext resolveContext) {
        return resolveModuleMember(name, location, direction, resolveContext);
    }

    public @Nullable List<? extends RatedResolveResult> resolveModuleMember(String name,
                                                                            @Nullable PsiElement scopeOwner,
                                                                            AccessDirection direction,
                                                                            PyResolveContext resolveContext) {
        if (MODULE_MEMBERS.contains(name)) {
            var type = getModuleClassType();
            if (type != null) {
                PyExpression pyExpression = scopeOwner instanceof PyExpression expression ? expression : null;
                return type.resolveMember(name, pyExpression, direction, resolveContext);
            }
        }
        return resolveMemberInPackageOrModule(null, myModule, name, scopeOwner, resolveContext);
    }

    public static @Nullable List<? extends RatedResolveResult> resolveMemberInPackageOrModule(@Nullable PyImportedModule importedModule,
                                                                                              PsiFileSystemItem anchor,
                                                                                              String name,
                                                                                              @Nullable PsiElement location,
                                                                                              PyResolveContext resolveContext) {


        PyFile module = PyUtil.as(PyUtil.turnDirIntoInit(anchor), PyFile.class);
        if (module != null) {
            PsiElement overridingMember = resolveByOverridingMembersProviders(module, name, resolveContext);
            if (overridingMember != null) {
                return ResolveResultList.to(overridingMember);
            }

            List<RatedResolveResult> attributes = module.multiResolveName(name);
            if (!attributes.isEmpty()) {
                return attributes;
            }
        }


        if (PyUtil.isPackage(anchor, location)) {
            ResolveResultList implicitMembers = new ResolveResultList();
            processImplicitPackageMembers(anchor, location, importedModule, n -> name.endsWith(n), results -> {
                implicitMembers.addAll(convertDirsToInit(results));
                return implicitMembers.isEmpty();
            }, resolveContext);
            if (!implicitMembers.isEmpty()) {
                return implicitMembers;
            }
        }

        if (module != null) {
            PsiElement member = resolveByMembersProviders(module, name, resolveContext);
            if (member != null) {
                return ResolveResultList.to(member);
            }
        }
        return null;
    }

    private static void processImplicitPackageMembers(PsiFileSystemItem anchor,
                                                      @Nullable PsiElement location,
                                                      @Nullable PyImportedModule importedModule,
                                                      Predicate<String> filter,
                                                      Predicate<List<? extends RatedResolveResult>> resultProcessor,
                                                      @Nullable PyResolveContext resolveContext) {
        PsiElement realLocation = location == null ? null : PyPsiUtils.getRealContext(location);
        List<PyImportElement> importElements = new ArrayList<>();
        PyFile module = PyUtil.as(PyUtil.turnDirIntoInit(anchor), PyFile.class);
        if (anchor.getVirtualFile() == null) {
            return;
        }
        PsiElement footHold = realLocation != null ? realLocation.getContainingFile() : (module != null ? module : anchor);
        if (footHold == null) {
            return;
        }
        PyImportElement origImportElement = importedModule != null ? importedModule.getImportElement() : null;
        if (importedModule != null && (realLocation == null || !inSameFile(realLocation, importedModule))) {
            if (origImportElement != null) {
                importElements.add(origImportElement);
            }
        }
        else if (realLocation != null) {
            ScopeOwner owner = ScopeUtil.getScopeOwner(realLocation);
            if (owner != null) {
                boolean maySwitchToAst = resolveContext == null || resolveContext.getTypeEvalContext().maySwitchToAST(realLocation);
                if (maySwitchToAst) {
                    importElements.addAll(getVisibleImports(owner));
                }
                else if (owner instanceof PyFile pyFile) {
                    importElements.addAll(getTopImports(pyFile));
                }
            }
            else if (realLocation instanceof PyFile pyFile) {
                importElements.addAll(getTopImports(pyFile));
            }

            if (module != null) {
                if (!inSameFile(realLocation, module)) {
                    importElements.addAll(module.getImportTargets());
                }
                List<PyFromImportStatement> imports = module.getFromImports();
                for (PyFromImportStatement anImport : imports) {
                    Collections.addAll(importElements, anImport.getImportElements());
                }
            }
        }

        if (importElements.isEmpty()) {
            return;
        }

        Set<String> seen = new HashSet<>();
        if (!processImplicitlyImportedByImportElements(anchor, footHold,
                                                       importElements, name -> filter.test(name) && seen.add(name),
                                                       resultProcessor)) {
            return;
        }

        if (location != null) {
            processImplicitlyImportedByLocation(anchor, location,
                                                name -> filter.test(name) && seen.add(name),
                                                resultProcessor);
        }
    }

    private static boolean processImplicitlyImportedByImportElements(PsiFileSystemItem anchor,
                                                                     PsiElement footHold,
                                                                     List<PyImportElement> importElements,
                                                                     Predicate<String> filter,
                                                                     Predicate<List<? extends RatedResolveResult>> resultProcessor) {
        PyFile module = PyUtil.as(PyUtil.turnDirIntoInit(anchor), PyFile.class);
        List<QualifiedName> packageQNames = QualifiedNameFinder.findImportableQNames(footHold, anchor.getVirtualFile());
        for (PyImportElement importElement : importElements) {
            for (QualifiedName packageQName : packageQNames) {
                for (QualifiedName importedQName : getImportedQNames(importElement)) {
                    String directChild = findFirstComponentAfterPrefix(importedQName, packageQName);
                    if (directChild != null && filter.test(directChild)) {
                        List<RatedResolveResult> results =
                            ResolveImportUtil.resolveChildren(anchor, directChild, module, true, true, false, false);
                        if (!resultProcessor.test(ResolveResultList.asImportedResults(results, importElement))) {
                            return false;
                        }
                    }
                }
            }
        }
        return true;
    }

    private static void processImplicitlyImportedByLocation(PsiFileSystemItem anchor,
                                                            PsiElement location,
                                                            Predicate<String> filter,
                                                            Predicate<List<? extends RatedResolveResult>> resultProcessor) {

        if (location.getContainingFile().getVirtualFile() == null) {
            return;
        }
        ScopeOwner owner = location instanceof ScopeOwner ? (ScopeOwner)location : ScopeUtil.getScopeOwner(location);
        if (owner == null) {
            return;
        }

        List<PyImportElement> visibleImports = getVisibleImports(owner);
        PyFile module = PyUtil.as(PyUtil.turnDirIntoInit(anchor), PyFile.class);
        List<QualifiedName> packageQNames =
            QualifiedNameFinder.findImportableQNames(location.getContainingFile(), anchor.getVirtualFile());
        List<QualifiedName> locationQNames =
            QualifiedNameFinder.findImportableQNames(location, location.getContainingFile().getVirtualFile());
        for (QualifiedName locationQName : locationQNames) {
            for (QualifiedName packageQName : packageQNames) {
                String directChild = findFirstComponentAfterPrefix(locationQName, packageQName);
                if (directChild != null && filter.test(directChild)) {
                    QualifiedName mainPackage = QualifiedName.fromComponents(locationQName.getFirstComponent());
                    PyImportElement packageImportElement =
                        ContainerUtil.find(visibleImports,
                                           el -> ContainerUtil.exists(getImportedQNames(el), qName -> qName.matchesPrefix(mainPackage)));

                    if (packageImportElement != null) {
                        List<RatedResolveResult> results =
                            ResolveImportUtil.resolveChildren(anchor, directChild, module, true, true, false, false);
                        if (!resultProcessor.test(ResolveResultList.asImportedResults(results, packageImportElement))) {
                            return;
                        }
                    }
                }
            }
        }
    }

    private static @Nullable String findFirstComponentAfterPrefix(QualifiedName qualifiedName, QualifiedName prefix) {
        if (qualifiedName.matchesPrefix(prefix) && qualifiedName.getComponentCount() > prefix.getComponentCount()) {
            return qualifiedName.removeHead(prefix.getComponentCount()).getFirstComponent();
        }
        else {
            return null;
        }
    }

    private static List<? extends RatedResolveResult> convertDirsToInit(List<? extends RatedResolveResult> ratedResolveList) {
        return ContainerUtil.map(ratedResolveList, result -> {
            PsiElement element = result.getElement();
            if (element instanceof PsiDirectory) {
                PsiElement pkgInit = PyUtil.turnDirIntoInit(element);
                return pkgInit != null ? result.replace(pkgInit) : result;
            }
            else {
                return result;
            }
        });
    }

    private static @Nullable PsiElement resolveByMembersProviders(PyFile module, String name, PyResolveContext resolveContext) {
        for (PyModuleMembersProvider provider : PyModuleMembersProvider.EP_NAME.getExtensionList()) {
            if (!(provider instanceof PyOverridingModuleMembersProvider)) {
                PsiElement element = provider.resolveMember(module, name, resolveContext);
                if (element != null) {
                    return element;
                }
            }
        }
        return null;
    }

    private static @Nullable PsiElement resolveByOverridingMembersProviders(PyFile module,
                                                                            String name,
                                                                            PyResolveContext resolveContext) {
        for (PyModuleMembersProvider provider : PyModuleMembersProvider.EP_NAME.getExtensionList()) {
            if (provider instanceof PyOverridingModuleMembersProvider) {
                PsiElement element = provider.resolveMember(module, name, resolveContext);
                if (element != null) {
                    return element;
                }
            }
        }
        return null;
    }


    private static List<QualifiedName> getImportedQNames(PyImportElement element) {
        List<QualifiedName> importedQNames = new ArrayList<>();
        PyStatement stmt = element.getContainingImportStatement();
        PyFromImportStatement fromImportStatement = ObjectUtil.tryCast(stmt, PyFromImportStatement.class);
        if (fromImportStatement != null) {
            QualifiedName importedQName = fromImportStatement.getImportSourceQName();
            String visibleName = element.getVisibleName();
            if (importedQName != null) {
                importedQNames.add(importedQName);
                if (visibleName != null) {
                    importedQNames.add(importedQName.append(visibleName));
                }
            }
            else {
                List<PsiElement> elements =
                    ResolveImportUtil.resolveFromImportStatementSource(fromImportStatement, element.getImportedQName());
                for (PsiElement psiElement : elements) {
                    if (psiElement instanceof PsiFile) {
                        VirtualFile virtualFile = ((PsiFile)psiElement).getVirtualFile();
                        QualifiedName qName = QualifiedNameFinder.findShortestImportableQName(element, virtualFile);
                        if (qName != null) {
                            importedQNames.add(qName);
                        }
                    }
                }
            }
        }
        else if (stmt instanceof PyImportStatement) {
            QualifiedName importedQName = element.getImportedQName();
            if (importedQName != null) {
                importedQNames.add(importedQName);
            }
        }
        if (!ResolveImportUtil.isAbsoluteImportEnabledFor(element) ||
            (fromImportStatement != null && fromImportStatement.getRelativeLevel() == 1)) {
            PsiFile file = element.getContainingFile();
            if (file != null) {
                file = file.getOriginalFile();
            }
            QualifiedName absoluteQName = QualifiedNameFinder.findShortestImportableQName(file);
            if (file != null && absoluteQName != null) {
                QualifiedName prefixQName = PyUtil.isPackage(file) ? absoluteQName : absoluteQName.removeLastComponent();
                if (prefixQName.getComponentCount() > 0) {
                    List<QualifiedName> results = new ArrayList<>(importedQNames);
                    for (QualifiedName qName : importedQNames) {
                        List<String> components = new ArrayList<>();
                        components.addAll(prefixQName.getComponents());
                        components.addAll(qName.getComponents());
                        results.add(QualifiedName.fromComponents(components));
                    }
                    return results;
                }
            }
        }
        return importedQNames;
    }

    private static List<PyImportElement> getTopImports(PyFile file) {
        List<PyImportElement> importElements = new ArrayList<>(file.getImportTargets());
        for (PyFromImportStatement fromImportStatement : file.getFromImports()) {
            importElements.addAll(Arrays.asList(fromImportStatement.getImportElements()));
        }
        return importElements;
    }

    private static List<PyImportElement> getVisibleImports(ScopeOwner owner) {
        List<PyImportElement> visibleImports = new ArrayList<>();
        PyResolveUtil.scopeCrawlUp(new PsiScopeProcessor() {
            @Override
            public boolean execute(PsiElement element, ResolveState state) {
                if (element instanceof PyImportElement) {
                    visibleImports.add((PyImportElement)element);
                }
                return true;
            }
        }, owner, null, null);
        return visibleImports;
    }

    /**
     * @param directory the module directory
     * @return a list of submodules of the specified module directory, either files or dirs, for easier naming; may contain file names
     * not suitable for import.
     */
    private static List<PsiFileSystemItem> getSubmodulesList(PsiDirectory directory, @Nullable PsiElement anchor) {
        List<PsiFileSystemItem> result = new ArrayList<>();

        // file modules
        for (PsiFile f : directory.getFiles()) {
            String filename = f.getName();
            // if we have a binary module, we'll most likely also have a stub for it in site-packages
            if (!isExcluded(f) && (f instanceof PyFile && !filename.equals(PyNames.INIT_DOT_PY)) || isBinaryModule(filename)) {
                result.add(f);
            }
        }
        // dir modules
        for (PsiDirectory dir : directory.getSubdirectories()) {
            if (!isExcluded(dir) && PyUtil.isPackage(dir, anchor)) {
                result.add(dir);
            }
        }
        return result;
    }

    private static boolean isExcluded(PsiFileSystemItem file) {
        return FileIndexFacade.getInstance(file.getProject()).isExcludedFile(file.getVirtualFile());
    }

    private static boolean isBinaryModule(String filename) {
        String ext = FileUtil.getExtension(filename);
        if (SystemInfo.isWindows) {
            return "pyd".equalsIgnoreCase(ext);
        }
        else {
            return "so".equals(ext);
        }
    }

    @Override
    public Object[] getCompletionVariants(String completionPrefix, PsiElement location, ProcessingContext context) {
        TypeEvalContext typeEvalContext = TypeEvalContext.codeCompletion(location.getProject(), location.getContainingFile());
        List<LookupElement> result = getCompletionVariantsAsLookupElements(location, context, false, false, typeEvalContext);
        return result.toArray();
    }

    public List<LookupElement> getCompletionVariantsAsLookupElements(PsiElement location,
                                                                              ProcessingContext context,
                                                                              boolean wantAllSubmodules,
                                                                              boolean suppressParentheses,
                                                                              TypeEvalContext typeEvalContext) {
        List<LookupElement> result = new ArrayList<>();

        Set<String> namesAlready = context.get(CTX_NAMES);
        PointInImport point = ResolveImportUtil.getPointInImport(location);
        PyResolveContext resolveContext = PyResolveContext.defaultContext(typeEvalContext);

        for (PyModuleMembersProvider provider : PyModuleMembersProvider.EP_NAME.getExtensionList()) {
            for (PyCustomMember member : provider.getMembers(myModule, point, typeEvalContext)) {
                String name = member.getName();
                if (namesAlready != null && !namesAlready.add(name)) {
                    continue;
                }
                if (PyUtil.isClassPrivateName(name)) {
                    continue;
                }
                CompletionVariantsProcessor processor = createCompletionVariantsProcessor(location, suppressParentheses, point);
                PsiElement resolved = member.resolve(location, resolveContext);
                if (resolved != null) {
                    processor.execute(resolved, ResolveState.initial());
                    List<LookupElement> lookupList = processor.getResultList();
                    if (!lookupList.isEmpty()) {
                        LookupElement element = lookupList.get(0);
                        if (name.equals(element.getLookupString())) {
                            result.add(element);
                            continue;
                        }
                    }
                }
                result.add(LookupElementBuilder.create(name).withIcon(member.getIcon()).withTypeText(member.getShortType()));
            }
        }
        if (point == PointInImport.NONE || point == PointInImport.AS_NAME) { // when not imported from, add regular attributes
            CompletionVariantsProcessor processor = createCompletionVariantsProcessor(location, suppressParentheses, point);
            myModule.processDeclarations(processor, ResolveState.initial(), null, location);
            if (namesAlready != null) {
                for (LookupElement le : processor.getResultList()) {
                    String name = le.getLookupString();
                    if (!namesAlready.contains(name)) {
                        result.add(le);
                        namesAlready.add(name);
                    }
                }
            }
            else {
                result.addAll(processor.getResultList());
            }
        }
        if (PyUtil.isPackage(myModule)) { // our module is a dir, not a single file
            if (point == PointInImport.AS_MODULE ||
                point == PointInImport.AS_NAME ||
                wantAllSubmodules) { // when imported from somehow, add submodules
                result.addAll(getSubModuleVariants(myModule.getContainingDirectory(), location, namesAlready));
            }
            else {
                result.addAll(collectImportedSubmodulesAsLookupElements(myModule, location, namesAlready));
            }
        }
        return result;
    }

    private static CompletionVariantsProcessor createCompletionVariantsProcessor(PsiElement location,
                                                                                          boolean suppressParentheses,
                                                                                          PointInImport point) {
        Condition<PsiElement> nodeFilter =
            psiElement -> !(psiElement instanceof PyImportElement) ||
                          PsiTreeUtil.getParentOfType(psiElement, PyImportStatementBase.class) instanceof PyFromImportStatement;

        return new CompletionVariantsProcessor(location,
                                               nodeFilter,
                                               null,
                                               point == PointInImport.AS_NAME, // no parens after imported function names
                                               suppressParentheses);
    }

    public static List<LookupElement> collectImportedSubmodulesAsLookupElements(PsiFileSystemItem pyPackage,
                                                                                         PsiElement location,
                                                                                         @Nullable Set<? super String> existingNames) {


        List<PsiElement> elements = new ArrayList<>();
        processImplicitPackageMembers(pyPackage, location, null, any -> true, results -> {

            elements.addAll(ResolveResultList.getElements(results));
            return true;
        }, null);
        return ContainerUtil.mapNotNull(elements,
                                        element -> {
                                          if (element instanceof PsiFileSystemItem) {
                                            return buildFileLookupElement(location.getContainingFile(), (PsiFileSystemItem)element,
                                                                          existingNames);
                                          }
                                          else if (element instanceof PsiNamedElement) {
                                            return LookupElementBuilder.createWithIcon((PsiNamedElement)element);
                                          }
                                          return null;
                                        });
    }


    public static List<LookupElement> getSubModuleVariants(@Nullable PsiDirectory directory,
                                                                    PsiElement location,
                                                                    @Nullable Set<? super String> namesAlready) {
        if (directory == null) {
            return Collections.emptyList();
        }
        List<LookupElement> result = new ArrayList<>();
        List<PsiFileSystemItem> items = getSubmodulesList(directory, location);
        PsiDirectory skeletonsDir = ResolveImportUtil.findCorrespondingSkeletonsDir(directory);
        if (skeletonsDir != null) {
            items = ContainerUtil.concat(items, getSubmodulesList(skeletonsDir, location));
        }
        for (PsiFileSystemItem item : items) {
            if (item != location.getContainingFile().getOriginalFile()) {
                LookupElement lookupElement = buildFileLookupElement(location.getContainingFile(), item, namesAlready);
                if (lookupElement != null) {
                    lookupElement.putUserData(PyCompletionMlElementInfo.getKey(), PyCompletionMlElementKind.PACKAGE_OR_MODULE.asInfo());
                    result.add(lookupElement);
                }
            }
        }
        return result;
    }

    public static @Nullable LookupElementBuilder buildFileLookupElement(PsiFile file,
                                                                        PsiFileSystemItem item,
                                                                        @Nullable Set<? super String> existingNames) {
        String s = FileUtil.getNameWithoutExtension(item.getName());
        if (!PyNames.isIdentifier(s)) {
            return null;
        }
        if (existingNames != null) {
            if (existingNames.contains(s)) {
                return null;
            }
            else {
                existingNames.add(s);
            }
        }
        return PyCompletionUtilsKt.createLookupElementBuilder(file, item);
    }

    @Override
    public String getName() {
        QualifiedName qualifiedName = myQualifiedName.get();
        return qualifiedName != null ? qualifiedName.toString() : "";
    }

    @Override
    public boolean isBuiltin() {
        return true;
    }

    @Override
    public void assertValid(String message) {
        if (!myModule.isValid()) {
            throw new PsiInvalidElementAccessException(myModule, myModule.getClass() + ": " + message);
        }
    }

    public static Set<String> getPossibleInstanceMembers() {
        return MODULE_MEMBERS;
    }

    @Override
    public @Nullable PyQualifiedNameOwner getDeclarationElement() {
        return ObjectUtil.doIfNotNull(getModuleClassType(), PyClassType::getPyClass);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PyModuleType type)) {
            return false;
        }
        return myModule.equals(type.myModule);
    }

    @Override
    public int hashCode() {
        return Objects.hash(myModule);
    }

    @Override
    public <T> T acceptTypeVisitor(PyTypeVisitor<T> visitor) {
        if (visitor instanceof PyTypeVisitorExt<T> visitorExt) {
            return visitorExt.visitPyModuleType(this);
        }
        return visitor.visitPyType(this);
    }
}
